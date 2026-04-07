package com.mobai.alert.service;

import com.mobai.alert.api.EnterpriseWechatApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
            System.out.println("[忽略] " + recordKey + " 在冷却窗口内已发送过通知");
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
        BigDecimal closePrice = decimal(kline.getClose(), 2);
        BigDecimal amplitude = calculateAmplitude(kline).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volume = convertToWan(decimal(kline.getVolume(), 0));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime klineTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.getEndTime()), ZoneId.systemDefault());
        String interval = StringUtils.hasText(kline.getInterval()) ? kline.getInterval() : "15m";

        StringBuilder builder = new StringBuilder();
        builder.append(signal.getTitle()).append("：**").append(kline.getSymbol()).append("**\n")
                .append("> 周期：").append(interval).append("\n")
                .append("> 收盘价：").append(closePrice).append(" USDT\n")
                .append("> 振幅：").append(amplitude).append("%\n")
                .append("> 成交额：").append(volume).append(" 万USDT\n")
                .append("> 触发位：").append(formatNullablePrice(signal.getTriggerPrice())).append("\n")
                .append("> 失效位：").append(formatNullablePrice(signal.getInvalidationPrice())).append("\n")
                .append("> 量能倍数：").append(formatNullableRatio(signal.getVolumeRatio())).append("\n")
                .append("> 逻辑：").append(signal.getSummary()).append("\n")
                .append("> 当前时间：").append(MESSAGE_TIME_FORMATTER.format(now)).append("\n")
                .append("> K线时间：").append(MESSAGE_TIME_FORMATTER.format(klineTime)).append("\n")
                .append("[点击查看实时K线图](https://www.binance.com/en/futures/")
                .append(kline.getSymbol())
                .append("?type=spot&layout=pro&interval=")
                .append(interval)
                .append(")");
        return builder.toString();
    }

    private BigDecimal calculateAmplitude(BinanceKlineDTO kline) {
        BigDecimal high = new BigDecimal(kline.getHigh());
        BigDecimal low = new BigDecimal(kline.getLow());
        return high.subtract(low).abs().divide(low, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal convertToWan(BigDecimal amount) {
        return amount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(String value, int scale) {
        return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private String formatNullablePrice(BigDecimal price) {
        if (price == null) {
            return "-";
        }
        return price.setScale(2, RoundingMode.HALF_UP) + " USDT";
    }

    private String formatNullableRatio(BigDecimal ratio) {
        if (ratio == null) {
            return "-";
        }
        return ratio.setScale(2, RoundingMode.HALF_UP) + " 倍";
    }
}
