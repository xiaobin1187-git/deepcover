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
