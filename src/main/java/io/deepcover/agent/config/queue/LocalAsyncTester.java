package io.deepcover.agent.config.queue;

import io.deepcover.agent.entity.CodeEntity;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

/**
 * @Author shudian
 * @Date 2024/3/15-10:04
 * @Version 1.0
 */
public class LocalAsyncTester {
    static class MyHandler implements LocalAsyncConsumer {
        @Override
        public void init(Map<String, Object> properties) {

        }

        @Override
        public void consume(List<CodeEntity> msg) {
            System.out.println(Thread.currentThread().getId() + "." + Thread.currentThread().getName() + "消费了" + msg.size() + "条数据");
        }

        @Override
        public void onError(List<CodeEntity> msg, Throwable t) {

        }
    }

    static class MyMsg implements LocalAsyncMsg {

        private String msg;

        public MyMsg(String msg) {
            this.msg = msg;
        }

        @Override
        public int getSize() {
            try {
                return this.msg == null ? 0 : msg.getBytes("UTF-8").length;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        LocalAsyncEngine localAsyncEngine = new LocalAsyncEngine(10, 100, 300, 2, MyHandler.class, null);
        localAsyncEngine.start();
        localAsyncEngine.offerMsg(new CodeEntity());

        Thread.sleep(1000);
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
//        for(int i=0;i<100000;i++){
//            localAsyncEngine.offerMsg(new MyMsg("dddddddddddddddddddddddd"+i));
//        }
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());
        localAsyncEngine.offerMsg(new CodeEntity());

        Thread.sleep(10000000);
        localAsyncEngine.shutdown();

    }
}
