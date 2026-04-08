/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
