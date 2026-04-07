package io.deepcover.agent.entity;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.deepcover.agent.config.DeepCoverConfig;
import io.deepcover.agent.config.queue.LocalAsyncMsg;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
//@ToString(callSuper = true)
public class CodeEntity implements Serializable{
    private static final long serialVersionUID = 1L;

//    /**
//     * code信息
//     */
//    private String codeInfo;

    //HTTP
    private String type;
    private String addr;
    private Integer port;
    private String method;
    private String url;
    private Long beginTime;
    private String env;
    private String serviceName;
    private String branch;
    private String traceId;
    private Integer processId;
    private ArrayList<LineEntity> codeInfo;
    private Integer  codeInfoSize;
    private Integer isSend;

    @Override
    public String toString() {
        JSONArray jsonArray = new JSONArray(codeInfo.size());
        for(LineEntity line:codeInfo){
            jsonArray.add(JSONObject.parse(line.toString()));
        }
        return new JSONObject()
            .fluentPut("type",type)
            .fluentPut("addr",addr)
            .fluentPut("port",port)
            .fluentPut("method",method)
            .fluentPut("url",url)
            .fluentPut("beginTime",beginTime)
            .fluentPut("env",env)
            .fluentPut("serviceName",serviceName)
            .fluentPut("branch",branch)
            .fluentPut("traceId",traceId)
            .fluentPut("processId",processId)
            .fluentPut("codeInfoSize",codeInfoSize)
            .fluentPut("codeInfo",jsonArray)
            .toJSONString();
    }

    public  static void main(String[] args){
        for(int i=0;i<100;i++){
            JSONObject json = new JSONObject();
            json.put("qq","qq");
            json.put("list",new ArrayList<>());
            LineEntity line = new LineEntity();
            line.setClassName("3333");
            Set<Integer> lineNum = new LinkedHashSet<>();
            lineNum.add(1);lineNum.add(2);
            line.setLineNum(lineNum);
            ArrayList list =(ArrayList<LineEntity>)json.get("list");
            list.add(line);
//            json.getJSONArray("list").add(line);
            try{
                json.toString();
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        CodeEntity test =new CodeEntity();
        test.setServiceName("daddd");
        ArrayList<LineEntity> list = new ArrayList<LineEntity>();
        for(int i=0;i<10;i++){
            LineEntity line = new LineEntity();
            line.setClassName("3333"+i);
            Set<Integer> lineNum = new LinkedHashSet<>();
            lineNum.add(1);lineNum.add(2);
            line.setInvokeId(i);
            line.setLineNum(lineNum);
            list.add(line);
        }


        test.setCodeInfo(list);
//        Map<String,Object> map = (Map<String, Object>) test;
        System.out.println(test.toString());

    }
}
