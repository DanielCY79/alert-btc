package com.mobai.alert.service;

import com.mobai.alert.api.EnterpriseWechatApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertNotificationService {

    private static final long COOLDOWN_PERIOD = 2 * 60 * 60 * 1000L;
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final EnterpriseWechatApi enterpriseWechatApi;
    private final Map<String, Long> sentRecords = new ConcurrentHashMap<>();

    public AlertNotificationService(EnterpriseWechatApi enterpriseWechatApi) {
        this.enterpriseWechatApi = enterpriseWechatApi;
    }

    public void send(AlertSignal signal) {
        String recordKey = signal.getKline().getSymbol() + signal.getType();
        if (!allowSend(recordKey)) {
            System.out.println("[\u62D2\u7EDD] " + recordKey + " \u51B7\u5374\u65F6\u95F4\u5185\u5DF2\u53D1\u9001\u8FC7\u901A\u77E5");
            return;
        }
        enterpriseWechatApi.sendGroupMessage(buildMessage(signal));
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredRecords() {
        long currentTime = System.currentTimeMillis();
        sentRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > COOLDOWN_PERIOD);
    }

    private boolean allowSend(String recordKey) {
        long currentTime = System.currentTimeMillis();
        Long lastSentTime = sentRecords.get(recordKey);
        if (lastSentTime == null || currentTime - lastSentTime > COOLDOWN_PERIOD) {
            sentRecords.put(recordKey, currentTime);
            return true;
        }
        return false;
    }

    private String buildMessage(AlertSignal signal) {
        BinanceKlineDTO kline = signal.getKline();
        BigDecimal closePrice = new BigDecimal(kline.getClose()).setScale(4, RoundingMode.HALF_DOWN);
        BigDecimal amplitude = calculateAmplitude(kline).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volume = convertToWan(new BigDecimal(kline.getVolume()).setScale(0, RoundingMode.HALF_DOWN));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime klineTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.getEndTime()), ZoneId.systemDefault());

        return signal.getTitle() + "\uFF1A**" + kline.getSymbol() + "**\n"
                + " \u6536\u76D8\u4EF7\uFF1A" + closePrice + " USDT\n"
                + " \u632F\u5E45\uFF1A" + amplitude + "%\n"
                + " \u6210\u4EA4\u989D\uFF1A" + volume + "\u4E07USDT\n"
                + " \u5F53\u524D\u65F6\u95F4\uFF1A" + MESSAGE_TIME_FORMATTER.format(now) + "\n"
                + " K\u7EBF\u65F6\u95F4\uFF1A" + MESSAGE_TIME_FORMATTER.format(klineTime) + "\n"
                + " [\u70B9\u51FB\u67E5\u770B\u5B9E\u65F6K\u7EBF\u56FE](https://www.binance.com/en/futures/" + kline.getSymbol() + "?type=spot&layout=pro&interval=1m)";
    }

    private BigDecimal calculateAmplitude(BinanceKlineDTO kline) {
        BigDecimal high = new BigDecimal(kline.getHigh());
        BigDecimal low = new BigDecimal(kline.getLow());
        return high.subtract(low).abs().divide(low, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal convertToWan(BigDecimal amount) {
        return amount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
    }
}
