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
