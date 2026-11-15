package com.swiftdrop.gateway.service;

import com.swiftdrop.gateway.exception.RateLimitExceededException;
import com.swiftdrop.gateway.service.impl.ChatbotServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotServiceImplSecurityTest {

    @Test
    void parcelSpecificQueriesFailClosedWithoutCallingGemini() {
        GeminiService geminiService = mock(GeminiService.class);
        ChatbotServiceImpl service = new ChatbotServiceImpl(geminiService);
        ReflectionTestUtils.setField(service, "freeLimit", 4);

        var response = service.processMessage("jwt-subject:someone@example.test", "Where is my parcel SWF-2024-001234?");

        assertThat(response.getReply()).contains("not available through chat");
        verify(geminiService, never()).generateResponse(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void concurrentMessagesCannotExceedInMemoryPerKeyLimit() throws Exception {
        GeminiService geminiService = mock(GeminiService.class);
        when(geminiService.generateResponse(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        ChatbotServiceImpl service = new ChatbotServiceImpl(geminiService);
        ReflectionTestUtils.setField(service, "freeLimit", 4);

        int calls = 20;
        CountDownLatch ready = new CountDownLatch(calls);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger limited = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(calls)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timed out");
                    try {
                        service.processMessage("remote-ip:test-client", "Hello SwiftBot");
                        succeeded.incrementAndGet();
                    } catch (RateLimitExceededException expected) {
                        limited.incrementAndGet();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
        }

        assertThat(succeeded.get()).isEqualTo(4);
        assertThat(limited.get()).isEqualTo(calls - 4);
        assertThat(service.getUsage("remote-ip:test-client").getUsedCount()).isEqualTo(4);
    }
}
