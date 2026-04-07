package io.deepcover.agent;

import com.alibaba.jvm.sandbox.api.resource.ModuleEventWatcher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * 备用
 */
public class CodeProcess implements ModuleEventWatcher.Progress {
    @Override
    public void begin(int i) {
        log.info("begin"+i);
    }

    @Override
    public void progressOnSuccess(Class aClass, int i) {
        log.info("progressOnSuccess"+i);
    }

    @Override
    public void progressOnFailed(Class aClass, int i, Throwable throwable) {
        log.info("progressOnFailed"+i);
    }

    @Override
    public void finish(int i, int i1) {
        log.info("finish"+i);

    }
}
