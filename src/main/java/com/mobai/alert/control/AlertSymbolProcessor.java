package com.mobai.alert.control;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.service.MarketFeatureSnapshotService;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.state.runtime.BreakoutRecord;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.strategy.AlertRuleEvaluator;
import com.mobai.alert.strategy.policy.CompositeFactorSignalPolicy;
import com.mobai.alert.strategy.policy.SignalPolicyDecision;
import com.mobai.alert.strategy.shared.StrategySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个交易对的监控执行器。
 * 负责加载行情、提取特征、评估策略、执行因子过滤，并在满足条件时发送告警。
 */
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
    private final MarketFeatureSnapshotService marketFeatureSnapshotService;
    private final CompositeFactorSignalPolicy compositeFactorSignalPolicy;
    private final Map<String, BreakoutRecord> breakoutRecords = new ConcurrentHashMap<>();

    public AlertSymbolProcessor(BinanceApi binanceApi,
                                AlertRuleEvaluator alertRuleEvaluator,
                                AlertNotificationService alertNotificationService,
                                MarketFeatureSnapshotService marketFeatureSnapshotService,
                                CompositeFactorSignalPolicy compositeFactorSignalPolicy) {
        this.binanceApi = binanceApi;
        this.alertRuleEvaluator = alertRuleEvaluator;
        this.alertNotificationService = alertNotificationService;
        this.marketFeatureSnapshotService = marketFeatureSnapshotService;
        this.compositeFactorSignalPolicy = compositeFactorSignalPolicy;
    }

    /**
     * 处理一个交易对的完整监控流程。
     */
    public void process(String symbol) {
        if (shouldSkip(symbol)) {
            return;
        }

        log.info("Processing symbol={}, interval={}, limit={}",
                symbol,
                klineInterval,
                klineLimit);

        List<BinanceKlineDTO> klines = loadRecentKlines(symbol);
        if (CollectionUtils.isEmpty(klines) || klines.size() < 3) {
            log.warn("Skip symbol={} because available klines are insufficient, count={}",
                    symbol,
                    klines == null ? 0 : klines.size());
            return;
        }

        log.info("Loaded recent klines, symbol={}, count={}, latestEndTime={}",
                symbol,
                klines.size(),
                klines.get(klines.size() - 1).getEndTime());

        FeatureSnapshot featureSnapshot = marketFeatureSnapshotService.buildSnapshot(symbol, klineInterval, klines);
        logFeatureSnapshot(featureSnapshot);

        if (sendIfPresent(alertRuleEvaluator.evaluateRangeFailedBreakdownLong(klines), featureSnapshot)) {
            return;
        }
        if (sendIfPresent(alertRuleEvaluator.evaluateRangeFailedBreakoutShort(klines), featureSnapshot)) {
            return;
        }

        boolean breakoutTriggered = alertRuleEvaluator.evaluateTrendBreakout(klines)
                .map(signal -> recordBreakout(signal, longBreakoutKey(), shortBreakoutKey(), featureSnapshot))
                .orElse(false);
        if (breakoutTriggered) {
            return;
        }

        breakoutTriggered = alertRuleEvaluator.evaluateTrendBreakdown(klines)
                .map(signal -> recordBreakout(signal, shortBreakoutKey(), longBreakoutKey(), featureSnapshot))
                .orElse(false);
        if (breakoutTriggered) {
            return;
        }

        BreakoutRecord longBreakout = breakoutRecords.get(longBreakoutKey());
        if (longBreakout != null
                && sendIfPresent(
                alertRuleEvaluator.evaluateBreakoutPullback(klines, longBreakout.breakoutLevel(), longBreakout.targetPrice(), true),
                featureSnapshot)) {
            return;
        }

        BreakoutRecord shortBreakout = breakoutRecords.get(shortBreakoutKey());
        if (shortBreakout != null) {
            sendIfPresent(
                    alertRuleEvaluator.evaluateBreakoutPullback(klines, shortBreakout.breakoutLevel(), shortBreakout.targetPrice(), false),
                    featureSnapshot
            );
        }
    }

    /**
     * 周期清理已过期的突破记忆。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredBreakoutRecords() {
        long currentTime = System.currentTimeMillis();
        int before = breakoutRecords.size();
        breakoutRecords.entrySet().removeIf(entry -> currentTime - entry.getValue().timestamp() > breakoutRecordTtlMs);
        int removed = before - breakoutRecords.size();
        if (removed > 0) {
            log.info("Removed expired breakout memories, count={}", removed);
        }
    }

    /**
     * 当前仅处理目标交易对，且要求交易状态为可交易。
     */
    private boolean shouldSkip(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            return true;
        }
        return !targetSymbol.equalsIgnoreCase(symbol);
    }

    /**
     * 按当前配置拉取最近 K 线。
     */
    private List<BinanceKlineDTO> loadRecentKlines(String symbol) {
        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol(symbol);
        request.setInterval(klineInterval);
        request.setLimit(klineLimit);
        request.setTimeZone("8");
        return binanceApi.listKline(request);
    }

    /**
     * 如果策略返回信号，则先经过复合因子过滤，再决定是否发送。
     */
    private boolean sendIfPresent(Optional<AlertSignal> signalOptional, FeatureSnapshot featureSnapshot) {
        if (signalOptional.isEmpty()) {
            return false;
        }

        SignalPolicyDecision decision = compositeFactorSignalPolicy.evaluate(signalOptional.get(), featureSnapshot);
        if (!decision.allowed()) {
            log.info("Signal blocked by composite policy, symbol={}, signalType={}, direction={}, score={}, reasons={}",
                    signalOptional.get().getKline().getSymbol(),
                    signalOptional.get().getType(),
                    signalOptional.get().getDirection(),
                    StrategySupport.scaleOrNull(decision.score()),
                    String.join(" | ", decision.reasons()));
            return false;
        }

        AlertSignal signal = decision.signal();
        log.info("Signal approved, symbol={}, signalType={}, direction={}, trigger={}, target={}, compositeScore={}",
                signal.getKline().getSymbol(),
                signal.getType(),
                signal.getDirection(),
                signal.getTriggerPrice(),
                signal.getTargetPrice(),
                StrategySupport.scaleOrNull(signal.getContextScore()));
        alertNotificationService.send(signal);
        return true;
    }

    /**
     * 对通过过滤的突破信号做发送与缓存，供后续回踩策略继续使用。
     */
    private boolean recordBreakout(AlertSignal signal, String recordKey, String oppositeKey, FeatureSnapshot featureSnapshot) {
        SignalPolicyDecision decision = compositeFactorSignalPolicy.evaluate(signal, featureSnapshot);
        if (!decision.allowed()) {
            log.info("Breakout blocked by composite policy, symbol={}, signalType={}, direction={}, score={}, reasons={}",
                    signal.getKline().getSymbol(),
                    signal.getType(),
                    signal.getDirection(),
                    StrategySupport.scaleOrNull(decision.score()),
                    String.join(" | ", decision.reasons()));
            return false;
        }

        AlertSignal qualifiedSignal = decision.signal();
        log.info("Breakout approved, symbol={}, signalType={}, breakoutLevel={}, target={}, compositeScore={}",
                qualifiedSignal.getKline().getSymbol(),
                qualifiedSignal.getType(),
                qualifiedSignal.getTriggerPrice(),
                qualifiedSignal.getTargetPrice(),
                StrategySupport.scaleOrNull(qualifiedSignal.getContextScore()));
        alertNotificationService.send(qualifiedSignal);
        breakoutRecords.put(recordKey, new BreakoutRecord(
                qualifiedSignal.getTriggerPrice(),
                qualifiedSignal.getTargetPrice(),
                System.currentTimeMillis()
        ));
        breakoutRecords.remove(oppositeKey);
        log.info("Stored breakout memory, currentKey={}, clearedOppositeKey={}", recordKey, oppositeKey);
        return true;
    }

    /**
     * 多头突破记忆键。
     */
    private String longBreakoutKey() {
        return targetSymbol + ":LONG";
    }

    /**
     * 空头突破记忆键。
     */
    private String shortBreakoutKey() {
        return targetSymbol + ":SHORT";
    }

    /**
     * 记录特征快照摘要，便于排查因子是否准备齐全。
     */
    private void logFeatureSnapshot(FeatureSnapshot featureSnapshot) {
        if (featureSnapshot == null || featureSnapshot.getCompositeFactors() == null || featureSnapshot.getQuality() == null) {
            return;
        }

        log.info("Feature snapshot ready, symbol={}, asOfTime={}, trendBias={}, breakoutConfirmation={}, crowding={}, eventBias={}, regimeRisk={}, priceReady={}, derivativeReady={}, relevantEvents={}",
                featureSnapshot.getSymbol(),
                featureSnapshot.getAsOfTime(),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getTrendBiasScore()),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getBreakoutConfirmationScore()),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getCrowdingScore()),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getEventBiasScore()),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getRegimeRiskScore()),
                featureSnapshot.getQuality().isPriceReady(),
                featureSnapshot.getQuality().isDerivativeReady(),
                featureSnapshot.getQuality().getRelevantEventCount());
    }
}
