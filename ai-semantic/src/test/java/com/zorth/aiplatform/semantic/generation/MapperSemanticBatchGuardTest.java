package com.zorth.aiplatform.semantic.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zorth.aiplatform.semantic.exception.SemanticGenerationAlreadyRunningException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MapperSemanticBatchGuardTest {

    @Test
    void rejectsConcurrentRunAndReleasesAfterFailure() throws Exception {
        MapperSemanticBatchGuard guard = new MapperSemanticBatchGuard();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger concurrentRejections = new AtomicInteger();
        Thread first = new Thread(() -> guard.run(() -> {
            started.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("batch failed");
        }));
        first.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertThrows(SemanticGenerationAlreadyRunningException.class, () -> guard.run(() -> "nope"));
        concurrentRejections.incrementAndGet();
        release.countDown();
        first.join(5_000);
        assertEquals("ok", guard.run(() -> "ok"));
        assertEquals(1, concurrentRejections.get());
    }

    @Test
    void releasesAfterSuccess() {
        MapperSemanticBatchGuard guard = new MapperSemanticBatchGuard();
        assertEquals(1, guard.run(() -> 1));
        assertEquals(2, guard.run(() -> 2));
    }
}
