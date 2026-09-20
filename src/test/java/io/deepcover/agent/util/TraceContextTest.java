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
        assertTrue("traceId should be 32 lowercase hex chars", traceId.matches("[0-9a-f]{32}"));
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
    public void testStartTraceUsesPropagatedTraceId() {
        String propagated = "4bf92f3577b34da6a3ce929d0e0e4736";
        assertEquals(propagated, TraceContext.startTrace(propagated));
        assertEquals(propagated, TraceContext.traceId());
    }

    @Test
    public void testStartTraceCreatesNewRequestTrace() {
        String first = TraceContext.startTrace(null);
        String second = TraceContext.startTrace(null);
        assertNotEquals(first, second);
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
