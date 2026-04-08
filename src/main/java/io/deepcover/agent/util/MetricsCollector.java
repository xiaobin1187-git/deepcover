package io.deepcover.agent.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime metrics collector for monitoring DeepCover agent status.
 * All counters are thread-safe via AtomicLong.
 */
public class MetricsCollector {

    private static final long START_TIME = System.currentTimeMillis();

    // Request metrics
    public static final AtomicLong totalRequests = new AtomicLong(0);
    public static final AtomicLong collectedRequests = new AtomicLong(0);
    public static final AtomicLong droppedRequests = new AtomicLong(0);

    // Line collection metrics
    public static final AtomicLong totalLinesCollected = new AtomicLong(0);
    public static final AtomicLong methodThresholdReached = new AtomicLong(0);

    // Send metrics
    public static final AtomicLong sendSuccess = new AtomicLong(0);
    public static final AtomicLong sendFailed = new AtomicLong(0);
    public static final AtomicLong queueOfferFailed = new AtomicLong(0);

    // Circuit breaker metrics
    public static final AtomicLong circuitBreakerTripped = new AtomicLong(0);

    public static long getUptimeSeconds() {
        return (System.currentTimeMillis() - START_TIME) / 1000;
    }
}
