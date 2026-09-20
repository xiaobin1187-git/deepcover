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

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONObject;
import io.deepcover.agent.config.kafka.KafkaProducerEngine;
import io.deepcover.agent.config.queue.LocalAsyncConfig;
import io.deepcover.agent.util.IpUtil;
import io.deepcover.agent.util.PropertyUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class DeepCoverConfig {
    //下面是获取启动功能时，传入的代码分支，以及服务名称和测试环境信息。其中服务名称和测试环境信息，和服务部署有关，需要按实际情况而定。
    public final static String branch = PropertyUtil.getSystemPropertyOrDefault("branch", "master");
    public final static String serviceName = PropertyUtil.getSystemPropertyOrDefault("app.name", PropertyUtil.getEnvPropertyOrDefault("APP_NAME","unknown"));
    //区分发布平台环境code
    public final static String envCode = PropertyUtil.getSystemPropertyOrDefault("env.code", PropertyUtil.getEnvPropertyOrDefault("env_name","unknown"));
    //区分采集的环境，决定后续采集的信息发送到对应数据中心
    public static String env;

    public static String dataCenterAddr = "";
    public static String configCenterAddr = "";
    public static String getDeepCoverInfo = "/deepcover-brain/deepcover/info";
    public static String reportServerInfo = "/deepcover-brain/deepcover/report/server/info";
    public static boolean configCenterEnabled = true;
    public static Integer limitCodeMethodSize = 500;
    public static Integer limitCodeMethodLineSize = 500;
    public static Integer sendDataCenterType = 1;

    public static String KAFKA_BOOTSTRAP_SERVERS = "";
    public static String KAFKA_TOPIC = "";

    static {// properties文件略
        env =PropertyUtil.getSystemPropertyOrDefault("deepcover.env", PropertyUtil.getSystemPropertyOrDefault("env","unknown")).toLowerCase();
        if(!env.equals("unknown")){
            try (InputStream inputStream = DeepCoverConfig.class.getResourceAsStream("/deepcover.properties")) {
                if (inputStream != null) {
                    Properties pro = new Properties();
                    pro.load(inputStream);
                    dataCenterAddr = pro.getProperty(env+".dataCenterAddr", dataCenterAddr);
                    configCenterAddr=pro.getProperty(env+".configCenterAddr", configCenterAddr);
                    getDeepCoverInfo=pro.getProperty("getDeepCoverInfo", getDeepCoverInfo);
                    reportServerInfo=pro.getProperty("reportServerInfo", reportServerInfo);
                    KAFKA_BOOTSTRAP_SERVERS=pro.getProperty(env+".KAFKA_BOOTSTRAP_SERVERS", KAFKA_BOOTSTRAP_SERVERS);
                    KAFKA_TOPIC=pro.getProperty(env+".KAFKA_TOPIC", KAFKA_TOPIC);
                } else {
                    log.info("deepcover.properties not found, using JVM system properties");
                }
            } catch (IOException e) {
                log.error("读取deepcover本地配置文件异常",e);
            }
        }
    }
    public static String packageName = "";
    /**
     * 异常发生阈值；默认1分钟10个
     * 当{@code ExceptionAware} 感知到异常次数超过阈值后，会降级模块
     */
    public static Integer exceptionThreshold = 10;

    public static Integer exceptionCalcTime = 1;

    public static Integer exceptionPauseTime = 5;
    /**
     * 熔断之后，间隔检测时间
     */
    public static volatile long exceptionThresholdTime=0l ;
    /**
     * 采样率；最小力度万分之一
     * 10000 代表 100%
     */
    public static Integer sampleRate = 10000;

    /**
     * 节点状态上报周期
     */
    public static Integer reportPeriod = 10;

    /**
     * 过滤不需要采集的url地址，多个用分号隔开
     * 例如"/health;"
     */
    public static String ignoreUrls = "";

    /**
     * 过滤不需要采集的class，支持正则，多个用分号隔开
     * 例如"com.example.*.model.*";最后一个不能加';'
     */
    public static String ignoreClasses = "";

    public static String ignoreMethods = "";

    public static String ignoreAnnos = "";

    public static Integer configVersion = 0;

    public static Integer queueNum = 1;

    public static Integer queueSize = 100;

    public static Integer queueMsgSize = 50;

    public static Integer queueRecycleTime = 10;

    /**
     * 获取上报pod的本地ip
     */
    public static String localIp= IpUtil.getLocalIp();

    //after事件后的批量发送 处理后的覆盖行信息
    //后续改成异步
    public static boolean init() {
        applySystemProperties();
        if(serviceName.equals("unknown")){
            log.error("启动失败：app.name请先在JVM启动参数中配置");
            return false;
        }
        if(env.equals("unknown")){
            log.error("启动失败：env或deepcover.env没有配置");
            return false;
        }
        if (configCenterEnabled && StringUtils.isNotBlank(configCenterAddr)) {
            if (!loadFromConfigCenter()) {
                return false;
            }
        }
        if (!validate()) {
            return false;
        }
        log.info("env: {}, serviceName: {}, configSource: {}", env, serviceName,
                !configCenterEnabled || StringUtils.isBlank(configCenterAddr) ? "local" : "config-center");
        if (sendDataCenterType == 2) {
            KafkaProducerEngine.initKafka();
        }
        LocalAsyncConfig.init();
        return true;
    }

    private static boolean loadFromConfigCenter() {
        String url = configCenterAddr + getDeepCoverInfo + "/" + serviceName;
        try {
            log.info("开始初始化配置，访问配置中心: {}", url);
            HttpResponse httpResponse = HttpRequest.get(url).timeout(1000).execute();
            if (httpResponse.getStatus() != 200) {
                log.error("配置中心请求失败,url={},code={}", url, httpResponse.getStatus());
                return false;
            }
            JSONObject response = JSONObject.parseObject(httpResponse.body());
            JSONObject info = response == null ? null : response.getJSONObject("info");
            if (info == null) {
                log.error("service_name:{},未在配置中心设置", serviceName);
                return false;
            }
            applyRemoteConfig(info);
            return true;
        } catch (Exception e) {
            log.error("配置中心请求异常,url={}", url, e);
            return false;
        }
    }

    public static void applyRemoteConfig(JSONObject info) {
        if (info == null) {
            throw new IllegalArgumentException("remote config must not be null");
        }

        Integer candidateSampleRate = remoteInteger(info, "sampleRate", sampleRate);
        Integer candidateExceptionThreshold = remoteInteger(info, "exceptionThreshold", exceptionThreshold);
        Integer candidateExceptionCalcTime = remoteInteger(info, "exceptionCalcTime", exceptionCalcTime);
        Integer candidateExceptionPauseTime = remoteInteger(info, "exceptionPauseTime", exceptionPauseTime);
        Integer candidateReportPeriod = remoteInteger(info, "reportPeriod", reportPeriod);
        String candidateIgnoreClasses = normalizePatterns(info.getString("ignoreClasses"), ignoreClasses);
        String candidateIgnoreMethods = normalizePatterns(info.getString("ignoreMethods"), ignoreMethods);
        String candidateIgnoreAnnos = normalizePatterns(info.getString("ignoreAnnos"), ignoreAnnos);
        String candidateIgnoreUrls = normalizePatterns(info.getString("ignoreUrls"), ignoreUrls);
        Integer candidateConfigVersion = remoteInteger(info, "version", configVersion);
        String candidatePackageName = StringUtils.defaultIfBlank(info.getString("packageName"), packageName);
        Integer candidateLimitCodeMethodSize = remoteInteger(info, "limitCodeMethodSize", limitCodeMethodSize);
        Integer candidateLimitCodeMethodLineSize = remoteInteger(info, "limitCodeMethodLineSize", limitCodeMethodLineSize);
        Integer candidateSendDataCenterType = remoteInteger(info, "sendDataCenterType", sendDataCenterType);
        Integer candidateQueueNum = remoteInteger(info, "queueNum", queueNum);
        Integer candidateQueueSize = remoteInteger(info, "queueSize", queueSize);
        Integer candidateQueueMsgSize = remoteInteger(info, "queueMsgSize", queueMsgSize);
        Integer candidateQueueRecycleTime = remoteInteger(info, "queueRecycleTime", queueRecycleTime);

        String validationError = validationError(candidatePackageName, candidateSampleRate,
                candidateExceptionThreshold, candidateExceptionCalcTime, candidateExceptionPauseTime,
                candidateReportPeriod, candidateLimitCodeMethodSize, candidateLimitCodeMethodLineSize,
                candidateSendDataCenterType, candidateQueueNum, candidateQueueSize,
                candidateQueueMsgSize, candidateQueueRecycleTime);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        sampleRate = candidateSampleRate;
        exceptionThreshold = candidateExceptionThreshold;
        exceptionCalcTime = candidateExceptionCalcTime;
        exceptionPauseTime = candidateExceptionPauseTime;
        reportPeriod = candidateReportPeriod;
        ignoreClasses = candidateIgnoreClasses;
        ignoreMethods = candidateIgnoreMethods;
        ignoreAnnos = candidateIgnoreAnnos;
        ignoreUrls = candidateIgnoreUrls;
        configVersion = candidateConfigVersion;
        packageName = candidatePackageName;
        limitCodeMethodSize = candidateLimitCodeMethodSize;
        limitCodeMethodLineSize = candidateLimitCodeMethodLineSize;
        sendDataCenterType = candidateSendDataCenterType;
        queueNum = candidateQueueNum;
        queueSize = candidateQueueSize;
        queueMsgSize = candidateQueueMsgSize;
        queueRecycleTime = candidateQueueRecycleTime;
    }

    private static void applySystemProperties() {
        dataCenterAddr = PropertyUtil.getSystemPropertyOrDefault("deepcover.dataCenterAddr", dataCenterAddr);
        configCenterAddr = PropertyUtil.getSystemPropertyOrDefault("deepcover.configCenterAddr", configCenterAddr);
        configCenterEnabled = Boolean.parseBoolean(PropertyUtil.getSystemPropertyOrDefault("deepcover.configCenterEnabled", String.valueOf(configCenterEnabled)));
        getDeepCoverInfo = PropertyUtil.getSystemPropertyOrDefault("deepcover.getDeepCoverInfo", getDeepCoverInfo);
        reportServerInfo = PropertyUtil.getSystemPropertyOrDefault("deepcover.reportServerInfo", reportServerInfo);
        KAFKA_BOOTSTRAP_SERVERS = PropertyUtil.getSystemPropertyOrDefault("deepcover.kafkaBootstrapServers", KAFKA_BOOTSTRAP_SERVERS);
        KAFKA_TOPIC = PropertyUtil.getSystemPropertyOrDefault("deepcover.kafkaTopic", KAFKA_TOPIC);
        packageName = PropertyUtil.getSystemPropertyOrDefault("deepcover.packageName", packageName);
        ignoreClasses = PropertyUtil.getSystemPropertyOrDefault("deepcover.ignoreClasses", ignoreClasses);
        ignoreMethods = PropertyUtil.getSystemPropertyOrDefault("deepcover.ignoreMethods", ignoreMethods);
        ignoreAnnos = PropertyUtil.getSystemPropertyOrDefault("deepcover.ignoreAnnos", ignoreAnnos);
        ignoreUrls = PropertyUtil.getSystemPropertyOrDefault("deepcover.ignoreUrls", ignoreUrls);
        sampleRate = integerProperty("deepcover.sampleRate", sampleRate);
        limitCodeMethodSize = integerProperty("deepcover.limitCodeMethodSize", limitCodeMethodSize);
        limitCodeMethodLineSize = integerProperty("deepcover.limitCodeMethodLineSize", limitCodeMethodLineSize);
        sendDataCenterType = integerProperty("deepcover.sendDataCenterType", sendDataCenterType);
        exceptionThreshold = integerProperty("deepcover.exceptionThreshold", exceptionThreshold);
        exceptionCalcTime = integerProperty("deepcover.exceptionCalcTime", exceptionCalcTime);
        exceptionPauseTime = integerProperty("deepcover.exceptionPauseTime", exceptionPauseTime);
        reportPeriod = integerProperty("deepcover.reportPeriod", reportPeriod);
        queueNum = integerProperty("deepcover.queueNum", queueNum);
        queueSize = integerProperty("deepcover.queueSize", queueSize);
        queueMsgSize = integerProperty("deepcover.queueMsgSize", queueMsgSize);
        queueRecycleTime = integerProperty("deepcover.queueRecycleTime", queueRecycleTime);
    }

    private static Integer integerProperty(String key, Integer defaultValue) {
        String value = System.getProperty(key);
        return StringUtils.isBlank(value) ? defaultValue : Integer.valueOf(value);
    }

    private static Integer remoteInteger(JSONObject info, String key, Integer defaultValue) {
        Integer value = info.getInteger(key);
        return value == null ? defaultValue : value;
    }

    private static String normalizePatterns(String value, String defaultValue) {
        String result = value == null ? defaultValue : value.trim();
        return result == null ? "" : result.replaceAll(";+$", "");
    }

    private static boolean validate() {
        String validationError = validationError(packageName, sampleRate, exceptionThreshold,
                exceptionCalcTime, exceptionPauseTime, reportPeriod, limitCodeMethodSize,
                limitCodeMethodLineSize, sendDataCenterType, queueNum, queueSize,
                queueMsgSize, queueRecycleTime);
        if (validationError == null) {
            return true;
        }
        log.error("启动失败：{}", validationError);
        return false;
    }

    private static String validationError(String candidatePackageName,
                                          Integer candidateSampleRate,
                                          Integer candidateExceptionThreshold,
                                          Integer candidateExceptionCalcTime,
                                          Integer candidateExceptionPauseTime,
                                          Integer candidateReportPeriod,
                                          Integer candidateLimitCodeMethodSize,
                                          Integer candidateLimitCodeMethodLineSize,
                                          Integer candidateSendDataCenterType,
                                          Integer candidateQueueNum,
                                          Integer candidateQueueSize,
                                          Integer candidateQueueMsgSize,
                                          Integer candidateQueueRecycleTime) {
        if (StringUtils.isBlank(candidatePackageName)) {
            return "请通过配置中心或 -Ddeepcover.packageName 配置采集包正则";
        }
        if (candidateSampleRate == null || candidateSampleRate < 0 || candidateSampleRate > 10000) {
            return "sampleRate 必须位于 0 到 10000 之间,current=" + candidateSampleRate;
        }
        if (!isPositive(candidateExceptionThreshold) || !isPositive(candidateExceptionCalcTime)
                || !isPositive(candidateExceptionPauseTime) || !isPositive(candidateReportPeriod)) {
            return "异常阈值、异常窗口、暂停时间和上报周期必须为正数";
        }
        if (!isPositive(candidateLimitCodeMethodSize) || !isPositive(candidateLimitCodeMethodLineSize)
                || !isPositive(candidateQueueNum) || !isPositive(candidateQueueSize)
                || !isPositive(candidateQueueMsgSize) || !isPositive(candidateQueueRecycleTime)) {
            return "采集阈值和队列参数必须为正数";
        }
        if (candidateSendDataCenterType == null
                || (candidateSendDataCenterType != 1 && candidateSendDataCenterType != 2)) {
            return "sendDataCenterType 仅支持 1(HTTP) 或 2(Kafka),current=" + candidateSendDataCenterType;
        }
        if (candidateSendDataCenterType == 1 && StringUtils.isBlank(dataCenterAddr)) {
            return "HTTP 模式需要配置 deepcover.dataCenterAddr";
        }
        if (candidateSendDataCenterType == 2
                && (StringUtils.isBlank(KAFKA_BOOTSTRAP_SERVERS) || StringUtils.isBlank(KAFKA_TOPIC))) {
            return "Kafka 模式需要配置 broker 和 topic";
        }
        return null;
    }

    private static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }
}
