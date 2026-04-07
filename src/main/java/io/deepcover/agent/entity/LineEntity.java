package io.deepcover.agent.entity;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 代码行信息
 *
 * @author yingzhu
 * @date 2022年11月28日
 */
//@Data
  @Getter
  @Setter
public class LineEntity {
  private String className;

  private String methodName;

  private List<String> parameters;

  private Set<Integer> lineNum = new LinkedHashSet<>();


  private Integer invokeId;

  private Long beginTime;

  private Long callLineCount;

  @Override
  public boolean equals(Object obj) {
    if(obj instanceof LineEntity) {
      LineEntity line = (LineEntity)obj;
      return this.getClassName().equals(line.getClassName())
              &&this.getMethodName().equals(line.getMethodName())&&this.getParameters().toString().equals(line.getParameters().toString())
              &&this.getLineNum().toString().equals(line.getLineNum().toString());
    }else {
      return false;
    }
  }

  @Override
  public String toString() {
    return new JSONObject()
        .fluentPut("invokeId", invokeId)
        .fluentPut("beginTime", beginTime)
        .fluentPut("callLineCount", callLineCount)
        .fluentPut("className", className)
        .fluentPut("methodName", methodName)
        .fluentPut("parameters", parameters)
        .fluentPut("lineNum", lineNum)
        .toJSONString();
  }
}
