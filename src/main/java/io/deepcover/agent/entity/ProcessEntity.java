package io.deepcover.agent.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 模型信息
 *
 * @author yingzhu
 * @date 2022年11月28日
 */
@Data
public class ProcessEntity implements Serializable {
  private static final long serialVersionUID = 1L;

  private Long processId;

  private String env;

}
