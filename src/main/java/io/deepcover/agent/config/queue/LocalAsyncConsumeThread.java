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

import io.deepcover.agent.config.DeepCoverConfig;
import io.deepcover.agent.entity.CodeEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地异步处理消费线程
 *
 * @Author shudian
 * @Date 2024/3/15-9:32
 * @Version 1.0
 */
public class LocalAsyncConsumeThread extends Thread {

    private volatile boolean running;
    private volatile long recycleTime;
    private int maxQueueSize;
    private LocalAsyncEngine.QueueAndSize queue;
    private LocalAsyncConsumer localAsyncHandler;

    public LocalAsyncConsumeThread() {
    }

    public LocalAsyncConsumeThread(String threadName,
                                   LocalAsyncConsumer localAsyncHandler,
                                   LocalAsyncEngine.QueueAndSize queue,
                                   int maxQueueSize,
                                   long recycleTime) {
        super(threadName);
        this.queue = queue;
        this.recycleTime = recycleTime;
        this.maxQueueSize = maxQueueSize;
        this.localAsyncHandler = localAsyncHandler;
    }

    @Override
    public void run() {
        this.running = true;
        while (this.running) {
            if (!consume()) {
                try {
                    Thread.sleep(DeepCoverConfig.queueRecycleTime);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private boolean consume() {
        List<CodeEntity> consumeList = new ArrayList<>(maxQueueSize);
        queue.drainTo(consumeList);
        if (!consumeList.isEmpty()) {
            try {
                localAsyncHandler.consume(consumeList);
            } catch (Throwable t) {
                localAsyncHandler.onError(consumeList, t);
            } finally {
                consumeList.clear();
            }
            return true;
        }
        return false;
    }

    public void shutdown() {
        this.running = false;
    }
}
