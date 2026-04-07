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

    // --- isValid 相关测试 (通过 inTimeSample 间接测试) ---

    @Test
    public void testInTimeSampleWithValidTraceId() {
        // 构造一个合法的 traceId: 长度 >= 5, 尾部6位中前5位是数字
        // 格式: xxxTxxx.{digits}
        String traceId = "10110024003T30758T17337307800530007";
        // 这个traceId的tras[2] = "17337307800530007"
        // substring(len-7, len-4) = "300" -> 300 < 10000 -> true
        boolean result = TraceUtil.inTimeSample(traceId);
        assertTrue("Valid traceId should be sampled at 100% rate", result);
    }

    @Test
    public void testInTimeSampleWithBlankTraceId() {
        assertFalse(TraceUtil.inTimeSample(""));
        assertFalse(TraceUtil.inTimeSample(null));
        assertFalse(TraceUtil.inTimeSample("   "));
    }

    @Test
    public void testInTimeSampleWithNA() {
        assertFalse(TraceUtil.inTimeSample("N/A"));
    }

    @Test
    public void testInTimeSampleWithIgnoredTrace() {
        assertFalse(TraceUtil.inTimeSample("Ignored_Trace"));
    }

    @Test
    public void testInTimeSampleWithTooShort() {
        assertFalse(TraceUtil.inTimeSample("abc"));
    }

    @Test
    public void testInTimeSampleWithTooLong() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 41; i++) {
            sb.append("a");
        }
        assertFalse(TraceUtil.inTimeSample(sb.toString()));
    }

    @Test
    public void testInTimeSampleWithWrongFormat() {
        // 没有 T 或 . 分隔
        assertFalse(TraceUtil.inTimeSample("1234567890123456"));
    }

    @Test
    public void testInTimeSampleWithZeroSampleRate() {
        DeepCoverConfig.sampleRate = 0;
        // 任何 calTrace >= 0 都不满足 < 0
        String traceId = "10110024003T30758T17337307800530007";
        assertFalse(TraceUtil.inTimeSample(traceId));
    }

    @Test
    public void testInTimeSampleWithLowSampleRate() {
        // 构造 traceId 使 calTrace >= 200，sampleRate = 200 时不被采样
        // tras[2] = "123456789012345678901", substring(14,17) = "890", calTrace = 890
        // 890 < 200 = false
        DeepCoverConfig.sampleRate = 200;
        String traceId = "10110024003T30758T123456789012345678901";
        assertFalse(TraceUtil.inTimeSample(traceId));
    }
}
