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
 * @Author shudian
 * @Date 2024/3/15-10:04
 * @Version 1.0
 */
@Slf4j
public class LocalAsyncConfig {
    private static LocalAsyncEngine localAsyncEngine =null;
    static class MyHandler implements LocalAsyncConsumer {
        @Override
        public void init(Map<String, Object> properties) {

        }

        @Override
        public void consume(List<CodeEntity> msg) {
            try{
                if(DeepCoverConfig.sendDataCenterType==1){
                    HttpClient2.batchDoPost(DeepCoverConfig.dataCenterAddr,msg);
                }else if(DeepCoverConfig.sendDataCenterType==2){
                    KafkaProducerEngine.batchSendMessage(msg);
                }
                MetricsCollector.sendSuccess.addAndGet(msg.size());
                log.debug(Thread.currentThread().getId() + "." + Thread.currentThread().getName() + ":消费数据条数=" + msg.size());

            }catch(Exception e){
                log.error("队列消费异常：采集发送数据异常");
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

    public static void  init(){
        if(localAsyncEngine == null){
            localAsyncEngine = new LocalAsyncEngine(DeepCoverConfig.queueNum, DeepCoverConfig.queueSize, DeepCoverConfig.queueMsgSize, DeepCoverConfig.queueRecycleTime, MyHandler.class, null);
            localAsyncEngine.start();
        }
    }
    public static void sendMessage(CodeEntity codeEntity){
        boolean isSend = localAsyncEngine.offerMsg(codeEntity);
//        if(!isSend){
//            log.warn("发送队列已满，不发送，traceId={},url={}",codeEntity.getTraceId(),codeEntity.getUrl());
//        }
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
