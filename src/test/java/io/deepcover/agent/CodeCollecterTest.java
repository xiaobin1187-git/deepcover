package io.deepcover.agent;

import com.alibaba.fastjson.JSONObject;
import io.deepcover.agent.config.DeepCoverConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodeCollecterTest {

    private int originalSampleRate;
    private String originalPackageName;

    @Before
    public void setUp() {
        originalSampleRate = DeepCoverConfig.sampleRate;
        originalPackageName = DeepCoverConfig.packageName;
    }

    @After
    public void tearDown() {
        DeepCoverConfig.sampleRate = originalSampleRate;
        DeepCoverConfig.packageName = originalPackageName;
    }

    @Test
    public void testSyncConfigSeparatesDynamicAndRestartRequiredSettings() {
        DeepCoverConfig.packageName = "io.deepcover.old.*";
        Map<String, String> params = new HashMap<>();
        params.put("sampleRate", "1000");
        params.put("packageName", "io.deepcover.new.*");

        JSONObject result = invokeSyncConfig(params);

        assertTrue(result.getBooleanValue("success"));
        assertEquals(1000, DeepCoverConfig.sampleRate.intValue());
        assertEquals("io.deepcover.old.*", DeepCoverConfig.packageName);
        assertTrue(result.getJSONArray("applied").contains("sampleRate"));
        assertTrue(result.getJSONArray("restartRequired").contains("packageName"));
    }

    @Test
    public void testSyncConfigRejectsInvalidSampleRate() {
        Map<String, String> params = new HashMap<>();
        params.put("sampleRate", "10001");

        JSONObject result = invokeSyncConfig(params);

        assertFalse(result.getBooleanValue("success"));
        assertEquals(originalSampleRate, DeepCoverConfig.sampleRate.intValue());
    }

    @Test
    public void testSyncConfigRollsBackEarlierValuesWhenLaterValidationFails() {
        Map<String, String> params = new HashMap<>();
        params.put("sampleRate", "1000");
        params.put("exceptionThreshold", "0");

        JSONObject result = invokeSyncConfig(params);

        assertFalse(result.getBooleanValue("success"));
        assertEquals(originalSampleRate, DeepCoverConfig.sampleRate.intValue());
    }

    private JSONObject invokeSyncConfig(Map<String, String> params) {
        StringWriter output = new StringWriter();
        new CodeCollecter().syncConfig(params, new PrintWriter(output));
        return JSONObject.parseObject(output.toString().trim());
    }
}
