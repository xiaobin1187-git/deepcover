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
package io.deepcover.agent.util.http;

import io.deepcover.agent.ext.CodeAdvice;
import lombok.Data;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Data
public class HttpAccessUtil {
    final long beginTimestamp = System.currentTimeMillis();
    String from;
    Integer port;
    String method;
    String uri;
    Map<String, String[]> parameterMap;
    String userAgent;
    int status = 200;

    HttpAccessUtil(String from, Integer port,String method, String uri, Map<String, String[]> parameterMap, String userAgent) {
        this.from = from;
        this.port=port;
        this.method = method;
        this.uri = uri;
        this.parameterMap = parameterMap;
        this.userAgent = userAgent;
    }

    public HttpAccessUtil() {
    }

    void setStatus(int status) {
        this.status = status;
    }

    public HttpAccessUtil wrapperHttpAccess(Object[] params) {

        // 俘虏HttpServletRequest参数为傀儡
        final IHttpServletRequest httpServletRequest = InterfaceProxyUtils.puppet(
                IHttpServletRequest.class,
                params[0]);

        // 俘虏HttpServletRequest参数为傀儡
//        final HttpServletRequest httpServletRequest2 = InterfaceProxyUtils.puppet(
//                HttpServletRequest.class,
//                advice.getParameterArray()[0]);
//        Integer port =httpServletRequest.getServerPort();
//        int port2 =httpServletRequest2.getServerPort();
        // 初始化HttpAccess
        return new HttpAccessUtil(
                httpServletRequest.getRemoteAddress(),
                httpServletRequest.getServerPort(),
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getParameterMap(),
                httpServletRequest.getHeader("User-Agent")
        );
    }
}
