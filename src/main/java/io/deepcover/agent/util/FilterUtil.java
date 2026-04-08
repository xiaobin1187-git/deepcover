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
package io.deepcover.agent.util;

import com.alibaba.jvm.sandbox.api.filter.Filter;

public class FilterUtil  implements Filter {

    private final String[] javaNameRegex;
    private final String[] javaMethodRegex;

    public FilterUtil(String[] javaNameRegex, String[] javaMethodRegex) {
        this.javaNameRegex = javaNameRegex;
        this.javaMethodRegex = javaMethodRegex;
    }
    @Override
    public boolean doClassFilter(int access, String javaClassName, String superClassTypeJavaClassName, String[] interfaceTypeJavaClassNameArray, String[] annotationTypeJavaClassNameArray) {
        for(String nameRegex:this.javaNameRegex){
            if(javaClassName.matches(nameRegex)){
                return false;
            }
        }
        return true;
//        return javaClassName.matches(this.javaNameRegex);
    }
    @Override
    public boolean doMethodFilter(int access, String javaMethodName, String[] parameterTypeJavaClassNameArray, String[] throwsTypeJavaClassNameArray, String[] annotationTypeJavaClassNameArray) {
        for(String methodRegex:this.javaMethodRegex){
            if(javaMethodName.matches(methodRegex)){
                return false;
            }
        }
        return true;

//        return javaMethodName.matches(this.javaMethodRegex);
    }
}
