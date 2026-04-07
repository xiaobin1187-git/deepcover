package io.deepcover.agent.util;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * TraceContext 单元测试
 */
public class TraceContextTest {

    @After
    public void tearDown() {
        TraceContext.clear();
    }

    @Test
    public void testTraceIdNotNull() {
        String traceId = TraceContext.traceId();
        assertNotNull(traceId);
        assertFalse(traceId.isEmpty());
    }

    @Test
    public void testTraceIdFormat() {
        String traceId = TraceContext.traceId();
        // 格式: {timestamp}T0.{random8chars}
        assertTrue("traceId should contain 'T0.'", traceId.contains("T0."));
        String[] parts = traceId.split("T0\\.");
        assertEquals("traceId should have 2 parts split by 'T0.'", 2, parts.length);
        // timestamp part should be numeric
        assertTrue("timestamp part should be numeric", parts[0].matches("\\d+"));
        // random part should be 8 hex chars
        assertEquals("random part should be 8 chars", 8, parts[1].length());
    }

    @Test
    public void testTraceIdThreadLocalConsistency() {
        // 同一线程多次获取应返回相同值
        String first = TraceContext.traceId();
        String second = TraceContext.traceId();
        assertEquals(first, second);
    }

    @Test
    public void testSetTraceId() {
        String customId = "1234567890T0.abc12345";
        TraceContext.setTraceId(customId);
        assertEquals(customId, TraceContext.traceId());
    }

    @Test
    public void testClearAndRegenerate() {
        String first = TraceContext.traceId();
        TraceContext.clear();
        String second = TraceContext.traceId();
        // 清除后应该生成新的traceId
        assertNotNull(second);
        // 两次生成的traceId应该不同（极小概率相同，时间戳部分不同）
        assertNotEquals(first, second);
    }

    @Test
    public void testMultiThreadIsolation() throws InterruptedException {
        final String[] thread1Id = new String[1];
        final String[] thread2Id = new String[1];

        Thread t1 = new Thread(() -> {
            TraceContext.setTraceId("thread1-trace-id");
            thread1Id[0] = TraceContext.traceId();
        });
        Thread t2 = new Thread(() -> {
            TraceContext.setTraceId("thread2-trace-id");
            thread2Id[0] = TraceContext.traceId();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals("thread1-trace-id", thread1Id[0]);
        assertEquals("thread2-trace-id", thread2Id[0]);
    }
}
