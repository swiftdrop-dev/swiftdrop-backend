package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.entity.OutboxEvent;
import com.swiftdrop.dispatch.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventService {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishEvent(String aggregateType, String aggregateId, String eventType, String payloadJson) {
        OutboxEvent event = new OutboxEvent(aggregateType, aggregateId, eventType, payloadJson);
        outboxEventRepository.save(event);
        log.info("Saved dispatch outbox event [type={}, aggregateId={}]", eventType, aggregateId);
    }
}
