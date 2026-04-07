package io.deepcover.agent.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.*;

@Slf4j
public class ExecutorThreadPoolConfig {
    public static ScheduledExecutorService scheduleEx = Executors.newScheduledThreadPool(1,
            new BasicThreadFactory.Builder().namingPattern("code-coverage-schedule-%d").daemon(true).build());
    public static ExecutorService executorSendMsg =
            new ThreadPoolExecutor(
                    4,
                    8,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(256),
                    new ThreadFactoryBuilder().setNameFormat("code-coverage-send-msg-%d").build(),
                    new ThreadPoolExecutor.CallerRunsPolicy());

//public static ScheduledExecutorService scheduleSendMsgEx = Executors.newScheduledThreadPool(1,
//        new BasicThreadFactory.Builder().namingPattern("code-coverage-send-msg-%d").daemon(true).build());


//public static void main(String[] args){
//    //异步线程，unload时候一定要shutdown无法关掉
//    while(true){
//        executorSendMsg.shutdown();
//        log.info("tttt{}",executorSendMsg.isShutdown());
//        try{
//            executorSendMsg.submit(new Runnable(){
//                @Override
//                public void run() {
//                    Thread.currentThread().setName("module-"+Thread.currentThread().getId());
//                    int b = 1+ 100;
//                    System.out.println(b);
//                }
//            });
//        }catch (RejectedExecutionException e){
//            log.info("线程池拒绝处理",e);
////            e.printStackTrace();
//        }catch(Exception e){
//            log.info("线程池异常",e);
//        }
//
//    }
//}
}
