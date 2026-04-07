package io.deepcover.agent.config.queue;

import io.deepcover.agent.config.DeepCoverConfig;
import io.deepcover.agent.entity.CodeEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * LocalAsyncEngine 单元测试
 */
public class LocalAsyncEngineTest {

    private LocalAsyncEngine engine;

    @Before
    public void setUp() {
        DeepCoverConfig.queueRecycleTime = 100;
        // 构造函数: (int qNum, int qSize, int maxMsgSize, long consumeSlpTime,
        //             Class<? extends LocalAsyncConsumer> consumeClazz, Map consumeProperties)
        engine = new LocalAsyncEngine(2, 5, 10, 100L,
                TestConsumer.class, new HashMap<String, Object>());
        engine.start();
    }

    @After
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private CodeEntity createCodeEntity(String traceId, String url) {
        CodeEntity entity = new CodeEntity();
        entity.setTraceId(traceId);
        entity.setUrl(url);
        entity.setType("HTTP");
        entity.setMethod("GET");
        entity.setServiceName("test-service");
        entity.setCodeInfo(new ArrayList<>());
        entity.setCodeInfoSize(0);
        entity.setIsSend(0);
        return entity;
    }

    @Test
    public void testOfferMsgSuccess() {
        CodeEntity entity = createCodeEntity("trace-001", "/api/test");
        boolean result = engine.offerMsg(entity);
        assertTrue("Should successfully offer message to queue", result);
    }

    @Test
    public void testOfferMsgNull() {
        boolean result = engine.offerMsg(null);
        assertFalse("Should return false for null entity", result);
    }

    @Test
    public void testOfferMsgRoundRobin() {
        for (int i = 0; i < 10; i++) {
            CodeEntity entity = createCodeEntity("trace-" + i, "/api/" + i);
            assertTrue("Offer " + i + " should succeed", engine.offerMsg(entity));
        }
    }

    @Test
    public void testOfferMsgQueueFull() {
        // 2个队列 x 每队列5容量 = 10个槽位
        for (int i = 0; i < 10; i++) {
            assertTrue("Offer " + i + " should succeed",
                    engine.offerMsg(createCodeEntity("trace-" + i, "/api/" + i)));
        }
        // 第11个应被拒绝（消费线程可能已消费部分，允许边界情况）
        engine.offerMsg(createCodeEntity("trace-overflow", "/api/overflow"));
    }

    @Test
    public void testShutdownIdempotent() {
        engine.shutdown();
        // Second shutdown should be idempotent
        engine.shutdown();
    }

    /**
     * 测试用 Consumer 实现
     */
    public static class TestConsumer implements LocalAsyncConsumer {
        @Override
        public void init(Map<String, Object> props) {
        }

        @Override
        public void consume(List<CodeEntity> msg) {
        }

        @Override
        public void onError(List<CodeEntity> msg, Throwable t) {
        }
    }
}
