package com.swiftdrop.webhook.delivery;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
@Configuration
@Profile("local-webhooks")
@EnableScheduling
@ConditionalOnProperty(name="webhooks.worker.enabled",havingValue="true",matchIfMissing=true)
public class DeliveryWorker {
 private final DeliveryWorkflow workflow;
 public DeliveryWorker(DeliveryWorkflow workflow){this.workflow=workflow;}
 @Scheduled(fixedDelayString="${webhooks.worker-delay-ms:1000}")
 public void deliver(){workflow.tick();}
}
