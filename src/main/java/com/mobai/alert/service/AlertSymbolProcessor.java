package com.mobai.alert.service;

import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Service
public class AlertSymbolProcessor {

    private static final long BACK_COOLDOWN_PERIOD = 2 * 60 * 60 * 1000L;

    @Value("${monitoring.exclude.symbol:}")
    private String excludeSymbol;

    private final BinanceApi binanceApi;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final AlertNotificationService alertNotificationService;
    private final Map<String, Long> backRecords = new ConcurrentHashMap<>();

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
        if (CollectionUtils.isEmpty(klines) || klines.size() < 4) {
            return;
        }

        BinanceKlineDTO referenceKline = klines.get(2);
        List<BinanceKlineDTO> recentThreeClosedKlines = collectDescending(klines, klines.size() - 2, 1);
        if (allMatch(recentThreeClosedKlines, alertRuleEvaluator::isContinuousThreeMatch)) {
            alertNotificationService.send(new AlertSignal("\u8FDE\u7EED 3 \u6839K\u7EBF\u62C9\u5347", referenceKline, "1"));
            backRecords.put(symbolDTO.getSymbol(), System.currentTimeMillis());
        }

        if (backRecords.containsKey(symbolDTO.getSymbol())) {
            BinanceKlineDTO latestClosedKline = klines.get(klines.size() - 2);
            if (alertRuleEvaluator.isBacktrackMatch(latestClosedKline)) {
                alertNotificationService.send(new AlertSignal("\u56DE\u8E29\u4EA4\u6613\u5BF9", latestClosedKline, "2"));
            }
        }

        List<BinanceKlineDTO> recentTwoClosedKlines = collectDescending(klines, klines.size() - 2, 2);
        if (allMatch(recentTwoClosedKlines, alertRuleEvaluator::isContinuousTwoMatch)) {
            alertNotificationService.send(new AlertSignal("\u8FDE\u7EED 2 \u6839K\u7EBF\u62C9\u5347", referenceKline, "3"));
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredBackRecords() {
        long currentTime = System.currentTimeMillis();
        backRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > BACK_COOLDOWN_PERIOD);
    }

    private boolean shouldSkip(BinanceSymbolsDetailDTO symbolDTO) {
        String symbol = symbolDTO.getSymbol();
        if (!StringUtils.hasText(symbol) || !symbol.contains("USDT")) {
            return true;
        }
        if (isExcluded(symbol)) {
            return true;
        }
        return !Objects.equals(symbolDTO.getStatus(), "TRADING");
    }

    private boolean isExcluded(String symbol) {
        if (!StringUtils.hasText(excludeSymbol)) {
            return false;
        }
        return Arrays.stream(excludeSymbol.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(symbol::equals);
    }

    private List<BinanceKlineDTO> loadRecentKlines(String symbol) {
        BinanceKlineDTO reqDTO = new BinanceKlineDTO();
        reqDTO.setSymbol(symbol);
        reqDTO.setInterval("1m");
        reqDTO.setLimit(5);
        reqDTO.setTimeZone("8");
        Instant previousMinute = Instant.now().minus(1, ChronoUnit.MINUTES);
        reqDTO.setEndTime(System.currentTimeMillis());
        reqDTO.setStartTime(previousMinute.toEpochMilli());
        return binanceApi.listKline(reqDTO);
    }

    private List<BinanceKlineDTO> collectDescending(List<BinanceKlineDTO> klines, int startIndex, int endIndex) {
        List<BinanceKlineDTO> result = new ArrayList<>();
        for (int i = startIndex; i >= endIndex && i >= 0; i--) {
            result.add(klines.get(i));
        }
        return result;
    }

    private boolean allMatch(List<BinanceKlineDTO> klines, Predicate<BinanceKlineDTO> predicate) {
        if (CollectionUtils.isEmpty(klines)) {
            return false;
        }
        for (BinanceKlineDTO kline : klines) {
            if (!predicate.test(kline)) {
                return false;
            }
        }
        return true;
    }
}
