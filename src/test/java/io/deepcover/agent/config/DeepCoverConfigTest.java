package io.deepcover.agent.config;

import com.alibaba.fastjson.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DeepCoverConfigTest {

    private Integer originalSampleRate;
    private Integer originalQueueSize;
    private Integer originalQueueMsgSize;
    private String originalIgnoreUrls;
    private String originalPackageName;
    private String originalDataCenterAddr;
    private Integer originalSendDataCenterType;

    @Before
    public void setUp() {
        originalSampleRate = DeepCoverConfig.sampleRate;
        originalQueueSize = DeepCoverConfig.queueSize;
        originalQueueMsgSize = DeepCoverConfig.queueMsgSize;
        originalIgnoreUrls = DeepCoverConfig.ignoreUrls;
        originalPackageName = DeepCoverConfig.packageName;
        originalDataCenterAddr = DeepCoverConfig.dataCenterAddr;
        originalSendDataCenterType = DeepCoverConfig.sendDataCenterType;

        DeepCoverConfig.packageName = "io.deepcover.test.*";
        DeepCoverConfig.dataCenterAddr = "http://127.0.0.1:18081/collect";
        DeepCoverConfig.sendDataCenterType = 1;
    }

    @After
    public void tearDown() {
        DeepCoverConfig.sampleRate = originalSampleRate;
        DeepCoverConfig.queueSize = originalQueueSize;
        DeepCoverConfig.queueMsgSize = originalQueueMsgSize;
        DeepCoverConfig.ignoreUrls = originalIgnoreUrls;
        DeepCoverConfig.packageName = originalPackageName;
        DeepCoverConfig.dataCenterAddr = originalDataCenterAddr;
        DeepCoverConfig.sendDataCenterType = originalSendDataCenterType;
    }

    @Test
    public void testInvalidRemoteConfigDoesNotPartiallyApply() {
        int previousSampleRate = DeepCoverConfig.sampleRate;
        int previousQueueSize = DeepCoverConfig.queueSize;
        JSONObject info = new JSONObject();
        info.put("sampleRate", 1000);
        info.put("queueSize", 0);

        try {
            DeepCoverConfig.applyRemoteConfig(info);
            fail("Expected invalid remote config to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(previousSampleRate, DeepCoverConfig.sampleRate.intValue());
            assertEquals(previousQueueSize, DeepCoverConfig.queueSize.intValue());
        }
    }

    @Test
    public void testValidRemoteConfigAppliesAsSingleUpdate() {
        JSONObject info = new JSONObject();
        info.put("sampleRate", 1000);
        info.put("queueMsgSize", 25);
        info.put("ignoreUrls", "/health;;");

        DeepCoverConfig.applyRemoteConfig(info);

        assertEquals(1000, DeepCoverConfig.sampleRate.intValue());
        assertEquals(25, DeepCoverConfig.queueMsgSize.intValue());
        assertEquals("/health", DeepCoverConfig.ignoreUrls);
    }
}
