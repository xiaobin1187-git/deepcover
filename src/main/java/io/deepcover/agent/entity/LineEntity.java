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
package io.deepcover.agent.entity;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 代码行信息
 *
 * @author DeepCover Contributors
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

  private boolean limitReached;

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof LineEntity)) {
      return false;
    }
    LineEntity line = (LineEntity) obj;
    return Objects.equals(className, line.className)
            && Objects.equals(methodName, line.methodName)
            && Objects.equals(parameters, line.parameters)
            && orderedLinesEqual(lineNum, line.lineNum);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(className, methodName, parameters);
    if (lineNum != null) {
      for (Integer line : lineNum) {
        result = 31 * result + Objects.hashCode(line);
      }
    }
    return result;
  }

  private boolean orderedLinesEqual(Set<Integer> left, Set<Integer> right) {
    if (left == right) {
      return true;
    }
    if (left == null || right == null || left.size() != right.size()) {
      return false;
    }
    Iterator<Integer> leftIterator = left.iterator();
    Iterator<Integer> rightIterator = right.iterator();
    while (leftIterator.hasNext()) {
      if (!Objects.equals(leftIterator.next(), rightIterator.next())) {
        return false;
      }
    }
    return true;
  }

  JSONObject toJsonObject() {
    return new JSONObject()
        .fluentPut("invokeId", invokeId)
        .fluentPut("beginTime", beginTime)
        .fluentPut("callLineCount", callLineCount)
        .fluentPut("className", className)
        .fluentPut("methodName", methodName)
        .fluentPut("parameters", parameters)
        .fluentPut("lineNum", lineNum);
  }

  @Override
  public String toString() {
    return toJsonObject().toJSONString();
  }
}
