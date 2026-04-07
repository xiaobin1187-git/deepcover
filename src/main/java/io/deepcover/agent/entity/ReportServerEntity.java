package io.deepcover.agent.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class ReportServerEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 服务本身ip
     */
    private String ip;
    /**
     * 配置版本号
     */
    private Integer version;

    /**
     * 服务名
     */
    private String serviceName;

    /**
     * 服务名
     */
    private String envCode;
}
