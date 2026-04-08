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
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
@Slf4j
public class IpUtil {
    public static String getLocalIp(){
        InetAddress address = null;
        try {
            //获取本机域名
            String hostName = InetAddress.getLocalHost().getHostName();
            //用域名创建 InetAddress对象
            address = InetAddress.getByName(hostName);
        } catch (UnknownHostException e) {
            log.error("[serviceName={}]获取服务器本机ip异常：{}", DeepCoverConfig.serviceName,e);
        }

        //获取的是该网站的ip地址，如果我们所有的请求都通过nginx的，所以这里获取到的其实是nginx服务器的IP地址
        return address.getHostAddress();
    }
}
