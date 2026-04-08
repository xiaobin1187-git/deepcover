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
import io.deepcover.agent.util.MetricsCollector;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地异步处理引擎
 *
 * @Author shudian
 * @Date 2024/3/14-19:04
 * @Version 1.0
 */
@Slf4j
public class LocalAsyncEngine {

    private int qNum;
    private int qSize;
    private int maxMsgSize;//单位MB
    private long consumeSlpTime;
    private Class localAsyncHandler;
    private Map<String, Object> consumeProps;
    private List<QueueAndSize> qList;
    private volatile int round = 0; //不是严格一致的，考虑性能
    private List<LocalAsyncConsumeThread> consumeThreads;
    private Object lock = new Object();
    private volatile boolean running = false;


    public LocalAsyncEngine() {
    }

    /**
     * 初始化异步处理引擎
     *
     * @param qNum              队列数量
     * @param qSize             每个队列的数据上线
     * @param maxMsgSize        所有数据的大小上限
     * @param consumeSlpTime    处理线程休眠时间
     * @param consumeClazz      业务处理器类对象
     * @param consumeProperties 业务处理器初始化时用到的参数
     */
    public LocalAsyncEngine(int qNum, int qSize, int maxMsgSize, long consumeSlpTime,
                            Class<? extends LocalAsyncConsumer> consumeClazz, Map<String, Object> consumeProperties) {
        this.qNum = qNum;
        this.qSize = qSize;
        this.maxMsgSize = maxMsgSize;
        this.consumeSlpTime = consumeSlpTime;
        this.localAsyncHandler = consumeClazz;
        this.consumeProps = consumeProperties;
    }

    /**
     * 启动异步处理引擎
     */
    public void start() {
        synchronized (lock) {
            qList = new ArrayList<>();
            consumeThreads = new ArrayList<>();
            for (int i = 0; i < qNum; i++) {
                QueueAndSize queueAndSize = new QueueAndSize(qSize);
                qList.add(queueAndSize);
                LocalAsyncConsumer consumer = createNewConsumer(this.localAsyncHandler);
                String threadName = "LocalAsyncEngine.Consumer." + i + ".Thread";
                LocalAsyncConsumeThread td = new LocalAsyncConsumeThread(threadName, consumer, queueAndSize, this.qSize, this.consumeSlpTime);
                td.setDaemon(true);
                consumeThreads.add(td);
            }
            for (int i = 0; i < qNum; i++) {
                consumeThreads.get(i).start();
            }
            this.running = true;
        }
    }

    public LocalAsyncConsumer createNewConsumer(Class<? extends LocalAsyncConsumer> localAsyncClazz) {
        try {
            LocalAsyncConsumer localAsyncConsumer = localAsyncClazz.newInstance();
            localAsyncConsumer.init(this.consumeProps);
            return localAsyncConsumer;
        } catch (Exception e) {
            throw new RuntimeException("create LocalAsyncConsumer instance error", e);
        }
    }

    public boolean offerMsg(CodeEntity codeEntity) {
        if (codeEntity == null) {
            return false;
        }
        int queueSelect = Math.abs(round++ % qNum);
        QueueAndSize queueAndSize = qList.get(queueSelect);
        boolean isSend = queueAndSize.offer(codeEntity);
        if(!isSend){
            MetricsCollector.queueOfferFailed.incrementAndGet();
            log.warn("发送队列已满,不发送,队列{}当前长度={},traceId={},url={}",queueSelect,queueAndSize.queue.size(),codeEntity.getTraceId(),codeEntity.getUrl());
        }
        return isSend;
    }

      /*
    synchronized (i) {
        if (i > qSize) {
            i = 0;
        }
        i++;
        QueueAndSize queueAndSize = qList.get(Math.abs(round++ % qNum));
        return queueAndSize.offer(msg);
    }*/

    public void shutdown() {
        synchronized (this.lock) {
            if (!this.running) {
                return;
            }
            for (LocalAsyncConsumeThread td : this.consumeThreads) {
                td.shutdown();
            }
            this.running = false;
        }
    }

    static class QueueAndSize {
        private AtomicLong qSize;
        private ArrayBlockingQueue<CodeEntity> queue;

        public QueueAndSize(int size) {
            qSize = new AtomicLong();
            queue = new ArrayBlockingQueue<>(size);
        }

        public boolean offer(CodeEntity msg) {
            return queue.offer(msg);
        }

        public Object take() throws InterruptedException {
            return queue.take();
        }

        public int drainTo(Collection c) {
            return queue.drainTo(c);
        }
    }

}
