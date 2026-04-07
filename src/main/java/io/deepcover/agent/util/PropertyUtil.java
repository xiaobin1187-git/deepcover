package io.deepcover.agent.util;


import org.apache.commons.lang3.StringUtils;

/**
 * @description:
 * @author: wuchen
 * @time: 2023/2/28 20:03
 */
public class PropertyUtil {

    /**
     * 获取系统属性带默认值
     *
     * @param key          属性key
     * @param defaultValue 默认值
     * @return 属性值 or 默认值
     */
    public static String getSystemPropertyOrDefault(String key, String defaultValue) {
        String property = System.getProperty(key);
        return StringUtils.isEmpty(property) ? defaultValue : property;
    }

    /**
     * 获取环境变量
     * @param key
     * @param defaultValue
     * @return
     */
    public static String getEnvPropertyOrDefault(String key, String defaultValue) {
        String property = System.getenv(key);
        return StringUtils.isEmpty(property) ? defaultValue : property;
    }
}
