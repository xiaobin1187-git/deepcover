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
import io.deepcover.agent.entity.ReportServerEntity;
import io.deepcover.agent.ext.CodeEventWatcher;
import io.deepcover.agent.util.MetricsCollector;
import io.deepcover.agent.util.http.HttpClient2;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.MetaInfServices;

import javax.annotation.Resource;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @description: 代码行采集器
 * @author: wuchen
 * @time: 2023/3/9 20:19
 */

@MetaInfServices(Module.class)
@Information(id = "deepcover", version = "0.0.3", author = "wuchen")
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
        ExecutorThreadPoolConfig.scheduleEx.scheduleAtFixedRate(new Thread(new Runnable() {

            @Override
            public void run() {
                Thread.currentThread().setName("code-module-sync-"+Thread.currentThread().getId());
                reportServerInfo();
            }
        }), DeepCoverConfig.reportPeriod,DeepCoverConfig.reportPeriod, TimeUnit.SECONDS);

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
        try {
            DeepCoverConfig.sampleRate=param.get("sampleRate")==null?DeepCoverConfig.sampleRate:Integer.valueOf(param.get("sampleRate"));
            DeepCoverConfig.exceptionThreshold=param.get("exceptionThreshold")==null?DeepCoverConfig.exceptionThreshold:Integer.valueOf(param.get("exceptionThreshold"));
            DeepCoverConfig.exceptionCalcTime=param.get("exceptionCalcTime")==null?DeepCoverConfig.exceptionCalcTime:Integer.valueOf(param.get("exceptionCalcTime"));
            DeepCoverConfig.exceptionPauseTime=param.get("exceptionPauseTime")==null?DeepCoverConfig.exceptionPauseTime:Integer.valueOf(param.get("exceptionPauseTime"));
            DeepCoverConfig.reportPeriod=param.get("reportPeriod")==null?DeepCoverConfig.reportPeriod:Integer.valueOf(param.get("reportPeriod"));
            DeepCoverConfig.ignoreAnnos=param.getOrDefault("ignoreAnnos",DeepCoverConfig.ignoreAnnos);
            DeepCoverConfig.ignoreUrls=param.getOrDefault("ignoreUrls",DeepCoverConfig.ignoreUrls);
            DeepCoverConfig.ignoreClasses=param.getOrDefault("ignoreClasses",DeepCoverConfig.ignoreClasses);
            DeepCoverConfig.ignoreMethods=param.getOrDefault("ignoreMethods",DeepCoverConfig.ignoreMethods);
            DeepCoverConfig.packageName=param.getOrDefault("packageName",DeepCoverConfig.packageName);
            DeepCoverConfig.configVersion=param.get("configVersion")==null?DeepCoverConfig.configVersion:Integer.valueOf(param.get("configVersion"));
            DeepCoverConfig.limitCodeMethodSize=param.get("limitCodeMethodSize")==null?DeepCoverConfig.limitCodeMethodSize:Integer.valueOf(param.get("limitCodeMethodSize"));
            DeepCoverConfig.limitCodeMethodLineSize=param.get("limitCodeMethodLineSize")==null?DeepCoverConfig.limitCodeMethodLineSize:Integer.valueOf(param.get("limitCodeMethodLineSize"));
            DeepCoverConfig.sendDataCenterType=param.get("sendDataCenterType")==null?DeepCoverConfig.sendDataCenterType:Integer.valueOf(param.get("sendDataCenterType"));
            DeepCoverConfig.queueNum=param.get("queueNum")==null?DeepCoverConfig.queueNum:Integer.valueOf(param.get("queueNum"));
            DeepCoverConfig.queueSize=param.get("queueSize")==null?DeepCoverConfig.queueSize:Integer.valueOf(param.get("queueSize"));
            DeepCoverConfig.queueMsgSize=param.get("queueMsgSize")==null?DeepCoverConfig.queueMsgSize:Integer.valueOf(param.get("queueMsgSize"));
            DeepCoverConfig.queueRecycleTime=param.get("queueRecycleTime")==null?DeepCoverConfig.queueRecycleTime:Integer.valueOf(param.get("queueRecycleTime"));

            writer.println(String.format("[%s][version=%s] syncConfig success.", DeepCoverConfig.serviceName,DeepCoverConfig.configVersion));

        }catch (Exception e){
            writer.println(String.format("[%s][version=%s] syncConfig failed.", DeepCoverConfig.serviceName,DeepCoverConfig.configVersion));
            log.error("更新配置失败,version={},{}",DeepCoverConfig.configVersion,e);
        }finally {
            writer.flush();
            writer.close();
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
        metrics.put("sendType", DeepCoverConfig.sendDataCenterType == 1 ? "HTTP" : "Kafka");

        // Request counters
        metrics.put("totalRequests", MetricsCollector.totalRequests.get());
        metrics.put("collectedRequests", MetricsCollector.collectedRequests.get());
        metrics.put("droppedRequests", MetricsCollector.droppedRequests.get());

        // Line collection
        metrics.put("totalLinesCollected", MetricsCollector.totalLinesCollected.get());
        metrics.put("methodThresholdReached", MetricsCollector.methodThresholdReached.get());

        // Send stats
        metrics.put("sendSuccess", MetricsCollector.sendSuccess.get());
        metrics.put("sendFailed", MetricsCollector.sendFailed.get());
        metrics.put("queueOfferFailed", MetricsCollector.queueOfferFailed.get());

        // Circuit breaker
        metrics.put("circuitBreakerTripped", MetricsCollector.circuitBreakerTripped.get());
        metrics.put("circuitBreakerPaused", DeepCoverConfig.exceptionThresholdTime > 0
                && (System.currentTimeMillis() - DeepCoverConfig.exceptionThresholdTime) < DeepCoverConfig.exceptionPauseTime * 1000);

        writer.println(JSONObject.toJSONString(metrics));
        writer.flush();
        writer.close();
    }

    // 输出信息到客户端
    private void output(final PrintWriter writer, final String format, final Object... objectArray) {
        writer.println(String.format(format, objectArray));
    }

    public void reportServerInfo(){
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
                    log.warn("deepcover配置被修改，部分配置进行变更:{}",info.toString());

                    DeepCoverConfig.sampleRate=(Integer) info.getOrDefault("sampleRate",DeepCoverConfig.sampleRate);
                    //异常熔断规则
                    DeepCoverConfig.exceptionThreshold=(Integer) info.getOrDefault("exceptionThreshold",10);
                    DeepCoverConfig.exceptionCalcTime=(Integer) info.getOrDefault("exceptionCalcTime",1);
                    DeepCoverConfig.exceptionPauseTime=(Integer) info.getOrDefault("exceptionPauseTime",5);

                    DeepCoverConfig.reportPeriod=(Integer) info.getOrDefault("reportPeriod",DeepCoverConfig.reportPeriod);
                    DeepCoverConfig.ignoreAnnos=(String) info.getOrDefault("ignoreAnnos",DeepCoverConfig.ignoreAnnos);
                    DeepCoverConfig.ignoreUrls=(String) info.getOrDefault("ignoreUrls",DeepCoverConfig.ignoreUrls);
                    DeepCoverConfig.configVersion=info.getInteger("version");
                    DeepCoverConfig.limitCodeMethodSize=(Integer) info.getOrDefault("limitCodeMethodSize",DeepCoverConfig.limitCodeMethodSize);
                    DeepCoverConfig.limitCodeMethodLineSize=(Integer) info.getOrDefault("limitCodeMethodLineSize",DeepCoverConfig.limitCodeMethodLineSize);
                    DeepCoverConfig.sendDataCenterType=(Integer) info.getOrDefault("sendDataCenterType",2);

                    DeepCoverConfig.queueMsgSize=(Integer) info.getOrDefault("queueMsgSize",50);
                    DeepCoverConfig.queueRecycleTime=(Integer) info.getOrDefault("queueRecycleTime",10);

                    int queueNum = (Integer) info.getOrDefault("queueNum",1);
                    int queueSize = (Integer) info.getOrDefault("queueSize",100);
                    int queueMsgSize = (Integer) info.getOrDefault("queueMsgSize",50);
                    DeepCoverConfig.queueNum=queueNum;
                    DeepCoverConfig.queueSize=queueSize;
                    DeepCoverConfig.queueMsgSize = queueMsgSize;
                    //                    if(CodeCoverageCollecter.codeEventWatcher!=null){
//                        CodeCoverageCollecter.codeEventWatcher.onUnWatched();
//                    }
//                    ExecutorThreadPoolConfig.scheduleEx.schedule(new Thread(new Runnable() {
//                        @Override
//                        public void run() {
//                            Thread.currentThread().setName("code-module-"+Thread.currentThread().getName());
//                            new HttpCodeModule(moduleEventWatcher).run();
//                        }
//                    }), 0l,TimeUnit.SECONDS);
                }
            }

            log.debug("当前线程：" + Thread.currentThread().getName() + " 当前时间" + LocalDateTime.now());
        }catch (Exception e){
            log.warn("上报服务信息请求异常,url={},errMsg：{}", DeepCoverConfig.configCenterAddr+DeepCoverConfig.reportServerInfo,e.getMessage());
        }
    }
}
