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
package io.deepcover.agent;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.jvm.sandbox.api.Information;
import com.alibaba.jvm.sandbox.api.Module;
import com.alibaba.jvm.sandbox.api.ModuleLifecycle;
import com.alibaba.jvm.sandbox.api.annotation.Command;
import com.alibaba.jvm.sandbox.api.resource.ModuleEventWatcher;
import io.deepcover.agent.config.DeepCoverConfig;
import io.deepcover.agent.config.ExecutorThreadPoolConfig;
import io.deepcover.agent.config.kafka.KafkaProducerEngine;
import io.deepcover.agent.config.queue.LocalAsyncConfig;
import io.deepcover.agent.entity.ReportServerEntity;
import io.deepcover.agent.ext.CodeEventWatcher;
import io.deepcover.agent.util.MetricsCollector;
import io.deepcover.agent.util.http.HttpClient2;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.MetaInfServices;

import javax.annotation.Resource;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @description: 代码行采集器
 * @author: DeepCover Contributors
 * @time: 2023/3/9 20:19
 */

@MetaInfServices(Module.class)
@Information(id = "deepcover", version = "0.0.3", author = "DeepCover Contributors")
@Slf4j
public class CodeCollecter implements Module, ModuleLifecycle {

    @Resource
    private ModuleEventWatcher moduleEventWatcher;

    public static CodeEventWatcher codeEventWatcher;

    @Override
    public void onLoad() {
        log.info("CodeCollecter onLoad");
    }

    @Override
    public void onUnload() {
        log.info("CodeCollecter onUnload");
        ExecutorThreadPoolConfig.scheduleEx.shutdown();
        ExecutorThreadPoolConfig.executorSendMsg.shutdown();
        try {
            if (!ExecutorThreadPoolConfig.executorSendMsg.awaitTermination(5, TimeUnit.SECONDS)) {
                ExecutorThreadPoolConfig.executorSendMsg.shutdownNow();
                log.warn("executorSendMsg did not terminate in 5s, forced shutdown");
            }
            if (!ExecutorThreadPoolConfig.scheduleEx.awaitTermination(5, TimeUnit.SECONDS)) {
                ExecutorThreadPoolConfig.scheduleEx.shutdownNow();
                log.warn("scheduleEx did not terminate in 5s, forced shutdown");
            }
        } catch (InterruptedException e) {
            ExecutorThreadPoolConfig.executorSendMsg.shutdownNow();
            ExecutorThreadPoolConfig.scheduleEx.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LocalAsyncConfig.shutdown();
        HttpClient2.shutdown();
        KafkaProducerEngine.shutdown();
    }

    @Override
    public void onActive() {
        log.info("CodeCollecter onActive");
    }

    @Override
    public void onFrozen() {
        log.info("CodeCollecter onFrozen");
    }

    @Override
    public void loadCompleted() {
        log.info("CodeCollecter start loadCompleted");
        if(!DeepCoverConfig.init()){
            log.error("配置初始化失败");
            return;
        }
//        new CodeModule(moduleEventWatcher).start();
        ExecutorThreadPoolConfig.scheduleEx.schedule(new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("code-module-load-"+Thread.currentThread().getId());
                new HttpCodeModule(moduleEventWatcher).run();
            }
        }), 0l,TimeUnit.SECONDS);
        //异步线程，unload时候一定要shutdown无法关掉
//        ExecutorThreadPoolConfig.scheduleEx.submit(() ->{
//                Thread.currentThread().setName("module-"+Thread.currentThread().getName());
//                new HttpCodeModule(moduleEventWatcher).run();
//            }
//        );
//        ExecutorThreadPoolConfig.executor.shutdown();
//        try { // 设置等待时间等待子线程运行完毕
//
//            if(!ExecutorThreadPoolConfig.executor.awaitTermination(2000, TimeUnit.MILLISECONDS)){ // 等待时间内子线程并未全部运行完毕就直接关闭
//
//                ExecutorThreadPoolConfig.executor.shutdownNow();
//
//            }
//
//        }catch(InterruptedException e){
//            ExecutorThreadPoolConfig.executor.shutdownNow();
//
//        }
//
//        System.out.println("main thread finished");
        if (DeepCoverConfig.configCenterEnabled && StringUtils.isNotBlank(DeepCoverConfig.configCenterAddr)) {
            ExecutorThreadPoolConfig.scheduleEx.scheduleAtFixedRate(new Thread(new Runnable() {

            @Override
            public void run() {
                Thread.currentThread().setName("code-module-sync-"+Thread.currentThread().getId());
                reportServerInfo();
            }
            }), DeepCoverConfig.reportPeriod,DeepCoverConfig.reportPeriod, TimeUnit.SECONDS);
        }

    }

    @Command("startCodeModule")
    public void startCodeModule (){
        log.info("code module thread start");
        if(CodeCollecter.codeEventWatcher!=null){
            CodeCollecter.codeEventWatcher.onUnWatched();
        }
        //异步线程，unload时候一定要shutdown无法关掉
//        ExecutorThreadPoolConfig.scheduleEx.submit(() ->{
//                    Thread.currentThread().setName("module-"+Thread.currentThread().getName());
//                    new HttpCodeModule(moduleEventWatcher).run();
//                }
//        );
        ExecutorThreadPoolConfig.scheduleEx.schedule(new Thread(new Runnable() {
            @Override
            public void run() {
//                Thread.currentThread().setName("code-module-"+Thread.currentThread().getName());
                new HttpCodeModule(moduleEventWatcher).run();
            }
        }), 0l,TimeUnit.SECONDS);


        log.info("code module thread finished");
    }

    /**
     * 彻底卸载，必须重启才能再次采集
     * @param param
     * @param writer
     */
    @Command("unloadCodeModule")
    public void unloadCodeModule (final Map<String, String> param,final PrintWriter writer){
        log.info("unload code module thread start");
        String serviceName = param.get("serviceName");
        if(serviceName==null || !serviceName.equals(DeepCoverConfig.serviceName)){
            writer.println(String.format("[传入应用=%s] 和[本应用=%s] 不匹配 ，unload failed.", serviceName,DeepCoverConfig.serviceName));
            writer.flush();
            writer.close();
            log.error("unload code module thread failed");
            return;
        }
        if(CodeCollecter.codeEventWatcher!=null){
            CodeCollecter.codeEventWatcher.onUnWatched();
        }

        ExecutorThreadPoolConfig.scheduleEx.shutdown();
        ExecutorThreadPoolConfig.executorSendMsg.shutdown();
        try {
            if (!ExecutorThreadPoolConfig.executorSendMsg.awaitTermination(5, TimeUnit.SECONDS)) {
                ExecutorThreadPoolConfig.executorSendMsg.shutdownNow();
                log.warn("executorSendMsg did not terminate in 5s, forced shutdown");
            }
            if (!ExecutorThreadPoolConfig.scheduleEx.awaitTermination(5, TimeUnit.SECONDS)) {
                ExecutorThreadPoolConfig.scheduleEx.shutdownNow();
                log.warn("scheduleEx did not terminate in 5s, forced shutdown");
            }
        } catch (InterruptedException e) {
            ExecutorThreadPoolConfig.executorSendMsg.shutdownNow();
            ExecutorThreadPoolConfig.scheduleEx.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LocalAsyncConfig.shutdown();
        HttpClient2.shutdown();
        KafkaProducerEngine.shutdown();
        writer.println(String.format("[应用=%s] unload success.", DeepCoverConfig.serviceName));
        writer.flush();
        writer.close();
        log.info("unload code module thread finished");
    }


    /**
     * 对外暴露的接口，map要是string,string，不然接受不到请求
     * @param param
     */
    @Command("syncConfig")
    public void syncConfig (final Map<String, String> param,final PrintWriter writer){
        log.info("开始同步配置信息:{}",param);
        Integer previousSampleRate = DeepCoverConfig.sampleRate;
        Integer previousExceptionThreshold = DeepCoverConfig.exceptionThreshold;
        Integer previousExceptionCalcTime = DeepCoverConfig.exceptionCalcTime;
        Integer previousExceptionPauseTime = DeepCoverConfig.exceptionPauseTime;
        Integer previousLimitCodeMethodSize = DeepCoverConfig.limitCodeMethodSize;
        Integer previousLimitCodeMethodLineSize = DeepCoverConfig.limitCodeMethodLineSize;
        Integer previousQueueMsgSize = DeepCoverConfig.queueMsgSize;
        Integer previousQueueRecycleTime = DeepCoverConfig.queueRecycleTime;
        Integer previousSendDataCenterType = DeepCoverConfig.sendDataCenterType;
        Integer previousConfigVersion = DeepCoverConfig.configVersion;
        String previousIgnoreUrls = DeepCoverConfig.ignoreUrls;
        try {
            List<String> applied = new ArrayList<>();
            List<String> restartRequired = new ArrayList<>();
            if (param.containsKey("sampleRate")) {
                int value = Integer.parseInt(param.get("sampleRate"));
                if (value < 0 || value > 10000) {
                    throw new IllegalArgumentException("sampleRate must be between 0 and 10000");
                }
                DeepCoverConfig.sampleRate = value;
                applied.add("sampleRate");
            }
            DeepCoverConfig.exceptionThreshold = updatePositiveInteger(param, "exceptionThreshold", DeepCoverConfig.exceptionThreshold, applied);
            DeepCoverConfig.exceptionCalcTime = updatePositiveInteger(param, "exceptionCalcTime", DeepCoverConfig.exceptionCalcTime, applied);
            DeepCoverConfig.exceptionPauseTime = updatePositiveInteger(param, "exceptionPauseTime", DeepCoverConfig.exceptionPauseTime, applied);
            DeepCoverConfig.limitCodeMethodSize = updatePositiveInteger(param, "limitCodeMethodSize", DeepCoverConfig.limitCodeMethodSize, applied);
            DeepCoverConfig.limitCodeMethodLineSize = updatePositiveInteger(param, "limitCodeMethodLineSize", DeepCoverConfig.limitCodeMethodLineSize, applied);
            DeepCoverConfig.queueMsgSize = updatePositiveInteger(param, "queueMsgSize", DeepCoverConfig.queueMsgSize, applied);
            DeepCoverConfig.queueRecycleTime = updatePositiveInteger(param, "queueRecycleTime", DeepCoverConfig.queueRecycleTime, applied);
            if (param.containsKey("ignoreUrls")) {
                DeepCoverConfig.ignoreUrls = param.get("ignoreUrls");
                applied.add("ignoreUrls");
            }
            Integer requestedSendDataCenterType = null;
            if (param.containsKey("sendDataCenterType")) {
                int value = Integer.parseInt(param.get("sendDataCenterType"));
                if (value != 1 && value != 2) {
                    throw new IllegalArgumentException("sendDataCenterType must be 1 or 2");
                }
                if (value == 1 && StringUtils.isBlank(DeepCoverConfig.dataCenterAddr)) {
                    throw new IllegalArgumentException("HTTP send type requires deepcover.dataCenterAddr");
                }
                if (value == 2 && (StringUtils.isBlank(DeepCoverConfig.KAFKA_BOOTSTRAP_SERVERS)
                        || StringUtils.isBlank(DeepCoverConfig.KAFKA_TOPIC))) {
                    throw new IllegalArgumentException("Kafka send type requires broker and topic");
                }
                requestedSendDataCenterType = value;
            }
            if (param.containsKey("configVersion")) {
                DeepCoverConfig.configVersion = Integer.parseInt(param.get("configVersion"));
                applied.add("configVersion");
            }
            if (requestedSendDataCenterType != null) {
                DeepCoverConfig.sendDataCenterType = requestedSendDataCenterType;
                if (requestedSendDataCenterType == 2) {
                    KafkaProducerEngine.initKafka();
                } else if (previousSendDataCenterType == 2) {
                    KafkaProducerEngine.shutdown();
                }
                applied.add("sendDataCenterType");
            }
            addRestartRequired(param, restartRequired, "reportPeriod", "ignoreAnnos", "ignoreClasses",
                    "ignoreMethods", "packageName", "queueNum", "queueSize");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("serviceName", DeepCoverConfig.serviceName);
            result.put("configVersion", DeepCoverConfig.configVersion);
            result.put("applied", applied);
            result.put("restartRequired", restartRequired);
            writer.println(JSONObject.toJSONString(result));

        }catch (Exception e){
            DeepCoverConfig.sampleRate = previousSampleRate;
            DeepCoverConfig.exceptionThreshold = previousExceptionThreshold;
            DeepCoverConfig.exceptionCalcTime = previousExceptionCalcTime;
            DeepCoverConfig.exceptionPauseTime = previousExceptionPauseTime;
            DeepCoverConfig.limitCodeMethodSize = previousLimitCodeMethodSize;
            DeepCoverConfig.limitCodeMethodLineSize = previousLimitCodeMethodLineSize;
            DeepCoverConfig.queueMsgSize = previousQueueMsgSize;
            DeepCoverConfig.queueRecycleTime = previousQueueRecycleTime;
            DeepCoverConfig.sendDataCenterType = previousSendDataCenterType;
            DeepCoverConfig.configVersion = previousConfigVersion;
            DeepCoverConfig.ignoreUrls = previousIgnoreUrls;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("serviceName", DeepCoverConfig.serviceName);
            result.put("configVersion", DeepCoverConfig.configVersion);
            result.put("error", e.getMessage());
            writer.println(JSONObject.toJSONString(result));
            log.error("更新配置失败,version={}",DeepCoverConfig.configVersion,e);
        }finally {
            writer.flush();
            writer.close();
        }


    }

    private Integer updatePositiveInteger(Map<String, String> param, String key, Integer currentValue, List<String> applied) {
        if (!param.containsKey(key)) {
            return currentValue;
        }
        int value = Integer.parseInt(param.get(key));
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        applied.add(key);
        return value;
    }

    private void addRestartRequired(Map<String, String> param, List<String> restartRequired, String... keys) {
        for (String key : keys) {
            if (param.containsKey(key)) {
                restartRequired.add(key);
            }
        }
    }

    /**
     * 暴露运行时监控指标
     * @param writer
     */
    @Command("metrics")
    public void metrics(final PrintWriter writer) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("serviceName", DeepCoverConfig.serviceName);
        metrics.put("env", DeepCoverConfig.env);
        metrics.put("uptimeSeconds", MetricsCollector.getUptimeSeconds());
        metrics.put("configVersion", DeepCoverConfig.configVersion);
        metrics.put("sampleRate", DeepCoverConfig.sampleRate);
        metrics.put("sendType", DeepCoverConfig.sendDataCenterType == 1 ? "HTTP"
                : DeepCoverConfig.sendDataCenterType == 2 ? "Kafka" : "UNKNOWN");

        // Request counters
        metrics.put("totalRequests", MetricsCollector.totalRequests.get());
        metrics.put("collectedRequests", MetricsCollector.collectedRequests.get());
        metrics.put("droppedRequests", MetricsCollector.droppedRequests.get());
        metrics.put("sampledOutRequests", MetricsCollector.sampledOutRequests.get());
        metrics.put("ignoredRequests", MetricsCollector.ignoredRequests.get());
        metrics.put("emptyRequests", MetricsCollector.emptyRequests.get());
        metrics.put("thresholdDroppedRequests", MetricsCollector.thresholdDroppedRequests.get());

        // Line collection
        metrics.put("totalLinesCollected", MetricsCollector.totalLinesCollected.get());
        metrics.put("methodThresholdReached", MetricsCollector.methodThresholdReached.get());

        // Send stats
        metrics.put("sendSuccess", MetricsCollector.sendSuccess.get());
        metrics.put("sendFailed", MetricsCollector.sendFailed.get());
        metrics.put("queueOfferFailed", MetricsCollector.queueOfferFailed.get());
        metrics.put("queueDepth", LocalAsyncConfig.getQueueDepth());
        metrics.put("queueCapacity", LocalAsyncConfig.getQueueCapacity());
        metrics.put("queueCount", LocalAsyncConfig.getQueueCount());
        metrics.put("queueRunning", LocalAsyncConfig.isRunning());

        // Circuit breaker
        metrics.put("circuitBreakerTripped", MetricsCollector.circuitBreakerTripped.get());
        metrics.put("circuitBreakerDroppedRequests", MetricsCollector.circuitBreakerDroppedRequests.get());
        metrics.put("circuitBreakerPaused", DeepCoverConfig.exceptionThresholdTime > 0
                && (System.currentTimeMillis() - DeepCoverConfig.exceptionThresholdTime) < DeepCoverConfig.exceptionPauseTime * 1000L);

        writer.println(JSONObject.toJSONString(metrics));
        writer.flush();
        writer.close();
    }

    // 输出信息到客户端
    private void output(final PrintWriter writer, final String format, final Object... objectArray) {
        writer.println(String.format(format, objectArray));
    }

    public void reportServerInfo(){
        if (!DeepCoverConfig.configCenterEnabled || StringUtils.isBlank(DeepCoverConfig.configCenterAddr)) {
            return;
        }
        try {
            ReportServerEntity serverEntity = new ReportServerEntity();
            //获取本机域名
            String hostName = InetAddress.getLocalHost().getHostName();
            //用域名创建 InetAddress对象
            InetAddress address = InetAddress.getByName(hostName);
            //获取的是该网站的ip地址，如果我们所有的请求都通过nginx的，所以这里获取到的其实是nginx服务器的IP地址
            serverEntity.setIp(address.getHostAddress());
            serverEntity.setVersion(DeepCoverConfig.configVersion);
            serverEntity.setServiceName(DeepCoverConfig.serviceName);
            serverEntity.setEnvCode(DeepCoverConfig.envCode);

            String url = DeepCoverConfig.configCenterAddr+DeepCoverConfig.reportServerInfo;
            HttpRequest httpRequest = HttpRequest.post(url).body(JSONObject.toJSONString(serverEntity)).timeout(5000);
            HttpResponse httpResponse = httpRequest.execute();
            int code = httpResponse.getStatus();
            if (code != 200) {
                //monitor,后期改成warn
                log.warn("上报服务信息发送失败,url:{},code:{}", url,code);
            }else{
                JSONObject info=JSONObject.parseObject(httpResponse.body()).getJSONObject("info");
                if(info==null){
                    log.error("service_name:{},未在配置中心设置",DeepCoverConfig.serviceName);
                }else if(info.getInteger("version")>DeepCoverConfig.configVersion) {
                    String packageName = DeepCoverConfig.packageName;
                    String ignoreClasses = DeepCoverConfig.ignoreClasses;
                    String ignoreMethods = DeepCoverConfig.ignoreMethods;
                    String ignoreAnnos = DeepCoverConfig.ignoreAnnos;
                    Integer queueNum = DeepCoverConfig.queueNum;
                    Integer queueSize = DeepCoverConfig.queueSize;
                    Integer reportPeriod = DeepCoverConfig.reportPeriod;
                    DeepCoverConfig.applyRemoteConfig(info);
                    List<String> restartRequired = new ArrayList<>();
                    if (!packageName.equals(DeepCoverConfig.packageName)) restartRequired.add("packageName");
                    if (!ignoreClasses.equals(DeepCoverConfig.ignoreClasses)) restartRequired.add("ignoreClasses");
                    if (!ignoreMethods.equals(DeepCoverConfig.ignoreMethods)) restartRequired.add("ignoreMethods");
                    if (!ignoreAnnos.equals(DeepCoverConfig.ignoreAnnos)) restartRequired.add("ignoreAnnos");
                    if (!queueNum.equals(DeepCoverConfig.queueNum)) restartRequired.add("queueNum");
                    if (!queueSize.equals(DeepCoverConfig.queueSize)) restartRequired.add("queueSize");
                    if (!reportPeriod.equals(DeepCoverConfig.reportPeriod)) restartRequired.add("reportPeriod");
                    DeepCoverConfig.packageName = packageName;
                    DeepCoverConfig.ignoreClasses = ignoreClasses;
                    DeepCoverConfig.ignoreMethods = ignoreMethods;
                    DeepCoverConfig.ignoreAnnos = ignoreAnnos;
                    DeepCoverConfig.queueNum = queueNum;
                    DeepCoverConfig.queueSize = queueSize;
                    DeepCoverConfig.reportPeriod = reportPeriod;
                    if (DeepCoverConfig.sendDataCenterType == 2) {
                        KafkaProducerEngine.initKafka();
                    }
                    log.warn("deepcover动态配置已更新,restartRequired={}", restartRequired);
                }
            }

            log.debug("当前线程：" + Thread.currentThread().getName() + " 当前时间" + LocalDateTime.now());
        }catch (Exception e){
            log.warn("上报服务信息请求异常,url={}", DeepCoverConfig.configCenterAddr+DeepCoverConfig.reportServerInfo,e);
        }
    }
}
