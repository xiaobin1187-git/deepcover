package io.deepcover.agent.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class MetricsCollectorTest {

    @Test
    public void testTotalRequestsCounter() {
        long before = MetricsCollector.totalRequests.get();
        MetricsCollector.totalRequests.incrementAndGet();
        assertEquals(before + 1, MetricsCollector.totalRequests.get());
    }

    @Test
    public void testCollectedRequestsCounter() {
        long before = MetricsCollector.collectedRequests.get();
        MetricsCollector.collectedRequests.incrementAndGet();
        assertEquals(before + 1, MetricsCollector.collectedRequests.get());
    }

    @Test
    public void testDroppedRequestsCounter() {
        long before = MetricsCollector.droppedRequests.get();
        MetricsCollector.droppedRequests.incrementAndGet();
        assertEquals(before + 1, MetricsCollector.droppedRequests.get());
    }

    @Test
    public void testSendSuccessCounter() {
        long before = MetricsCollector.sendSuccess.get();
        MetricsCollector.sendSuccess.addAndGet(5);
        assertEquals(before + 5, MetricsCollector.sendSuccess.get());
    }

    @Test
    public void testSendFailedCounter() {
        long before = MetricsCollector.sendFailed.get();
        MetricsCollector.sendFailed.addAndGet(3);
        assertEquals(before + 3, MetricsCollector.sendFailed.get());
    }

    @Test
    public void testQueueOfferFailedCounter() {
        long before = MetricsCollector.queueOfferFailed.get();
        MetricsCollector.queueOfferFailed.incrementAndGet();
        assertEquals(before + 1, MetricsCollector.queueOfferFailed.get());
    }

    @Test
    public void testCircuitBreakerTrippedCounter() {
        long before = MetricsCollector.circuitBreakerTripped.get();
        MetricsCollector.circuitBreakerTripped.incrementAndGet();
        assertEquals(before + 1, MetricsCollector.circuitBreakerTripped.get());
    }

    @Test
    public void testTotalLinesCollectedCounter() {
        long before = MetricsCollector.totalLinesCollected.get();
        MetricsCollector.totalLinesCollected.addAndGet(100);
        assertEquals(before + 100, MetricsCollector.totalLinesCollected.get());
    }

    @Test
    public void testMethodThresholdReachedCounter() {
        long before = MetricsCollector.methodThresholdReached.get();
        MetricsCollector.methodThresholdReached.incrementAndGet();
        assertEquals(before + 1, MetricsCollector.methodThresholdReached.get());
    }

    @Test
    public void testUptimeSeconds() {
        long uptime = MetricsCollector.getUptimeSeconds();
        assertTrue(uptime >= 0);
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        final int threadCount = 10;
        final int incrementsPerThread = 1000;
        Thread[] threads = new Thread[threadCount];

        long before = MetricsCollector.totalRequests.get();
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    MetricsCollector.totalRequests.incrementAndGet();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertEquals(before + threadCount * incrementsPerThread, MetricsCollector.totalRequests.get());
    }
}
