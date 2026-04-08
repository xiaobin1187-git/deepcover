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
package io.deepcover.agent.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.*;

@Slf4j
public class ExecutorThreadPoolConfig {
    public static ScheduledExecutorService scheduleEx = Executors.newScheduledThreadPool(1,
            new BasicThreadFactory.Builder().namingPattern("code-coverage-schedule-%d").daemon(true).build());
    public static ExecutorService executorSendMsg =
            new ThreadPoolExecutor(
                    4,
                    8,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(256),
                    new ThreadFactoryBuilder().setNameFormat("code-coverage-send-msg-%d").build(),
                    new ThreadPoolExecutor.CallerRunsPolicy());

//public static ScheduledExecutorService scheduleSendMsgEx = Executors.newScheduledThreadPool(1,
//        new BasicThreadFactory.Builder().namingPattern("code-coverage-send-msg-%d").daemon(true).build());


//public static void main(String[] args){
//    //异步线程，unload时候一定要shutdown无法关掉
//    while(true){
//        executorSendMsg.shutdown();
//        log.info("tttt{}",executorSendMsg.isShutdown());
//        try{
//            executorSendMsg.submit(new Runnable(){
//                @Override
//                public void run() {
//                    Thread.currentThread().setName("module-"+Thread.currentThread().getId());
//                    int b = 1+ 100;
//                    System.out.println(b);
//                }
//            });
//        }catch (RejectedExecutionException e){
//            log.info("线程池拒绝处理",e);
////            e.printStackTrace();
//        }catch(Exception e){
//            log.info("线程池异常",e);
//        }
//
//    }
//}
}
