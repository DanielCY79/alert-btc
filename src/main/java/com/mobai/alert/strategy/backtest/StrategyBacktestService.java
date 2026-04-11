package com.mobai.alert.strategy.backtest;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.state.backtest.BacktestConfig;
import com.mobai.alert.state.backtest.BacktestReport;
import com.mobai.alert.state.backtest.BatchBacktestResult;
import com.mobai.alert.state.backtest.BreakoutMemory;
import com.mobai.alert.state.backtest.SensitivityResult;
import com.mobai.alert.state.backtest.TradeRecord;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.AlertRuleEvaluator;
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

    public StrategyBacktestService(BinanceApi binanceApi) {
        this.binanceApi = binanceApi;
    }

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

        BacktestReport baselineReport = runBacktest(history, baseline);
        BigDecimal baselineTotalR = baselineReport.totalR();
        List<SensitivityResult> variants = new ArrayList<>();

        variants.add(runSensitivity(history, baselineTotalR, "rangeLookback=28", baseline.withRangeLookback(28)));
        variants.add(runSensitivity(history, baselineTotalR, "rangeLookback=48", baseline.withRangeLookback(48)));
        variants.add(runSensitivity(history, baselineTotalR, "breakoutVolumeMultiplier=1.30", baseline.withBreakoutVolumeMultiplier(new BigDecimal("1.30"))));
        variants.add(runSensitivity(history, baselineTotalR, "breakoutVolumeMultiplier=1.80", baseline.withBreakoutVolumeMultiplier(new BigDecimal("1.80"))));
        variants.add(runSensitivity(history, baselineTotalR, "breakoutCloseBuffer=0.002", baseline.withBreakoutCloseBuffer(new BigDecimal("0.002"))));
        variants.add(runSensitivity(history, baselineTotalR, "breakoutCloseBuffer=0.004", baseline.withBreakoutCloseBuffer(new BigDecimal("0.004"))));

        log.info("默认回测批次执行完成，基线 trades={}，winRate={}，totalR={}，profitFactor={}",
                baselineReport.tradeCount(),
                percent(baselineReport.winRate()),
                scale(baselineReport.totalR()),
                scale(baselineReport.profitFactor()));
        return new BatchBacktestResult(history.size(), baselineReport, variants);
    }

    public BacktestReport runBacktest(List<BinanceKlineDTO> history, BacktestConfig config) {
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

        TradeRecord activeTrade = null;
        for (int barIndex = 1; barIndex < history.size(); barIndex++) {
            BinanceKlineDTO currentBar = history.get(barIndex);
            cleanupExpiredBreakoutMemories(breakoutMemories, currentBar.getStartTime(), config.breakoutRecordTtlMs());

            if (activeTrade == null) {
                Optional<AlertSignal> signalOptional = detectSignal(history.subList(0, barIndex + 1), evaluator, breakoutMemories);
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

        BacktestReport report = summarize(history, trades, signalCounts, config);
        log.info("单组回测完成，symbol={}，trades={}，winRate={}，avgR={}，totalR={}，maxDD={}，signalMix={}",
                config.symbol(),
                report.tradeCount(),
                percent(report.winRate()),
                scale(report.averageR()),
                scale(report.totalR()),
                scale(report.maxDrawdownR()),
                report.signalCounts());
        return report;
    }

    public String formatBatchResult(BatchBacktestResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("Backtest dataset bars: ").append(result.barCount()).append("\n");
        builder.append(formatReport("Baseline", result.baseline())).append("\n");
        builder.append("Sensitivity\n");
        for (SensitivityResult variant : result.variants()) {
            builder.append(formatSensitivity(variant)).append("\n");
        }
        return builder.toString();
    }

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
                " signalMix=" + report.signalCounts();
    }

    private String formatSensitivity(SensitivityResult result) {
        BacktestReport report = result.report();
        BigDecimal delta = report.totalR().subtract(result.baselineTotalR());
        return " - " + result.label() +
                " | trades=" + report.tradeCount() +
                " winRate=" + percent(report.winRate()) +
                " avgR=" + scale(report.averageR()) +
                " totalR=" + scale(report.totalR()) +
                " deltaR=" + signed(delta) +
                " profitFactor=" + scale(report.profitFactor());
    }

    private SensitivityResult runSensitivity(List<BinanceKlineDTO> history, BigDecimal baselineTotalR, String label, BacktestConfig variant) {
        log.info("开始执行敏感性测试：{}", label);
        SensitivityResult result = new SensitivityResult(label, runBacktest(history, variant), baselineTotalR);
        log.info("敏感性测试完成：{}，totalR={}，deltaR={}",
                label,
                scale(result.report().totalR()),
                signed(result.report().totalR().subtract(baselineTotalR)));
        return result;
    }

    private Optional<AlertSignal> detectSignal(List<BinanceKlineDTO> klines,
                                               AlertRuleEvaluator evaluator,
                                               Map<String, BreakoutMemory> breakoutMemories) {
        Optional<AlertSignal> signal = evaluator.evaluateRangeFailedBreakdownLong(klines);
        if (signal.isPresent()) {
            return signal;
        }

        signal = evaluator.evaluateRangeFailedBreakoutShort(klines);
        if (signal.isPresent()) {
            return signal;
        }

        signal = evaluator.evaluateTrendBreakout(klines);
        if (signal.isPresent()) {
            breakoutMemories.put("LONG", new BreakoutMemory(signal.get().getTriggerPrice(), signal.get().getTargetPrice(), signal.get().getKline().getEndTime()));
            breakoutMemories.remove("SHORT");
            return signal;
        }

        signal = evaluator.evaluateTrendBreakdown(klines);
        if (signal.isPresent()) {
            breakoutMemories.put("SHORT", new BreakoutMemory(signal.get().getTriggerPrice(), signal.get().getTargetPrice(), signal.get().getKline().getEndTime()));
            breakoutMemories.remove("LONG");
            return signal;
        }

        BreakoutMemory longMemory = breakoutMemories.get("LONG");
        if (longMemory != null) {
            signal = evaluator.evaluateBreakoutPullback(klines, longMemory.breakoutLevel(), longMemory.targetPrice(), true);
            if (signal.isPresent()) {
                return signal;
            }
        }

        BreakoutMemory shortMemory = breakoutMemories.get("SHORT");
        if (shortMemory != null) {
            signal = evaluator.evaluateBreakoutPullback(klines, shortMemory.breakoutLevel(), shortMemory.targetPrice(), false);
            if (signal.isPresent()) {
                return signal;
            }
        }

        return Optional.empty();
    }

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

    private BacktestReport summarize(List<BinanceKlineDTO> history,
                                     List<TradeRecord> trades,
                                     Map<String, Integer> signalCounts,
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

        return new BacktestReport(history.size(), trades, signalCounts, tradeCount, winRate, averageR, totalR, profitFactor, maxDrawdown, config);
    }

    private void cleanupExpiredBreakoutMemories(Map<String, BreakoutMemory> breakoutMemories, long currentTime, long ttlMs) {
        breakoutMemories.entrySet().removeIf(entry -> currentTime - entry.getValue().signalTime() > ttlMs);
    }

    private boolean isTargetValid(TradeDirection direction, BigDecimal entryPrice, BigDecimal targetPrice) {
        if (targetPrice == null) {
            return false;
        }
        return direction == TradeDirection.LONG
                ? targetPrice.compareTo(entryPrice) > 0
                : targetPrice.compareTo(entryPrice) < 0;
    }

    private int maxHoldingBars(String signalType, BacktestConfig config) {
        if (signalType.startsWith("RANGE_FAILURE")) {
            return config.rangeHoldingBars();
        }
        if (signalType.startsWith("BREAKOUT_PULLBACK")) {
            return config.pullbackHoldingBars();
        }
        return config.breakoutHoldingBars();
    }

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

    private void setField(AlertRuleEvaluator evaluator, String fieldName, Object value) {
        try {
            Field field = AlertRuleEvaluator.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(evaluator, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set evaluator field " + fieldName, e);
        }
    }

    private BacktestConfig baselineConfig() {
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
                fallbackTargetMultiple
        );
    }

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
}
