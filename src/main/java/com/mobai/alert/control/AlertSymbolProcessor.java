package com.mobai.alert.control;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.service.MarketFeatureSnapshotService;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.state.runtime.BreakoutRecord;
import com.mobai.alert.state.runtime.MarketState;
import com.mobai.alert.state.runtime.RuntimePosition;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.AlertRuleEvaluator;
import com.mobai.alert.strategy.policy.CompositeFactorSignalPolicy;
import com.mobai.alert.strategy.policy.MarketStateDecision;
import com.mobai.alert.strategy.policy.MarketStateMachine;
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
import java.math.BigDecimal;

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

    @Value("${monitoring.multi-timeframe.role:single-timeframe}")
    private String multiTimeframeRole;

    @Value("${monitoring.multi-timeframe.context-interval:}")
    private String contextInterval;

    @Value("${monitoring.multi-timeframe.context-kline-limit:0}")
    private int contextKlineLimit;

    @Value("${monitoring.strategy.breakout.record.ttl.ms:43200000}")
    private long breakoutRecordTtlMs;

    @Value("${monitoring.strategy.breakout.record.max-bars:24}")
    private int breakoutRecordMaxBars;

    @Value("${monitoring.strategy.breakout.follow-through.max-bars:2}")
    private int breakoutFollowThroughMaxBars;

    @Value("${monitoring.position.failed-follow-through.max-bars:2}")
    private int failedFollowThroughMaxBars;

    @Value("${monitoring.position.failed-follow-through.adverse-r:0.35}")
    private BigDecimal failedFollowThroughAdverseR;

    @Value("${monitoring.position.failed-follow-through.min-body-ratio:0.40}")
    private BigDecimal failedFollowThroughMinBodyRatio;

    @Value("${monitoring.position.failed-follow-through.close-location:0.35}")
    private BigDecimal failedFollowThroughCloseLocation;

    @Value("${monitoring.position.scale-out.trigger-r:1.00}")
    private BigDecimal scaleOutTriggerR;

    @Value("${monitoring.position.scale-out.fraction:0.50}")
    private BigDecimal scaleOutFraction;

    @Value("${monitoring.position.trailing.activation-r:1.20}")
    private BigDecimal trailingActivationR;

    @Value("${monitoring.position.trailing.distance-r:1.00}")
    private BigDecimal trailingDistanceR;

    @Value("${monitoring.position.pyramid.max-adds:1}")
    private int pyramidMaxAdds;

    @Value("${monitoring.position.pyramid.trigger-r:1.60}")
    private BigDecimal pyramidTriggerR;

    @Value("${monitoring.position.pyramid.add-size:0.35}")
    private BigDecimal pyramidAddFraction;

    private final BinanceApi binanceApi;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final AlertNotificationService alertNotificationService;
    private final MarketFeatureSnapshotService marketFeatureSnapshotService;
    private final CompositeFactorSignalPolicy compositeFactorSignalPolicy;
    private final MarketStateMachine marketStateMachine;
    private final Map<String, BreakoutRecord> breakoutRecords = new ConcurrentHashMap<>();
    private final Map<String, MarketState> marketStates = new ConcurrentHashMap<>();
    private final Map<String, RuntimePosition> activePositions = new ConcurrentHashMap<>();

    public AlertSymbolProcessor(BinanceApi binanceApi,
                                AlertRuleEvaluator alertRuleEvaluator,
                                AlertNotificationService alertNotificationService,
                                MarketFeatureSnapshotService marketFeatureSnapshotService,
                                CompositeFactorSignalPolicy compositeFactorSignalPolicy,
                                MarketStateMachine marketStateMachine) {
        this.binanceApi = binanceApi;
        this.alertRuleEvaluator = alertRuleEvaluator;
        this.alertNotificationService = alertNotificationService;
        this.marketFeatureSnapshotService = marketFeatureSnapshotService;
        this.compositeFactorSignalPolicy = compositeFactorSignalPolicy;
        this.marketStateMachine = marketStateMachine;
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

        if (sendExitIfPresent(symbol, klines)) {
            return;
        }

        FeatureSnapshot featureSnapshot = buildExecutionSnapshot(symbol, klines);
        logFeatureSnapshot(featureSnapshot);

        if (sendIfPresent(alertRuleEvaluator.evaluateRangeFailedBreakdownLong(klines), featureSnapshot)) {
            return;
        }
        if (sendIfPresent(alertRuleEvaluator.evaluateRangeFailedBreakoutShort(klines), featureSnapshot)) {
            return;
        }

        if (confirmPendingBreakout(klines, longBreakoutKey(symbol), shortBreakoutKey(symbol), featureSnapshot)) {
            return;
        }
        if (confirmPendingBreakout(klines, shortBreakoutKey(symbol), longBreakoutKey(symbol), featureSnapshot)) {
            return;
        }

        boolean breakoutCaptured = alertRuleEvaluator.evaluateTrendBreakout(klines)
                .map(signal -> rememberBreakoutCandidate(signal, longBreakoutKey(symbol), shortBreakoutKey(symbol), true))
                .orElse(false);
        if (breakoutCaptured) {
            return;
        }

        breakoutCaptured = alertRuleEvaluator.evaluateTrendBreakdown(klines)
                .map(signal -> rememberBreakoutCandidate(signal, shortBreakoutKey(symbol), longBreakoutKey(symbol), false))
                .orElse(false);
        if (breakoutCaptured) {
            return;
        }

        BreakoutRecord longBreakout = activeConfirmedBreakout(klines, longBreakoutKey(symbol));
        if (longBreakout != null
                && sendIfPresent(
                alertRuleEvaluator.evaluateBreakoutPullback(klines, longBreakout.breakoutLevel(), longBreakout.targetPrice(), true),
                featureSnapshot)) {
            return;
        }
        if (supportsSecondEntry(featureSnapshot)
                && sendIfPresent(
                alertRuleEvaluator.evaluateSecondEntryLong(
                        klines,
                        longBreakout == null ? null : longBreakout.breakoutLevel(),
                        longBreakout == null ? null : longBreakout.targetPrice()
                ),
                featureSnapshot)) {
            return;
        }

        BreakoutRecord shortBreakout = activeConfirmedBreakout(klines, shortBreakoutKey(symbol));
        if (shortBreakout != null
                && sendIfPresent(
                    alertRuleEvaluator.evaluateBreakoutPullback(klines, shortBreakout.breakoutLevel(), shortBreakout.targetPrice(), false),
                    featureSnapshot
            )) {
            return;
        }
        if (supportsSecondEntry(featureSnapshot)) {
            sendIfPresent(
                    alertRuleEvaluator.evaluateSecondEntryShort(
                            klines,
                            shortBreakout == null ? null : shortBreakout.breakoutLevel(),
                            shortBreakout == null ? null : shortBreakout.targetPrice()
                    ),
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
        breakoutRecords.entrySet().removeIf(entry -> currentTime - entry.getValue().breakoutTime() > breakoutRecordTtlMs);
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
        return loadRecentKlines(symbol, klineInterval, klineLimit);
    }

    private List<BinanceKlineDTO> loadRecentKlines(String symbol, String interval, int limit) {
        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol(symbol);
        request.setInterval(interval);
        request.setLimit(limit);
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

        AlertSignal rawSignal = signalOptional.get();
        SignalPolicyDecision decision = compositeFactorSignalPolicy.evaluate(rawSignal, featureSnapshot);
        if (!decision.allowed()) {
            if (tryHandleBlockedCounterTrendSignal(rawSignal, decision, featureSnapshot)) {
                return true;
            }
            log.info("Signal blocked by composite policy, symbol={}, signalType={}, direction={}, score={}, reasons={}",
                    rawSignal.getKline().getSymbol(),
                    rawSignal.getType(),
                    rawSignal.getDirection(),
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
        rememberManagedRuntimePosition(signal);
        return true;
    }

    /**
     * 对通过过滤的突破信号做发送与缓存，供后续回踩策略继续使用。
     */
    private boolean tryHandleBlockedCounterTrendSignal(AlertSignal blockedSignal,
                                                       SignalPolicyDecision decision,
                                                       FeatureSnapshot featureSnapshot) {
        if (!isHigherTimeframeConflict(decision)
                || blockedSignal == null
                || blockedSignal.getKline() == null
                || !StringUtils.hasText(blockedSignal.getKline().getSymbol())
                || blockedSignal.getDirection() == null) {
            return false;
        }

        String symbol = blockedSignal.getKline().getSymbol();
        RuntimePosition activePosition = activePositions.get(symbol);
        if (activePosition == null || activePosition.direction() == null || activePosition.direction() == blockedSignal.getDirection()) {
            return false;
        }

        String conflictReason = decision.reasons().stream()
                .filter(reason -> reason.startsWith("Block: "))
                .findFirst()
                .orElse("Block: higher-timeframe conflict");
        String contextDetail = buildConflictContext(blockedSignal, decision, featureSnapshot);
        AlertSignal managementSignal = buildConflictManagementSignal(
                symbol,
                blockedSignal,
                activePosition,
                conflictReason,
                contextDetail
        );
        if (managementSignal == null) {
            return false;
        }

        if (managementSignal.getType().startsWith("EXIT_CONFLICT_CLOSE_")) {
            activePositions.remove(symbol);
        }
        sendRuntimeManagementSignal(symbol, activePosition, managementSignal);
        return true;
    }

    private boolean isHigherTimeframeConflict(SignalPolicyDecision decision) {
        if (decision == null || decision.allowed() || decision.reasons() == null || decision.reasons().isEmpty()) {
            return false;
        }
        boolean hasHigherTimeframeContext = decision.reasons().stream().anyMatch(reason -> reason.startsWith("HigherTF("));
        boolean hasBlockingReason = decision.reasons().stream().anyMatch(reason -> reason.startsWith("Block: "));
        boolean missingContextOnly = decision.reasons().stream().anyMatch("Block: missing higher-timeframe context"::equals);
        return hasHigherTimeframeContext && hasBlockingReason && !missingContextOnly;
    }

    private AlertSignal buildConflictManagementSignal(String symbol,
                                                     AlertSignal blockedSignal,
                                                     RuntimePosition activePosition,
                                                     String conflictReason,
                                                     String contextDetail) {
        BinanceKlineDTO referenceBar = blockedSignal.getKline();
        if (activePosition.canConflictReduce()) {
            BigDecimal reducedSize = activePosition.applyConflictReduce();
            return buildConflictReduceSignal(
                    symbol,
                    referenceBar,
                    activePosition,
                    blockedSignal,
                    conflictReason,
                    contextDetail,
                    reducedSize
            );
        }
        if (activePosition.canTightenConflictStop()) {
            BigDecimal previousStop = activePosition.stopPrice();
            if (activePosition.tightenStopForConflict()) {
                return buildConflictTightenStopSignal(
                        symbol,
                        referenceBar,
                        activePosition,
                        blockedSignal,
                        conflictReason,
                        contextDetail,
                        previousStop
                );
            }
        }
        activePosition.prepareForConflictExit();
        return buildConflictCloseSignal(symbol, referenceBar, activePosition, blockedSignal, conflictReason, contextDetail);
    }

    private String buildConflictContext(AlertSignal blockedSignal,
                                        SignalPolicyDecision decision,
                                        FeatureSnapshot featureSnapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("blockedSignal=").append(defaultText(blockedSignal == null ? null : blockedSignal.getType(), "-"));
        builder.append(" | blockedDirection=").append(blockedSignal == null ? "-" : blockedSignal.getDirection());
        if (decision != null && decision.reasons() != null && !decision.reasons().isEmpty()) {
            builder.append(" | policyReasons=").append(String.join(" / ", decision.reasons()));
        }
        FeatureSnapshot contextSnapshot = featureSnapshot == null ? null : featureSnapshot.getContextSnapshot();
        if (contextSnapshot != null) {
            builder.append(" | higherTFInterval=").append(defaultText(contextSnapshot.getInterval(), "-"));
            builder.append(" | higherTFState=").append(contextSnapshot.getMarketState() == null ? "-" : contextSnapshot.getMarketState());
        }
        return builder.toString();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean rememberBreakoutCandidate(AlertSignal signal,
                                              String recordKey,
                                              String oppositeKey,
                                              boolean bullishBreakout) {
        BreakoutRecord breakoutRecord = new BreakoutRecord(
                signal.getTriggerPrice(),
                signal.getInvalidationPrice(),
                signal.getTargetPrice(),
                signal.getKline().getEndTime(),
                bullishBreakout,
                false,
                null
        );
        breakoutRecords.put(recordKey, breakoutRecord);
        breakoutRecords.remove(oppositeKey);
        log.info("Stored pending breakout memory, key={}, breakoutLevel={}, invalidation={}, target={}, bullish={}",
                recordKey,
                breakoutRecord.breakoutLevel(),
                breakoutRecord.invalidationPrice(),
                breakoutRecord.targetPrice(),
                breakoutRecord.bullish());
        return true;
    }

    private boolean confirmPendingBreakout(List<BinanceKlineDTO> klines,
                                           String recordKey,
                                           String oppositeKey,
                                           FeatureSnapshot featureSnapshot) {
        BreakoutRecord breakoutRecord = breakoutRecords.get(recordKey);
        if (breakoutRecord == null || breakoutRecord.followThroughConfirmed()) {
            return false;
        }

        int barsSinceBreakout = barsSinceBreakout(klines, breakoutRecord.breakoutTime());
        if (barsSinceBreakout <= 0) {
            return false;
        }
        if (barsSinceBreakout > breakoutFollowThroughMaxBars || isBreakoutRejected(klines, breakoutRecord)) {
            breakoutRecords.remove(recordKey);
            log.info("Dropped pending breakout memory, key={}, barsSinceBreakout={}, rejected={}",
                    recordKey,
                    barsSinceBreakout,
                    isBreakoutRejected(klines, breakoutRecord));
            return false;
        }

        Optional<AlertSignal> followThroughSignal = alertRuleEvaluator.evaluateBreakoutFollowThrough(
                klines,
                breakoutRecord.breakoutLevel(),
                breakoutRecord.invalidationPrice(),
                breakoutRecord.targetPrice(),
                breakoutRecord.bullish()
        );
        if (followThroughSignal.isEmpty()) {
            return false;
        }

        BreakoutRecord confirmedRecord = breakoutRecord.confirm(followThroughSignal.get().getKline().getEndTime());
        breakoutRecords.put(recordKey, confirmedRecord);
        breakoutRecords.remove(oppositeKey);
        log.info("Breakout follow-through confirmed, key={}, breakoutLevel={}, confirmationTime={}",
                recordKey,
                confirmedRecord.breakoutLevel(),
                confirmedRecord.followThroughTime());

        SignalPolicyDecision decision = compositeFactorSignalPolicy.evaluate(followThroughSignal.get(), featureSnapshot);
        if (!decision.allowed()) {
            log.info("Confirmed breakout blocked by composite policy, symbol={}, signalType={}, direction={}, score={}, reasons={}",
                    followThroughSignal.get().getKline().getSymbol(),
                    followThroughSignal.get().getType(),
                    followThroughSignal.get().getDirection(),
                    StrategySupport.scaleOrNull(decision.score()),
                    String.join(" | ", decision.reasons()));
            return true;
        }

        AlertSignal qualifiedSignal = decision.signal();
        log.info("Confirmed breakout approved, symbol={}, signalType={}, breakoutLevel={}, target={}, compositeScore={}",
                qualifiedSignal.getKline().getSymbol(),
                qualifiedSignal.getType(),
                qualifiedSignal.getTriggerPrice(),
                qualifiedSignal.getTargetPrice(),
                StrategySupport.scaleOrNull(qualifiedSignal.getContextScore()));
        alertNotificationService.send(qualifiedSignal);
        rememberManagedRuntimePosition(qualifiedSignal);
        return true;
    }

    private BreakoutRecord activeConfirmedBreakout(List<BinanceKlineDTO> klines, String recordKey) {
        BreakoutRecord breakoutRecord = breakoutRecords.get(recordKey);
        if (breakoutRecord == null) {
            return null;
        }
        if (!breakoutRecord.followThroughConfirmed()) {
            return null;
        }
        if (isBreakoutMemoryExpired(klines, breakoutRecord)) {
            breakoutRecords.remove(recordKey);
            return null;
        }
        return breakoutRecord;
    }

    private boolean isBreakoutMemoryExpired(List<BinanceKlineDTO> klines, BreakoutRecord breakoutRecord) {
        if (System.currentTimeMillis() - breakoutRecord.breakoutTime() > breakoutRecordTtlMs) {
            return true;
        }
        return barsSinceBreakout(klines, breakoutRecord.breakoutTime()) > breakoutRecordMaxBars;
    }

    private int barsSinceBreakout(List<BinanceKlineDTO> klines, long breakoutTime) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        int count = 0;
        for (BinanceKlineDTO kline : closedKlines) {
            if (kline.getEndTime() > breakoutTime) {
                count++;
            }
        }
        return count;
    }

    private boolean isBreakoutRejected(List<BinanceKlineDTO> klines, BreakoutRecord breakoutRecord) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        if (closedKlines.isEmpty()) {
            return false;
        }
        BigDecimal latestClose = StrategySupport.valueOf(StrategySupport.last(closedKlines).getClose());
        return breakoutRecord.bullish()
                ? latestClose.compareTo(breakoutRecord.invalidationPrice()) <= 0
                : latestClose.compareTo(breakoutRecord.invalidationPrice()) >= 0;
    }

    /**
     * 多头突破记忆键。
     */
    private String longBreakoutKey(String symbol) {
        return stateKey(symbol, klineInterval) + ":LONG";
    }

    /**
     * 空头突破记忆键。
     */
    private String shortBreakoutKey(String symbol) {
        return stateKey(symbol, klineInterval) + ":SHORT";
    }

    /**
     * 记录特征快照摘要，便于排查因子是否准备齐全。
     */
    private FeatureSnapshot buildExecutionSnapshot(String symbol, List<BinanceKlineDTO> executionKlines) {
        FeatureSnapshot executionSnapshot = marketFeatureSnapshotService.buildSnapshot(symbol, klineInterval, executionKlines);
        applyMarketState(symbol, klineInterval, executionSnapshot);

        FeatureSnapshot contextSnapshot = loadContextSnapshot(symbol);
        if (contextSnapshot != null) {
            executionSnapshot.setContextSnapshot(contextSnapshot);
        }
        marketFeatureSnapshotService.rememberLatestSnapshot(executionSnapshot);
        return executionSnapshot;
    }

    private FeatureSnapshot loadContextSnapshot(String symbol) {
        if (!isExecutionRole() || !StringUtils.hasText(contextInterval) || contextInterval.equalsIgnoreCase(klineInterval)) {
            return null;
        }

        int resolvedLimit = contextKlineLimit > 0 ? contextKlineLimit : klineLimit;
        List<BinanceKlineDTO> contextKlines = loadRecentKlines(symbol, contextInterval, resolvedLimit);
        if (CollectionUtils.isEmpty(contextKlines) || contextKlines.size() < 3) {
            log.warn("Skip higher timeframe context, symbol={}, executionInterval={}, contextInterval={}, count={}",
                    symbol,
                    klineInterval,
                    contextInterval,
                    contextKlines == null ? 0 : contextKlines.size());
            return null;
        }

        FeatureSnapshot contextSnapshot = marketFeatureSnapshotService.buildSnapshot(symbol, contextInterval, contextKlines);
        applyMarketState(symbol, contextInterval, contextSnapshot);
        return contextSnapshot;
    }

    private void applyMarketState(String symbol, String interval, FeatureSnapshot featureSnapshot) {
        if (featureSnapshot == null) {
            return;
        }

        String stateKey = stateKey(symbol, interval);
        MarketState previousState = marketStates.getOrDefault(stateKey, MarketState.UNKNOWN);
        MarketStateDecision decision = marketStateMachine.evaluate(featureSnapshot, previousState);
        featureSnapshot.setMarketState(decision.state());
        featureSnapshot.setMarketStateComment(decision.comment());
        marketStates.put(stateKey, decision.state());

        if (previousState != decision.state()) {
            log.info("Market state transitioned, symbol={}, interval={}, previousState={}, currentState={}, comment={}",
                    symbol,
                    interval,
                    previousState,
                    decision.state(),
                    decision.comment());
        }
    }

    private boolean isExecutionRole() {
        return "execution".equalsIgnoreCase(multiTimeframeRole);
    }

    private String stateKey(String symbol, String interval) {
        return symbol + "@" + interval;
    }

    private void logFeatureSnapshot(FeatureSnapshot featureSnapshot) {
        if (featureSnapshot == null || featureSnapshot.getCompositeFactors() == null || featureSnapshot.getQuality() == null) {
            return;
        }

        FeatureSnapshot contextSnapshot = featureSnapshot.getContextSnapshot();
        log.info("Feature snapshot ready, symbol={}, interval={}, asOfTime={}, marketState={}, stateComment={}, trendBias={}, breakoutConfirmation={}, crowding={}, eventBias={}, regimeRisk={}, contextInterval={}, contextState={}, contextTrendBias={}, contextBreakout={}, priceReady={}, derivativeReady={}, relevantEvents={}",
                featureSnapshot.getSymbol(),
                featureSnapshot.getInterval(),
                featureSnapshot.getAsOfTime(),
                featureSnapshot.getMarketState(),
                featureSnapshot.getMarketStateComment(),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getTrendBiasScore()),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getBreakoutConfirmationScore()),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getCrowdingScore()),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getEventBiasScore()),
                StrategySupport.scaleOrNull(featureSnapshot.getCompositeFactors().getRegimeRiskScore()),
                contextSnapshot == null ? "-" : contextSnapshot.getInterval(),
                contextSnapshot == null ? "-" : contextSnapshot.getMarketState(),
                contextSnapshot == null || contextSnapshot.getCompositeFactors() == null
                        ? "-"
                        : StrategySupport.scaleOrNull(contextSnapshot.getCompositeFactors().getTrendBiasScore()),
                contextSnapshot == null || contextSnapshot.getCompositeFactors() == null
                        ? "-"
                        : StrategySupport.scaleOrNull(contextSnapshot.getCompositeFactors().getBreakoutConfirmationScore()),
                featureSnapshot.getQuality().isPriceReady(),
                featureSnapshot.getQuality().isDerivativeReady(),
                featureSnapshot.getQuality().getRelevantEventCount());
    }

    private boolean sendExitIfPresent(String symbol, List<BinanceKlineDTO> klines) {
        RuntimePosition activePosition = activePositions.get(symbol);
        if (activePosition == null || CollectionUtils.isEmpty(klines) || klines.size() < 2) {
            return false;
        }

        AlertSignal pendingSignal = null;
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        for (BinanceKlineDTO closedBar : closedKlines) {
            if (!activePosition.shouldProcessClosedBar(closedBar.getEndTime())) {
                continue;
            }
            RuntimeManagementEvent event = evaluateClosedRuntimeBar(symbol, klines, closedBar, activePosition);
            if (event.closePosition()) {
                activePositions.remove(symbol);
                sendRuntimeManagementSignal(symbol, activePosition, event.signal());
                return true;
            }
            if (event.signal() != null) {
                pendingSignal = event.signal();
            }
        }

        RuntimeManagementEvent liveEvent = evaluateLiveRuntimeBar(symbol, klines, StrategySupport.last(klines), activePosition);
        if (liveEvent.closePosition()) {
            activePositions.remove(symbol);
            sendRuntimeManagementSignal(symbol, activePosition, liveEvent.signal());
            return true;
        }
        if (liveEvent.signal() != null) {
            pendingSignal = liveEvent.signal();
        }

        if (pendingSignal == null) {
            return false;
        }

        sendRuntimeManagementSignal(symbol, activePosition, pendingSignal);
        return true;
    }

    private boolean isFailedFollowThrough(BinanceKlineDTO latest, RuntimePosition activePosition, int barsSinceEntry) {
        BigDecimal riskPerUnit = activePosition.riskPerUnit();
        if (riskPerUnit.compareTo(BigDecimal.ZERO) <= 0
                || activePosition.direction() == null
                || barsSinceEntry <= 0
                || barsSinceEntry > failedFollowThroughMaxBars) {
            return false;
        }

        BigDecimal latestClose = StrategySupport.valueOf(latest.getClose());
        BigDecimal adverseThreshold = riskPerUnit.multiply(failedFollowThroughAdverseR);
        BigDecimal bodyRatio = StrategySupport.bodyRatio(latest);
        BigDecimal closeLocation = StrategySupport.closeLocation(latest);
        if (activePosition.direction() == TradeDirection.LONG) {
            return latestClose.compareTo(activePosition.entryPrice().subtract(adverseThreshold)) <= 0
                    && StrategySupport.isBearish(latest)
                    && bodyRatio.compareTo(failedFollowThroughMinBodyRatio) >= 0
                    && closeLocation.compareTo(failedFollowThroughCloseLocation) <= 0;
        }

        BigDecimal strongOppositeClose = BigDecimal.ONE.subtract(failedFollowThroughCloseLocation);
        return latestClose.compareTo(activePosition.entryPrice().add(adverseThreshold)) >= 0
                && StrategySupport.isBullish(latest)
                && bodyRatio.compareTo(failedFollowThroughMinBodyRatio) >= 0
                && closeLocation.compareTo(strongOppositeClose) >= 0;
    }

    private AlertSignal buildFailedFollowThroughExitSignal(String symbol,
                                                           List<BinanceKlineDTO> klines,
                                                           BinanceKlineDTO latest,
                                                           RuntimePosition activePosition,
                                                           int barsSinceEntry) {
        BigDecimal averageVolume = StrategySupport.averageVolume(
                StrategySupport.trailingWindow(
                        StrategySupport.closedKlines(klines),
                        Math.min(10, Math.max(2, StrategySupport.closedKlines(klines).size() - 1)),
                        1
                )
        );
        BigDecimal volumeRatio = StrategySupport.ratio(StrategySupport.volumeOf(latest), averageVolume);
        String directionLabel = activePosition.direction() == TradeDirection.LONG ? "做多" : "做空";
        String summary = String.format(
                "前序 %s 在入场后第 %d 根 K 线出现 failed follow-through，当前应优先结束 %s 计划。",
                activePosition.signalType(),
                barsSinceEntry,
                directionLabel
        );
        return new AlertSignal(
                activePosition.direction(),
                symbol + " failed follow-through 提前离场",
                latest,
                "EXIT_FAILED_FOLLOW_THROUGH_" + activePosition.direction().name(),
                summary,
                StrategySupport.valueOf(latest.getClose()).setScale(2, java.math.RoundingMode.HALF_UP),
                activePosition.stopPrice() == null ? null : activePosition.stopPrice().setScale(2, java.math.RoundingMode.HALF_UP),
                activePosition.targetPrice() == null ? null : activePosition.targetPrice().setScale(2, java.math.RoundingMode.HALF_UP),
                StrategySupport.scaleOrNull(volumeRatio),
                null,
                "entryType=" + activePosition.signalType()
                        + " | entryPrice=" + StrategySupport.scaleOrNull(activePosition.entryPrice())
                        + " | barsSinceEntry=" + barsSinceEntry
        );
    }

    private RuntimeManagementEvent evaluateClosedRuntimeBar(String symbol,
                                                            List<BinanceKlineDTO> klines,
                                                            BinanceKlineDTO closedBar,
                                                            RuntimePosition activePosition) {
        RuntimeManagementEvent priceEvent = evaluateRuntimeBar(symbol, klines, closedBar, activePosition);
        if (priceEvent.closePosition()) {
            return priceEvent;
        }

        int heldBars = activePosition.closedBarsSinceEntry() + 1;
        if (isFailedFollowThrough(closedBar, activePosition, heldBars)) {
            return RuntimeManagementEvent.close(
                    buildManagedFailedFollowThroughExitSignal(symbol, klines, closedBar, activePosition, heldBars)
            );
        }

        activePosition.updateAfterClosedBar(
                StrategySupport.valueOf(closedBar.getHigh()),
                StrategySupport.valueOf(closedBar.getLow()),
                StrategySupport.valueOf(closedBar.getClose()),
                closedBar.getEndTime()
        );

        if (!priceEvent.scaleOutTriggered()) {
            return RuntimeManagementEvent.none();
        }
        return RuntimeManagementEvent.alert(buildScaleOutSignal(symbol, klines, closedBar, activePosition));
    }

    private RuntimeManagementEvent evaluateLiveRuntimeBar(String symbol,
                                                          List<BinanceKlineDTO> klines,
                                                          BinanceKlineDTO liveBar,
                                                          RuntimePosition activePosition) {
        RuntimeManagementEvent priceEvent = evaluateRuntimeBar(symbol, klines, liveBar, activePosition);
        if (priceEvent.closePosition()) {
            return priceEvent;
        }
        if (!priceEvent.scaleOutTriggered()) {
            return RuntimeManagementEvent.none();
        }
        return RuntimeManagementEvent.alert(buildScaleOutSignal(symbol, klines, liveBar, activePosition));
    }

    private RuntimeManagementEvent evaluateRuntimeBar(String symbol,
                                                      List<BinanceKlineDTO> klines,
                                                      BinanceKlineDTO bar,
                                                      RuntimePosition activePosition) {
        if (bar == null || activePosition.direction() == null || activePosition.stopPrice() == null || activePosition.targetPrice() == null) {
            return RuntimeManagementEvent.none();
        }

        BigDecimal open = StrategySupport.valueOf(bar.getOpen());
        BigDecimal high = StrategySupport.valueOf(bar.getHigh());
        BigDecimal low = StrategySupport.valueOf(bar.getLow());
        boolean scaleOutTriggered = false;

        if (activePosition.direction() == TradeDirection.LONG) {
            if (open.compareTo(activePosition.stopPrice()) <= 0) {
                return RuntimeManagementEvent.close(
                        buildStopExitSignal(symbol, klines, bar, activePosition, open, "GAP_STOP")
                );
            }
            if (open.compareTo(activePosition.targetPrice()) >= 0) {
                return RuntimeManagementEvent.close(
                        buildTargetExitSignal(symbol, klines, bar, activePosition, open, "GAP_TARGET")
                );
            }

            activePosition.activatePendingAddOn(open);

            boolean hitStop = low.compareTo(activePosition.stopPrice()) <= 0;
            boolean hitTarget = high.compareTo(activePosition.targetPrice()) >= 0;
            if (hitStop && hitTarget) {
                return RuntimeManagementEvent.close(
                        buildStopExitSignal(symbol, klines, bar, activePosition, activePosition.stopPrice(), "BOTH_HIT_STOP_PRIORITY")
                );
            }
            if (hitStop) {
                return RuntimeManagementEvent.close(
                        buildStopExitSignal(symbol, klines, bar, activePosition, activePosition.stopPrice(), "STOP")
                );
            }
            if (activePosition.canScaleOut() && high.compareTo(activePosition.scaleOutPrice()) >= 0) {
                activePosition.applyScaleOut();
                scaleOutTriggered = true;
            }
            if (hitTarget) {
                return RuntimeManagementEvent.close(
                        buildTargetExitSignal(symbol, klines, bar, activePosition, activePosition.targetPrice(), "TARGET")
                );
            }
            return scaleOutTriggered ? RuntimeManagementEvent.scaleOutPending() : RuntimeManagementEvent.none();
        }

        if (open.compareTo(activePosition.stopPrice()) >= 0) {
            return RuntimeManagementEvent.close(
                    buildStopExitSignal(symbol, klines, bar, activePosition, open, "GAP_STOP")
            );
        }
        if (open.compareTo(activePosition.targetPrice()) <= 0) {
            return RuntimeManagementEvent.close(
                    buildTargetExitSignal(symbol, klines, bar, activePosition, open, "GAP_TARGET")
            );
        }

        activePosition.activatePendingAddOn(open);

        boolean hitStop = high.compareTo(activePosition.stopPrice()) >= 0;
        boolean hitTarget = low.compareTo(activePosition.targetPrice()) <= 0;
        if (hitStop && hitTarget) {
            return RuntimeManagementEvent.close(
                    buildStopExitSignal(symbol, klines, bar, activePosition, activePosition.stopPrice(), "BOTH_HIT_STOP_PRIORITY")
            );
        }
        if (hitStop) {
            return RuntimeManagementEvent.close(
                    buildStopExitSignal(symbol, klines, bar, activePosition, activePosition.stopPrice(), "STOP")
            );
        }
        if (activePosition.canScaleOut() && low.compareTo(activePosition.scaleOutPrice()) <= 0) {
            activePosition.applyScaleOut();
            scaleOutTriggered = true;
        }
        if (hitTarget) {
            return RuntimeManagementEvent.close(
                    buildTargetExitSignal(symbol, klines, bar, activePosition, activePosition.targetPrice(), "TARGET")
            );
        }
        return scaleOutTriggered ? RuntimeManagementEvent.scaleOutPending() : RuntimeManagementEvent.none();
    }

    private AlertSignal buildManagedFailedFollowThroughExitSignal(String symbol,
                                                                  List<BinanceKlineDTO> klines,
                                                                  BinanceKlineDTO latest,
                                                                  RuntimePosition activePosition,
                                                                  int barsSinceEntry) {
        String directionLabel = activePosition.direction() == TradeDirection.LONG ? "多头" : "空头";
        String summary = String.format(
                "前序 %s 在进场后第 %d 根 K 线出现 failed follow-through，当前应优先结束 %s 计划。",
                activePosition.signalType(),
                barsSinceEntry,
                directionLabel
        );
        return buildRuntimeManagementSignal(
                symbol,
                klines,
                latest,
                activePosition,
                "EXIT_FAILED_FOLLOW_THROUGH_" + activePosition.direction().name(),
                symbol + " failed follow-through 提前离场",
                summary,
                StrategySupport.valueOf(latest.getClose()),
                "FAILED_FOLLOW_THROUGH"
        );
    }

    private AlertSignal buildScaleOutSignal(String symbol,
                                            List<BinanceKlineDTO> klines,
                                            BinanceKlineDTO bar,
                                            RuntimePosition activePosition) {
        String summary = String.format(
                "前序 %s 已触及首个减仓位，建议先兑现 %s 仓位，并把剩余仓位按新的防守位继续管理。",
                activePosition.signalType(),
                formatFraction(activePosition.scaleOutFraction())
        );
        return buildRuntimeManagementSignal(
                symbol,
                klines,
                bar,
                activePosition,
                "EXIT_SCALE_OUT_" + activePosition.direction().name(),
                symbol + " 触发首个减仓位",
                summary,
                activePosition.scaleOutPrice(),
                "SCALE_OUT"
        );
    }

    private AlertSignal buildTargetExitSignal(String symbol,
                                              List<BinanceKlineDTO> klines,
                                              BinanceKlineDTO bar,
                                              RuntimePosition activePosition,
                                              BigDecimal exitPrice,
                                              String reasonTag) {
        String summary = String.format(
                "前序 %s 已触达计划目标，当前应优先完成止盈%s。",
                activePosition.signalType(),
                "GAP_TARGET".equals(reasonTag) ? "（开盘直接越过目标）" : ""
        );
        return buildRuntimeManagementSignal(
                symbol,
                klines,
                bar,
                activePosition,
                "EXIT_TARGET_" + activePosition.direction().name(),
                symbol + " 命中目标止盈",
                summary,
                exitPrice,
                reasonTag
        );
    }

    private AlertSignal buildStopExitSignal(String symbol,
                                            List<BinanceKlineDTO> klines,
                                            BinanceKlineDTO bar,
                                            RuntimePosition activePosition,
                                            BigDecimal exitPrice,
                                            String reasonTag) {
        boolean trailingStop = activePosition.isTrailingStopActive();
        String summary = trailingStop
                ? String.format("前序 %s 已回吐至 trailing stop，当前应按计划结束剩余仓位。", activePosition.signalType())
                : String.format("前序 %s 已触发止损/保护位，当前应无条件离场。", activePosition.signalType());
        return buildRuntimeManagementSignal(
                symbol,
                klines,
                bar,
                activePosition,
                (trailingStop ? "EXIT_TRAILING_STOP_" : "EXIT_STOP_") + activePosition.direction().name(),
                trailingStop ? symbol + " 触发 trailing stop" : symbol + " 触发止损离场",
                summary,
                exitPrice,
                reasonTag
        );
    }

    private AlertSignal buildConflictReduceSignal(String symbol,
                                                  BinanceKlineDTO bar,
                                                  RuntimePosition activePosition,
                                                  AlertSignal blockedSignal,
                                                  String conflictReason,
                                                  String contextDetail,
                                                  BigDecimal reducedSize) {
        String summary = String.format(
                "前序 %s 持仓期间，出现与大级别方向相悖的 %s，当前更适合先减仓 %s，保留主趋势仓位观察，而不是直接反手。",
                activePosition.signalType(),
                blockedSignal.getType(),
                formatFraction(reducedSize)
        );
        return buildRuntimeManagementSignal(
                symbol,
                List.of(bar),
                bar,
                activePosition,
                "EXIT_CONFLICT_REDUCE_" + activePosition.direction().name(),
                symbol + " 高周期冲突，先减仓观察",
                summary,
                StrategySupport.valueOf(bar.getClose()),
                "CONFLICT_REDUCE",
                contextDetail + " | conflictReason=" + conflictReason
        );
    }

    private AlertSignal buildConflictTightenStopSignal(String symbol,
                                                       BinanceKlineDTO bar,
                                                       RuntimePosition activePosition,
                                                       AlertSignal blockedSignal,
                                                       String conflictReason,
                                                       String contextDetail,
                                                       BigDecimal previousStop) {
        String summary = String.format(
                "前序 %s 持仓期间，出现与大级别方向相悖的 %s，当前更适合把防守位从 %s 收紧到 %s，先保护仓位，再等市场给出更清晰的答案。",
                activePosition.signalType(),
                blockedSignal.getType(),
                StrategySupport.scaleOrNull(previousStop),
                StrategySupport.scaleOrNull(activePosition.stopPrice())
        );
        return buildRuntimeManagementSignal(
                symbol,
                List.of(bar),
                bar,
                activePosition,
                "EXIT_CONFLICT_TIGHTEN_STOP_" + activePosition.direction().name(),
                symbol + " 高周期冲突，收紧防守",
                summary,
                activePosition.stopPrice(),
                "CONFLICT_TIGHTEN_STOP",
                contextDetail + " | conflictReason=" + conflictReason
        );
    }

    private AlertSignal buildConflictCloseSignal(String symbol,
                                                 BinanceKlineDTO bar,
                                                 RuntimePosition activePosition,
                                                 AlertSignal blockedSignal,
                                                 String conflictReason,
                                                 String contextDetail) {
        String summary = String.format(
                "前序 %s 持仓期间，高低周期冲突继续扩大，并出现 %s，当前更适合先结束剩余仓位，等待重新同向的入场机会。",
                activePosition.signalType(),
                blockedSignal.getType()
        );
        return buildRuntimeManagementSignal(
                symbol,
                List.of(bar),
                bar,
                activePosition,
                "EXIT_CONFLICT_CLOSE_" + activePosition.direction().name(),
                symbol + " 高周期冲突，先退出等待",
                summary,
                StrategySupport.valueOf(bar.getClose()),
                "CONFLICT_CLOSE",
                contextDetail + " | conflictReason=" + conflictReason
        );
    }


    private AlertSignal buildRuntimeManagementSignal(String symbol,
                                                     List<BinanceKlineDTO> klines,
                                                     BinanceKlineDTO bar,
                                                     RuntimePosition activePosition,
                                                     String type,
                                                     String title,
                                                     String summary,
                                                     BigDecimal referencePrice,
                                                     String reasonTag) {
        return buildRuntimeManagementSignal(
                symbol,
                klines,
                bar,
                activePosition,
                type,
                title,
                summary,
                referencePrice,
                reasonTag,
                null
        );
    }

    private AlertSignal buildRuntimeManagementSignal(String symbol,
                                                     List<BinanceKlineDTO> klines,
                                                     BinanceKlineDTO bar,
                                                     RuntimePosition activePosition,
                                                     String type,
                                                     String title,
                                                     String summary,
                                                     BigDecimal referencePrice,
                                                     String reasonTag,
                                                     String extraContext) {
        return new AlertSignal(
                activePosition.direction(),
                title,
                bar,
                type,
                summary,
                scalePrice(referencePrice),
                scalePrice(activePosition.stopPrice()),
                scalePrice(activePosition.targetPrice()),
                buildVolumeRatio(klines, bar),
                null,
                buildPositionContext(activePosition, reasonTag, extraContext),
                scalePrice(activePosition.entryPrice()),
                scalePrice(activePosition.initialStopPrice())
        );
    }

    private BigDecimal buildVolumeRatio(List<BinanceKlineDTO> klines, BinanceKlineDTO referenceBar) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        if (closedKlines.isEmpty()) {
            return null;
        }

        List<BinanceKlineDTO> baseline;
        int referenceIndex = closedKlines.indexOf(referenceBar);
        if (referenceIndex > 0) {
            int start = Math.max(0, referenceIndex - 10);
            baseline = closedKlines.subList(start, referenceIndex);
        } else {
            int start = Math.max(0, closedKlines.size() - Math.min(10, closedKlines.size()));
            baseline = closedKlines.subList(start, closedKlines.size());
        }
        if (baseline.isEmpty()) {
            return null;
        }
        return StrategySupport.scaleOrNull(
                StrategySupport.ratio(StrategySupport.volumeOf(referenceBar), StrategySupport.averageVolume(baseline))
        );
    }

    private String buildPositionContext(RuntimePosition activePosition, String reasonTag, String extraContext) {
        String context = "entryType=" + activePosition.signalType()
                + " | entryPrice=" + StrategySupport.scaleOrNull(activePosition.entryPrice())
                + " | initialStop=" + StrategySupport.scaleOrNull(activePosition.initialStopPrice())
                + " | activeStop=" + StrategySupport.scaleOrNull(activePosition.stopPrice())
                + " | remainingSize=" + StrategySupport.scaleOrNull(activePosition.remainingSize())
                + " | scaleOut=" + activePosition.scaleOutTaken()
                + " | conflictReduced=" + activePosition.conflictReduced()
                + " | conflictStopTightened=" + activePosition.conflictStopTightened()
                + " | addOns=" + activePosition.addOnsUsed()
                + " | pendingAddOn=" + activePosition.pendingAddOn()
                + " | addOnsLocked=" + activePosition.addOnsLocked()
                + " | reason=" + reasonTag;
        if (!StringUtils.hasText(extraContext)) {
            return context;
        }
        return context + " | " + extraContext;
    }

    private BigDecimal scalePrice(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String formatFraction(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.multiply(new BigDecimal("100")).setScale(0, java.math.RoundingMode.HALF_UP) + "%";
    }

    private void sendRuntimeManagementSignal(String symbol, RuntimePosition activePosition, AlertSignal signal) {
        if (signal == null) {
            return;
        }
        alertNotificationService.send(signal);
        log.info("Runtime management alert sent, symbol={}, originalSignalType={}, alertType={}, stop={}, target={}, remainingSize={}",
                symbol,
                activePosition.signalType(),
                signal.getType(),
                activePosition.stopPrice(),
                activePosition.targetPrice(),
                activePosition.remainingSize());
    }

    private void rememberManagedRuntimePosition(AlertSignal signal) {
        if (signal == null || signal.getKline() == null || signal.getKline().getSymbol() == null || !tracksRuntimePosition(signal)) {
            return;
        }
        activePositions.put(signal.getKline().getSymbol(), new RuntimePosition(
                signal.getType(),
                signal.getDirection(),
                signal.getKline().getEndTime(),
                signal.getTriggerPrice(),
                signal.getInvalidationPrice(),
                signal.getTargetPrice(),
                scaleOutTriggerR,
                scaleOutFraction,
                trailingActivationR,
                trailingDistanceR,
                pyramidMaxAdds,
                pyramidTriggerR,
                pyramidAddFraction
        ));
    }

    private boolean tracksRuntimePosition(AlertSignal signal) {
        return StringUtils.hasText(signal.getType())
                && (signal.getType().startsWith("CONFIRMED_BREAKOUT")
                || signal.getType().startsWith("BREAKOUT_PULLBACK")
                || signal.getType().startsWith("SECOND_ENTRY"));
    }

    private record RuntimeManagementEvent(AlertSignal signal, boolean closePosition, boolean scaleOutTriggered) {
        private static RuntimeManagementEvent none() {
            return new RuntimeManagementEvent(null, false, false);
        }

        private static RuntimeManagementEvent alert(AlertSignal signal) {
            return new RuntimeManagementEvent(signal, false, false);
        }

        private static RuntimeManagementEvent close(AlertSignal signal) {
            return new RuntimeManagementEvent(signal, true, false);
        }

        private static RuntimeManagementEvent scaleOutPending() {
            return new RuntimeManagementEvent(null, false, true);
        }
    }

    private void rememberRuntimePosition(AlertSignal signal) {
        if (signal == null || signal.getKline() == null || signal.getKline().getSymbol() == null || !tracksFailedFollowThrough(signal)) {
            return;
        }
        activePositions.put(signal.getKline().getSymbol(), new RuntimePosition(
                signal.getType(),
                signal.getDirection(),
                signal.getKline().getEndTime(),
                signal.getTriggerPrice(),
                signal.getInvalidationPrice(),
                signal.getTargetPrice()
        ));
    }

    private boolean tracksFailedFollowThrough(AlertSignal signal) {
        return StringUtils.hasText(signal.getType())
                && (signal.getType().startsWith("CONFIRMED_BREAKOUT")
                || signal.getType().startsWith("BREAKOUT_PULLBACK")
                || signal.getType().startsWith("SECOND_ENTRY"));
    }

    private boolean supportsSecondEntry(FeatureSnapshot featureSnapshot) {
        if (featureSnapshot == null || featureSnapshot.getMarketState() == null) {
            return true;
        }
        return switch (featureSnapshot.getMarketState()) {
            case UNKNOWN, BREAKOUT, PULLBACK, TREND -> true;
            case RANGE -> false;
        };
    }
}
