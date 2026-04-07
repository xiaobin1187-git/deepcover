package io.deepcover.agent.ext;

import com.alibaba.jvm.sandbox.api.event.*;
import com.alibaba.jvm.sandbox.api.listener.EventListener;
import com.alibaba.jvm.sandbox.api.listener.ext.Attachment;
import com.alibaba.jvm.sandbox.api.listener.ext.Behavior;
import com.alibaba.jvm.sandbox.api.util.BehaviorDescriptor;
import com.alibaba.jvm.sandbox.api.util.CacheGet;
import com.alibaba.jvm.sandbox.api.util.GaStringUtils;
import com.alibaba.jvm.sandbox.api.util.LazyGet;
import io.deepcover.agent.config.DeepCoverConfig;
import io.deepcover.agent.entity.CodeEntity;
import io.deepcover.agent.util.ExceptionAwareUtil;
import io.deepcover.agent.util.TraceContext;
import io.deepcover.agent.util.TraceUtil;
import io.deepcover.agent.util.http.HttpAccessUtil;
import lombok.extern.slf4j.Slf4j;
import java.com.alibaba.jvm.sandbox.spy.Spy;
import java.com.alibaba.jvm.sandbox.spy.SpyTraceEnum;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;


/**
 * 通知监听器
 *
 * @author yingzhu
 * @since {@code sandbox-api:1.0.10}
 */
@Slf4j
public class CodeAdviceAdapterListener implements EventListener {

    private static final ConcurrentHashMap<String, Pattern> URL_PATTERN_CACHE = new ConcurrentHashMap<>();

    private final CodeAdviceListener adviceListener;

    public CodeAdviceAdapterListener(final CodeAdviceListener adviceListener) {
        this.adviceListener = adviceListener;
    }

    private final ThreadLocal<OpStack> opStackRef = ThreadLocal.withInitial(OpStack::new);
    private final ThreadLocal<String> traceIdRef = ThreadLocal.withInitial(String::new);

    @Override
    final public void onEvent(final Event event) throws Throwable {
        try {
            switchEvent(event);
        } catch (Throwable throwable) {
            opStackRef.remove();
            // uncaught exception
            log.warn("code exception occurred when dispatch event={}", event, throwable);
            ExceptionAwareUtil.exceptionOverflow(throwable);
        } finally {
            if (!Spy.access()) {
                opStackRef.remove();
                traceIdRef.remove();
                Spy.traceIdThreadLocal.remove();
                return;
            }
            switch (event.type) {
                case LINE:
                    break;
                default:
                    // 如果执行到TOP的最后一个事件，则需要主动清理占用的资源
                    if (opStackRef.get().isEmpty()) {
                        opStackRef.remove();
                        traceIdRef.remove();
                        Spy.traceIdThreadLocal.remove();
                    }
            }
        }

    }


    // 执行事件
    private void switchEvent(final Event event) throws Throwable {

        switch (event.type) {
            case BEFORE: {
                final BeforeEvent bEvent = (BeforeEvent) event;
                HttpAccessUtil httpAccess = null;
                if ("javax.servlet.http.HttpServlet".equals(bEvent.javaClassName)) {
                    switch (Spy.traceIdThreadLocal.get()) {
                        case INIT: {
                            String traceId = TraceContext.traceId();
                            if (!access(traceId)) {
                                log.debug("event access failed,traceId={},", TraceContext.traceId());
                                Spy.traceIdThreadLocal.set(SpyTraceEnum.REFUSE);
                                return;
                            }
                            httpAccess = new HttpAccessUtil().wrapperHttpAccess(bEvent.argumentArray);
                            String[] urls = DeepCoverConfig.ignoreUrls.split(";");
                            for (String url : urls) {
                                if (url.length() > 0) {
                                    Pattern p = URL_PATTERN_CACHE.computeIfAbsent(url, Pattern::compile);
                                    if (p.matcher(httpAccess.getUri()).matches()) {
                                        Spy.traceIdThreadLocal.set(SpyTraceEnum.REFUSE);
                                        return;
                                    }
                                }
                            }
                            Spy.traceIdThreadLocal.set(SpyTraceEnum.PASS);
                            traceIdRef.set(traceId);
                            break;
                        }
                        case PASS: {
                            return;
                        }
                        case REFUSE: {
                            if (traceIdRef.get().equals(TraceContext.traceId())) {
                                return;
                            } else {
                                Spy.traceIdThreadLocal.set(SpyTraceEnum.INIT);
                                return;
                            }
                        }
                    }
                } else {
                    switch (Spy.traceIdThreadLocal.get()) {
                        case REFUSE:
                            return;
                        case INIT:
                            return;
                    }
                }

                final ClassLoader loader = toClassLoader(bEvent.javaClassLoader);
                final CodeAdvice advice = new CodeAdvice(
                        bEvent.processId,
                        bEvent.invokeId,
                        new LazyGet<Behavior>() {

                            private final ClassLoader _loader = loader;
                            private final String _javaClassName = bEvent.javaClassName;
                            private final String _javaMethodName = bEvent.javaMethodName;
                            private final String _javaMethodDesc = bEvent.javaMethodDesc;

                            @Override
                            protected Behavior initialValue() throws Throwable {
                                return toBehavior(
                                        toClass(_loader, _javaClassName),
                                        _javaMethodName,
                                        _javaMethodDesc
                                );
                            }
                        },
                        loader,
                        bEvent.argumentArray,
                        bEvent.target
                );

                final CodeAdvice top;
                final CodeAdvice parent;

                // 顶层调用
                final OpStack opStack = opStackRef.get();
                if ("javax.servlet.http.HttpServlet".equals(bEvent.javaClassName)) {
                    if (!opStack.isEmpty()) {
                        Spy.traceIdThreadLocal.set(SpyTraceEnum.REFUSE);
                        return;
                    }
                }
                if (opStack.isEmpty()) {
                    top = parent = advice;
                }

                // 非顶层
                else {
                    parent = opStack.peek().advice;
                    top = parent.getProcessTop();
                }

                //提取attachment信息
                if (top.attachment() != null) {
                    CodeEntity codeEntity = top.attachment();
                    if (codeEntity.getCodeInfoSize() >= DeepCoverConfig.limitCodeMethodSize) {
                        Spy.traceIdThreadLocal.set(SpyTraceEnum.REFUSE);
                        opStackRef.remove();
                        log.warn("单请求采集的代码节点数,超过上限:{},不继续采集,nowClass={},nowMethod={},traceId={},url={}", DeepCoverConfig.limitCodeMethodSize, bEvent.javaClassName, bEvent.javaMethodName, codeEntity.getTraceId(), codeEntity.getUrl());
                        return;
                    }
                }
                advice.applyBefore(top, parent);

                opStackRef.get().pushForBegin(advice);
                if (advice.getProcessTop().attachment() != null || httpAccess == null) {
                    return;
                }
                adviceListener.before(advice, httpAccess);
                break;
            }

            case RETURN: {
                final ReturnEvent rEvent = (ReturnEvent) event;
                final OpStack opStack = opStackRef.get();
                final WrapAdvice wrapAdvice = opStack.popByExpectInvokeId(rEvent.invokeId);
                if (null != wrapAdvice) {
                    CodeAdvice advice = wrapAdvice.advice.applyReturn(rEvent.object);
                    try {
                        adviceListener.afterReturning(advice);
                    } finally {
                        adviceListener.after(advice);
                    }
                }
                break;
            }
            case THROWS: {
                final ThrowsEvent tEvent = (ThrowsEvent) event;
                final OpStack opStack = opStackRef.get();
                final WrapAdvice wrapAdvice = opStack.popByExpectInvokeId(tEvent.invokeId);
                if (null != wrapAdvice) {
                    CodeAdvice advice = wrapAdvice.advice.applyThrows(tEvent.throwable);
                    try {
                        adviceListener.afterThrowing(advice);
                    } finally {
                        adviceListener.after(advice);
                    }
                }
                break;
            }

            case CALL_BEFORE: {
                final CallBeforeEvent cbEvent = (CallBeforeEvent) event;
                //特殊编译情况下，行号为-1，过滤不需要
                String javaClassName = toJavaClassName(cbEvent.owner);
                if ("javax.servlet.http.HttpServlet".equals(javaClassName) || cbEvent.lineNumber == -1) {
                    return;
                }
                final OpStack opStack = opStackRef.get();
                final WrapAdvice wrapAdvice = opStack.peekByExpectInvokeId(cbEvent.invokeId);
                if (null == wrapAdvice) {
                    return;
                }

                //提取attachment信息
                CodeEntity codeEntity = wrapAdvice.advice.getProcessTop().attachment();
                if (null == codeEntity) {
                    return;
                }
                final CallTarget target;
                wrapAdvice.attach(target = new CallTarget(
                        cbEvent.lineNumber,
                        javaClassName,
                        cbEvent.name,
                        cbEvent.desc
                ));
                adviceListener.beforeCall(
                        wrapAdvice.advice,
                        target.callLineNum,
                        target.callJavaClassName,
                        target.callJavaMethodName,
                        target.callJavaMethodDesc
                );
                break;
            }

            case LINE: {
                final LineEvent lEvent = (LineEvent) event;

                //特殊编译情况下，行号为-1，过滤不需要
                if (lEvent.lineNumber == -1) {
                    return;
                }
                final OpStack opStack = opStackRef.get();
                final WrapAdvice wrapAdvice = opStack.peekByExpectInvokeId(lEvent.invokeId);
                if (null == wrapAdvice) {
                    return;
                }

                //提取attachment信息
                CodeEntity codeEntity = wrapAdvice.advice.getProcessTop().attachment();
                if (null == codeEntity) {
                    return;
                }
                Behavior behavior = wrapAdvice.advice.getBehavior();
                if ("javax.servlet.http.HttpServlet".equals(behavior.getDeclaringClass().getName())) {
                    return;
                }
                adviceListener.beforeLine(wrapAdvice.advice, lEvent.lineNumber, behavior);
                break;
            }

            default:
                //ignore
        }//switch
    }


    // --- 以下为内部操作实现 ---


    /**
     * 通知操作堆栈
     */
    private static class OpStack {

        private final Stack<WrapAdvice> adviceStack = new Stack<>();

        boolean isEmpty() {
            return adviceStack.isEmpty();
        }

        WrapAdvice peek() {
            return adviceStack.peek();
        }

        void pushForBegin(final CodeAdvice advice) {
            adviceStack.push(new WrapAdvice(advice));
        }

        /**
         * 在通知堆栈中，BEFORE:[RETURN/THROWS]的invokeId是配对的，
         * 如果发生错位则说明BEFORE的事件没有被成功压入堆栈，没有被正确的处理，外界没有正确感知BEFORE
         * 所以这里也要进行修正行的忽略对应的[RETURN/THROWS]
         *
         * @param expectInvokeId 期待的invokeId
         *                       必须要求和BEFORE的invokeId配对
         * @return 如果invokeId配对成功，则返回对应的Advice，否则返回null
         */
        WrapAdvice popByExpectInvokeId(final int expectInvokeId) {
            return !adviceStack.isEmpty()
                    && adviceStack.peek().advice.getInvokeId() == expectInvokeId
                    ? adviceStack.pop()
                    : null;
        }

        WrapAdvice peekByExpectInvokeId(final int expectInvokeId) {
            return !adviceStack.isEmpty()
                    && adviceStack.peek().advice.getInvokeId() == expectInvokeId
                    ? adviceStack.peek()
                    : null;
        }

    }

    // change internalClassName to javaClassName
    private String toJavaClassName(final String internalClassName) {
        if (GaStringUtils.isEmpty(internalClassName)) {
            return internalClassName;
        } else {

            // #302
            return internalClassName.replace('/', '.');
            // return internalClassName.replaceAll("/", ".");

        }
    }

    // 提取ClassLoader，从BeforeEvent中获取到的ClassLoader
    private ClassLoader toClassLoader(ClassLoader loader) {
        return null == loader
                // 如果此处为null，则说明遇到了来自Bootstrap的类，
                ? CodeAdviceAdapterListener.class.getClassLoader()
                : loader;
    }

    // 根据JavaClassName从ClassLoader中提取出Class<?>对象
    private Class<?> toClass(ClassLoader loader, String javaClassName) throws ClassNotFoundException {
        return toClassLoader(loader).loadClass(javaClassName);
    }


    /**
     * 行为缓存KEY对象
     */
    private static class BehaviorCacheKey {
        private final Class<?> clazz;
        private final String javaMethodName;
        private final String javaMethodDesc;

        private BehaviorCacheKey(final Class<?> clazz,
                                 final String javaMethodName,
                                 final String javaMethodDesc) {
            this.clazz = clazz;
            this.javaMethodName = javaMethodName;
            this.javaMethodDesc = javaMethodDesc;
        }

        @Override
        public int hashCode() {
            return clazz.hashCode()
                    + javaMethodName.hashCode()
                    + javaMethodDesc.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BehaviorCacheKey)) {
                return false;
            }
            final BehaviorCacheKey key = (BehaviorCacheKey) o;
            return clazz.equals(key.clazz)
                    && javaMethodName.equals(key.javaMethodName)
                    && javaMethodDesc.equals(key.javaMethodDesc);
        }

    }

    // 行为缓存，为了增加性能，不要每次都从class通过反射获取行为
    private final CacheGet<BehaviorCacheKey, Behavior> toBehaviorCacheGet
            = new CacheGet<BehaviorCacheKey, Behavior>() {
        @Override
        protected Behavior load(BehaviorCacheKey key) {
            if ("<init>".equals(key.javaMethodName)) {
                for (final Constructor<?> constructor : key.clazz.getDeclaredConstructors()) {
                    if (key.javaMethodDesc.equals(new BehaviorDescriptor(constructor).getDescriptor())) {
                        return new Behavior.ConstructorImpl(constructor);
                    }
                }
            } else {
                for (final Method method : key.clazz.getDeclaredMethods()) {
                    if (key.javaMethodName.equals(method.getName())
                            && key.javaMethodDesc.equals(new BehaviorDescriptor(method).getDescriptor())) {
                        return new Behavior.MethodImpl(method);
                    }
                }
            }
            return null;
        }
    };

    /**
     * CALL目标对象
     */
    private static class CallTarget {

        final int callLineNum;
        final String callJavaClassName;
        final String callJavaMethodName;
        final String callJavaMethodDesc;

        CallTarget(int callLineNum, String callJavaClassName, String callJavaMethodName, String callJavaMethodDesc) {
            this.callLineNum = callLineNum;
            this.callJavaClassName = callJavaClassName;
            this.callJavaMethodName = callJavaMethodName;
            this.callJavaMethodDesc = callJavaMethodDesc;
        }
    }

    /**
     * 通知内部封装，主要是要封装掉attachment
     */
    private static class WrapAdvice implements Attachment {

        final CodeAdvice advice;
        Object attachment;

        WrapAdvice(CodeAdvice advice) {
            this.advice = advice;
        }

        @Override
        public void attach(Object attachment) {
            this.attachment = attachment;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T attachment() {
            return (T) attachment;
        }
    }

    /**
     * 根据提供的行为名称、行为描述从指定的Class中获取对应的行为
     *
     * @param clazz          指定的Class
     * @param javaMethodName 行为名称
     * @param javaMethodDesc 行为参数声明
     * @return 匹配的行为
     * @throws NoSuchMethodException 如果匹配不到行为，则抛出该异常
     */
    private Behavior toBehavior(final Class<?> clazz,
                                final String javaMethodName,
                                final String javaMethodDesc) throws NoSuchMethodException {
        final Behavior behavior = toBehaviorCacheGet.getFromCache(new BehaviorCacheKey(clazz, javaMethodName, javaMethodDesc));
        if (null == behavior) {
            throw new NoSuchMethodException(String.format("%s.%s(%s)", clazz.getName(), javaMethodName, javaMethodDesc));
        }
        return behavior;
    }

    /**
     * 事件是否可以通过
     * <p>
     * 降级之后只有回放流量可以通过
     *
     * @return 是否通过
     */
    protected boolean access(String traceId) {
        if (DeepCoverConfig.exceptionThresholdTime > 0L && System.currentTimeMillis() - DeepCoverConfig.exceptionThresholdTime > DeepCoverConfig.exceptionPauseTime * 60 * 1000) {
            DeepCoverConfig.exceptionThresholdTime = 0L;
            ExceptionAwareUtil.clear();
        }
        return DeepCoverConfig.exceptionThresholdTime == 0L && TraceUtil.inTimeSample(traceId);
//        return TraceUtil.inTimeSample(traceId);
    }

}
