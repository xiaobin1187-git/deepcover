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
import io.deepcover.agent.config.kafka.KafkaProducerEngine;
import io.deepcover.agent.entity.CodeEntity;
import io.deepcover.agent.util.ExceptionAwareUtil;
import io.deepcover.agent.util.MetricsCollector;
import io.deepcover.agent.util.http.HttpClient2;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

/**
 * @author DeepCover Contributors
 * @Date 2024/3/15-10:04
 * @Version 1.0
 */
@Slf4j
public class LocalAsyncConfig {
    private static volatile LocalAsyncEngine localAsyncEngine =null;
    static class MyHandler implements LocalAsyncConsumer {
        @Override
        public void init(Map<String, Object> properties) {

        }

        @Override
        public void consume(List<CodeEntity> msg) {
            try{
                if(DeepCoverConfig.sendDataCenterType==1){
                    int successCount = HttpClient2.batchDoPost(DeepCoverConfig.dataCenterAddr,msg);
                    MetricsCollector.sendSuccess.addAndGet(successCount);
                    MetricsCollector.sendFailed.addAndGet(msg.size() - successCount);
                }else if(DeepCoverConfig.sendDataCenterType==2){
                    KafkaProducerEngine.batchSendMessage(msg);
                }else{
                    MetricsCollector.sendFailed.addAndGet(msg.size());
                    log.error("unsupported sendDataCenterType={}", DeepCoverConfig.sendDataCenterType);
                }
                log.debug(Thread.currentThread().getId() + "." + Thread.currentThread().getName() + ":消费数据条数=" + msg.size());

            }catch(Exception e){
                log.error("队列消费异常：采集发送数据异常", e);
                MetricsCollector.sendFailed.addAndGet(msg.size());
                ExceptionAwareUtil.exceptionOverflow(e);
            }

        }

        @Override
        public void onError(List<CodeEntity> msg, Throwable t) {
            log.error("队列消费异常",t);
        }
    }

    static class MyMsg implements LocalAsyncMsg {

        private String msg;

        public MyMsg(String msg) {
            this.msg = msg;
        }

        @Override
        public int getSize() {
            try {
                return this.msg == null ? 0 : msg.getBytes("UTF-8").length;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static synchronized void init(){
        if(localAsyncEngine == null){
            localAsyncEngine = new LocalAsyncEngine(DeepCoverConfig.queueNum, DeepCoverConfig.queueSize, DeepCoverConfig.queueMsgSize, DeepCoverConfig.queueRecycleTime, MyHandler.class, null);
            localAsyncEngine.start();
        }
    }
    public static boolean sendMessage(CodeEntity codeEntity){
        LocalAsyncEngine engine = localAsyncEngine;
        if (engine == null) {
            MetricsCollector.queueOfferFailed.incrementAndGet();
            MetricsCollector.droppedRequests.incrementAndGet();
            log.error("local async engine is not initialized");
            return false;
        }
        return engine.offerMsg(codeEntity);
    }

    public static synchronized void shutdown(){
        LocalAsyncEngine engine = localAsyncEngine;
        localAsyncEngine = null;
        if (engine != null) {
            engine.shutdown();
        }
    }

    public static int getQueueDepth() {
        LocalAsyncEngine engine = localAsyncEngine;
        return engine == null ? 0 : engine.getQueueDepth();
    }

    public static int getQueueCapacity() {
        LocalAsyncEngine engine = localAsyncEngine;
        return engine == null ? 0 : engine.getQueueCapacity();
    }

    public static int getQueueCount() {
        LocalAsyncEngine engine = localAsyncEngine;
        return engine == null ? 0 : engine.getQueueCount();
    }

    public static boolean isRunning() {
        LocalAsyncEngine engine = localAsyncEngine;
        return engine != null && engine.isRunning();
    }
    public static void main(String[] args) throws InterruptedException {
        LocalAsyncEngine localAsyncEngine = new LocalAsyncEngine(10, 100, 100, 2, MyHandler.class, null);
        localAsyncEngine.start();
        localAsyncEngine.offerMsg(new CodeEntity());

        Thread.sleep(1000);
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
//        for(int i=0;i<100000;i++){
//            localAsyncEngine.offerMsg(new MyMsg("dddddddddddddddddddddddd"+i));
//        }
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());

        Thread.sleep(10000000);
        localAsyncEngine.shutdown();

    }
}
