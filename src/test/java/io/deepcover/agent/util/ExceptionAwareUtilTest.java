package io.deepcover.agent.util;

import io.deepcover.agent.config.DeepCoverConfig;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ExceptionAwareUtil 熔断机制单元测试
 */
public class ExceptionAwareUtilTest {

    @Before
    public void setUp() {
        ExceptionAwareUtil.clear();
        DeepCoverConfig.exceptionThreshold = 5;
        DeepCoverConfig.exceptionCalcTime = 1; // 1 minute window
        DeepCoverConfig.exceptionPauseTime = 5;
        DeepCoverConfig.exceptionThresholdTime = 0L;
    }

    @Test
    public void testClearResetsState() {
        ExceptionAwareUtil.exceptionOverflow(new RuntimeException("test error"));
        ExceptionAwareUtil.clear();
        // After clear, the internal counter should be 0
        // exceptionThresholdTime should not be set by subsequent calls below threshold
        DeepCoverConfig.exceptionThreshold = 100;
        for (int i = 0; i < 50; i++) {
            ExceptionAwareUtil.exceptionOverflow(new RuntimeException("after clear"));
        }
        assertEquals(0L, DeepCoverConfig.exceptionThresholdTime);
    }

    @Test
    public void testExceptionThresholdReached() {
        assertEquals(0L, DeepCoverConfig.exceptionThresholdTime);
        for (int i = 0; i < 5; i++) {
            ExceptionAwareUtil.exceptionOverflow(new RuntimeException("error " + i));
        }
        // 阈值到达后 exceptionThresholdTime 应被设置
        assertTrue("exceptionThresholdTime should be set after threshold reached",
                DeepCoverConfig.exceptionThresholdTime > 0);
    }

    @Test
    public void testExceptionThresholdNotReached() {
        DeepCoverConfig.exceptionThreshold = 10;
        for (int i = 0; i < 5; i++) {
            ExceptionAwareUtil.exceptionOverflow(new RuntimeException("error " + i));
        }
        assertEquals("Threshold not reached, time should remain 0",
                0L, DeepCoverConfig.exceptionThresholdTime);
    }

    @Test
    public void testNullExceptionMessage() {
        // NPE without message should use class name
        NullPointerException npe = new NullPointerException();
        ExceptionAwareUtil.exceptionOverflow(npe);
        // Should not throw, just count
        DeepCoverConfig.exceptionThreshold = 100;
        ExceptionAwareUtil.exceptionOverflow(npe);
    }

    @Test
    public void testEmptyExceptionMessage() {
        ExceptionAwareUtil.exceptionOverflow(new RuntimeException(""));
        // Empty message should fallback to class canonical name
    }

    @Test
    public void testDifferentExceptionTypes() {
        ExceptionAwareUtil.exceptionOverflow(new RuntimeException("runtime"));
        ExceptionAwareUtil.exceptionOverflow(new IllegalArgumentException("illegal"));
        ExceptionAwareUtil.exceptionOverflow(new NullPointerException("npe"));
        // Should track each type separately
    }

    @Test
    public void testSameExceptionRepeatedly() {
        for (int i = 0; i < 5; i++) {
            ExceptionAwareUtil.exceptionOverflow(new RuntimeException("same error"));
        }
        assertTrue(DeepCoverConfig.exceptionThresholdTime > 0);
    }
}
