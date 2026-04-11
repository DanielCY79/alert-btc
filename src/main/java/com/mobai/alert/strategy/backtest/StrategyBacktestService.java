package com.mobai.alert.strategy.backtest;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.service.BacktestFeatureSnapshotService;
import com.mobai.alert.state.backtest.BacktestConfig;
import com.mobai.alert.state.backtest.BacktestReport;
import com.mobai.alert.state.backtest.BatchBacktestResult;
import com.mobai.alert.state.backtest.BreakoutMemory;
import com.mobai.alert.state.backtest.SensitivityResult;
import com.mobai.alert.state.backtest.TradeRecord;
import com.mobai.alert.state.runtime.MarketState;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.AlertRuleEvaluator;
import com.mobai.alert.strategy.policy.CompositeFactorPolicyProfile;
import com.mobai.alert.strategy.policy.CompositeFactorSignalPolicy;
import com.mobai.alert.strategy.policy.MarketStateDecision;
import com.mobai.alert.strategy.policy.MarketStateMachine;
import com.mobai.alert.strategy.policy.PolicyWeights;
import com.mobai.alert.strategy.policy.SignalPolicyDecision;
import com.mobai.alert.strategy.shared.StrategySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 策略回测服务。
 * 负责拉取历史数据、驱动信号检测、模拟持仓管理，并输出基线和敏感性测试结果。
 */
@Service
public class StrategyBacktestService {
    private static final Logger log = LoggerFactory.getLogger(StrategyBacktestService.class);

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final long THREE_MINUTES_MS = 3L * 60L * 1000L;
    private static final long FIFTEEN_MINUTES_MS = 15L * 60L * 1000L;
    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    private static final long FOUR_HOURS_MS = 4L * 60L * 60L * 1000L;
    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int PAGE_LIMIT = 1000;

    private final BinanceApi binanceApi;

    @Value("${backtest.symbol:BTCUSDT}")
    private String backtestSymbol;

    @Value("${backtest.interval:4h}")
    private String backtestInterval;

    @Value("${backtest.start:2020-01-01T00:00:00Z}")
    private String backtestStart;

    @Value("${backtest.end:}")
    private String backtestEnd;

    @Value("${monitoring.strategy.trend.fast-period:20}")
    private int fastPeriod;

    @Value("${monitoring.strategy.trend.slow-period:60}")
    private int slowPeriod;

    @Value("${monitoring.strategy.range.lookback:36}")
    private int rangeLookback;

    @Value("${monitoring.strategy.range.min-width:0.03}")
    private BigDecimal rangeMinWidth;

    @Value("${monitoring.strategy.range.max-width:0.18}")
    private BigDecimal rangeMaxWidth;

    @Value("${monitoring.strategy.range.edge-tolerance:0.015}")
    private BigDecimal rangeEdgeTolerance;

    @Value("${monitoring.strategy.range.required-edge-touches:2}")
    private int requiredEdgeTouches;

    @Value("${monitoring.strategy.range.overlap-threshold:0.45}")
    private BigDecimal overlapThreshold;

    @Value("${monitoring.strategy.range.min-overlap-bars:12}")
    private int minOverlapBars;

    @Value("${monitoring.strategy.range.ma-flat-threshold:0.012}")
    private BigDecimal maFlatThreshold;

    @Value("${monitoring.strategy.breakout.close-buffer:0.003}")
    private BigDecimal breakoutCloseBuffer;

    @Value("${monitoring.strategy.breakout.volume-multiplier:1.5}")
    private BigDecimal breakoutVolumeMultiplier;

    @Value("${monitoring.strategy.breakout.body-ratio-threshold:0.45}")
    private BigDecimal breakoutBodyRatioThreshold;

    @Value("${monitoring.strategy.breakout.max-extension:0.05}")
    private BigDecimal breakoutMaxExtension;

    @Value("${monitoring.strategy.breakout.failure-buffer:0.008}")
    private BigDecimal breakoutFailureBuffer;

    @Value("${monitoring.strategy.failure.probe-buffer:0.003}")
    private BigDecimal failureProbeBuffer;

    @Value("${monitoring.strategy.failure.reentry-buffer:0.001}")
    private BigDecimal failureReentryBuffer;

    @Value("${monitoring.strategy.failure.min-wick-body-ratio:1.20}")
    private BigDecimal failureMinWickBodyRatio;

    @Value("${monitoring.strategy.pullback.touch-tolerance:0.008}")
    private BigDecimal pullbackTouchTolerance;

    @Value("${monitoring.strategy.pullback.hold-buffer:0.006}")
    private BigDecimal pullbackHoldBuffer;

    @Value("${monitoring.strategy.pullback.max-volume-ratio:1.10}")
    private BigDecimal pullbackMaxVolumeRatio;

    @Value("${monitoring.strategy.breakout.follow-through.close-buffer:0.001}")
    private BigDecimal breakoutFollowThroughCloseBuffer;

    @Value("${monitoring.strategy.breakout.follow-through.min-body-ratio:0.25}")
    private BigDecimal breakoutFollowThroughMinBodyRatio;

    @Value("${monitoring.strategy.breakout.follow-through.min-close-location:0.55}")
    private BigDecimal breakoutFollowThroughMinCloseLocation;

    @Value("${monitoring.strategy.breakout.follow-through.min-volume-ratio:0.80}")
    private BigDecimal breakoutFollowThroughMinVolumeRatio;

    @Value("${monitoring.strategy.second-entry.lookback:12}")
    private int secondEntryLookback = 12;

    @Value("${monitoring.strategy.second-entry.min-pullback-bars:2}")
    private int secondEntryMinPullbackBars = 2;

    @Value("${monitoring.strategy.second-entry.min-body-ratio:0.20}")
    private BigDecimal secondEntryMinBodyRatio = new BigDecimal("0.20");

    @Value("${monitoring.strategy.second-entry.min-close-location:0.55}")
    private BigDecimal secondEntryMinCloseLocation = new BigDecimal("0.55");

    @Value("${monitoring.strategy.second-entry.invalidation-buffer:0.001}")
    private BigDecimal secondEntryInvalidationBuffer = new BigDecimal("0.001");

    @Value("${monitoring.strategy.breakout.record.ttl.ms:43200000}")
    private long breakoutRecordTtlMs;

    @Value("${monitoring.strategy.breakout.record.max-bars:24}")
    private int breakoutRecordMaxBars;

    @Value("${monitoring.strategy.breakout.follow-through.max-bars:2}")
    private int breakoutFollowThroughMaxBars;

    @Value("${backtest.holding-bars.range:12}")
    private int rangeHoldingBars;

    @Value("${backtest.holding-bars.breakout:18}")
    private int breakoutHoldingBars;

    @Value("${backtest.holding-bars.pullback:18}")
    private int pullbackHoldingBars;

    @Value("${backtest.fallback-target-r.multiple:1.50}")
    private BigDecimal fallbackTargetMultiple;

    @Value("${backtest.position.scale-out.trigger-r:1.00}")
    private BigDecimal scaleOutTriggerR;

    @Value("${backtest.position.scale-out.fraction:0.50}")
    private BigDecimal scaleOutFraction;

    @Value("${backtest.position.trailing.activation-r:1.20}")
    private BigDecimal trailingActivationR;

    @Value("${backtest.position.trailing.distance-r:1.00}")
    private BigDecimal trailingDistanceR;

    @Value("${backtest.position.pyramid.max-adds:1}")
    private int pyramidMaxAdds;

    @Value("${backtest.position.pyramid.trigger-r:1.60}")
    private BigDecimal pyramidTriggerR;

    @Value("${backtest.position.pyramid.add-size:0.35}")
    private BigDecimal pyramidAddFraction;

    @Value("${backtest.position.failed-follow-through.max-bars:2}")
    private int failedFollowThroughMaxBars = 2;

    @Value("${backtest.position.failed-follow-through.adverse-r:0.35}")
    private BigDecimal failedFollowThroughAdverseR = new BigDecimal("0.35");

    @Value("${backtest.position.failed-follow-through.min-body-ratio:0.40}")
    private BigDecimal failedFollowThroughMinBodyRatio = new BigDecimal("0.40");

    @Value("${backtest.position.failed-follow-through.close-location:0.35}")
    private BigDecimal failedFollowThroughCloseLocation = new BigDecimal("0.35");

    @Value("${backtest.policy.missing-derivative-penalty:0.00}")
    private BigDecimal backtestMissingDerivativePenalty;

    @Value("${backtest.multi-timeframe.role:${monitoring.multi-timeframe.role:single-timeframe}}")
    private String backtestMultiTimeframeRole = "single-timeframe";

    @Value("${backtest.multi-timeframe.context-interval:${monitoring.multi-timeframe.context-interval:}}")
    private String backtestContextInterval;

    private final BacktestFeatureSnapshotService backtestFeatureSnapshotService;
    private final CompositeFactorSignalPolicy compositeFactorSignalPolicy;
    private final MarketStateMachine marketStateMachine;

    public StrategyBacktestService(BinanceApi binanceApi,
                                   BacktestFeatureSnapshotService backtestFeatureSnapshotService,
                                   CompositeFactorSignalPolicy compositeFactorSignalPolicy,
                                   MarketStateMachine marketStateMachine) {
        this.binanceApi = binanceApi;
        this.backtestFeatureSnapshotService = backtestFeatureSnapshotService;
        this.compositeFactorSignalPolicy = compositeFactorSignalPolicy;
        this.marketStateMachine = marketStateMachine;
    }

    /**
     * 执行默认回测批次，包括基线回测和若干敏感性测试。
     */
    public BatchBacktestResult runDefaultBacktestBatch() {
        BacktestConfig baseline = baselineConfig();
        log.info("开始准备默认回测批次，symbol={}，interval={}，start={}，end={}",
                baseline.symbol(),
                baseline.interval(),
                Instant.ofEpochMilli(baseline.startTime()),
                Instant.ofEpochMilli(baseline.endTime()));
        List<BinanceKlineDTO> history = loadHistoricalKlines(
                baseline.symbol(),
                baseline.interval(),
                baseline.startTime(),
                baseline.endTime()
        );
        log.info("历史K线加载完成，共 {} 根，开始执行基线回测与敏感性测试", history.size());

        BacktestReport baselineReport = runBacktest(history, baseline, false);
        BacktestReport policyFilteredBaseline = runBacktest(history, baseline, true);
        BigDecimal baselineTotalR = policyFilteredBaseline.totalR();
        List<SensitivityResult> variants = new ArrayList<>();

        variants.add(runSensitivity(history, baselineTotalR, "rangeLookback=28", baseline.withRangeLookback(28)));
        variants.add(runSensitivity(history, baselineTotalR, "rangeLookback=48", baseline.withRangeLookback(48)));
        variants.add(runSensitivity(history, baselineTotalR, "breakoutVolumeMultiplier=1.30", baseline.withBreakoutVolumeMultiplier(new BigDecimal("1.30"))));
        variants.add(runSensitivity(history, baselineTotalR, "breakoutVolumeMultiplier=1.80", baseline.withBreakoutVolumeMultiplier(new BigDecimal("1.80"))));
        variants.add(runSensitivity(history, baselineTotalR, "breakoutCloseBuffer=0.002", baseline.withBreakoutCloseBuffer(new BigDecimal("0.002"))));
        variants.add(runSensitivity(history, baselineTotalR, "breakoutCloseBuffer=0.004", baseline.withBreakoutCloseBuffer(new BigDecimal("0.004"))));
        variants.add(runSensitivity(history, baselineTotalR, "policy.baseScore=0.45", baseline.withPolicyBaseScore(new BigDecimal("0.45"))));
        variants.add(runSensitivity(history, baselineTotalR, "policy.baseScore=0.55", baseline.withPolicyBaseScore(new BigDecimal("0.55"))));
        variants.add(runSensitivity(history, baselineTotalR, "policy.rangeFailureMinScore=0.46", baseline.withPolicyRangeFailureMinScore(new BigDecimal("0.46"))));
        variants.add(runSensitivity(history, baselineTotalR, "policy.breakoutMinScore=0.50", baseline.withPolicyBreakoutMinScore(new BigDecimal("0.50"))));
        variants.add(runSensitivity(history, baselineTotalR, "policy.breakoutMinScore=0.60", baseline.withPolicyBreakoutMinScore(new BigDecimal("0.60"))));
        variants.add(runSensitivity(history, baselineTotalR, "policy.pullbackMinScore=0.58", baseline.withPolicyPullbackMinScore(new BigDecimal("0.58"))));
        variants.add(runSensitivity(history, baselineTotalR, "policy.maxRegimeRisk=0.80", baseline.withPolicyMaxRegimeRisk(new BigDecimal("0.80"))));
        variants.add(runSensitivity(history, baselineTotalR, "policy.crowdingExtreme=0.70", baseline.withPolicyCrowdingExtreme(new BigDecimal("0.70"))));
        PolicyWeights aggressiveBreakoutWeights = new PolicyWeights(
                baseline.policyProfile().breakoutWeights().trendWeight(),
                new BigDecimal("0.32"),
                baseline.policyProfile().breakoutWeights().eventWeight(),
                baseline.policyProfile().breakoutWeights().crowdingWeight(),
                baseline.policyProfile().breakoutWeights().regimeRiskWeight()
        );
        variants.add(runSensitivity(history, baselineTotalR, "policy.breakout.breakoutWeight=0.32", baseline.withPolicyBreakoutWeights(aggressiveBreakoutWeights)));
        PolicyWeights trendLedBreakoutWeights = new PolicyWeights(
                new BigDecimal("0.24"),
                baseline.policyProfile().breakoutWeights().breakoutWeight(),
                baseline.policyProfile().breakoutWeights().eventWeight(),
                baseline.policyProfile().breakoutWeights().crowdingWeight(),
                baseline.policyProfile().breakoutWeights().regimeRiskWeight()
        );
        variants.add(runSensitivity(history, baselineTotalR, "policy.breakout.trendWeight=0.24", baseline.withPolicyBreakoutWeights(trendLedBreakoutWeights)));
        PolicyWeights defensivePullbackWeights = new PolicyWeights(
                baseline.policyProfile().pullbackWeights().trendWeight(),
                baseline.policyProfile().pullbackWeights().breakoutWeight(),
                baseline.policyProfile().pullbackWeights().eventWeight(),
                baseline.policyProfile().pullbackWeights().crowdingWeight(),
                new BigDecimal("0.24")
        );
        variants.add(runSensitivity(history, baselineTotalR, "policy.pullback.regimeRiskWeight=0.24", baseline.withPolicyPullbackWeights(defensivePullbackWeights)));

        log.info("默认回测批次执行完成，基线 trades={}，winRate={}，totalR={}，profitFactor={}",
                baselineReport.tradeCount(),
                percent(baselineReport.winRate()),
                scale(baselineReport.totalR()),
                scale(baselineReport.profitFactor()));
        log.info("Backtest batch finished, rawTrades={}, rawTotalR={}, policyTrades={}, policyTotalR={}, blockedByPolicy={}",
                baselineReport.tradeCount(),
                scale(baselineReport.totalR()),
                policyFilteredBaseline.tradeCount(),
                scale(policyFilteredBaseline.totalR()),
                policyFilteredBaseline.blockedSignalCount());
        return new BatchBacktestResult(history.size(), baselineReport, policyFilteredBaseline, variants);
    }

    /**
     * 执行不带复合因子过滤的单次回测。
     */
    public BacktestReport runBacktest(List<BinanceKlineDTO> history, BacktestConfig config) {
        return runBacktest(history, config, false);
    }

    /**
     * 执行单次回测，可选择是否启用复合因子过滤。
     */
    public BacktestReport runBacktest(List<BinanceKlineDTO> history, BacktestConfig config, boolean applyCompositePolicy) {
        log.info("开始执行单组回测，symbol={}，interval={}，bars={}，rangeLookback={}，breakoutCloseBuffer={}，breakoutVolumeMultiplier={}",
                config.symbol(),
                config.interval(),
                history.size(),
                config.rangeLookback(),
                config.breakoutCloseBuffer(),
                config.breakoutVolumeMultiplier());
        AlertRuleEvaluator evaluator = buildEvaluator(config);
        Map<String, BreakoutMemory> breakoutMemories = new HashMap<>();
        List<TradeRecord> trades = new ArrayList<>();
        Map<String, Integer> signalCounts = new LinkedHashMap<>();
        SignalAudit audit = new SignalAudit();
        MarketState currentMarketState = MarketState.UNKNOWN;
        HigherTimeframeBacktestContext higherTimeframeContext = prepareHigherTimeframeBacktestContext(config);

        ManagedPosition activePosition = null;
        for (int barIndex = 1; barIndex < history.size(); barIndex++) {
            BinanceKlineDTO currentBar = history.get(barIndex);
            List<BinanceKlineDTO> visibleKlines = history.subList(0, barIndex + 1);
            cleanupExpiredBreakoutMemories(breakoutMemories, visibleKlines, config);
            FeatureSnapshot featureSnapshot = backtestFeatureSnapshotService == null
                    ? null
                    : backtestFeatureSnapshotService.buildSnapshot(config.symbol(), config.interval(), visibleKlines);
            if (featureSnapshot != null) {
                currentMarketState = applyBacktestMarketState(featureSnapshot, currentMarketState);
                FeatureSnapshot contextSnapshot = higherTimeframeContext.snapshotFor(
                        currentBar.getEndTime(),
                        backtestFeatureSnapshotService,
                        marketStateMachine
                );
                if (contextSnapshot != null) {
                    featureSnapshot.setContextSnapshot(contextSnapshot);
                }
            }

            if (activePosition == null) {
                Optional<AlertSignal> signalOptional = detectSignal(
                        visibleKlines,
                        evaluator,
                        breakoutMemories,
                        featureSnapshot,
                        applyCompositePolicy,
                        config.policyProfile(),
                        audit
                );
                if (signalOptional.isPresent()) {
                    AlertSignal signal = signalOptional.get();
                    signalCounts.merge(signal.getType(), 1, Integer::sum);
                    activePosition = openPosition(signal, currentBar, barIndex, config);
                }
            }

            if (activePosition != null) {
                activePosition = evaluatePositionOnBar(activePosition, currentBar, barIndex, trades, config);
            }
        }

        if (activePosition != null) {
            BinanceKlineDTO lastBar = history.get(history.size() - 1);
            trades.add(activePosition.forceClose(lastBar.getEndTime(), decimal(lastBar.getClose()), "FORCED_END"));
        }

        BacktestReport report = summarize(history, trades, signalCounts, audit, applyCompositePolicy, config);
        log.info("单组回测完成，symbol={}，trades={}，winRate={}，avgR={}，totalR={}，maxDD={}，signalMix={}",
                config.symbol(),
                report.tradeCount(),
                percent(report.winRate()),
                scale(report.averageR()),
                scale(report.totalR()),
                scale(report.maxDrawdownR()),
                report.signalCounts(),
                report.blockedSignalCount(),
                report.compositePolicyApplied());
        log.info("Backtest run summary, symbol={}, trades={}, blockedSignals={}, compositePolicy={}",
                config.symbol(),
                report.tradeCount(),
                report.blockedSignalCount(),
                report.compositePolicyApplied());
        return report;
    }

    private MarketState applyBacktestMarketState(FeatureSnapshot snapshot, MarketState previousState) {
        MarketStateDecision stateDecision = marketStateMachine.evaluate(snapshot, previousState);
        snapshot.setMarketState(stateDecision.state());
        snapshot.setMarketStateComment(stateDecision.comment());
        return stateDecision.state();
    }

    /**
     * 把一批回测结果格式化成便于阅读的文本摘要。
     */
    public String formatBatchResult(BatchBacktestResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("Backtest dataset bars: ").append(result.barCount()).append("\n");
        builder.append(formatReport("Raw Baseline", result.baseline())).append("\n");
        builder.append(formatReport("Policy Baseline", result.policyFilteredBaseline())).append("\n");
        builder.append(formatComparison(result.baseline(), result.policyFilteredBaseline())).append("\n");
        builder.append("Policy Sensitivity\n");
        for (SensitivityResult variant : result.variants()) {
            builder.append(formatSensitivity(variant)).append("\n");
        }
        return builder.toString();
    }

    /**
     * 格式化单份回测报告。
     */
    private String formatReport(String label, BacktestReport report) {
        return label + " | " +
                report.config().symbol() + " " + report.config().interval() + " " +
                Instant.ofEpochMilli(report.config().startTime()) + " -> " + Instant.ofEpochMilli(report.config().endTime()) +
                " | trades=" + report.tradeCount() +
                " winRate=" + percent(report.winRate()) +
                " avgR=" + scale(report.averageR()) +
                " totalR=" + scale(report.totalR()) +
                " profitFactor=" + scale(report.profitFactor()) +
                " maxDD=" + scale(report.maxDrawdownR()) +
                " rawSignals=" + report.rawSignalCount() +
                " blockedSignals=" + report.blockedSignalCount() +
                " policy=" + report.compositePolicyApplied() +
                " signalMix=" + report.signalCounts() +
                " blockedMix=" + report.blockedSignalCounts();
    }

    /**
     * 格式化原始基线与过滤基线之间的差异。
     */
    private String formatComparison(BacktestReport rawReport, BacktestReport policyReport) {
        return "Comparison | tradesDelta=" + signed(BigDecimal.valueOf(policyReport.tradeCount() - rawReport.tradeCount())) +
                " totalRDelta=" + signed(policyReport.totalR().subtract(rawReport.totalR())) +
                " winRateDelta=" + signed(policyReport.winRate().subtract(rawReport.winRate()).multiply(ONE_HUNDRED)) + "%" +
                " avgRDelta=" + signed(policyReport.averageR().subtract(rawReport.averageR())) +
                " maxDDDelta=" + signed(policyReport.maxDrawdownR().subtract(rawReport.maxDrawdownR())) +
                " blockedByPolicy=" + policyReport.blockedSignalCount();
    }

    /**
     * 格式化单个敏感性测试结果。
     */
    private String formatSensitivity(SensitivityResult result) {
        BacktestReport report = result.report();
        BigDecimal delta = report.totalR().subtract(result.baselineTotalR());
        return " - " + result.label() +
                " | trades=" + report.tradeCount() +
                " winRate=" + percent(report.winRate()) +
                " avgR=" + scale(report.averageR()) +
                " totalR=" + scale(report.totalR()) +
                " deltaR=" + signed(delta) +
                " blocked=" + report.blockedSignalCount() +
                " profitFactor=" + scale(report.profitFactor());
    }

    /**
     * 执行一组敏感性测试变体。
     */
    private SensitivityResult runSensitivity(List<BinanceKlineDTO> history, BigDecimal baselineTotalR, String label, BacktestConfig variant) {
        log.info("开始执行敏感性测试：{}", label);
        SensitivityResult result = new SensitivityResult(label, runBacktest(history, variant, true), baselineTotalR);
        log.info("敏感性测试完成：{}，totalR={}，deltaR={}",
                label,
                scale(result.report().totalR()),
                signed(result.report().totalR().subtract(baselineTotalR)));
        return result;
    }

    /**
     * 按策略顺序检测信号，并维护突破记忆。
     */
    private Optional<AlertSignal> detectSignal(List<BinanceKlineDTO> klines,
                                               AlertRuleEvaluator evaluator,
                                               Map<String, BreakoutMemory> breakoutMemories,
                                               FeatureSnapshot featureSnapshot,
                                               boolean applyCompositePolicy,
                                               CompositeFactorPolicyProfile policyProfile,
                                               SignalAudit audit) {
        Optional<AlertSignal> signal = qualifySignal(
                evaluator.evaluateRangeFailedBreakdownLong(klines),
                featureSnapshot,
                applyCompositePolicy,
                policyProfile,
                audit
        );
        if (signal.isPresent()) {
            return signal;
        }

        signal = qualifySignal(
                evaluator.evaluateRangeFailedBreakoutShort(klines),
                featureSnapshot,
                applyCompositePolicy,
                policyProfile,
                audit
        );
        if (signal.isPresent()) {
            return signal;
        }

        BreakoutConfirmationResult confirmation = confirmBreakoutMemory(
                "LONG",
                "SHORT",
                klines,
                evaluator,
                breakoutMemories,
                featureSnapshot,
                applyCompositePolicy,
                policyProfile,
                audit
        );
        if (confirmation.confirmed()) {
            return confirmation.signal();
        }

        confirmation = confirmBreakoutMemory(
                "SHORT",
                "LONG",
                klines,
                evaluator,
                breakoutMemories,
                featureSnapshot,
                applyCompositePolicy,
                policyProfile,
                audit
        );
        if (confirmation.confirmed()) {
            return confirmation.signal();
        }

        Optional<AlertSignal> breakoutCandidate = evaluator.evaluateTrendBreakout(klines);
        if (breakoutCandidate.isPresent()) {
            rememberBreakoutCandidate(breakoutMemories, "LONG", "SHORT", breakoutCandidate.get(), true);
            return Optional.empty();
        }

        breakoutCandidate = evaluator.evaluateTrendBreakdown(klines);
        if (breakoutCandidate.isPresent()) {
            rememberBreakoutCandidate(breakoutMemories, "SHORT", "LONG", breakoutCandidate.get(), false);
            return Optional.empty();
        }

        BreakoutMemory longMemory = activeConfirmedMemory(breakoutMemories, "LONG", klines, false);
        if (longMemory != null) {
            signal = qualifySignal(
                    evaluator.evaluateBreakoutPullback(klines, longMemory.breakoutLevel(), longMemory.targetPrice(), true),
                    featureSnapshot,
                    applyCompositePolicy,
                    policyProfile,
                    audit
            );
            if (signal.isPresent()) {
                return signal;
            }
        }

        BreakoutMemory shortMemory = activeConfirmedMemory(breakoutMemories, "SHORT", klines, false);
        if (shortMemory != null) {
            signal = qualifySignal(
                    evaluator.evaluateBreakoutPullback(klines, shortMemory.breakoutLevel(), shortMemory.targetPrice(), false),
                    featureSnapshot,
                    applyCompositePolicy,
                    policyProfile,
                    audit
            );
            if (signal.isPresent()) {
                return signal;
            }
        }

        if (supportsSecondEntry(featureSnapshot)) {
            signal = qualifySignal(
                    evaluator.evaluateSecondEntryLong(
                            klines,
                            longMemory == null ? null : longMemory.breakoutLevel(),
                            longMemory == null ? null : longMemory.targetPrice()
                    ),
                    featureSnapshot,
                    applyCompositePolicy,
                    policyProfile,
                    audit
            );
            if (signal.isPresent()) {
                return signal;
            }

            signal = qualifySignal(
                    evaluator.evaluateSecondEntryShort(
                            klines,
                            shortMemory == null ? null : shortMemory.breakoutLevel(),
                            shortMemory == null ? null : shortMemory.targetPrice()
                    ),
                    featureSnapshot,
                    applyCompositePolicy,
                    policyProfile,
                    audit
            );
            if (signal.isPresent()) {
                return signal;
            }
        }

        return Optional.empty();
    }

    private BreakoutConfirmationResult confirmBreakoutMemory(String key,
                                                             String oppositeKey,
                                                             List<BinanceKlineDTO> klines,
                                                             AlertRuleEvaluator evaluator,
                                                             Map<String, BreakoutMemory> breakoutMemories,
                                                             FeatureSnapshot featureSnapshot,
                                                             boolean applyCompositePolicy,
                                                             CompositeFactorPolicyProfile policyProfile,
                                                             SignalAudit audit) {
        BreakoutMemory memory = activeConfirmedMemory(breakoutMemories, key, klines, true);
        if (memory == null || memory.followThroughConfirmed()) {
            return BreakoutConfirmationResult.notConfirmed();
        }

        Optional<AlertSignal> followThrough = evaluator.evaluateBreakoutFollowThrough(
                klines,
                memory.breakoutLevel(),
                memory.invalidationPrice(),
                memory.targetPrice(),
                memory.bullish()
        );
        if (followThrough.isEmpty()) {
            return BreakoutConfirmationResult.notConfirmed();
        }

        breakoutMemories.put(key, memory.confirm(followThrough.get().getKline().getEndTime()));
        breakoutMemories.remove(oppositeKey);

        Optional<AlertSignal> qualified = qualifySignal(
                followThrough,
                featureSnapshot,
                applyCompositePolicy,
                policyProfile,
                audit
        );
        return new BreakoutConfirmationResult(true, qualified);
    }

    private void rememberBreakoutCandidate(Map<String, BreakoutMemory> breakoutMemories,
                                           String key,
                                           String oppositeKey,
                                           AlertSignal signal,
                                           boolean bullish) {
        breakoutMemories.put(key, new BreakoutMemory(
                signal.getTriggerPrice(),
                signal.getInvalidationPrice(),
                signal.getTargetPrice(),
                signal.getKline().getEndTime(),
                bullish,
                false,
                null
        ));
        breakoutMemories.remove(oppositeKey);
    }

    private BreakoutMemory activeConfirmedMemory(Map<String, BreakoutMemory> breakoutMemories,
                                                 String key,
                                                 List<BinanceKlineDTO> klines,
                                                 boolean allowPending) {
        BreakoutMemory memory = breakoutMemories.get(key);
        if (memory == null) {
            return null;
        }
        if (isBreakoutMemoryExpired(memory, klines) || (!allowPending && !memory.followThroughConfirmed())) {
            breakoutMemories.remove(key);
            return null;
        }
        if (!allowPending && isBreakoutRejected(memory, klines)) {
            breakoutMemories.remove(key);
            return null;
        }
        if (allowPending
                && !memory.followThroughConfirmed()
                && (barsSinceBreakout(klines, memory.signalTime()) > breakoutFollowThroughMaxBars || isBreakoutRejected(memory, klines))) {
            breakoutMemories.remove(key);
            return null;
        }
        return memory;
    }

    private boolean isBreakoutMemoryExpired(BreakoutMemory memory, List<BinanceKlineDTO> klines) {
        List<BinanceKlineDTO> closedKlines = closedKlines(klines);
        if (closedKlines.isEmpty()) {
            return false;
        }
        long latestClosedTime = closedKlines.get(closedKlines.size() - 1).getEndTime();
        if (latestClosedTime - memory.signalTime() > breakoutRecordTtlMs) {
            return true;
        }
        return barsSinceBreakout(klines, memory.signalTime()) > breakoutRecordMaxBars;
    }

    private boolean isBreakoutRejected(BreakoutMemory memory, List<BinanceKlineDTO> klines) {
        List<BinanceKlineDTO> closedKlines = closedKlines(klines);
        if (closedKlines.isEmpty()) {
            return false;
        }
        BigDecimal latestClose = decimal(closedKlines.get(closedKlines.size() - 1).getClose());
        return memory.bullish()
                ? latestClose.compareTo(memory.invalidationPrice()) <= 0
                : latestClose.compareTo(memory.invalidationPrice()) >= 0;
    }

    private int barsSinceBreakout(List<BinanceKlineDTO> klines, long signalTime) {
        int bars = 0;
        for (BinanceKlineDTO kline : closedKlines(klines)) {
            if (kline.getEndTime() > signalTime) {
                bars++;
            }
        }
        return bars;
    }

    private List<BinanceKlineDTO> closedKlines(List<BinanceKlineDTO> klines) {
        return klines.size() < 2 ? List.of() : klines.subList(0, klines.size() - 1);
    }

    /**
     * 对原始信号做复合因子过滤。
     */
    private Optional<AlertSignal> qualifySignal(Optional<AlertSignal> rawSignal,
                                                FeatureSnapshot featureSnapshot,
                                                boolean applyCompositePolicy,
                                                CompositeFactorPolicyProfile policyProfile,
                                                SignalAudit audit) {
        if (rawSignal.isEmpty()) {
            return Optional.empty();
        }

        AlertSignal signal = rawSignal.get();
        audit.recordRaw(signal.getType());
        CompositeFactorPolicyProfile evaluationProfile = applyCompositePolicy || policyProfile == null
                ? policyProfile
                : policyProfile.withEnabled(false);
        SignalPolicyDecision decision = compositeFactorSignalPolicy.evaluate(signal, featureSnapshot, evaluationProfile);
        if (decision.allowed()) {
            return Optional.of(decision.signal());
        }

        audit.recordBlocked(signal.getType());
        return Optional.empty();
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

    /**
     * 根据信号和当前 K 线开盘价创建新仓位。
     */
    private ManagedPosition openPosition(AlertSignal signal, BinanceKlineDTO entryBar, int barIndex, BacktestConfig config) {
        BigDecimal entryPrice = decimal(entryBar.getOpen());
        BigDecimal stopPrice = signal.getInvalidationPrice();
        BigDecimal riskPerUnit = entryPrice.subtract(stopPrice).abs();
        if (riskPerUnit.compareTo(ZERO) == 0) {
            return null;
        }

        BigDecimal targetPrice = signal.getTargetPrice();
        if (!isTargetValid(signal.getDirection(), entryPrice, targetPrice)) {
            BigDecimal rewardDistance = riskPerUnit.multiply(config.fallbackTargetMultiple());
            targetPrice = signal.getDirection() == TradeDirection.LONG
                    ? entryPrice.add(rewardDistance)
                    : entryPrice.subtract(rewardDistance);
        }

        return new ManagedPosition(
                signal.getType(),
                signal.getDirection(),
                signal.getKline().getEndTime(),
                entryBar.getStartTime(),
                barIndex,
                entryPrice,
                stopPrice,
                targetPrice,
                riskPerUnit,
                maxHoldingBars(signal.getType(), config),
                config.scaleOutTriggerR(),
                config.scaleOutFraction(),
                config.trailingActivationR(),
                config.trailingDistanceR(),
                config.pyramidMaxAdds(),
                config.pyramidTriggerR(),
                config.pyramidAddFraction(),
                failedFollowThroughMaxBars,
                failedFollowThroughAdverseR,
                failedFollowThroughMinBodyRatio,
                failedFollowThroughCloseLocation
        );
    }

    /**
     * 在单根 K 线上评估持仓是否止损、止盈或超时退出。
     */
    private ManagedPosition evaluatePositionOnBar(ManagedPosition position,
                                                  BinanceKlineDTO bar,
                                                  int barIndex,
                                                  List<TradeRecord> completedTrades,
                                                  BacktestConfig config) {
        BigDecimal open = decimal(bar.getOpen());
        BigDecimal high = decimal(bar.getHigh());
        BigDecimal low = decimal(bar.getLow());
        BigDecimal close = decimal(bar.getClose());

        if (position.direction() == TradeDirection.LONG) {
            if (open.compareTo(position.stopPrice()) <= 0) {
                completedTrades.add(position.closeAll(bar.getEndTime(), open, "GAP_STOP"));
                return null;
            }
            if (open.compareTo(position.targetPrice()) >= 0) {
                completedTrades.add(position.closeAll(bar.getEndTime(), open, "GAP_TARGET"));
                return null;
            }

            position.activatePendingAddOn(open);

            boolean hitStop = low.compareTo(position.stopPrice()) <= 0;
            boolean hitTarget = high.compareTo(position.targetPrice()) >= 0;
            if (hitStop && hitTarget) {
                completedTrades.add(position.closeAll(bar.getEndTime(), position.stopPrice(), "BOTH_HIT_STOP_PRIORITY"));
                return null;
            }
            if (hitStop) {
                completedTrades.add(position.closeAll(bar.getEndTime(), position.stopPrice(), "STOP"));
                return null;
            }
            if (position.canScaleOut() && high.compareTo(position.scaleOutPrice()) >= 0) {
                position.scaleOut(position.scaleOutPrice());
            }
            if (hitTarget) {
                completedTrades.add(position.closeAll(bar.getEndTime(), position.targetPrice(), "TARGET"));
                return null;
            }
        } else {
            if (open.compareTo(position.stopPrice()) >= 0) {
                completedTrades.add(position.closeAll(bar.getEndTime(), open, "GAP_STOP"));
                return null;
            }
            if (open.compareTo(position.targetPrice()) <= 0) {
                completedTrades.add(position.closeAll(bar.getEndTime(), open, "GAP_TARGET"));
                return null;
            }

            position.activatePendingAddOn(open);

            boolean hitStop = high.compareTo(position.stopPrice()) >= 0;
            boolean hitTarget = low.compareTo(position.targetPrice()) <= 0;
            if (hitStop && hitTarget) {
                completedTrades.add(position.closeAll(bar.getEndTime(), position.stopPrice(), "BOTH_HIT_STOP_PRIORITY"));
                return null;
            }
            if (hitStop) {
                completedTrades.add(position.closeAll(bar.getEndTime(), position.stopPrice(), "STOP"));
                return null;
            }
            if (position.canScaleOut() && low.compareTo(position.scaleOutPrice()) <= 0) {
                position.scaleOut(position.scaleOutPrice());
            }
            if (hitTarget) {
                completedTrades.add(position.closeAll(bar.getEndTime(), position.targetPrice(), "TARGET"));
                return null;
            }
        }

        int heldBars = barIndex - position.entryBarIndex() + 1;
        if (position.shouldExitFailedFollowThrough(bar, heldBars)) {
            completedTrades.add(position.closeAll(bar.getEndTime(), close, "FAILED_FOLLOW_THROUGH"));
            return null;
        }
        if (heldBars >= position.maxHoldingBars()) {
            completedTrades.add(position.closeAll(bar.getEndTime(), close, "TIME"));
            return null;
        }

        position.updateAfterBar(high, low, close);
        return position;
    }

    /**
     * 汇总交易结果并生成回测报告。
     */
    private BacktestReport summarize(List<BinanceKlineDTO> history,
                                     List<TradeRecord> trades,
                                     Map<String, Integer> signalCounts,
                                     SignalAudit audit,
                                     boolean applyCompositePolicy,
                                     BacktestConfig config) {
        int wins = 0;
        BigDecimal grossProfit = ZERO;
        BigDecimal grossLoss = ZERO;
        BigDecimal totalR = ZERO;
        BigDecimal maxDrawdown = ZERO;
        BigDecimal equity = ZERO;
        BigDecimal peak = ZERO;

        for (TradeRecord trade : trades) {
            BigDecimal r = trade.realizedR();
            totalR = totalR.add(r);
            equity = equity.add(r);
            if (r.compareTo(ZERO) > 0) {
                wins++;
                grossProfit = grossProfit.add(r);
            } else if (r.compareTo(ZERO) < 0) {
                grossLoss = grossLoss.add(r.abs());
            }

            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            BigDecimal drawdown = peak.subtract(equity);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        int tradeCount = trades.size();
        BigDecimal winRate = tradeCount == 0 ? ZERO : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(tradeCount), 8, RoundingMode.HALF_UP);
        BigDecimal averageR = tradeCount == 0 ? ZERO : totalR.divide(BigDecimal.valueOf(tradeCount), 8, RoundingMode.HALF_UP);
        BigDecimal profitFactor = grossLoss.compareTo(ZERO) == 0 ? grossProfit : grossProfit.divide(grossLoss, 8, RoundingMode.HALF_UP);

        return new BacktestReport(
                history.size(),
                trades,
                signalCounts,
                audit.blockedSignalCounts(),
                audit.rawSignalCount(),
                audit.blockedSignalCount(),
                applyCompositePolicy,
                tradeCount,
                winRate,
                averageR,
                totalR,
                profitFactor,
                maxDrawdown,
                config
        );
    }

    /**
     * 清理超时的突破记忆。
     */
    private void cleanupExpiredBreakoutMemories(Map<String, BreakoutMemory> breakoutMemories,
                                                List<BinanceKlineDTO> visibleKlines,
                                                BacktestConfig config) {
        List<BinanceKlineDTO> closedKlines = closedKlines(visibleKlines);
        if (closedKlines.isEmpty()) {
            return;
        }
        long latestClosedTime = closedKlines.get(closedKlines.size() - 1).getEndTime();
        breakoutMemories.entrySet().removeIf(entry -> {
            BreakoutMemory memory = entry.getValue();
            long elapsedMs = latestClosedTime - memory.signalTime();
            int barsSince = barsSinceBreakout(visibleKlines, memory.signalTime());
            if (!memory.followThroughConfirmed() && barsSince > breakoutFollowThroughMaxBars) {
                return true;
            }
            return elapsedMs > config.breakoutRecordTtlMs() || barsSince > breakoutRecordMaxBars;
        });
    }

    /**
     * 判断目标价相对入场方向是否有效。
     */
    private HigherTimeframeBacktestContext prepareHigherTimeframeBacktestContext(BacktestConfig config) {
        if (!isBacktestExecutionRole()
                || !StringUtils.hasText(backtestContextInterval)
                || backtestContextInterval.equalsIgnoreCase(config.interval())
                || binanceApi == null) {
            return HigherTimeframeBacktestContext.disabled();
        }

        List<BinanceKlineDTO> contextHistory = loadHistoricalKlines(
                config.symbol(),
                backtestContextInterval,
                config.startTime(),
                config.endTime()
        );
        if (contextHistory.size() < 3) {
            log.warn("Skip higher timeframe backtest context, symbol={}, executionInterval={}, contextInterval={}, bars={}",
                    config.symbol(),
                    config.interval(),
                    backtestContextInterval,
                    contextHistory.size());
            return HigherTimeframeBacktestContext.disabled();
        }

        log.info("Higher timeframe backtest context ready, symbol={}, executionInterval={}, contextInterval={}, bars={}",
                config.symbol(),
                config.interval(),
                backtestContextInterval,
                contextHistory.size());
        return new HigherTimeframeBacktestContext(config.symbol(), backtestContextInterval, contextHistory);
    }

    private boolean isBacktestExecutionRole() {
        return "execution".equalsIgnoreCase(backtestMultiTimeframeRole);
    }

    private boolean isTargetValid(TradeDirection direction, BigDecimal entryPrice, BigDecimal targetPrice) {
        if (targetPrice == null) {
            return false;
        }
        return direction == TradeDirection.LONG
                ? targetPrice.compareTo(entryPrice) > 0
                : targetPrice.compareTo(entryPrice) < 0;
    }

    /**
     * 根据策略类型返回最大持仓 K 线数。
     */
    private int maxHoldingBars(String signalType, BacktestConfig config) {
        if (signalType.startsWith("RANGE_FAILURE")) {
            return config.rangeHoldingBars();
        }
        if (signalType.startsWith("BREAKOUT_PULLBACK") || signalType.startsWith("SECOND_ENTRY")) {
            return config.pullbackHoldingBars();
        }
        return config.breakoutHoldingBars();
    }

    /**
     * 分页拉取历史 K 线，并按时间排序后返回。
     */
    private List<BinanceKlineDTO> loadHistoricalKlines(String symbol, String interval, long startTime, long endTime) {
        List<BinanceKlineDTO> all = new ArrayList<>();
        long cursor = startTime;
        int pageCount = 0;
        log.info("开始分页拉取历史K线，symbol={}，interval={}，start={}，end={}",
                symbol,
                interval,
                Instant.ofEpochMilli(startTime),
                Instant.ofEpochMilli(endTime));

        while (cursor < endTime) {
            BinanceKlineDTO request = new BinanceKlineDTO();
            request.setSymbol(symbol);
            request.setInterval(interval);
            request.setLimit(PAGE_LIMIT);
            request.setStartTime(cursor);
            request.setEndTime(endTime);

            List<BinanceKlineDTO> page = binanceApi.listKline(request);
            if (page.isEmpty()) {
                log.warn("历史K线分页拉取返回空结果，提前结束，cursor={}", Instant.ofEpochMilli(cursor));
                break;
            }

            all.addAll(page);
            pageCount++;
            long nextCursor = page.get(page.size() - 1).getStartTime() + intervalMs(interval);
            if (nextCursor <= cursor) {
                log.warn("历史K线分页游标未向前推进，提前结束，cursor={}，nextCursor={}", cursor, nextCursor);
                break;
            }
            cursor = nextCursor;
            if (page.size() < PAGE_LIMIT) {
                break;
            }
        }

        log.info("历史K线分页拉取完成，symbol={}，总页数={}，总K线数量={}", symbol, pageCount, all.size());
        return all.stream()
                .sorted(Comparator.comparingLong(BinanceKlineDTO::getStartTime))
                .collect(Collectors.toList());
    }

    /**
     * 用反射把回测配置写入策略评估器。
     */
    private AlertRuleEvaluator buildEvaluator(BacktestConfig config) {
        AlertRuleEvaluator evaluator = new AlertRuleEvaluator();
        setField(evaluator, "fastPeriod", config.fastPeriod());
        setField(evaluator, "slowPeriod", config.slowPeriod());
        setField(evaluator, "rangeLookback", config.rangeLookback());
        setField(evaluator, "rangeMinWidth", config.rangeMinWidth());
        setField(evaluator, "rangeMaxWidth", config.rangeMaxWidth());
        setField(evaluator, "rangeEdgeTolerance", config.rangeEdgeTolerance());
        setField(evaluator, "requiredEdgeTouches", config.requiredEdgeTouches());
        setField(evaluator, "overlapThreshold", config.overlapThreshold());
        setField(evaluator, "minOverlapBars", config.minOverlapBars());
        setField(evaluator, "maFlatThreshold", config.maFlatThreshold());
        setField(evaluator, "breakoutCloseBuffer", config.breakoutCloseBuffer());
        setField(evaluator, "breakoutVolumeMultiplier", config.breakoutVolumeMultiplier());
        setField(evaluator, "breakoutBodyRatioThreshold", config.breakoutBodyRatioThreshold());
        setField(evaluator, "breakoutMaxExtension", config.breakoutMaxExtension());
        setField(evaluator, "breakoutFailureBuffer", config.breakoutFailureBuffer());
        setField(evaluator, "failureProbeBuffer", config.failureProbeBuffer());
        setField(evaluator, "failureReentryBuffer", config.failureReentryBuffer());
        setField(evaluator, "failureMinWickBodyRatio", config.failureMinWickBodyRatio());
        setField(evaluator, "pullbackTouchTolerance", config.pullbackTouchTolerance());
        setField(evaluator, "pullbackHoldBuffer", config.pullbackHoldBuffer());
        setField(evaluator, "pullbackMaxVolumeRatio", config.pullbackMaxVolumeRatio());
        setField(evaluator, "breakoutFollowThroughCloseBuffer", config.breakoutFollowThroughCloseBuffer());
        setField(evaluator, "breakoutFollowThroughMinBodyRatio", config.breakoutFollowThroughMinBodyRatio());
        setField(evaluator, "breakoutFollowThroughMinCloseLocation", config.breakoutFollowThroughMinCloseLocation());
        setField(evaluator, "breakoutFollowThroughMinVolumeRatio", config.breakoutFollowThroughMinVolumeRatio());
        setField(evaluator, "secondEntryLookback", secondEntryLookback);
        setField(evaluator, "secondEntryMinPullbackBars", secondEntryMinPullbackBars);
        setField(evaluator, "secondEntryMinBodyRatio", secondEntryMinBodyRatio);
        setField(evaluator, "secondEntryMinCloseLocation", secondEntryMinCloseLocation);
        setField(evaluator, "secondEntryInvalidationBuffer", secondEntryInvalidationBuffer);
        return evaluator;
    }

    /**
     * 反射写入单个字段。
     */
    private void setField(AlertRuleEvaluator evaluator, String fieldName, Object value) {
        try {
            Field field = AlertRuleEvaluator.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(evaluator, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set evaluator field " + fieldName, e);
        }
    }

    /**
     * 组装默认基线回测配置。
     */
    private BacktestConfig baselineConfig() {
        CompositeFactorPolicyProfile backtestPolicyProfile = compositeFactorSignalPolicy.currentProfile()
                .withMissingDerivativePenalty(backtestMissingDerivativePenalty);
        return new BacktestConfig(
                backtestSymbol,
                backtestInterval,
                parseInstant(backtestStart, Instant.parse("2020-01-01T00:00:00Z")).toEpochMilli(),
                parseInstant(backtestEnd, Instant.now()).toEpochMilli(),
                fastPeriod,
                slowPeriod,
                rangeLookback,
                rangeMinWidth,
                rangeMaxWidth,
                rangeEdgeTolerance,
                requiredEdgeTouches,
                overlapThreshold,
                minOverlapBars,
                maFlatThreshold,
                breakoutCloseBuffer,
                breakoutVolumeMultiplier,
                breakoutBodyRatioThreshold,
                breakoutMaxExtension,
                breakoutFailureBuffer,
                failureProbeBuffer,
                failureReentryBuffer,
                failureMinWickBodyRatio,
                pullbackTouchTolerance,
                pullbackHoldBuffer,
                pullbackMaxVolumeRatio,
                breakoutFollowThroughCloseBuffer,
                breakoutFollowThroughMinBodyRatio,
                breakoutFollowThroughMinCloseLocation,
                breakoutFollowThroughMinVolumeRatio,
                breakoutRecordTtlMs,
                rangeHoldingBars,
                breakoutHoldingBars,
                pullbackHoldingBars,
                fallbackTargetMultiple,
                scaleOutTriggerR,
                scaleOutFraction,
                trailingActivationR,
                trailingDistanceR,
                pyramidMaxAdds,
                pyramidTriggerR,
                pyramidAddFraction,
                backtestPolicyProfile
        );
    }

    /**
     * 解析时间配置，失败时回退到默认值。
     */
    private Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    /**
     * 把周期字符串换算成毫秒。
     */
    static long resolveIntervalMs(String interval) {
        return switch (interval) {
            case "3m" -> THREE_MINUTES_MS;
            case "15m" -> FIFTEEN_MINUTES_MS;
            case "1h" -> ONE_HOUR_MS;
            case "4h" -> FOUR_HOURS_MS;
            case "1d" -> ONE_DAY_MS;
            default -> FOUR_HOURS_MS;
        };
    }

    private long intervalMs(String interval) {
        return resolveIntervalMs(interval);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private String percent(BigDecimal ratio) {
        return ratio.multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private String scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String signed(BigDecimal value) {
        String prefix = value.compareTo(ZERO) > 0 ? "+" : "";
        return prefix + scale(value);
    }

    /**
     * 回测信号统计器。
     * 记录原始信号数量以及被复合因子拦截的分布。
     */
    private record BreakoutConfirmationResult(boolean confirmed, Optional<AlertSignal> signal) {
        private static BreakoutConfirmationResult notConfirmed() {
            return new BreakoutConfirmationResult(false, Optional.empty());
        }
    }

    private static final class HigherTimeframeBacktestContext {
        private final String symbol;
        private final String interval;
        private final List<BinanceKlineDTO> history;
        private int visibleCount;
        private long cachedAsOfTime = Long.MIN_VALUE;
        private FeatureSnapshot cachedSnapshot;
        private MarketState currentMarketState = MarketState.UNKNOWN;

        private HigherTimeframeBacktestContext(String symbol, String interval, List<BinanceKlineDTO> history) {
            this.symbol = symbol;
            this.interval = interval;
            this.history = history;
        }

        private static HigherTimeframeBacktestContext disabled() {
            return new HigherTimeframeBacktestContext(null, null, List.of());
        }

        private FeatureSnapshot snapshotFor(long executionBarEndTime,
                                            BacktestFeatureSnapshotService snapshotService,
                                            MarketStateMachine stateMachine) {
            if (history.isEmpty() || snapshotService == null || stateMachine == null) {
                return null;
            }

            while (visibleCount < history.size() && history.get(visibleCount).getEndTime() <= executionBarEndTime) {
                visibleCount++;
            }
            if (visibleCount < 3) {
                return null;
            }

            long asOfTime = history.get(visibleCount - 1).getEndTime();
            if (cachedSnapshot != null && cachedAsOfTime == asOfTime) {
                return cachedSnapshot;
            }

            FeatureSnapshot snapshot = snapshotService.buildSnapshot(symbol, interval, history.subList(0, visibleCount));
            MarketStateDecision stateDecision = stateMachine.evaluate(snapshot, currentMarketState);
            snapshot.setMarketState(stateDecision.state());
            snapshot.setMarketStateComment(stateDecision.comment());
            currentMarketState = stateDecision.state();
            cachedAsOfTime = asOfTime;
            cachedSnapshot = snapshot;
            return snapshot;
        }
    }

    private static final class ManagedPosition {
        private final String signalType;
        private final TradeDirection direction;
        private final long signalTime;
        private final long entryTime;
        private final int entryBarIndex;
        private final BigDecimal entryPrice;
        private final BigDecimal initialStopPrice;
        private final BigDecimal targetPrice;
        private final BigDecimal riskPerUnit;
        private final int maxHoldingBars;
        private final BigDecimal scaleOutFraction;
        private final BigDecimal scaleOutPrice;
        private final BigDecimal trailingActivationPrice;
        private final BigDecimal trailingDistancePrice;
        private final int pyramidMaxAdds;
        private final BigDecimal pyramidTriggerPrice;
        private final BigDecimal pyramidAddFraction;
        private final int failedFollowThroughMaxBars;
        private final BigDecimal failedFollowThroughAdverseR;
        private final BigDecimal failedFollowThroughMinBodyRatio;
        private final BigDecimal failedFollowThroughCloseLocation;
        private final List<PositionLot> openLots = new ArrayList<>();
        private BigDecimal stopPrice;
        private BigDecimal realizedR = ZERO;
        private boolean scaleOutTaken;
        private int addOnsUsed;
        private boolean pendingAddOn;
        private BigDecimal highestHigh;
        private BigDecimal lowestLow;

        private ManagedPosition(String signalType,
                                TradeDirection direction,
                                long signalTime,
                                long entryTime,
                                int entryBarIndex,
                                BigDecimal entryPrice,
                                BigDecimal initialStopPrice,
                                BigDecimal targetPrice,
                                BigDecimal riskPerUnit,
                                int maxHoldingBars,
                                BigDecimal scaleOutTriggerR,
                                BigDecimal scaleOutFraction,
                                BigDecimal trailingActivationR,
                                BigDecimal trailingDistanceR,
                                int pyramidMaxAdds,
                                BigDecimal pyramidTriggerR,
                                BigDecimal pyramidAddFraction,
                                int failedFollowThroughMaxBars,
                                BigDecimal failedFollowThroughAdverseR,
                                BigDecimal failedFollowThroughMinBodyRatio,
                                BigDecimal failedFollowThroughCloseLocation) {
            this.signalType = signalType;
            this.direction = direction;
            this.signalTime = signalTime;
            this.entryTime = entryTime;
            this.entryBarIndex = entryBarIndex;
            this.entryPrice = entryPrice;
            this.initialStopPrice = initialStopPrice;
            this.targetPrice = targetPrice;
            this.riskPerUnit = riskPerUnit;
            this.maxHoldingBars = maxHoldingBars;
            this.scaleOutFraction = clampFraction(scaleOutFraction);
            this.scaleOutPrice = directionalPrice(entryPrice, riskPerUnit.multiply(scaleOutTriggerR), direction);
            this.trailingActivationPrice = directionalPrice(entryPrice, riskPerUnit.multiply(trailingActivationR), direction);
            this.trailingDistancePrice = riskPerUnit.multiply(trailingDistanceR);
            this.pyramidMaxAdds = Math.max(0, pyramidMaxAdds);
            this.pyramidTriggerPrice = directionalPrice(entryPrice, riskPerUnit.multiply(pyramidTriggerR), direction);
            this.pyramidAddFraction = clampFraction(pyramidAddFraction);
            this.failedFollowThroughMaxBars = Math.max(0, failedFollowThroughMaxBars);
            this.failedFollowThroughAdverseR = failedFollowThroughAdverseR == null ? ZERO : failedFollowThroughAdverseR.max(ZERO);
            this.failedFollowThroughMinBodyRatio = failedFollowThroughMinBodyRatio == null ? ZERO : failedFollowThroughMinBodyRatio.max(ZERO);
            this.failedFollowThroughCloseLocation = failedFollowThroughCloseLocation == null
                    ? new BigDecimal("0.35")
                    : failedFollowThroughCloseLocation;
            this.stopPrice = initialStopPrice;
            this.highestHigh = entryPrice;
            this.lowestLow = entryPrice;
            this.openLots.add(new PositionLot(entryPrice, BigDecimal.ONE));
        }

        private TradeDirection direction() {
            return direction;
        }

        private BigDecimal stopPrice() {
            return stopPrice;
        }

        private BigDecimal targetPrice() {
            return targetPrice;
        }

        private int entryBarIndex() {
            return entryBarIndex;
        }

        private int maxHoldingBars() {
            return maxHoldingBars;
        }

        private BigDecimal scaleOutPrice() {
            return scaleOutPrice;
        }

        private boolean canScaleOut() {
            if (scaleOutTaken || scaleOutFraction.compareTo(ZERO) <= 0 || remainingSize().compareTo(ZERO) <= 0) {
                return false;
            }
            return direction == TradeDirection.LONG
                    ? scaleOutPrice.compareTo(entryPrice) > 0 && scaleOutPrice.compareTo(targetPrice) < 0
                    : scaleOutPrice.compareTo(entryPrice) < 0 && scaleOutPrice.compareTo(targetPrice) > 0;
        }

        private void scaleOut(BigDecimal exitPrice) {
            BigDecimal sizeToClose = remainingSize().multiply(scaleOutFraction);
            reduceExposure(sizeToClose, exitPrice);
            scaleOutTaken = true;
            if (direction == TradeDirection.LONG && stopPrice.compareTo(entryPrice) < 0) {
                stopPrice = entryPrice;
            }
            if (direction == TradeDirection.SHORT && stopPrice.compareTo(entryPrice) > 0) {
                stopPrice = entryPrice;
            }
        }

        private void activatePendingAddOn(BigDecimal openPrice) {
            if (!pendingAddOn || addOnsUsed >= pyramidMaxAdds || pyramidAddFraction.compareTo(ZERO) <= 0) {
                return;
            }
            openLots.add(new PositionLot(openPrice, pyramidAddFraction));
            addOnsUsed++;
            pendingAddOn = false;
        }

        private void updateAfterBar(BigDecimal high, BigDecimal low, BigDecimal close) {
            if (high.compareTo(highestHigh) > 0) {
                highestHigh = high;
            }
            if (low.compareTo(lowestLow) < 0) {
                lowestLow = low;
            }
            updateTrailingStop();
            scheduleAddOnIfNeeded(close);
        }

        private void updateTrailingStop() {
            if (trailingDistancePrice.compareTo(ZERO) <= 0) {
                return;
            }
            if (direction == TradeDirection.LONG && highestHigh.compareTo(trailingActivationPrice) >= 0) {
                BigDecimal candidateStop = highestHigh.subtract(trailingDistancePrice);
                if (candidateStop.compareTo(stopPrice) > 0) {
                    stopPrice = candidateStop;
                }
            }
            if (direction == TradeDirection.SHORT && lowestLow.compareTo(trailingActivationPrice) <= 0) {
                BigDecimal candidateStop = lowestLow.add(trailingDistancePrice);
                if (candidateStop.compareTo(stopPrice) < 0) {
                    stopPrice = candidateStop;
                }
            }
        }

        private void scheduleAddOnIfNeeded(BigDecimal close) {
            if (pendingAddOn || addOnsUsed >= pyramidMaxAdds || pyramidAddFraction.compareTo(ZERO) <= 0) {
                return;
            }
            if (!scaleOutTaken || !hasProtectedStop()) {
                return;
            }
            if (direction == TradeDirection.LONG && close.compareTo(pyramidTriggerPrice) >= 0) {
                pendingAddOn = true;
            }
            if (direction == TradeDirection.SHORT && close.compareTo(pyramidTriggerPrice) <= 0) {
                pendingAddOn = true;
            }
        }

        private boolean hasProtectedStop() {
            return direction == TradeDirection.LONG
                    ? stopPrice.compareTo(entryPrice) >= 0
                    : stopPrice.compareTo(entryPrice) <= 0;
        }

        private boolean shouldExitFailedFollowThrough(BinanceKlineDTO bar, int heldBars) {
            if (!isTrendContinuationSignal()
                    || scaleOutTaken
                    || failedFollowThroughMaxBars <= 0
                    || heldBars > failedFollowThroughMaxBars
                    || failedFollowThroughAdverseR.compareTo(ZERO) <= 0) {
                return false;
            }

            BigDecimal close = StrategySupport.valueOf(bar.getClose());
            BigDecimal adverseThreshold = riskPerUnit.multiply(failedFollowThroughAdverseR);
            BigDecimal bodyRatio = StrategySupport.bodyRatio(bar);
            BigDecimal closeLocation = StrategySupport.closeLocation(bar);
            if (direction == TradeDirection.LONG) {
                return close.compareTo(entryPrice.subtract(adverseThreshold)) <= 0
                        && StrategySupport.isBearish(bar)
                        && bodyRatio.compareTo(failedFollowThroughMinBodyRatio) >= 0
                        && closeLocation.compareTo(failedFollowThroughCloseLocation) <= 0;
            }

            BigDecimal strongOppositeClose = BigDecimal.ONE.subtract(failedFollowThroughCloseLocation);
            return close.compareTo(entryPrice.add(adverseThreshold)) >= 0
                    && StrategySupport.isBullish(bar)
                    && bodyRatio.compareTo(failedFollowThroughMinBodyRatio) >= 0
                    && closeLocation.compareTo(strongOppositeClose) >= 0;
        }

        private TradeRecord closeAll(long exitTime, BigDecimal exitPrice, String exitReason) {
            reduceExposure(remainingSize(), exitPrice);
            TradeRecord tradeRecord = new TradeRecord(
                    signalType,
                    direction,
                    signalTime,
                    entryTime,
                    entryBarIndex,
                    entryPrice,
                    initialStopPrice,
                    targetPrice,
                    riskPerUnit,
                    maxHoldingBars
            );
            return tradeRecord.closeManaged(exitTime, exitPrice, decorateReason(exitReason), realizedR);
        }

        private TradeRecord forceClose(long exitTime, BigDecimal exitPrice, String exitReason) {
            return closeAll(exitTime, exitPrice, exitReason);
        }

        private void reduceExposure(BigDecimal sizeToClose, BigDecimal exitPrice) {
            BigDecimal remainingToClose = sizeToClose.min(remainingSize()).max(ZERO);
            if (remainingToClose.compareTo(ZERO) <= 0) {
                return;
            }

            for (int i = 0; i < openLots.size() && remainingToClose.compareTo(ZERO) > 0; ) {
                PositionLot lot = openLots.get(i);
                BigDecimal closingSize = lot.size.min(remainingToClose);
                realizedR = realizedR.add(realizedRFor(lot.entryPrice, exitPrice, closingSize));
                lot.size = lot.size.subtract(closingSize);
                remainingToClose = remainingToClose.subtract(closingSize);
                if (lot.size.compareTo(ZERO) <= 0) {
                    openLots.remove(i);
                } else {
                    i++;
                }
            }
        }

        private BigDecimal realizedRFor(BigDecimal lotEntryPrice, BigDecimal exitPrice, BigDecimal size) {
            BigDecimal pnl = direction == TradeDirection.LONG
                    ? exitPrice.subtract(lotEntryPrice)
                    : lotEntryPrice.subtract(exitPrice);
            return pnl.multiply(size).divide(riskPerUnit, 8, RoundingMode.HALF_UP);
        }

        private BigDecimal remainingSize() {
            BigDecimal remaining = ZERO;
            for (PositionLot lot : openLots) {
                remaining = remaining.add(lot.size);
            }
            return remaining;
        }

        private String decorateReason(String exitReason) {
            return exitReason + " | scaleOut=" + scaleOutTaken + " | addOns=" + addOnsUsed + " | finalStop=" + stopPrice;
        }

        private boolean isTrendContinuationSignal() {
            return signalType.startsWith("CONFIRMED_BREAKOUT")
                    || signalType.startsWith("BREAKOUT_PULLBACK")
                    || signalType.startsWith("SECOND_ENTRY");
        }

        private static BigDecimal directionalPrice(BigDecimal entryPrice, BigDecimal distance, TradeDirection direction) {
            return direction == TradeDirection.LONG
                    ? entryPrice.add(distance)
                    : entryPrice.subtract(distance);
        }

        private static BigDecimal clampFraction(BigDecimal value) {
            if (value == null || value.compareTo(ZERO) <= 0) {
                return ZERO;
            }
            if (value.compareTo(BigDecimal.ONE) > 0) {
                return BigDecimal.ONE;
            }
            return value;
        }
    }

    private static final class PositionLot {
        private final BigDecimal entryPrice;
        private BigDecimal size;

        private PositionLot(BigDecimal entryPrice, BigDecimal size) {
            this.entryPrice = entryPrice;
            this.size = size;
        }
    }

    private static final class SignalAudit {
        private int rawSignalCount;
        private int blockedSignalCount;
        private final Map<String, Integer> blockedSignalCounts = new LinkedHashMap<>();

        void recordRaw(String signalType) {
            rawSignalCount++;
        }

        void recordBlocked(String signalType) {
            blockedSignalCount++;
            blockedSignalCounts.merge(signalType, 1, Integer::sum);
        }

        int rawSignalCount() {
            return rawSignalCount;
        }

        int blockedSignalCount() {
            return blockedSignalCount;
        }

        Map<String, Integer> blockedSignalCounts() {
            return blockedSignalCounts;
        }
    }
}
