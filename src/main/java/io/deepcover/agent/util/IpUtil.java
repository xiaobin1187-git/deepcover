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
