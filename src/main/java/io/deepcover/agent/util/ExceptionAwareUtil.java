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
package io.deepcover.agent.util;

import io.deepcover.agent.config.DeepCoverConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * 异常感知器
 * </p>
 *
 * @author yingzhu
 */
public class ExceptionAwareUtil {

    private final static Logger log = LoggerFactory.getLogger(ExceptionAwareUtil.class);

    private static AtomicLong counter = new AtomicLong(0);

    private static Long firstTime=0L;

    private static Map<String, AtomicInteger> errorCached = new HashMap<String, AtomicInteger>();

    public static void clear(){
        counter=new AtomicLong(0);
        errorCached = new HashMap<String, AtomicInteger>();
    }
    /**
     * 异常阈值检测
     *
     * @param throwable 异常类型
     */
//    public void exceptionOverflow(Throwable throwable) {
//        if (ea.exceptionOverflow(throwable, exceptionThreshold == null ? 10 : exceptionThreshold)) {
//            fusing = true;
//            ea.printErrorLog();
//        }
//    }

    /**
     * 异常超出阈值
     *
     * @param throwable          异常信息
     * @return 是否超过阈值
     */
    public static void exceptionOverflow(Throwable throwable) {
        if(counter.get()==0){
            firstTime = System.currentTimeMillis();
        }else if (System.currentTimeMillis()-firstTime>DeepCoverConfig.exceptionCalcTime*60*1000){
            clear();
            firstTime = System.currentTimeMillis();
        }
        String message = throwable.getMessage();
        if (StringUtils.isEmpty(message)) {
            message = throwable.getClass().getCanonicalName();
        }
        AtomicInteger ai = errorCached.get(message);
        if (ai == null) {
            ai = new AtomicInteger(0);
            errorCached.put(message, ai);
        }
        ai.incrementAndGet();
        if(counter.incrementAndGet() == DeepCoverConfig.exceptionThreshold){
            DeepCoverConfig.exceptionThresholdTime=System.currentTimeMillis();
            MetricsCollector.circuitBreakerTripped.incrementAndGet();
            printErrorLog();
        }
//        return counter.incrementAndGet() >= exceptionThreshold;
    }

    /**
     * 打印错误日志
     */
    public static void printErrorLog() {
//        log.error("Exception count overflow,current count is ({})", counter.get());
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, AtomicInteger> entry : errorCached.entrySet()) {
            builder.append("[").append(entry.getKey()).append("];count[").append(entry.getValue().get()).append("]\n\r");
        }
        log.error("采集暂停:{}分钟,异常总数:{}>={},异常原因:{}",DeepCoverConfig.exceptionPauseTime,counter.get(),DeepCoverConfig.exceptionThreshold,builder.toString());
    }
}
