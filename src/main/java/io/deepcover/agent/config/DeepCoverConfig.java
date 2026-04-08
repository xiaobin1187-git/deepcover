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

import java.io.IOException;
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

    public static String dataCenterAddr;
    public static String configCenterAddr;
    public static String getDeepCoverInfo;
    public static String reportServerInfo;
    public static Integer limitCodeMethodSize;
    public static Integer limitCodeMethodLineSize;
    public static Integer sendDataCenterType;

    public static String KAFKA_BOOTSTRAP_SERVERS;
    public static String KAFKA_TOPIC;

    static {// properties文件略
        env =PropertyUtil.getSystemPropertyOrDefault("deepcover.env", PropertyUtil.getSystemPropertyOrDefault("env","unknown")).toLowerCase();
        if(!env.equals("unknown")){
            Properties pro = new Properties();
            try {
                pro.load(DeepCoverConfig.class.getResourceAsStream("/deepcover.properties"));
                dataCenterAddr = pro.getProperty(env+".dataCenterAddr");
                configCenterAddr=pro.getProperty(env+".configCenterAddr");
                getDeepCoverInfo=pro.getProperty("getDeepCoverInfo");
                reportServerInfo=pro.getProperty("reportServerInfo");;
                KAFKA_BOOTSTRAP_SERVERS=pro.getProperty(env+".KAFKA_BOOTSTRAP_SERVERS");
                KAFKA_TOPIC=pro.getProperty(env+".KAFKA_TOPIC");
            } catch (IOException e) {
                log.error("读取deepcover本地配置文件异常",e);
            }
        }
    }
    public static String packageName;
    /**
     * 异常发生阈值；默认1分钟10个
     * 当{@code ExceptionAware} 感知到异常次数超过阈值后，会降级模块
     */
    public static Integer exceptionThreshold;

    public static Integer exceptionCalcTime;

    public static Integer exceptionPauseTime;
    /**
     * 熔断之后，间隔检测时间
     */
    public static volatile long exceptionThresholdTime=0l ;
    /**
     * 采样率；最小力度万分之一
     * 10000 代表 100%
     */
    public static Integer sampleRate;

    /**
     * 节点状态上报周期
     */
    public static Integer reportPeriod;

    /**
     * 过滤不需要采集的url地址，多个用分号隔开
     * 例如"/health;"
     */
    public static String ignoreUrls;

    /**
     * 过滤不需要采集的class，支持正则，多个用分号隔开
     * 例如"com.example.*.model.*";最后一个不能加';'
     */
    public static String ignoreClasses;

    public static String ignoreMethods;

    public static String ignoreAnnos;

    public static Integer configVersion;

    public static Integer queueNum;

    public static Integer queueSize;

    public static Integer queueMsgSize;

    public static Integer queueRecycleTime;

    /**
     * 获取上报pod的本地ip
     */
    public static String localIp= IpUtil.getLocalIp();

    //after事件后的批量发送 处理后的覆盖行信息
    //后续改成异步
    public static boolean init() {
        if(serviceName.equals("unknown")){
            log.error("启动失败：app.name请先在JVM启动参数中配置");
            return false;
        }
        if(env.equals("unknown")){
            log.error("启动失败：env或deepcover.env没有配置");
            return false;
        }
        try {
            String url =DeepCoverConfig.configCenterAddr+getDeepCoverInfo+"/"+serviceName;
            log.info("开始初始化配置，访问配置中心: {}", url);
            HttpRequest httpRequest = HttpRequest.get(url).timeout(1000);
            HttpResponse httpResponse = httpRequest.execute();
            int code = httpResponse.getStatus();
            if (code != 200) {
                log.error("请求失败：{}", code);
                return false;
            }else{
                log.info("请求返回报文：{}",httpResponse.body());
                JSONObject info=JSONObject.parseObject(httpResponse.body()).getJSONObject("info");
                if(info==null){
                    log.error("service_name:{},未在配置中心设置",serviceName);
                    return false;
                }
                DeepCoverConfig.sampleRate=(Integer) info.getOrDefault("sampleRate",100);
                //异常熔断规则
                DeepCoverConfig.exceptionThreshold=(Integer) info.getOrDefault("exceptionThreshold",10);
                DeepCoverConfig.exceptionCalcTime=(Integer) info.getOrDefault("exceptionCalcTime",1);
                DeepCoverConfig.exceptionPauseTime=(Integer) info.getOrDefault("exceptionPauseTime",5);

                DeepCoverConfig.reportPeriod=(Integer) info.getOrDefault("reportPeriod",10);
                String ignoreClasses =(String) info.getOrDefault("ignoreClasses","");
                DeepCoverConfig.ignoreClasses=ignoreClasses.endsWith(";")?ignoreClasses.substring(0,ignoreClasses.length()-2):ignoreClasses;
                DeepCoverConfig.ignoreMethods=(String) info.getOrDefault("ignoreMethods","");
                DeepCoverConfig.ignoreAnnos=(String) info.getOrDefault("ignoreAnnos","");
                DeepCoverConfig.ignoreUrls=(String) info.getOrDefault("ignoreUrls","");
                DeepCoverConfig.configVersion=info.getInteger("version");
                DeepCoverConfig.packageName=(String) info.getOrDefault("packageName","");
                DeepCoverConfig.limitCodeMethodSize=(Integer) info.getOrDefault("limitCodeMethodSize",500);
                DeepCoverConfig.limitCodeMethodLineSize=(Integer) info.getOrDefault("limitCodeMethodLineSize",500);
                DeepCoverConfig.sendDataCenterType=(Integer) info.getOrDefault("sendDataCenterType",2);
                DeepCoverConfig.queueNum=(Integer) info.getOrDefault("queueNum",1);
                DeepCoverConfig.queueSize=(Integer) info.getOrDefault("queueSize",100);
                DeepCoverConfig.queueMsgSize=(Integer) info.getOrDefault("queueMsgSize",50);
                DeepCoverConfig.queueRecycleTime=(Integer) info.getOrDefault("queueRecycleTime",10);
                log.info("env: {}  , serviceName: {}", DeepCoverConfig.env, DeepCoverConfig.serviceName);
                KafkaProducerEngine.initKafka();
                LocalAsyncConfig.init();
            }

        } catch (Exception e) {
            log.error("请求异常：{}", e.getMessage());
            return false;
        }
        return true;
    }
}
