package io.deepcover.agent.util;

import io.deepcover.agent.config.DeepCoverConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

@Slf4j
public class TraceUtil {

    private static boolean isValid(String traceId) {
        if (StringUtils.isBlank(traceId) || "N/A".equals(traceId) || "Ignored_Trace".equals(traceId)) {
            return false;
        }
        int len = traceId.length();
        if (len < 5 || len > 40) {
            log.warn("traceId:{},格式长度异常", traceId);
            return false;
        }

        return NumberUtils.isDigits(traceId.substring(len - 6, len - 1));
    }

    /**
     * 及时计算采样
     *
     * @param traceId
     * @return 是否被采样
     */
    public static boolean inTimeSample(String traceId) {
        if (isValid(traceId)) {
            String[] tras = traceId.split("T|\\.");
            if (tras.length != 3) {
                log.warn("traceId:{},格式不支持", traceId);
                return false;
            }
//            int theadIdEnd = Integer.parseInt(tras[1].substring(tras[1].length()-1,tras[1].length()));
            Long calTrace;
            try {
                calTrace = Long.parseLong(tras[2].substring(tras[2].length() - 7, tras[2].length() - 4));
            } catch (StringIndexOutOfBoundsException ex) {
                log.warn("StringIndexOutOfBoundsException cause by traceId:{}, tras[2]:{} substring .", traceId, tras[2]);
                return false;
            }
            return calTrace < DeepCoverConfig.sampleRate;
        } else {
            return false;
        }
    }

}
