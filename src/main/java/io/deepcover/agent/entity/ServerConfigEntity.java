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
package io.deepcover.agent.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 配置
 */
@Data
public class ServerConfigEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    private Long id;

    /**
     * 服务名
     */
    private String serviceName;

    /**
     * 配置版本号
     */
    private Integer version;

    /**
     * 过滤不需要采集的接口
     */
    private String ignoreUrls;

    /**
     * 过滤不需要采集的类
     */
    private String ignoreClasses;

    /**
     * 采样率
     */
    private Long sampleRate;
}
