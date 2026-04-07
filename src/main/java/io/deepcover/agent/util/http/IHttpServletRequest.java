package io.deepcover.agent.util.http;

import java.util.Map;

public interface IHttpServletRequest {
    @InterfaceProxyUtils.ProxyMethod(name = "getRemoteAddr")
    String getRemoteAddress();


    int getServerPort();

    String getMethod();

    String getRequestURI();

    Map<String, String[]> getParameterMap();

    String getHeader(String name);
}
