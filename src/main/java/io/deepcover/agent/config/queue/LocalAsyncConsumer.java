/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
