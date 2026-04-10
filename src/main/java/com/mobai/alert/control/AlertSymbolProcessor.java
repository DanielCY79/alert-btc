package com.mobai.alert.control;

import com.mobai.alert.access.facade.BinanceApi;
import com.mobai.alert.access.binance.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.binance.kline.dto.BinanceSymbolsDetailDTO;
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

    @Value("${monitoring.target-symbol:BTCUSDT}")
    private String targetSymbol;

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

        log.info("寮€濮嬪鐞嗕氦鏄撳锛宻ymbol={}锛宻tatus={}锛宨nterval={}锛宭imit={}",
                symbolDTO.getSymbol(),
                symbolDTO.getStatus(),
                klineInterval,
                klineLimit);

        List<BinanceKlineDTO> klines = loadRecentKlines(symbolDTO.getSymbol());
        if (CollectionUtils.isEmpty(klines) || klines.size() < 3) {
            log.warn("K 绾挎暟鎹笉瓒筹紝鏃犳硶璇勪及淇″彿锛宻ymbol={}锛屽疄闄?K 绾挎暟閲?{}", symbolDTO.getSymbol(), klines == null ? 0 : klines.size());
            return;
        }

        log.info("K 绾垮姞杞藉畬鎴愶紝symbol={}锛岃幏鍙栧埌 {} 鏍?K 绾匡紝鏈€鏂版敹鐩樻椂闂?{}",
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
                .map(signal -> recordBreakout(signal, longBreakoutKey(), shortBreakoutKey()))
                .orElse(false);
        if (breakoutTriggered) {
            return;
        }

        breakoutTriggered = alertRuleEvaluator.evaluateTrendBreakdown(klines)
                .map(signal -> recordBreakout(signal, shortBreakoutKey(), longBreakoutKey()))
                .orElse(false);
        if (breakoutTriggered) {
            return;
        }

        BreakoutRecord longBreakout = breakoutRecords.get(longBreakoutKey());
        if (longBreakout != null
                && sendIfPresent(alertRuleEvaluator.evaluateBreakoutPullback(klines, longBreakout.breakoutLevel(), longBreakout.targetPrice(), true))) {
            return;
        }

        BreakoutRecord shortBreakout = breakoutRecords.get(shortBreakoutKey());
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
            log.info("宸叉竻鐞嗚繃鏈熺獊鐮磋褰曪紝鏁伴噺={}", removed);
        }
    }

    private boolean shouldSkip(BinanceSymbolsDetailDTO symbolDTO) {
        String symbol = symbolDTO.getSymbol();
        if (!StringUtils.hasText(symbol) || !Objects.equals(symbol, targetSymbol)) {
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
            log.info("鍛戒腑绛栫暐淇″彿锛宻ymbol={}锛宻ignalType={}锛宒irection={}锛宼rigger={}锛宼arget={}",
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
        log.info("鍛戒腑绐佺牬绫讳俊鍙凤紝鍑嗗璁板綍绐佺牬涓婁笅鏂囷紝symbol={}锛宻ignalType={}锛宐reakoutLevel={}锛宼arget={}",
                signal.getKline().getSymbol(),
                signal.getType(),
                signal.getTriggerPrice(),
                signal.getTargetPrice());
        alertNotificationService.send(signal);
        breakoutRecords.put(recordKey, new BreakoutRecord(signal.getTriggerPrice(), signal.getTargetPrice(), System.currentTimeMillis()));
        breakoutRecords.remove(oppositeKey);
        log.info("绐佺牬璁板綍宸叉洿鏂帮紝褰撳墠璁板綍閿?{}锛屽凡娓呯悊瀵瑰悜璁板綍閿?{}", recordKey, oppositeKey);
        return true;
    }

    private String longBreakoutKey() {
        return targetSymbol + ":LONG";
    }

    private String shortBreakoutKey() {
        return targetSymbol + ":SHORT";
    }
}

