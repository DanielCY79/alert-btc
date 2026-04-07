package com.mobai.alert.service;

import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertSymbolProcessor {

    private static final String TARGET_SYMBOL = "BTCUSDT";

    @Value("${monitoring.kline.interval:15m}")
    private String klineInterval;

    @Value("${monitoring.kline.limit:80}")
    private int klineLimit;

    @Value("${monitoring.strategy.breakout.record.ttl.ms:43200000}")
    private long breakoutRecordTtlMs;

    private final BinanceApi binanceApi;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final AlertNotificationService alertNotificationService;
    private final Map<String, BreakoutRecord> breakoutRecords = new ConcurrentHashMap<>();

    public AlertSymbolProcessor(BinanceApi binanceApi,
                                AlertRuleEvaluator alertRuleEvaluator,
                                AlertNotificationService alertNotificationService) {
        this.binanceApi = binanceApi;
        this.alertRuleEvaluator = alertRuleEvaluator;
        this.alertNotificationService = alertNotificationService;
    }

    public void process(BinanceSymbolsDetailDTO symbolDTO) {
        if (shouldSkip(symbolDTO)) {
            return;
        }

        List<BinanceKlineDTO> klines = loadRecentKlines(symbolDTO.getSymbol());
        if (CollectionUtils.isEmpty(klines) || klines.size() < 3) {
            return;
        }

        boolean breakoutTriggered = alertRuleEvaluator.evaluateTrendBreakout(klines)
                .map(signal -> {
                    alertNotificationService.send(signal);
                    breakoutRecords.put(symbolDTO.getSymbol(), new BreakoutRecord(signal.getTriggerPrice(), System.currentTimeMillis()));
                    return true;
                })
                .orElse(false);

        if (breakoutTriggered) {
            return;
        }

        BreakoutRecord breakoutRecord = breakoutRecords.get(symbolDTO.getSymbol());
        if (breakoutRecord == null) {
            return;
        }

        alertRuleEvaluator.evaluateBreakoutPullback(klines, breakoutRecord.breakoutLevel())
                .ifPresent(alertNotificationService::send);
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredBreakoutRecords() {
        long currentTime = System.currentTimeMillis();
        breakoutRecords.entrySet().removeIf(entry -> currentTime - entry.getValue().timestamp() > breakoutRecordTtlMs);
    }

    private boolean shouldSkip(BinanceSymbolsDetailDTO symbolDTO) {
        String symbol = symbolDTO.getSymbol();
        if (!StringUtils.hasText(symbol) || !Objects.equals(symbol, TARGET_SYMBOL)) {
            return true;
        }
        return !Objects.equals(symbolDTO.getStatus(), "TRADING");
    }

    private List<BinanceKlineDTO> loadRecentKlines(String symbol) {
        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol(symbol);
        request.setInterval(klineInterval);
        request.setLimit(klineLimit);
        request.setTimeZone("8");
        return binanceApi.listKline(request);
    }

    private record BreakoutRecord(BigDecimal breakoutLevel, long timestamp) {
    }
}
