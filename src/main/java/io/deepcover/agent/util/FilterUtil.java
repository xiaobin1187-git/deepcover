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
