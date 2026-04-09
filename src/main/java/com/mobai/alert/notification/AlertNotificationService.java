package com.mobai.alert.notification;

import com.mobai.alert.access.dto.BinanceKlineDTO;
import com.mobai.alert.notification.channel.AlertNotifier;
import com.mobai.alert.notification.model.NotificationMessage;
import com.mobai.alert.state.signal.AlertSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationService.class);
    private static final long COOLDOWN_PERIOD = 2 * 60 * 60 * 1000L;
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final List<AlertNotifier> alertNotifiers;
    private final Map<String, AlertNotifier> notifierByChannel;
    private final Map<String, Long> sentRecords = new ConcurrentHashMap<>();

    @Value("${notification.channel:feishu}")
    private String notificationChannel;

    public AlertNotificationService(List<AlertNotifier> alertNotifiers) {
        this.alertNotifiers = alertNotifiers;
        this.notifierByChannel = new ConcurrentHashMap<>();
        for (AlertNotifier alertNotifier : alertNotifiers) {
            notifierByChannel.put(normalizeChannelName(alertNotifier.channelName()), alertNotifier);
        }
    }

    public void send(AlertSignal signal) {
        String recordKey = signal.getKline().getSymbol() + signal.getType();
        if (!allowSend(recordKey)) {
            log.info("告警发送被冷却窗口拦截，recordKey={}，symbol={}，signalType={}",
                    recordKey,
                    signal.getKline().getSymbol(),
                    signal.getType());
            return;
        }

        if (CollectionUtils.isEmpty(alertNotifiers)) {
            log.warn("未配置可用通知渠道，跳过发送，symbol={}，signalType={}",
                    signal.getKline().getSymbol(),
                    signal.getType());
            return;
        }

        String selectedChannel = normalizeChannelName(notificationChannel);
        AlertNotifier alertNotifier = notifierByChannel.get(selectedChannel);
        if (alertNotifier == null) {
            log.warn("未找到匹配的通知渠道配置，channel={}，可用渠道={}",
                    selectedChannel,
                    notifierByChannel.keySet());
            return;
        }

        log.info("准备发送告警，symbol={}，interval={}，signalType={}，direction={}，channel={}",
                signal.getKline().getSymbol(),
                signal.getKline().getInterval(),
                signal.getType(),
                signal.getDirection(),
                selectedChannel);

        NotificationMessage message = buildMessage(signal);
        try {
            alertNotifier.send(message);
        } catch (Exception e) {
            log.error("通知渠道发送失败，channel={}，symbol={}，signalType={}",
                    alertNotifier.channelName(),
                    signal.getKline().getSymbol(),
                    signal.getType(),
                    e);
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredRecords() {
        long currentTime = System.currentTimeMillis();
        int before = sentRecords.size();
        sentRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > COOLDOWN_PERIOD);
        int removed = before - sentRecords.size();
        if (removed > 0) {
            log.info("已清理过期告警发送记录，数量={}", removed);
        }
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

    private NotificationMessage buildMessage(AlertSignal signal) {
        BinanceKlineDTO kline = signal.getKline();
        BigDecimal closePrice = decimal(kline.getClose(), 2);
        BigDecimal amplitude = calculateAmplitude(kline).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volume = convertToWan(decimal(kline.getVolume(), 0));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime klineTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.getEndTime()), ZoneId.systemDefault());
        String interval = StringUtils.hasText(kline.getInterval()) ? kline.getInterval() : "15m";
        String chartUrl = buildChartUrl(kline.getSymbol(), interval);

        String markdownContent = new StringBuilder()
                .append(signal.getTitle()).append(" *").append(kline.getSymbol()).append("*\n")
                .append("> Direction: ").append(signal.getDirection()).append("\n")
                .append("> Interval: ").append(interval).append("\n")
                .append("> Close: ").append(closePrice).append(" USDT\n")
                .append("> Amplitude: ").append(amplitude).append("%\n")
                .append("> Quote Volume: ").append(volume).append(" 万USDT\n")
                .append("> Trigger: ").append(formatNullablePrice(signal.getTriggerPrice())).append("\n")
                .append("> Invalidation: ").append(formatNullablePrice(signal.getInvalidationPrice())).append("\n")
                .append("> Target: ").append(formatNullablePrice(signal.getTargetPrice())).append("\n")
                .append("> Volume Ratio: ").append(formatNullableRatio(signal.getVolumeRatio())).append("\n")
                .append("> Logic: ").append(signal.getSummary()).append("\n")
                .append("> Now: ").append(MESSAGE_TIME_FORMATTER.format(now)).append("\n")
                .append("> Kline Close: ").append(MESSAGE_TIME_FORMATTER.format(klineTime)).append("\n")
                .append("[Open Chart](").append(chartUrl).append(")")
                .toString();

        String plainTextContent = new StringBuilder()
                .append(signal.getTitle()).append(" ").append(kline.getSymbol()).append("\n")
                .append("Direction: ").append(signal.getDirection()).append("\n")
                .append("Interval: ").append(interval).append("\n")
                .append("Close: ").append(closePrice).append(" USDT\n")
                .append("Amplitude: ").append(amplitude).append("%\n")
                .append("Quote Volume: ").append(volume).append(" 万USDT\n")
                .append("Trigger: ").append(formatNullablePrice(signal.getTriggerPrice())).append("\n")
                .append("Invalidation: ").append(formatNullablePrice(signal.getInvalidationPrice())).append("\n")
                .append("Target: ").append(formatNullablePrice(signal.getTargetPrice())).append("\n")
                .append("Volume Ratio: ").append(formatNullableRatio(signal.getVolumeRatio())).append("\n")
                .append("Logic: ").append(signal.getSummary()).append("\n")
                .append("Now: ").append(MESSAGE_TIME_FORMATTER.format(now)).append("\n")
                .append("Kline Close: ").append(MESSAGE_TIME_FORMATTER.format(klineTime)).append("\n")
                .append("Open Chart: ").append(chartUrl)
                .toString();

        return new NotificationMessage(markdownContent, plainTextContent);
    }

    private String buildChartUrl(String symbol, String interval) {
        return "https://www.binance.com/en/futures/"
                + symbol
                + "?type=spot&layout=pro&interval="
                + interval;
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
        return ratio.setScale(2, RoundingMode.HALF_UP) + "x";
    }

    private String normalizeChannelName(String channelName) {
        if (channelName == null) {
            return "";
        }
        return channelName.trim().toLowerCase(Locale.ROOT);
    }
}
