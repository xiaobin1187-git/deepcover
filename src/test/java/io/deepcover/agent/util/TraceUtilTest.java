package io.deepcover.agent.util;

import io.deepcover.agent.config.DeepCoverConfig;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * TraceUtil 单元测试
 */
public class TraceUtilTest {

    @Before
    public void setUp() {
        DeepCoverConfig.sampleRate = 10000; // 100% 采样率
    }

    @Test
    public void testFullSampleRateAcceptsAnyNonBlankTraceId() {
        assertTrue(TraceUtil.inTimeSample("4bf92f3577b34da6a3ce929d0e0e4736"));
        assertTrue(TraceUtil.inTimeSample("legacy-trace-id"));
    }

    @Test
    public void testInTimeSampleWithBlankTraceId() {
        assertFalse(TraceUtil.inTimeSample(""));
        assertFalse(TraceUtil.inTimeSample(null));
        assertFalse(TraceUtil.inTimeSample("   "));
    }

    @Test
    public void testInTimeSampleWithZeroSampleRate() {
        DeepCoverConfig.sampleRate = 0;
        assertFalse(TraceUtil.inTimeSample("4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    public void testSamplingIsDeterministic() {
        DeepCoverConfig.sampleRate = 1000;
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        boolean first = TraceUtil.inTimeSample(traceId);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, TraceUtil.inTimeSample(traceId));
        }
    }

    @Test
    public void testTenPercentSamplingDistribution() {
        DeepCoverConfig.sampleRate = 1000;
        int sampled = 0;
        for (int i = 0; i < 10000; i++) {
            if (TraceUtil.inTimeSample("trace-" + i)) {
                sampled++;
            }
        }
        assertTrue("sampled=" + sampled, sampled >= 800 && sampled <= 1200);
    }

    @Test
    public void testSampleBucketRange() {
        int bucket = TraceUtil.sampleBucket("trace-id");
        assertTrue(bucket >= 0);
        assertTrue(bucket < 10000);
    }
}
