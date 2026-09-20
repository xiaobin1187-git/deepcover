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

import io.deepcover.agent.config.DeepCoverConfig;
import io.deepcover.agent.entity.CodeEntity;
import io.deepcover.agent.util.ExceptionAwareUtil;
import io.deepcover.agent.util.MetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.*;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
public class HttpClient2 {
    private static final Object HTTP_CLIENT_LOCK = new Object();
    private static final Object HTTP_ASYNC_CLIENT_LOCK = new Object();
    private static volatile CloseableHttpClient httpclient;
    private static volatile CloseableHttpAsyncClient httpAsyncClient;
    //
    private static final RequestConfig reqestConfig;
    //响应处理器
    private static final ResponseHandler<String> responseHandler;
    //jackson解析工具
//    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        System.setProperty("http.maxConnections", "50");
        System.setProperty("http.keepAlive", "true");
        //设置basic校验
//        credsProvider.setCredentials(
//                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM),
//                new UsernamePasswordCredentials("", ""));
        //初始化HTTP请求配置
        reqestConfig = RequestConfig.custom()
                .setContentCompressionEnabled(true)
                .setSocketTimeout(1000)
//                .setAuthenticationEnabled(true)
                .setConnectionRequestTimeout(1000)
                .setConnectTimeout(1000).build();

        //初始化response解析器
        responseHandler = new BasicResponseHandler();
    }

    private static CloseableHttpClient getHttpClient() {
        CloseableHttpClient client = httpclient;
        if (client == null) {
            synchronized (HTTP_CLIENT_LOCK) {
                client = httpclient;
                if (client == null) {
                    client = HttpClients.custom().build();
                    httpclient = client;
                }
            }
        }
        return client;
    }

    private static CloseableHttpAsyncClient getHttpAsyncClient() {
        CloseableHttpAsyncClient client = httpAsyncClient;
        if (client == null) {
            synchronized (HTTP_ASYNC_CLIENT_LOCK) {
                client = httpAsyncClient;
                if (client == null) {
                    client = HttpAsyncClients.custom().build();
                    client.start();
                    httpAsyncClient = client;
                }
            }
        }
        return client;
    }

    public static void shutdown() {
        synchronized (HTTP_ASYNC_CLIENT_LOCK) {
            try {
                if (httpAsyncClient != null) {
                    httpAsyncClient.close();
                    httpAsyncClient = null;
                    log.info("httpAsyncClient closed successfully");
                }
            } catch (IOException e) {
                log.error("Failed to close httpAsyncClient", e);
            }
        }
        synchronized (HTTP_CLIENT_LOCK) {
            try {
                if (httpclient != null) {
                    httpclient.close();
                    httpclient = null;
                    log.info("httpclient closed successfully");
                }
            } catch (IOException e) {
                log.error("Failed to close httpclient", e);
            }
        }
    }

    ConnectionKeepAliveStrategy myStrategy = new ConnectionKeepAliveStrategy() {
        @Override
        public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
            HeaderElementIterator it = new BasicHeaderElementIterator
                    (response.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (it.hasNext()) {
                HeaderElement he = it.nextElement();
                String param = he.getName();
                String value = he.getValue();
                if (value != null && param.equalsIgnoreCase
                        ("timeout")) {
                    return Long.parseLong(value) * 1000;
                }
            }
            return 60 * 1000;//如果没有约定，则默认定义时长为60s
        }
    };

    public static String getResponse(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setConfig(reqestConfig);
        get.setHeader("Connection", "Keep-Alive");
        String response = getHttpClient().execute(get, responseHandler);
        return response;
    }

    public static com.alibaba.fastjson.JSONObject getUrl3(String url) {
        HttpGet httpget = new HttpGet();
        try {
            httpget.setConfig(reqestConfig);
            httpget.setHeader("Connection", "Keep-Alive");
            httpget.setURI(URI.create(url));
            String response = getHttpClient().execute(httpget, responseHandler);
            com.alibaba.fastjson.JSONObject jsonObject = com.alibaba.fastjson.JSONObject.parseObject(response);
            return jsonObject;
        } catch (IOException e) {
            log.error("get url failed,url={}", url, e);
        }
        return null;
    }

    public static void batchDoAsyncPost(String url, List<CodeEntity> codeList) {
        for (CodeEntity code : codeList) {
            doAsyncPost(url, code);
        }
    }

    public static void doAsyncPost(String url, CodeEntity param) {
        HttpPost httppost = new HttpPost();
        if (param != null) {
            try {
                //初始化httpPost
                httppost.setConfig(reqestConfig);
                httppost.setURI(URI.create(url));
                httppost.setHeader("Connection", "Keep-Alive");
                httppost.setHeader("traceId", param.getTraceId());
                httppost.setHeader("serviceName", param.getServiceName());
                httppost.setHeader("processId", String.valueOf(param.getProcessId()));
                httppost.setHeader("codeInfoSize", String.valueOf(param.getCodeInfoSize()));
                httppost.setHeader("url", String.valueOf(param.getUrl()));
                StringEntity e = new StringEntity(String.valueOf(param), ContentType.APPLICATION_JSON);
                httppost.setEntity(e);
            } catch (Exception e) {
                //monitor,后期改成warn
                log.warn("发送数据中心-请求报文组装异常", e);
                MetricsCollector.sendFailed.incrementAndGet();
                ExceptionAwareUtil.exceptionOverflow(e);
                return;
            }
            // 执行http请求
            try {
                getHttpAsyncClient().execute(httppost, new FutureCallback<HttpResponse>() {
                @Override
                public void completed(HttpResponse response) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    EntityUtils.consumeQuietly(response.getEntity());
                    if (statusCode < HttpStatus.SC_OK || statusCode >= HttpStatus.SC_MULTIPLE_CHOICES) {
                        MetricsCollector.sendFailed.incrementAndGet();
                        log.warn("发送到数据中心-返回异常,url:{},traceId:{},code:{}", DeepCoverConfig.dataCenterAddr, param.getTraceId(), statusCode);
                        ExceptionAwareUtil.exceptionOverflow(new HttpResponseException(statusCode, DeepCoverConfig.dataCenterAddr));
                    } else {
                        MetricsCollector.sendSuccess.incrementAndGet();
                    }
                }

                @Override
                public void failed(Exception e) {
                    MetricsCollector.sendFailed.incrementAndGet();
                    log.warn("发送到数据中心异常,url:{},traceId:{},resMsg:{}", DeepCoverConfig.dataCenterAddr, param.getTraceId(), e.getMessage(), e);
                    ExceptionAwareUtil.exceptionOverflow(e);
                }

                @Override
                public void cancelled() {
                    MetricsCollector.sendFailed.incrementAndGet();
                    log.warn("发送到数据中心请求被取消,url:{},traceId:{}", DeepCoverConfig.dataCenterAddr, param.getTraceId());
                }
                });
            } catch (Exception e) {
                MetricsCollector.sendFailed.incrementAndGet();
                log.warn("发送到数据中心异常,url:{},traceId:{},resMsg:{}", DeepCoverConfig.dataCenterAddr, param.getTraceId(), e.getMessage(), e);
                ExceptionAwareUtil.exceptionOverflow(e);
            }
        }

    }

    public static String doPost(String url, Map<String, Object> param) throws IOException {
        HttpPost httppost = new HttpPost();
        try {
            //初始化httpPost
            httppost.setConfig(reqestConfig);
            httppost.setURI(URI.create(url));
            httppost.setHeader("Connection", "Keep-Alive");
            StringEntity e = new StringEntity(String.valueOf(param), ContentType.APPLICATION_JSON);
            httppost.setEntity(e);
        } catch (Exception e) {
            //monitor,后期改成warn
            log.warn("发送数据中心-返回失败doPost", e);
            return null;
        }
        // 执行http请求
        try {
            return getHttpClient().execute(httppost, responseHandler);
        } catch (Exception e) {
            log.error("发送数据中心-返回失败doPost,url={}", url, e);
        }

        return null;
    }

    public static void main(String[] args) throws IOException {
//        doPost("http://baidu.com", new HashMap<>());
        String traceId = "10110024003T30758T17337307800530007";
        String[] tras = traceId.split("T|\\.");
        System.out.println(tras.length);
        System.out.println("==============================");
        System.out.println(tras[2]);
        System.out.println(tras[2].length() - 7);
        System.out.println(tras[2].substring(tras[2].length() - 7));
        System.out.println(tras[2].length() - 4);
        System.out.println(tras[2].substring(tras[2].length() - 4));
        System.out.println(Long.parseLong(tras[2].substring(tras[2].length() - 7, tras[2].length() - 4)));
    }

    public static int batchDoPost(String url, List<CodeEntity> codeList) {
        int successCount = 0;
        for (CodeEntity code : codeList) {
            if (doPost(url, code)) {
                successCount++;
            }
        }
        return successCount;
    }

    public static boolean doPost(String url, CodeEntity param) {
        HttpPost httppost = new HttpPost();
        if (param != null) {
//                List<NameValuePair> paramList = new ArrayList<>();
//                for (String key : param.keySet()) {
//                    paramList.add(new BasicNameValuePair(key, (String) param.get(key)));
//                }
            // 模拟表单
//                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paramList);
            try {
                //初始化httpPost

                httppost.setConfig(reqestConfig);
                httppost.setURI(URI.create(url));
                httppost.setHeader("Connection", "Keep-Alive");
//            httppost.setEntity(e);
//            url=
                httppost.setHeader("traceId", param.getTraceId());
                httppost.setHeader("serviceName", param.getServiceName());
                httppost.setHeader("processId", String.valueOf(param.getProcessId()));
                httppost.setHeader("codeInfoSize", String.valueOf(param.getCodeInfoSize()));
                httppost.setHeader("url", String.valueOf(param.getUrl()));
                StringEntity e = new StringEntity(param.toString(), ContentType.APPLICATION_JSON);
                httppost.setEntity(e);
            } catch (Exception e) {
                //monitor,后期改成warn
                log.warn("发送数据中心-请求报文组装异常,traceid={}", param.getTraceId(), e);
                ExceptionAwareUtil.exceptionOverflow(e);
                return false;
            }

            try {
                // 执行http请求
                getHttpClient().execute(httppost, responseHandler);
                return true;
            } catch (HttpResponseException e) {
                //monitor,后期改成warn
                log.warn("发送到数据中心-返回异常,url:{},traceId:{},code:{},resMsg:{}", DeepCoverConfig.dataCenterAddr, param.getTraceId(), e.getStatusCode(), e.getMessage(), e);
//                log.info("发送数据中心-返回失败,traceId={},processId={}",param.getTraceId(),param.getProcessId(),e);
                ExceptionAwareUtil.exceptionOverflow(e);
            } catch (Exception e) {
                //monitor,后期改成warn
                log.warn("发送到数据中心异常,url:{},traceId:{},resMsg:{}", DeepCoverConfig.dataCenterAddr, param.getTraceId(), e.getMessage(), e);
                ExceptionAwareUtil.exceptionOverflow(e);
            }
        }
//        httppost.setURI(URI.create(url+"/"+param.get("traceId")));
        return false;
    }


}
