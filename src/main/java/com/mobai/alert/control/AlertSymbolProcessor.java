package com.mobai.alert.control;

import com.mobai.alert.access.dto.BinanceKlineDTO;
import com.mobai.alert.access.dto.BinanceSymbolsDetailDTO;
import com.mobai.alert.access.exchange.BinanceApi;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.state.runtime.BreakoutRecord;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.strategy.AlertRuleEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertSymbolProcessor {

    private static final Logger log = LoggerFactory.getLogger(AlertSymbolProcessor.class);
    private static final String TARGET_SYMBOL = "BTCUSDT";
    private static final String LONG_BREAKOUT_KEY = TARGET_SYMBOL + ":LONG";
    private static final String SHORT_BREAKOUT_KEY = TARGET_SYMBOL + ":SHORT";

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

        log.info("开始处理交易对，symbol={}，status={}，interval={}，limit={}",
                symbolDTO.getSymbol(),
                symbolDTO.getStatus(),
                klineInterval,
                klineLimit);

        List<BinanceKlineDTO> klines = loadRecentKlines(symbolDTO.getSymbol());
        if (CollectionUtils.isEmpty(klines) || klines.size() < 3) {
            log.warn("K 线数据不足，无法评估信号，symbol={}，实际K线数量={}", symbolDTO.getSymbol(), klines == null ? 0 : klines.size());
            return;
        }

        log.info("K 线加载完成，symbol={}，获取到 {} 根K线，最新收盘时间={}",
                symbolDTO.getSymbol(),
                klines.size(),
                klines.get(klines.size() - 1).getEndTime());

        if (sendIfPresent(alertRuleEvaluator.evaluateRangeFailedBreakdownLong(klines))) {
            return;
        }
        if (sendIfPresent(alertRuleEvaluator.evaluateRangeFailedBreakoutShort(klines))) {
            return;
        }

        boolean breakoutTriggered = alertRuleEvaluator.evaluateTrendBreakout(klines)
                .map(signal -> recordBreakout(signal, LONG_BREAKOUT_KEY, SHORT_BREAKOUT_KEY))
                .orElse(false);
        if (breakoutTriggered) {
            return;
        }

        breakoutTriggered = alertRuleEvaluator.evaluateTrendBreakdown(klines)
                .map(signal -> recordBreakout(signal, SHORT_BREAKOUT_KEY, LONG_BREAKOUT_KEY))
                .orElse(false);
        if (breakoutTriggered) {
            return;
        }

        BreakoutRecord longBreakout = breakoutRecords.get(LONG_BREAKOUT_KEY);
        if (longBreakout != null
                && sendIfPresent(alertRuleEvaluator.evaluateBreakoutPullback(klines, longBreakout.breakoutLevel(), longBreakout.targetPrice(), true))) {
            return;
        }

        BreakoutRecord shortBreakout = breakoutRecords.get(SHORT_BREAKOUT_KEY);
        if (shortBreakout != null) {
            sendIfPresent(alertRuleEvaluator.evaluateBreakoutPullback(klines, shortBreakout.breakoutLevel(), shortBreakout.targetPrice(), false));
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredBreakoutRecords() {
        long currentTime = System.currentTimeMillis();
        int before = breakoutRecords.size();
        breakoutRecords.entrySet().removeIf(entry -> currentTime - entry.getValue().timestamp() > breakoutRecordTtlMs);
        int removed = before - breakoutRecords.size();
        if (removed > 0) {
            log.info("已清理过期突破记录，数量={}", removed);
        }
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

    private boolean sendIfPresent(Optional<AlertSignal> signalOptional) {
        signalOptional.ifPresent(signal -> {
            log.info("命中策略信号，symbol={}，signalType={}，direction={}，trigger={}，target={}",
                    signal.getKline().getSymbol(),
                    signal.getType(),
                    signal.getDirection(),
                    signal.getTriggerPrice(),
                    signal.getTargetPrice());
            alertNotificationService.send(signal);
        });
        return signalOptional.isPresent();
    }

    private boolean recordBreakout(AlertSignal signal, String recordKey, String oppositeKey) {
        log.info("命中突破类信号，准备记录突破上下文，symbol={}，signalType={}，breakoutLevel={}，target={}",
                signal.getKline().getSymbol(),
                signal.getType(),
                signal.getTriggerPrice(),
                signal.getTargetPrice());
        alertNotificationService.send(signal);
        breakoutRecords.put(recordKey, new BreakoutRecord(signal.getTriggerPrice(), signal.getTargetPrice(), System.currentTimeMillis()));
        breakoutRecords.remove(oppositeKey);
        log.info("突破记录已更新，当前记录键={}，已清理对向记录键={}", recordKey, oppositeKey);
        return true;
    }
}
