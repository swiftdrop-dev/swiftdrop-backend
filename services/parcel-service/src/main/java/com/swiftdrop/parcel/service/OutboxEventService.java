package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.OutboxEvent;
import com.swiftdrop.parcel.repository.OutboxEventRepository;
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
    public OutboxEvent saveEvent(String aggregateType, String aggregateId, String eventType, String payloadJson) {
        OutboxEvent event = new OutboxEvent(aggregateType, aggregateId, eventType, payloadJson);
        OutboxEvent saved = outboxEventRepository.save(event);
        log.info("Saved parcel outbox event [type={}, aggregateId={}]", eventType, aggregateId);
        return saved;
    }
}
