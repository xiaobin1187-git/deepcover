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

import com.alibaba.fastjson.JSONObject;
import com.alibaba.jvm.sandbox.api.annotation.Command;
import com.alibaba.jvm.sandbox.api.listener.ext.Advice;
import com.alibaba.jvm.sandbox.api.listener.ext.Behavior;
import com.alibaba.jvm.sandbox.api.resource.ModuleEventWatcher;
import io.deepcover.agent.config.DeepCoverConfig;
import io.deepcover.agent.config.ExecutorThreadPoolConfig;
import io.deepcover.agent.config.kafka.KafkaProducerEngine;
import io.deepcover.agent.config.queue.LocalAsyncConfig;
import io.deepcover.agent.entity.CodeEntity;
import io.deepcover.agent.entity.LineEntity;
import io.deepcover.agent.ext.CodeAdvice;
import io.deepcover.agent.ext.CodeAdviceListener;
import io.deepcover.agent.ext.CodeEventWatchBuilder;
import io.deepcover.agent.util.CodeInfoReflectionUtils;
import io.deepcover.agent.util.ExceptionAwareUtil;
import io.deepcover.agent.util.MetricsCollector;
import io.deepcover.agent.util.TraceContext;
import io.deepcover.agent.util.http.HttpAccessUtil;
import io.deepcover.agent.util.http.HttpClient2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;

import java.com.alibaba.jvm.sandbox.spy.Spy;
import java.com.alibaba.jvm.sandbox.spy.SpyTraceEnum;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @description:采集代码信息
 * @author: wuchen
 * @time: 2023/3/2 17:51
 */
@Slf4j
//public class HttpCodeModule extends Thread {
public class HttpCodeModule {
//    private final Logger log = LoggerFactory.getLogger("CODE-COVERAGE-LOGGER");

    //    @Resource
    private ModuleEventWatcher moduleEventWatcher;

    public HttpCodeModule(ModuleEventWatcher watcher) {
        this.moduleEventWatcher = watcher;
    }

    private int watchId = 0;

    @Command("codecoverage")
//    @Override
    public void run() {

        if (watchId == 0) {
            try {
                createWatcher();//创建watcher
                watchId = 100;
                log.info("code-module 加载完成");

            } catch (Exception e) {
                log.error("code_module启动异常：{}",e);
            }
        } else {

        }
    }

    private void createWatcher() {
        log.info("createWatcher:{}",DeepCoverConfig.serviceName);
//        ProcessController.noneImmediately();
        //新建一个AdviceListener
        CodeAdviceListener adviceListener = new CodeAdviceListener() {
            @Override
            protected void before(CodeAdvice advice,HttpAccessUtil httpAccess)  {
                log.debug("createWatcher before");
                String clzName=advice.getBehavior().getDeclaringClass().getName();
                if (clzName.equals("javax.servlet.http.HttpServlet")) {
                    String traceId = TraceContext.traceId();
                    MetricsCollector.totalRequests.incrementAndGet();
                    //如果递进调用过程中的顶层通知，就在attachment中新增一个map，用于存放后续代码行信息
                    CodeEntity codeEntity =new CodeEntity();
                    codeEntity.setType("HTTP");
                    codeEntity.setAddr(DeepCoverConfig.localIp);
                    codeEntity.setPort(httpAccess.getPort());
                    codeEntity.setMethod(httpAccess.getMethod());
                    codeEntity.setUrl(httpAccess.getUri());
                    codeEntity.setBeginTime(httpAccess.getBeginTimestamp());
                    codeEntity.setEnv(DeepCoverConfig.env);
                    codeEntity.setServiceName(DeepCoverConfig.serviceName);
                    codeEntity.setBranch(DeepCoverConfig.branch);
                    codeEntity.setTraceId(traceId);
                    codeEntity.setProcessId(advice.getProcessId());
                    codeEntity.setCodeInfo(new ArrayList<>());
                    codeEntity.setCodeInfoSize(0);
                    codeEntity.setIsSend(0);
                    log.debug("开始采集,接口：{}",codeEntity.toString());
                    advice.getProcessTop().attach(codeEntity);
                }
            }

            @Override
            protected void beforeLine(CodeAdvice advice, int lineNum, Behavior behavior){
                log.debug("createWatcher beforeLine");
                //提取attachment信息
                CodeEntity codeEntity = advice.getProcessTop().attachment();
                if(codeEntity == null){
                    log.debug("CodeEntity is null, invokeId={}", advice.getInvokeId());
                    return;
                }
                if(codeEntity.getIsSend() == 1){
                    return;
                }
                //线程调用链路中的类和方法信息
                List<LineEntity> codeInfo = codeEntity.getCodeInfo();
                if(codeInfo == null){
                    log.warn("codeInfo is null, traceId={}", codeEntity.getTraceId());
                    return;
                }

                int traceSize=codeInfo.size();
                if(traceSize>0){
                    LineEntity lastOne = codeInfo.get(traceSize-1);
                    if(lastOne.getInvokeId().equals(advice.getInvokeId())){
                        lastOne.setCallLineCount(lastOne.getCallLineCount()+1);
                        if(lastOne.getCallLineCount()>=(long)DeepCoverConfig.limitCodeMethodLineSize){
                            log.warn("className={},methodName={},代码行执行次数超过:{},traceId:{},url={}",lastOne.getClassName(),lastOne.getMethodName(),DeepCoverConfig.limitCodeMethodLineSize,codeEntity.getTraceId(),codeEntity.getUrl());
                            Spy.traceIdThreadLocal.set(SpyTraceEnum.REFUSE);
                        }else{
                            Set<Integer> lineSet = lastOne.getLineNum();
                            lineSet.add(lineNum);
                        }
                        return;
                    }else{
                        codeEntity.setCodeInfoSize(codeEntity.getCodeInfoSize()+1);
                    }

                }

                //方法名称
                String methodName = behavior.getName();

                String className = queryClassName(behavior.getDeclaringClass(), methodName);
                if ("null".equals(className)) {
                    className = behavior.getDeclaringClass().getName();
                }
                int dollarIdx;
                if ((dollarIdx = className.indexOf('$')) != -1) {
                    className = className.substring(0, dollarIdx);
                }
                if ((dollarIdx = methodName.indexOf('$')) != -1) {
                    methodName = methodName.substring(dollarIdx + 1);
                }
                LineEntity lineEntity = new LineEntity();
                lineEntity.setBeginTime(System.currentTimeMillis());
                lineEntity.setInvokeId(advice.getInvokeId());
                lineEntity.setClassName(className);
                lineEntity.setMethodName(methodName);
                lineEntity.setCallLineCount(1L);
                Set<Integer> lineSet = new LinkedHashSet<>(8);
                lineSet.add(lineNum);
                lineEntity.setLineNum(lineSet);

                Class<?>[] paramsClazz = behavior.getParameterTypes();
                List<String> paramsList = new ArrayList<>(paramsClazz.length);
                for(Class<?> c: paramsClazz){
                    paramsList.add(c.getName());
                }

                lineEntity.setParameters(paramsList);

                //将单条消息添加到codeInfo
                codeInfo.add(lineEntity);

            }


            @Override
            protected void after(CodeAdvice advice)  {
                log.debug("createWatcher after");
                //提取attachment信息
                CodeEntity codeEntity = advice.getProcessTop().attachment();
                if(codeEntity!=null&&codeEntity.getIsSend()==1){
                    return;
                }
                String traceId = TraceContext.traceId();
//                if(!TraceUtil.inTimeSample(traceId)){
//                    return;
//                }

                //线程调用链路中的类和方法信息
                if(null!=codeEntity){
                    List<LineEntity> codeInfo = codeEntity.getCodeInfo();
                    int traceSize=codeInfo.size();
                    if(traceSize>0){
                        LineEntity lastOne = codeInfo.get(traceSize-1);
                        int index = codeInfo.indexOf(lastOne);
                        if(index<traceSize-1){
//                        codeInfo.remove(lastOne);
                            codeInfo.remove(traceSize-1);
                        }
                    }
                }



                String clzName=advice.getBehavior().getDeclaringClass().getName();
                if (clzName.equals("javax.servlet.http.HttpServlet")){
                    Spy.traceIdThreadLocal.set(SpyTraceEnum.REFUSE);

                    if(null==codeEntity || codeEntity.getCodeInfo().size()==0 ){
                        log.info("采集的信息为空,不发送,traceId={},url={}",codeEntity.getTraceId(),codeEntity.getUrl());
                        return;
                    }

                    if(codeEntity.getIsSend()!=1){
                        codeEntity.setIsSend(1);
                        if(codeEntity.getCodeInfoSize() >= DeepCoverConfig.limitCodeMethodSize){
                            log.info("单请求采集的代码节点数,超过上限:{},不发送,url:{},traceId:{}",DeepCoverConfig.limitCodeMethodSize,codeEntity.getUrl(),traceId);
                            codeEntity.setCodeInfoSize(DeepCoverConfig.limitCodeMethodSize+1);
                            return;
                        }
                        log.debug("采集结束，发送消息,traceId={}",traceId);
                        //调用结束后，将收集到的代码行信息上传到服务器中
                        try{
                            LocalAsyncConfig.sendMessage(codeEntity);
//                            sendMessage(codeEntity,traceId);
                        }catch (Exception e){
                            log.error("sendMessage exception,traceId={},codeInfo:{}",traceId,codeEntity.toString(),e);
                        }
                    }
                }
            }
        };

        CodeCollecter.codeEventWatcher=new CodeEventWatchBuilder(moduleEventWatcher, CodeEventWatchBuilder.PatternType.REGEX)//一定要选择这种表达式模式
                .ignoreClass(DeepCoverConfig.ignoreClasses.replaceAll(";","|"))
                .onAnyBehavior()
                .onClass("javax.servlet.http.HttpServlet")
                .onBehavior("service")
//                        .withParameterTypes(
//                           "javax.servlet.http.HttpServletRequest",
//                           "javax.servlet.http.HttpServletResponse"
//                        )
                .onClass(DeepCoverConfig.packageName).hasAnnotationTypes(".*")
                .onAnyBehavior()
                .onWatching()
//                        .withCall()
                .withLine()//有它，才能获取到行号
//                        .withProgress(codeCoverageProcess)//可以不用Process，我这里用它查看是否已经渲染完成，没有其它作用
                .onWatch(adviceListener);

//            }
//        });

    }

    //根据实际情况 构建匹配类的正则表达式
    private JSONObject parseParamsValue(Advice advice){
        //方法名称
        String methodName = advice.getBehavior().getName();
        if (methodName.contains("$")) {//lambda方法会带$符号，目前用一种简陋的方法解决
            methodName = methodName.split("\\$")[1];
        }

        Class<?> clazz =advice.getTarget().getClass() ;//TestMain.class;
        Class<?>[] paramsClazz = advice.getBehavior().getParameterTypes();
        Object[] paramValue=advice.getParameterArray();
        LocalVariableTableParameterNameDiscoverer u = new LocalVariableTableParameterNameDiscoverer();
        Method[] methods2 = clazz.getDeclaredMethods();
        JSONObject paramsJson = new JSONObject();

        for (Method method : methods2) {
            if (methodName.equals(method.getName())&&method.getParameters().length==paramsClazz.length) {
                String[] paramsKey = u.getParameterNames(method);
                for(int i=0;i<paramsKey.length;i++){
                    paramsJson.fluentPut(paramsKey[i],paramValue[i]);
                }
            }
        }

        return paramsJson;
    }
    //根据实际情况 构建匹配类的正则表达式
    private String bulidClassPattern(String serviceName) {
        //com(?!\.logger\.|\.frame\.|.*\.dto\.|.*\.constants\.|.*\.model\.).*
        //com.example.{serviceName}.*
        StringBuffer sb = new StringBuffer("com.example.");
        //common
        /*sb.append("\\.logger\\.").append("|")
                .append("\\.frame\\.").append("|")
                .append("\\.idgenerator\\.").append("|")
                .append("\\.pigeonV2\\.").append("|")
                .append("\\.liteflow\\.").append("|")
                .append(".*\\.constant\\.");
        sb.append(").*");*/
        sb.append(serviceName);
        sb.append(".*");
        log.info("ares采集的class：{}",sb.toString());
        return sb.toString();
    }


    //after事件后的批量发送 处理后的覆盖行信息
    //后续改成异步
    private void sendMessage(CodeEntity codeEntity,String traceId) {
        ExecutorThreadPoolConfig.executorSendMsg.submit(new Thread(new Runnable() {
            @Override
            public void run() {
//                Thread.currentThread().setName("code-module-send-"+Thread.currentThread().getId());
                try {
//                    Map<String, Object> map = new HashMap<>();//存放参数
//                    map.put("codeInfo",list.toString());
                    if(DeepCoverConfig.sendDataCenterType==1){
                        HttpClient2.doAsyncPost(DeepCoverConfig.dataCenterAddr,codeEntity);
                    }else if(DeepCoverConfig.sendDataCenterType==2){
                        KafkaProducerEngine.sendMessage(codeEntity);
                    }

                } catch(Exception e) {
                    //monitor,后期改成warn
                    log.warn("发送到数据中心异常,url:{},traceId:{},resMsg:{}", DeepCoverConfig.dataCenterAddr,traceId,e.getMessage(),e);
                    ExceptionAwareUtil.exceptionOverflow(e);
                }
            }
        }));
    }


    //校验方法是否当前类实现，如果不是，那么逐层向上获取父类，并验证是否是父类实现
    private String queryClassName(Class type, String methodName) {
        String className = null;
        Method[] methods =CodeInfoReflectionUtils.getDeclaredMethods(type);
//         type.getDeclaredMethods();//获取当前类实现的方法（getMethods()是获取所有方法，包括抽象）
        for (Method method : methods) {
            if (methodName.equals(method.getName())) {
                className = type.getName();
            }
        }
        if (className == null) {
            Class superClass = type.getSuperclass();
            if (superClass.getName().contains("java.lang.Object")) {
                return "null";
            } else {
                className = queryClassName(superClass, methodName);
            }
        }
        return className;
    }

}
