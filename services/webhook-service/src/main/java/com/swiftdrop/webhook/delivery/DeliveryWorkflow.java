package com.swiftdrop.webhook.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

/** Durable work queue. Outbound delivery is at-least-once; eventId remains stable on retries/replay. */
@Service
@Profile("local-webhooks")
public class DeliveryWorkflow implements StatusEventHandler {
 private final JdbcTemplate db;
 private final TransactionTemplate tx;
 private final ObjectMapper json;
 private final String merchant,secret,receiver;
 private final com.swiftdrop.webhook.security.MerchantIdentity identity;
 private final long backoff;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 public DeliveryWorkflow(JdbcTemplate db, PlatformTransactionManager manager, ObjectMapper json,
   @Value("${webhooks.local.merchant-id}") String merchant,
   @Value("${webhooks.local.signing-secret:}") String secret,
   @Value("${webhooks.local.receiver-url}") String receiver,
   @Value("${webhooks.retry-base-seconds:5}") long backoff,
   com.swiftdrop.webhook.security.MerchantIdentity identity) {
  this.identity=identity;
  this.db=db;this.tx=new TransactionTemplate(manager);this.json=json;this.merchant=merchant;this.secret=secret;this.receiver=receiver;this.backoff=Math.max(1,backoff);
 }
 public record Accepted(UUID id,String status) {}
 public Accepted sample(UUID subscription) {
  String merchant=identity.currentMerchantId();
  var rows=db.queryForList("SELECT callback_url,is_active FROM webhooks_dev.subscriptions WHERE id=? AND merchant_id=?",subscription,merchant);
  if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  if(!Boolean.TRUE.equals(rows.get(0).get("is_active"))) throw new ResponseStatusException(HttpStatus.CONFLICT);
  if(!receiver.equals(rows.get(0).get("callback_url"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(secret.length()<32) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
  String event=UUID.randomUUID().toString();
  try {
   String payload=json.writeValueAsString(Map.of("eventId",event,"event","parcel.status.changed","merchantId",merchant,"trackingNumber","SD-LOCAL-TEST","merchantOrderRef","LOCAL-TEST-ORDER","status","DELIVERED","timestamp",Instant.now().toString(),"test",true));
   return new Accepted(enqueue(subscription,event,receiver,payload),"PENDING");
  } catch(java.io.IOException ex){throw new IllegalStateException("Unable to encode test event");}
 }
 public void ingest(String payload) {
  try {
   if(payload==null || payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16384) throw new IllegalArgumentException("Event too large");
   JsonNode node=json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(payload);
   if(node==null || !node.isObject()) throw new IllegalArgumentException("Invalid status event");
   for(String field:List.of("eventId","merchantId","event","trackingNumber","status")) {
    if(!node.path(field).isTextual()) throw new IllegalArgumentException("Invalid status event field");
   }
   String event=node.path("eventId").asText(),owner=node.path("merchantId").asText();
   if(event.isBlank()||event.length()>100||owner.isBlank()||owner.length()>100||!"parcel.status.changed".equals(node.path("event").asText())
    ||node.path("trackingNumber").asText().isBlank()||!Set.of("CREATED","PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","FAILED").contains(node.path("status").asText())) throw new IllegalArgumentException("Invalid status event");
   // Local integration consumes only the explicitly configured test merchant.
   if(!merchant.equals(owner)) return;
   db.query("SELECT id,callback_url FROM webhooks_dev.subscriptions WHERE merchant_id=? AND is_active=true",(rs,n)->Map.entry(rs.getObject("id",UUID.class),rs.getString("callback_url")),owner)
    .forEach(sub->enqueue(sub.getKey(),event,sub.getValue(),payload));
  }catch(java.io.IOException ex){throw new IllegalArgumentException("Invalid JSON event");}
 }
 private UUID enqueue(UUID subscription,String event,String url,String payload) {
  UUID id=UUID.randomUUID();
  try {db.update("INSERT INTO webhooks_dev.deliveries(id,subscription_id,event_id,callback_url,payload,status) VALUES(?,?,?,?,?,'PENDING')",id,subscription,event,url,payload);return id;}
  catch(DuplicateKeyException ex){return db.queryForObject("SELECT id FROM webhooks_dev.deliveries WHERE subscription_id=? AND event_id=?",UUID.class,subscription,event);}
 }
 public void replay(UUID id) {
  String merchant=identity.currentMerchantId();
  var owned=db.queryForList("SELECT d.status,s.is_active FROM webhooks_dev.deliveries d JOIN webhooks_dev.subscriptions s ON s.id=d.subscription_id WHERE d.id=? AND s.merchant_id=?",id,merchant);
  if(owned.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  if(!Boolean.TRUE.equals(owned.get(0).get("is_active")))throw new ResponseStatusException(HttpStatus.CONFLICT);
  int changed=db.update("UPDATE webhooks_dev.deliveries SET status='PENDING',attempts=0,replay_count=replay_count+1,next_attempt_at=CURRENT_TIMESTAMP,completed_at=NULL,response_code=NULL,failure_reason=NULL,lease_until=NULL WHERE id=? AND status='DEAD_LETTER' AND EXISTS (SELECT 1 FROM webhooks_dev.subscriptions s WHERE s.id=subscription_id AND s.merchant_id=? AND s.is_active=true)",id,merchant);
  if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT);
 }
 public void tick() {
  db.update("UPDATE webhooks_dev.deliveries SET status='RETRYING',lease_until=NULL WHERE status='PROCESSING' AND lease_until<CURRENT_TIMESTAMP");
  List<UUID> due=db.query("SELECT id FROM webhooks_dev.deliveries WHERE status IN ('PENDING','RETRYING') AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 10",(rs,n)->rs.getObject(1,UUID.class));
  for(UUID id:due) process(id);
 }
 private void process(UUID id) {
  OffsetDateTime lease=OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  if(db.update("UPDATE webhooks_dev.deliveries SET status='PROCESSING',lease_until=? WHERE id=? AND status IN ('PENDING','RETRYING') AND next_attempt_at<=CURRENT_TIMESTAMP",lease,id)!=1)return;
  var row=db.queryForMap("SELECT d.*,s.is_active FROM webhooks_dev.deliveries d JOIN webhooks_dev.subscriptions s ON s.id=d.subscription_id WHERE d.id=?",id);
  int attempt=((Number)row.get("attempts")).intValue()+1,replay=((Number)row.get("replay_count")).intValue();
  Integer code=null;String failure=null;
  try {
   if(!Boolean.TRUE.equals(row.get("is_active")))failure="Subscription disabled";
   else if(!receiver.equals(row.get("callback_url")))failure="Destination is not the allowlisted local receiver";
   else if(secret.length()<32)failure="Local signing secret not configured";
   else {
    String payload=(String)row.get("payload");
    var req=HttpRequest.newBuilder(URI.create(receiver)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json")
      .header("X-SwiftDrop-Event-Id",(String)row.get("event_id"))
      .header("X-SwiftDrop-Signature","sha256="+LocalTestDeliveryService.sign(payload,secret))
      .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
    code=http.send(req,HttpResponse.BodyHandlers.discarding()).statusCode();
    if(code<200||code>=300)failure="Receiver returned a non-success status";
   }
  }catch(InterruptedException ex){Thread.currentThread().interrupt();failure="Delivery interrupted";}
  catch(Exception ex){failure="Receiver unavailable or timed out";}
  final Integer response=code;final String reason=failure;
  boolean success=failure==null;
  String status=success?"SUCCESS":attempt>=3?"DEAD_LETTER":"RETRYING";
  OffsetDateTime next=OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(backoff*(1L<<Math.min(attempt-1,10)));
  tx.executeWithoutResult(transaction->{
   // Fencing prevents an expired worker from overwriting a newer claim.
   int changed=db.update("UPDATE webhooks_dev.deliveries SET status=?,attempts=?,response_code=?,failure_reason=?,next_attempt_at=?,completed_at=?,lease_until=NULL WHERE id=? AND status='PROCESSING' AND lease_until=?",
    status,attempt,response,reason,next,(success||attempt>=3)?OffsetDateTime.now(ZoneOffset.UTC):null,id,lease);
   if(changed==1){
    db.update("INSERT INTO webhooks_dev.delivery_attempts(id,delivery_id,attempt_number,replay_number,response_code,failure_reason) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),id,attempt,replay,response,reason);
    if("DEAD_LETTER".equals(status))db.update("INSERT INTO webhooks_dev.dead_letter_outbox(id,delivery_id) VALUES(?,?)",UUID.randomUUID(),id);
   }
  });
 }
}
