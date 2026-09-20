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

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

@Data
@Slf4j
public class HttpAccessUtil {
    private static final ClassValue<RequestAccessor> REQUEST_ACCESSOR_CACHE =
            new ClassValue<RequestAccessor>() {
                @Override
                protected RequestAccessor computeValue(Class<?> type) {
                    return new RequestAccessor(type);
                }
            };

    final long beginTimestamp = System.currentTimeMillis();
    String from;
    Integer port;
    String method;
    String uri;
    Map<String, String[]> parameterMap;
    String userAgent;
    String traceId;
    int status = 200;

    HttpAccessUtil(Integer port, String method, String uri, String traceId) {
        this.port=port;
        this.method = method;
        this.uri = uri;
        this.parameterMap = Collections.emptyMap();
        this.traceId = traceId;
    }

    public HttpAccessUtil() {
    }

    void setStatus(int status) {
        this.status = status;
    }

    public HttpAccessUtil wrapperHttpAccess(Object[] params) {
        Object request = params[0];
        RequestAccessor requestAccessor = REQUEST_ACCESSOR_CACHE.get(request.getClass());
        String traceId = parseW3cTraceId(requestAccessor.getHeader(request, "traceparent"));
        if (traceId == null) {
            traceId = trimToNull(requestAccessor.getHeader(request, "X-B3-TraceId"));
        }
        if (traceId == null) {
            traceId = trimToNull(requestAccessor.getHeader(request, "X-Trace-Id"));
        }
        if (traceId == null) {
            traceId = trimToNull(requestAccessor.getHeader(request, "traceId"));
        }
        return new HttpAccessUtil(
                requestAccessor.getServerPort(request),
                requestAccessor.getMethod(request),
                requestAccessor.getRequestUri(request),
                traceId
        );
    }

    private String parseW3cTraceId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String value = traceparent.trim();
        int firstSeparator = value.indexOf('-');
        int secondSeparator = value.indexOf('-', firstSeparator + 1);
        int thirdSeparator = value.indexOf('-', secondSeparator + 1);
        if (firstSeparator <= 0
                || secondSeparator - firstSeparator != 33
                || thirdSeparator <= secondSeparator + 1
                || thirdSeparator >= value.length() - 1
                || value.indexOf('-', thirdSeparator + 1) != -1) {
            return null;
        }
        return value.substring(firstSeparator + 1, secondSeparator);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class RequestAccessor {
        private final Method getServerPort;
        private final Method getMethod;
        private final Method getRequestUri;
        private final Method getHeader;

        RequestAccessor(Class<?> requestType) {
            getServerPort = findMethod(requestType, "getServerPort");
            getMethod = findMethod(requestType, "getMethod");
            getRequestUri = findMethod(requestType, "getRequestURI");
            getHeader = findMethod(requestType, "getHeader", String.class);
        }

        int getServerPort(Object request) {
            return ((Number) invoke(getServerPort, request)).intValue();
        }

        String getMethod(Object request) {
            return (String) invoke(getMethod, request);
        }

        String getRequestUri(Object request) {
            return (String) invoke(getRequestUri, request);
        }

        String getHeader(Object request, String name) {
            return (String) invoke(getHeader, request, name);
        }

        private static Method findMethod(Class<?> requestType, String name, Class<?>... parameterTypes) {
            try {
                return requestType.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
                log.error("HTTP request method not found,class={},method={}", requestType.getName(), name, e);
                throw new IllegalArgumentException("Unsupported HTTP request type: " + requestType.getName(), e);
            }
        }

        private static Object invoke(Method method, Object target, Object... arguments) {
            try {
                return method.invoke(target, arguments);
            } catch (IllegalAccessException | InvocationTargetException e) {
                log.error("HTTP request method invocation failed,class={},method={}",
                        target.getClass().getName(), method.getName(), e);
                throw new IllegalStateException("Cannot read HTTP request metadata", e);
            }
        }
    }
}
