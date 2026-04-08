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

import java.util.UUID;

/**
 * Trace ID 生成工具类
 * 用于替代内部依赖 cat-toolkit-trace 的 TraceContext
 */
public class TraceContext {

    /**
     * 获取当前线程的 Trace ID
     * 使用 ThreadLocal 保持与原有行为一致
     */
    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<String>() {
        @Override
        protected String initialValue() {
            return generateTraceId();
        }
    };

    /**
     * 生成新的 Trace ID
     * 格式: {timestamp}-{random}
     */
    private static String generateTraceId() {
        long timestamp = System.currentTimeMillis();
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return timestamp + "T0." + random;
    }

    /**
     * 获取当前 Trace ID
     * @return Trace ID 字符串
     */
    public static String traceId() {
        String traceId = TRACE_ID_HOLDER.get();
        if (traceId == null) {
            traceId = generateTraceId();
            TRACE_ID_HOLDER.set(traceId);
        }
        return traceId;
    }

    /**
     * 设置当前线程的 Trace ID
     * @param traceId Trace ID
     */
    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    /**
     * 清除当前线程的 Trace ID
     */
    public static void clear() {
        TRACE_ID_HOLDER.remove();
    }
}
