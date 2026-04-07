package io.deepcover.agent.config.queue;

import io.deepcover.agent.entity.CodeEntity;

import java.util.List;
import java.util.Map;

/**
 * 本地异步处理消费者接口
 *
 * @Author shudian
 * @Date 2024/3/14-19:07
 * @Version 1.0
 */
public interface LocalAsyncConsumer {
    /**
     * 初始化业务处理器接口
     *
     * @param properties
     */
    public void init(Map<String, Object> properties);

    /**
     * 异步消费数据接口
     *
     * @param msg
     */
    public void consume(List<CodeEntity> msg);

    /**
     * 消费数据异常时供业务处理的接口
     *
     * @param msg
     * @param t
     */
    public void onError(List<CodeEntity> msg, Throwable t);
}
