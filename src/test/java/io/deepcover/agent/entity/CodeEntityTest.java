package io.deepcover.agent.entity;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * CodeEntity 单元测试
 */
public class CodeEntityTest {

    private CodeEntity codeEntity;

    @Before
    public void setUp() {
        codeEntity = new CodeEntity();
        codeEntity.setType("HTTP");
        codeEntity.setMethod("POST");
        codeEntity.setUrl("/api/demo");
        codeEntity.setTraceId("1700000000000T0.abcd1234");
        codeEntity.setServiceName("demo-service");
        codeEntity.setEnv("test");
        codeEntity.setBranch("master");
        codeEntity.setProcessId(12345);
        codeEntity.setCodeInfoSize(0);
        codeEntity.setIsSend(0);

        ArrayList<LineEntity> codeInfo = new ArrayList<>();
        LineEntity line1 = new LineEntity();
        line1.setClassName("com.example.Demo");
        line1.setMethodName("run");
        line1.setInvokeId(1);
        line1.setBeginTime(1700000000000L);
        line1.setCallLineCount(1L);
        Set<Integer> lines = new LinkedHashSet<>();
        lines.add(15);
        lines.add(22);
        line1.setLineNum(lines);
        codeInfo.add(line1);

        codeEntity.setCodeInfo(codeInfo);
    }

    @Test
    public void testGetterSetter() {
        assertEquals("HTTP", codeEntity.getType());
        assertEquals("POST", codeEntity.getMethod());
        assertEquals("/api/demo", codeEntity.getUrl());
        assertEquals("1700000000000T0.abcd1234", codeEntity.getTraceId());
        assertEquals("demo-service", codeEntity.getServiceName());
        assertEquals(Integer.valueOf(12345), codeEntity.getProcessId());
    }

    @Test
    public void testCodeInfoList() {
        assertNotNull(codeEntity.getCodeInfo());
        assertEquals(1, codeEntity.getCodeInfo().size());

        LineEntity line = codeEntity.getCodeInfo().get(0);
        assertEquals("com.example.Demo", line.getClassName());
        assertEquals("run", line.getMethodName());
        assertEquals(2, line.getLineNum().size());
    }

    @Test
    public void testToStringValidJson() {
        String json = codeEntity.toString();
        JSONObject parsed = JSONObject.parseObject(json);
        assertEquals("HTTP", parsed.getString("type"));
        assertEquals("POST", parsed.getString("method"));
        assertEquals("/api/demo", parsed.getString("url"));
        assertEquals("1700000000000T0.abcd1234", parsed.getString("traceId"));
        assertEquals("demo-service", parsed.getString("serviceName"));

        JSONArray codeInfoArray = parsed.getJSONArray("codeInfo");
        assertNotNull(codeInfoArray);
        assertEquals(1, codeInfoArray.size());

        JSONObject lineObj = codeInfoArray.getJSONObject(0);
        assertEquals("com.example.Demo", lineObj.getString("className"));
        JSONArray lineNums = lineObj.getJSONArray("lineNum");
        assertEquals(2, lineNums.size());
        assertTrue(lineNums.contains(15));
        assertTrue(lineNums.contains(22));
    }

    @Test
    public void testIsSendFlag() {
        assertEquals(Integer.valueOf(0), codeEntity.getIsSend());
        codeEntity.setIsSend(1);
        assertEquals(Integer.valueOf(1), codeEntity.getIsSend());
    }

    @Test
    public void testEmptyCodeInfo() {
        codeEntity.setCodeInfo(new ArrayList<>());
        String json = codeEntity.toString();
        JSONObject parsed = JSONObject.parseObject(json);
        JSONArray codeInfo = parsed.getJSONArray("codeInfo");
        assertNotNull(codeInfo);
        assertEquals(0, codeInfo.size());
    }

    @Test
    public void testMultipleLineEntities() {
        ArrayList<LineEntity> codeInfo = codeEntity.getCodeInfo();

        LineEntity line2 = new LineEntity();
        line2.setClassName("com.example.Util");
        line2.setMethodName("process");
        line2.setInvokeId(2);
        line2.setBeginTime(1700000000001L);
        line2.setCallLineCount(1L);
        Set<Integer> lines2 = new LinkedHashSet<>();
        lines2.add(30);
        line2.setLineNum(lines2);
        codeInfo.add(line2);

        codeEntity.setCodeInfoSize(2);
        assertEquals(2, codeEntity.getCodeInfo().size());

        String json = codeEntity.toString();
        JSONObject parsed = JSONObject.parseObject(json);
        assertEquals(2, parsed.getJSONArray("codeInfo").size());
    }
}
