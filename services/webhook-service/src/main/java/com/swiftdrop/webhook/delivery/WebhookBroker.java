package com.swiftdrop.webhook.delivery;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.TimeUnit;

@Configuration
@Profile("local-webhooks")
@ConditionalOnProperty(name="webhooks.broker.enabled",havingValue="true")
public class WebhookBroker {
 private final RabbitTemplate rabbit;private final JdbcTemplate db;private final StatusEventHandler workflow;
 @Value("${webhooks.broker.dead-exchange:swiftdrop.webhooks.local.dead}") private String deadExchange;
 public WebhookBroker(RabbitTemplate rabbit,JdbcTemplate db,StatusEventHandler workflow){this.rabbit=rabbit;this.db=db;this.workflow=workflow;rabbit.setMandatory(true);}
 @Bean Declarables webhookTopology(@Value("${webhooks.broker.exchange:swiftdrop.events.local}") String exchange,
   @Value("${webhooks.broker.queue:swiftdrop.webhooks.local}") String queue,
   @Value("${webhooks.broker.dead-exchange:swiftdrop.webhooks.local.dead}") String dead) {
  TopicExchange events=new TopicExchange(exchange,true,false);DirectExchange dlx=new DirectExchange(dead,true,false);
  Queue incoming=QueueBuilder.durable(queue).deadLetterExchange(dead).deadLetterRoutingKey("dead").build();
  Queue dlq=QueueBuilder.durable(queue+".dlq").build();
  return new Declarables(events,dlx,incoming,dlq,BindingBuilder.bind(incoming).to(events).with("parcel.status.changed"),BindingBuilder.bind(dlq).to(dlx).with("dead"));
 }
 @RabbitListener(queues="${webhooks.broker.queue:swiftdrop.webhooks.local}")
 public void consume(Message message){
  try{workflow.ingest(new String(message.getBody(),StandardCharsets.UTF_8));}
  catch(IllegalArgumentException ex){throw new AmqpRejectAndDontRequeueException("Invalid webhook event");}
 }
 @Scheduled(fixedDelay=5000)
 public void publishDeadLetters(){
  var pending=db.queryForList("SELECT o.id,o.delivery_id,d.event_id FROM webhooks_dev.dead_letter_outbox o JOIN webhooks_dev.deliveries d ON d.id=o.delivery_id WHERE o.published=false ORDER BY o.created_at LIMIT 20");
  for(var row:pending){
   CorrelationData confirmation=new CorrelationData(row.get("id").toString());
   String body="{\"deliveryId\":\""+row.get("delivery_id")+"\",\"status\":\"DEAD_LETTER\"}";
   try{
    rabbit.send(deadExchange,"dead",MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).setContentType("application/json").setMessageId(row.get("id").toString()).setDeliveryMode(MessageDeliveryMode.PERSISTENT).build(),confirmation);
    if(confirmation.getFuture().get(5,TimeUnit.SECONDS).isAck()&&confirmation.getReturned()==null)
     db.update("UPDATE webhooks_dev.dead_letter_outbox SET published=true WHERE id=?",row.get("id"));
   }catch(Exception ex){if(ex instanceof InterruptedException)Thread.currentThread().interrupt();return;}
  }
 }
}
