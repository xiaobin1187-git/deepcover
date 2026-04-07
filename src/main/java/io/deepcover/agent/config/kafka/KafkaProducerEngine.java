package io.deepcover.agent.config.kafka;

import io.deepcover.agent.config.DeepCoverConfig;
import io.deepcover.agent.entity.CodeEntity;
import io.deepcover.agent.util.ExceptionAwareUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KafkaProducerEngine {
    private static Producer<String, String> producer = null;
    public static void  initKafka(){
        if(producer==null){
            try{
                Properties props = new Properties();
                //设置接入点，请通过控制台获取对应Topic的接入点。
                props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, DeepCoverConfig.KAFKA_BOOTSTRAP_SERVERS);
                //消息队列Kafka版消息的序列化方式。
                props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
                props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
                //请求的最长等待时间。
                props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3 * 1000);
                //设置客户端内部重试次数。
                props.put(ProducerConfig.RETRIES_CONFIG, 1);
                //设置客户端内部重试间隔。
                props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 3000);
                //64KB
                props.put(ProducerConfig.BATCH_SIZE_CONFIG,"65535");
                //500ms
                props.put(ProducerConfig.LINGER_MS_CONFIG,500);

                //构造Producer对象，注意，该对象是线程安全的，一般来说，一个进程内一个Producer对象即可。
                //如果想提高性能，可以多构造几个对象，但不要太多，最好不要超过5个。

                producer = new KafkaProducer<String, String>(props);
            }catch (Exception e){
                log.error("kafka初始化失败",e);
            }
        }
    }

    public static void sendMessage(CodeEntity codeInfo){
        if(producer==null){
            log.warn("kafka生产者初始化失败，无法发送，请选择其他发送方式");
            ExceptionAwareUtil.exceptionOverflow(new NullPointerException("kafka producer is null"));
        }
        //发送消息，并获得一个Future对象。
        ProducerRecord<String, String> kafkaMessage =  new ProducerRecord<String, String>(DeepCoverConfig.KAFKA_TOPIC, codeInfo.toString());
        producer.send(kafkaMessage, new Callback(){
            @Override
            public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                if (e != null) {
                    log.warn("kafka发送失败，请选择其他发送方式，codeInfo={}",codeInfo.toString(),e);
                    ExceptionAwareUtil.exceptionOverflow(e);
                }
            }
        });
    }

    public static void shutdown() {
        if (producer != null) {
            try {
                producer.flush();
                producer.close(java.time.Duration.ofSeconds(10));
                log.info("Kafka producer closed successfully");
            } catch (Exception e) {
                log.error("Failed to close Kafka producer", e);
            }
            producer = null;
        }
    }

    public static void batchSendMessage(List<CodeEntity> codeList){
        if(producer==null){
            log.warn("kafka生产者初始化失败，无法发送，请选择其他发送方式");
            ExceptionAwareUtil.exceptionOverflow(new NullPointerException("kafka producer is null"));
        }
        //发送消息，并获得一个Future对象。
        for(CodeEntity codeInfo:codeList){
            String codeString = codeInfo.toString();
            int length = codeString.getBytes().length;
            log.info("start send to kafka,url={},traceId={},报文大小={}byte,codeTotalSize={},codeSize={}",codeInfo.getUrl(),codeInfo.getTraceId(),length,codeInfo.getCodeInfoSize(),codeInfo.getCodeInfo().size());
            ProducerRecord<String, String> kafkaMessage =  new ProducerRecord<String, String>(DeepCoverConfig.KAFKA_TOPIC,codeString);
            producer.send(kafkaMessage, new Callback(){
                @Override
                public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                    if (e != null) {
                        log.warn("kafka发送失败，请选择其他发送方式，codeInfo={}",codeInfo.toString(),e);
                        ExceptionAwareUtil.exceptionOverflow(e);
                    }
//                    else{
//                        log.info("kafka发送成功,url={},traceId={}",codeInfo.getUrl(),codeInfo.getTraceId());
//                    }
                }
            });
        }
    }
}
