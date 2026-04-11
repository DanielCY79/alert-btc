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
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.AlertRuleEvaluator;
import com.mobai.alert.strategy.policy.CompositeFactorPolicyProfile;
import com.mobai.alert.strategy.policy.CompositeFactorSignalPolicy;
import com.mobai.alert.strategy.policy.PolicyWeights;
import com.mobai.alert.strategy.policy.SignalPolicyDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private static final long FOUR_HOURS_MS = 4L * 60L * 60L * 1000L;
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

    @Value("${monitoring.strategy.breakout.record.ttl.ms:43200000}")
    private long breakoutRecordTtlMs;

    @Value("${backtest.holding-bars.range:12}")
    private int rangeHoldingBars;

    @Value("${backtest.holding-bars.breakout:18}")
    private int breakoutHoldingBars;

    @Value("${backtest.holding-bars.pullback:18}")
    private int pullbackHoldingBars;

    @Value("${backtest.fallback-target-r.multiple:1.50}")
    private BigDecimal fallbackTargetMultiple;

    @Value("${backtest.policy.missing-derivative-penalty:0.00}")
    private BigDecimal backtestMissingDerivativePenalty;

    private final BacktestFeatureSnapshotService backtestFeatureSnapshotService;
    private final CompositeFactorSignalPolicy compositeFactorSignalPolicy;

    public StrategyBacktestService(BinanceApi binanceApi,
                                   BacktestFeatureSnapshotService backtestFeatureSnapshotService,
                                   CompositeFactorSignalPolicy compositeFactorSignalPolicy) {
        this.binanceApi = binanceApi;
        this.backtestFeatureSnapshotService = backtestFeatureSnapshotService;
        this.compositeFactorSignalPolicy = compositeFactorSignalPolicy;
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

        TradeRecord activeTrade = null;
        for (int barIndex = 1; barIndex < history.size(); barIndex++) {
            BinanceKlineDTO currentBar = history.get(barIndex);
            cleanupExpiredBreakoutMemories(breakoutMemories, currentBar.getStartTime(), config.breakoutRecordTtlMs());

            if (activeTrade == null) {
                List<BinanceKlineDTO> visibleKlines = history.subList(0, barIndex + 1);
                FeatureSnapshot featureSnapshot = applyCompositePolicy
                        ? backtestFeatureSnapshotService.buildSnapshot(config.symbol(), config.interval(), visibleKlines)
                        : null;
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
                    activeTrade = openTrade(signal, currentBar, barIndex, config);
                }
            }

            if (activeTrade != null) {
                activeTrade = evaluateTradeOnBar(activeTrade, currentBar, barIndex, trades, config);
            }
        }

        if (activeTrade != null) {
            BinanceKlineDTO lastBar = history.get(history.size() - 1);
            trades.add(activeTrade.forceClose(lastBar.getEndTime(), decimal(lastBar.getClose()), "FORCED_END"));
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

        signal = qualifySignal(
                evaluator.evaluateTrendBreakout(klines),
                featureSnapshot,
                applyCompositePolicy,
                policyProfile,
                audit
        );
        if (signal.isPresent()) {
            breakoutMemories.put("LONG", new BreakoutMemory(signal.get().getTriggerPrice(), signal.get().getTargetPrice(), signal.get().getKline().getEndTime()));
            breakoutMemories.remove("SHORT");
            return signal;
        }

        signal = qualifySignal(
                evaluator.evaluateTrendBreakdown(klines),
                featureSnapshot,
                applyCompositePolicy,
                policyProfile,
                audit
        );
        if (signal.isPresent()) {
            breakoutMemories.put("SHORT", new BreakoutMemory(signal.get().getTriggerPrice(), signal.get().getTargetPrice(), signal.get().getKline().getEndTime()));
            breakoutMemories.remove("LONG");
            return signal;
        }

        BreakoutMemory longMemory = breakoutMemories.get("LONG");
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

        BreakoutMemory shortMemory = breakoutMemories.get("SHORT");
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

        return Optional.empty();
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
        if (!applyCompositePolicy) {
            return Optional.of(signal);
        }

        SignalPolicyDecision decision = compositeFactorSignalPolicy.evaluate(signal, featureSnapshot, policyProfile);
        if (decision.allowed()) {
            return Optional.of(decision.signal());
        }

        audit.recordBlocked(signal.getType());
        return Optional.empty();
    }

    /**
     * 根据信号和当前 K 线开盘价创建新仓位。
     */
    private TradeRecord openTrade(AlertSignal signal, BinanceKlineDTO entryBar, int barIndex, BacktestConfig config) {
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

        return new TradeRecord(
                signal.getType(),
                signal.getDirection(),
                signal.getKline().getEndTime(),
                entryBar.getStartTime(),
                barIndex,
                entryPrice,
                stopPrice,
                targetPrice,
                riskPerUnit,
                maxHoldingBars(signal.getType(), config)
        );
    }

    /**
     * 在单根 K 线上评估持仓是否止损、止盈或超时退出。
     */
    private TradeRecord evaluateTradeOnBar(TradeRecord trade,
                                           BinanceKlineDTO bar,
                                           int barIndex,
                                           List<TradeRecord> completedTrades,
                                           BacktestConfig config) {
        BigDecimal open = decimal(bar.getOpen());
        BigDecimal high = decimal(bar.getHigh());
        BigDecimal low = decimal(bar.getLow());
        BigDecimal close = decimal(bar.getClose());

        if (trade.direction() == TradeDirection.LONG) {
            if (open.compareTo(trade.stopPrice()) <= 0) {
                completedTrades.add(trade.close(bar.getEndTime(), open, "GAP_STOP"));
                return null;
            }
            if (open.compareTo(trade.targetPrice()) >= 0) {
                completedTrades.add(trade.close(bar.getEndTime(), open, "GAP_TARGET"));
                return null;
            }

            boolean hitStop = low.compareTo(trade.stopPrice()) <= 0;
            boolean hitTarget = high.compareTo(trade.targetPrice()) >= 0;
            if (hitStop && hitTarget) {
                completedTrades.add(trade.close(bar.getEndTime(), trade.stopPrice(), "BOTH_HIT_STOP_PRIORITY"));
                return null;
            }
            if (hitStop) {
                completedTrades.add(trade.close(bar.getEndTime(), trade.stopPrice(), "STOP"));
                return null;
            }
            if (hitTarget) {
                completedTrades.add(trade.close(bar.getEndTime(), trade.targetPrice(), "TARGET"));
                return null;
            }
        } else {
            if (open.compareTo(trade.stopPrice()) >= 0) {
                completedTrades.add(trade.close(bar.getEndTime(), open, "GAP_STOP"));
                return null;
            }
            if (open.compareTo(trade.targetPrice()) <= 0) {
                completedTrades.add(trade.close(bar.getEndTime(), open, "GAP_TARGET"));
                return null;
            }

            boolean hitStop = high.compareTo(trade.stopPrice()) >= 0;
            boolean hitTarget = low.compareTo(trade.targetPrice()) <= 0;
            if (hitStop && hitTarget) {
                completedTrades.add(trade.close(bar.getEndTime(), trade.stopPrice(), "BOTH_HIT_STOP_PRIORITY"));
                return null;
            }
            if (hitStop) {
                completedTrades.add(trade.close(bar.getEndTime(), trade.stopPrice(), "STOP"));
                return null;
            }
            if (hitTarget) {
                completedTrades.add(trade.close(bar.getEndTime(), trade.targetPrice(), "TARGET"));
                return null;
            }
        }

        int heldBars = barIndex - trade.entryBarIndex() + 1;
        if (heldBars >= trade.maxHoldingBars()) {
            completedTrades.add(trade.close(bar.getEndTime(), close, "TIME"));
            return null;
        }

        return trade;
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
    private void cleanupExpiredBreakoutMemories(Map<String, BreakoutMemory> breakoutMemories, long currentTime, long ttlMs) {
        breakoutMemories.entrySet().removeIf(entry -> currentTime - entry.getValue().signalTime() > ttlMs);
    }

    /**
     * 判断目标价相对入场方向是否有效。
     */
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
        if (signalType.startsWith("BREAKOUT_PULLBACK")) {
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
                breakoutRecordTtlMs,
                rangeHoldingBars,
                breakoutHoldingBars,
                pullbackHoldingBars,
                fallbackTargetMultiple,
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
    private long intervalMs(String interval) {
        return switch (interval) {
            case "4h" -> FOUR_HOURS_MS;
            case "1h" -> 60L * 60L * 1000L;
            case "15m" -> 15L * 60L * 1000L;
            case "1d" -> 24L * 60L * 60L * 1000L;
            default -> FOUR_HOURS_MS;
        };
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
