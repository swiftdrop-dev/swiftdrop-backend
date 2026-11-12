package com.swiftdrop.webhook.delivery;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local-webhooks")
@RequestMapping("/api/v1/merchant/webhooks")
public class LocalTestDeliveryController {
    private final LocalTestDeliveryService deliveries;
    private final DeliveryWorkflow workflow;
    public LocalTestDeliveryController(LocalTestDeliveryService deliveries, DeliveryWorkflow workflow) { this.deliveries = deliveries; this.workflow=workflow; }

    @PostMapping("/{id}/events")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public DeliveryWorkflow.Accepted enqueue(@PathVariable UUID id) { return workflow.sample(id); }

    @PostMapping("/deliveries/{id}/replay")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public java.util.Map<String,String> replay(@PathVariable UUID id) { workflow.replay(id); return java.util.Map.of("status","PENDING"); }

    @PostMapping("/{id}/test")
    public LocalTestDeliveryService.Result send(@PathVariable UUID id) { return deliveries.send(id); }
}
