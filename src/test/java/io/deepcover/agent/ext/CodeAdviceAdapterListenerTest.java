package io.deepcover.agent.ext;

import io.deepcover.agent.config.DeepCoverConfig;
import io.deepcover.agent.util.ExceptionAwareUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodeAdviceAdapterListenerTest {

    private Integer originalPauseTime;
    private long originalThresholdTime;
    private TestListener listener;

    @Before
    public void setUp() {
        originalPauseTime = DeepCoverConfig.exceptionPauseTime;
        originalThresholdTime = DeepCoverConfig.exceptionThresholdTime;
        DeepCoverConfig.exceptionPauseTime = 5;
        listener = new TestListener();
    }

    @After
    public void tearDown() {
        DeepCoverConfig.exceptionPauseTime = originalPauseTime;
        DeepCoverConfig.exceptionThresholdTime = originalThresholdTime;
        ExceptionAwareUtil.clear();
    }

    @Test
    public void testCircuitBreakerReportsPausedWithinPauseWindow() {
        DeepCoverConfig.exceptionThresholdTime = System.currentTimeMillis();

        assertTrue(listener.circuitBreakerPaused());
    }

    @Test
    public void testCircuitBreakerClearsExpiredPauseWindow() {
        DeepCoverConfig.exceptionThresholdTime = System.currentTimeMillis() - 6000L;

        assertFalse(listener.circuitBreakerPaused());
        assertEquals(0L, DeepCoverConfig.exceptionThresholdTime);
    }

    private static class TestListener extends CodeAdviceAdapterListener {

        TestListener() {
            super(new CodeAdviceListener());
        }

        boolean circuitBreakerPaused() {
            return isCircuitBreakerPaused();
        }
    }
}
