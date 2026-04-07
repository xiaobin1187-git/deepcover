package io.deepcover.agent.entity;

import com.alibaba.fastjson.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * LineEntity 单元测试
 */
public class LineEntityTest {

    private LineEntity lineEntity;

    @Before
    public void setUp() {
        lineEntity = new LineEntity();
        lineEntity.setClassName("com.example.DemoService");
        lineEntity.setMethodName("doProcess");
        lineEntity.setInvokeId(1001);
        lineEntity.setBeginTime(1700000000000L);
        lineEntity.setCallLineCount(1L);
        lineEntity.setParameters(new ArrayList<>());

        Set<Integer> lineNums = new LinkedHashSet<>();
        lineNums.add(10);
        lineNums.add(20);
        lineNums.add(30);
        lineEntity.setLineNum(lineNums);
    }

    @Test
    public void testGetterSetter() {
        assertEquals("com.example.DemoService", lineEntity.getClassName());
        assertEquals("doProcess", lineEntity.getMethodName());
        assertEquals(Integer.valueOf(1001), lineEntity.getInvokeId());
        assertEquals(Long.valueOf(1700000000000L), lineEntity.getBeginTime());
        assertEquals(Long.valueOf(1L), lineEntity.getCallLineCount());
    }

    @Test
    public void testLineNumSetBehavior() {
        // LinkedHashSet 自动去重
        Set<Integer> lineNums = lineEntity.getLineNum();
        lineNums.add(10); // 重复添加
        assertEquals(3, lineNums.size()); // 不应增加
        assertTrue(lineNums.contains(10));
        assertTrue(lineNums.contains(20));
        assertTrue(lineNums.contains(30));
    }

    @Test
    public void testLineNumOrderPreserved() {
        // LinkedHashSet 保持插入顺序
        Set<Integer> lineNums = lineEntity.getLineNum();
        Integer[] array = lineNums.toArray(new Integer[0]);
        assertArrayEquals(new Integer[]{10, 20, 30}, array);
    }

    @Test
    public void testLineNumO1Contains() {
        Set<Integer> lineNums = lineEntity.getLineNum();
        // O(1) 查找，Set.contains
        assertTrue(lineNums.contains(10));
        assertFalse(lineNums.contains(999));
    }

    @Test
    public void testToStringValidJson() {
        String json = lineEntity.toString();
        JSONObject parsed = JSONObject.parseObject(json);
        assertEquals("com.example.DemoService", parsed.getString("className"));
        assertEquals("doProcess", parsed.getString("methodName"));
        assertEquals(Integer.valueOf(1001), parsed.getInteger("invokeId"));
        assertNotNull(parsed.getJSONArray("lineNum"));
        assertEquals(3, parsed.getJSONArray("lineNum").size());
    }

    @Test
    public void testEqualsSameData() {
        LineEntity other = new LineEntity();
        other.setClassName("com.example.DemoService");
        other.setMethodName("doProcess");
        // parameters 需要设置才能避免 equals 中的 NPE
        other.setParameters(new ArrayList<>());

        Set<Integer> lineNums = new LinkedHashSet<>();
        lineNums.add(10);
        lineNums.add(20);
        lineNums.add(30);
        other.setLineNum(lineNums);

        assertEquals(lineEntity, other);
    }

    @Test
    public void testEqualsDifferentData() {
        LineEntity other = new LineEntity();
        other.setClassName("com.example.OtherService");
        other.setMethodName("doProcess");
        other.setLineNum(lineEntity.getLineNum());

        assertNotEquals(lineEntity, other);
    }

    @Test
    public void testDefaultLineNumNotNull() {
        LineEntity fresh = new LineEntity();
        assertNotNull(fresh.getLineNum());
        assertEquals(0, fresh.getLineNum().size());
    }
}
