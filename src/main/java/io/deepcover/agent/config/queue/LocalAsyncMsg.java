package io.deepcover.agent.config.queue;

/**
 * 本地异步处理数据封装类
 *
 * @Author shudian
 * @Date 2024/3/14-19:26
 * @Version 1.0
 */
public interface LocalAsyncMsg {

    /**
     * 返回消息大小，单位是字节
     *
     * @return
     */
    public int getSize();
}
