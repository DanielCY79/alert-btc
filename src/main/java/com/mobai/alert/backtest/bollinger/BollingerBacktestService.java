package com.mobai.alert.backtest.bollinger;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.service.AccessKlineBarHistoryService;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.backtest.BacktestStrategyRunner;
import com.mobai.alert.backtest.model.BacktestConfig;
import com.mobai.alert.backtest.model.BacktestReport;
import com.mobai.alert.backtest.model.BatchBacktestResult;
import com.mobai.alert.backtest.model.SensitivityResult;
import com.mobai.alert.backtest.model.TradeRecord;
import com.mobai.alert.strategy.bollinger.BollingerSignalEvaluator;
import com.mobai.alert.strategy.bollinger.shared.BollingerSupport;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import com.mobai.alert.strategy.priceaction.policy.CompositeFactorPolicyProfile;
import com.mobai.alert.strategy.priceaction.policy.PolicyWeights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(value = "backtest.strategy.type", havingValue = "bollinger")
public class BollingerBacktestService implements BacktestStrategyRunner {

    private static final Logger log = LoggerFactory.getLogger(BollingerBacktestService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Value("${backtest.symbol:${monitoring.target-symbol:BTCUSDT}}")
    private String backtestSymbol;

    @Value("${backtest.start:2020-01-01T00:00:00Z}")
    private String backtestStart;

    @Value("${backtest.end:}")
    private String backtestEnd;

    @Value("${monitoring.strategy.boll.entry-interval:1m}")
    private String entryInterval;

    @Value("${monitoring.strategy.boll.context-interval:4h}")
    private String contextInterval;

    @Value("${monitoring.strategy.boll.period:20}")
    private int bollPeriod;

    @Value("${monitoring.strategy.boll.entry-volume-lookback:20}")
    private int entryVolumeLookback;

    @Value("${monitoring.strategy.boll.stop-loss-pct:0.10}")
    private BigDecimal stopLossPct;

    @Value("${monitoring.strategy.boll.reentry-cooldown-bars:240}")
    private int reentryCooldownBars;

    @Value("${monitoring.strategy.boll.max-entries-per-context-bar:1}")
    private int maxEntriesPerContextBar;

    @Value("${backtest.export.initial-capital:10000}")
    private BigDecimal initialCapital;

    private final BollingerSignalEvaluator signalEvaluator;
    private final AccessKlineBarHistoryService klineHistoryService;

    @Autowired
    public BollingerBacktestService(BollingerSignalEvaluator signalEvaluator,
                                    AccessKlineBarHistoryService klineHistoryService) {
        this.signalEvaluator = signalEvaluator;
        this.klineHistoryService = klineHistoryService;
    }

    BollingerBacktestService(BinanceApi ignoredBinanceApi,
                             BollingerSignalEvaluator signalEvaluator) {
        this(signalEvaluator, null);
    }

    @Override
    public BatchBacktestResult runDefaultBacktestBatch() {
        BacktestConfig config = compatibilityConfig();
        long entryWarmupStart = Math.max(0L, config.startTime() - (long) (bollPeriod + entryVolumeLookback + 2) * resolveIntervalMs(entryInterval));
        long contextWarmupStart = Math.max(0L, config.startTime() - (long) (bollPeriod + 2) * resolveIntervalMs(contextInterval));
        List<BinanceKlineDTO> entryHistory = loadHistoricalKlines(config.symbol(), entryInterval, entryWarmupStart, config.endTime());
        List<BinanceKlineDTO> contextHistory = loadHistoricalKlines(config.symbol(), contextInterval, contextWarmupStart, config.endTime());
        BacktestReport report = runBacktest(entryHistory, contextHistory, config);
        return new BatchBacktestResult(report.barCount(), report, report, List.of());
    }

    @Override
    public String formatBatchResult(BatchBacktestResult result) {
        if (result == null) {
            return "No backtest result.";
        }
        CapitalSummary rawSummary = simulateCapital(result.baseline());
        CapitalSummary policySummary = simulateCapital(result.policyFilteredBaseline());
        StringBuilder builder = new StringBuilder();
        builder.append(formatReport("Raw Baseline", result.baseline(), rawSummary)).append("\n");
        builder.append(formatReport("Policy Baseline", result.policyFilteredBaseline(), policySummary)).append("\n");
        builder.append("Comparison | tradeDelta=")
                .append(result.policyFilteredBaseline().tradeCount() - result.baseline().tradeCount())
                .append(" netProfitDelta=").append(scaleMoney(policySummary.netProfit().subtract(rawSummary.netProfit()))).append(" USDT")
                .append(" returnDelta=").append(scaleMoney(policySummary.totalReturnPct().subtract(rawSummary.totalReturnPct()))).append("%")
                .append(" blockedSignals=")
                .append(result.policyFilteredBaseline().blockedSignalCount());
        if (!CollectionUtils.isEmpty(result.variants())) {
            builder.append("\nPolicy Sensitivity");
            for (SensitivityResult variant : result.variants()) {
                CapitalSummary variantSummary = simulateCapital(variant.report());
                builder.append("\n- ")
                        .append(variant.label())
                        .append(" | trades=").append(variant.report().tradeCount())
                        .append(" netProfit=").append(scaleMoney(variantSummary.netProfit())).append(" USDT")
                        .append(" return=").append(scaleMoney(variantSummary.totalReturnPct())).append("%");
            }
        }
        return builder.toString();
    }

    BacktestReport runBacktest(List<BinanceKlineDTO> entryHistory,
                               List<BinanceKlineDTO> contextHistory,
                               BacktestConfig config) {
        Map<String, Integer> signalCounts = new LinkedHashMap<>();
        List<TradeRecord> trades = new ArrayList<>();
        if (CollectionUtils.isEmpty(entryHistory) || CollectionUtils.isEmpty(contextHistory)) {
            return emptyReport(config);
        }

        OpenPosition openPosition = null;
        PendingEntry pendingEntry = null;
        PendingExit pendingExit = null;
        long cooldownUntilTime = Long.MIN_VALUE;
        long trackedContextBarEndTime = Long.MIN_VALUE;
        int entriesOnTrackedContextBar = 0;
        int contextCursor = 0;
        long entryIntervalMs = resolveIntervalMs(config.interval());

        for (int i = 0; i < entryHistory.size(); i++) {
            BinanceKlineDTO currentBar = entryHistory.get(i);
            while (contextCursor < contextHistory.size()
                    && contextHistory.get(contextCursor).getEndTime() <= currentBar.getEndTime()) {
                contextCursor++;
            }
            long latestClosedContextBarEndTime = contextCursor == 0
                    ? Long.MIN_VALUE
                    : contextHistory.get(contextCursor - 1).getEndTime();
            if (latestClosedContextBarEndTime != trackedContextBarEndTime) {
                trackedContextBarEndTime = latestClosedContextBarEndTime;
                entriesOnTrackedContextBar = 0;
            }

            if (pendingExit != null && openPosition != null) {
                TradeRecord closedTrade = openPosition.trade().close(
                        currentBar.getStartTime(),
                        BollingerSupport.valueOf(currentBar.getOpen()),
                        pendingExit.exitReason()
                );
                trades.add(closedTrade);
                cooldownUntilTime = nextCooldownTime(cooldownUntilTime, closedTrade, currentBar.getStartTime(), entryIntervalMs);
                openPosition = null;
                pendingExit = null;
            }

            if (pendingEntry != null && openPosition == null) {
                BigDecimal entryPrice = BollingerSupport.valueOf(currentBar.getOpen());
                BigDecimal stopPrice = entryPrice.multiply(BigDecimal.ONE.subtract(stopLossPct)).setScale(8, RoundingMode.HALF_UP);
                if (pendingEntry.contextBarEndTime() != trackedContextBarEndTime) {
                    trackedContextBarEndTime = pendingEntry.contextBarEndTime();
                    entriesOnTrackedContextBar = 0;
                }
                entriesOnTrackedContextBar++;
                openPosition = new OpenPosition(new TradeRecord(
                        pendingEntry.signalType(),
                        TradeDirection.LONG,
                        pendingEntry.signalTime(),
                        currentBar.getStartTime(),
                        i,
                        entryPrice,
                        stopPrice,
                        null,
                        entryPrice.subtract(stopPrice).abs(),
                        0
                ));
                pendingEntry = null;
            }

            if (currentBar.getEndTime() < config.startTime()) {
                continue;
            }
            if (currentBar.getEndTime() > config.endTime()) {
                break;
            }

            if (openPosition != null) {
                BigDecimal barOpen = BollingerSupport.valueOf(currentBar.getOpen());
                BigDecimal barLow = BollingerSupport.valueOf(currentBar.getLow());
                if (barOpen.compareTo(openPosition.stopPrice()) <= 0) {
                    TradeRecord closedTrade = openPosition.trade().close(currentBar.getStartTime(), barOpen, "GAP_STOP");
                    trades.add(closedTrade);
                    cooldownUntilTime = nextCooldownTime(cooldownUntilTime, closedTrade, currentBar.getStartTime(), entryIntervalMs);
                    openPosition = null;
                    pendingExit = null;
                    continue;
                }
                if (barLow.compareTo(openPosition.stopPrice()) <= 0) {
                    TradeRecord closedTrade = openPosition.trade().close(currentBar.getEndTime(), openPosition.stopPrice(), "STOP_LOSS");
                    trades.add(closedTrade);
                    cooldownUntilTime = nextCooldownTime(cooldownUntilTime, closedTrade, currentBar.getEndTime(), entryIntervalMs);
                    openPosition = null;
                    pendingExit = null;
                    continue;
                }
            }

            if (contextCursor < bollPeriod) {
                continue;
            }

            List<BinanceKlineDTO> closedEntryKlines = entryHistory.subList(0, i + 1);
            List<BinanceKlineDTO> closedContextKlines = contextHistory.subList(0, contextCursor);

            if (openPosition == null) {
                if (i + 1 >= entryHistory.size()) {
                    continue;
                }
                if (isCooldownActive(cooldownUntilTime, currentBar.getEndTime())) {
                    continue;
                }
                if (hasReachedContextEntryLimit(entriesOnTrackedContextBar)) {
                    continue;
                }
                Optional<AlertSignal> signal = signalEvaluator.evaluateLongEntry(closedEntryKlines, closedContextKlines);
                if (signal.isPresent()) {
                    signalCounts.merge(signal.get().getType(), 1, Integer::sum);
                    pendingEntry = new PendingEntry(signal.get().getType(), currentBar.getEndTime(), latestClosedContextBarEndTime);
                }
                continue;
            }

            int heldBars = Math.max(1, i - openPosition.trade().entryBarIndex() + 1);
            Optional<AlertSignal> exitSignal = signalEvaluator.evaluateLongExit(
                    closedEntryKlines,
                    closedContextKlines,
                    openPosition.trade().entryPrice(),
                    openPosition.stopPrice(),
                    heldBars
            );
            if (exitSignal.isPresent()) {
                if (i + 1 < entryHistory.size()) {
                    pendingExit = new PendingExit(exitReasonForSignal(exitSignal.get().getType()));
                } else {
                    TradeRecord closedTrade = openPosition.trade().close(
                            currentBar.getEndTime(),
                            BollingerSupport.valueOf(currentBar.getClose()),
                            exitReasonForSignal(exitSignal.get().getType())
                    );
                    trades.add(closedTrade);
                    cooldownUntilTime = nextCooldownTime(cooldownUntilTime, closedTrade, currentBar.getEndTime(), entryIntervalMs);
                    openPosition = null;
                }
            }
        }

        if (openPosition != null) {
            BinanceKlineDTO lastBar = entryHistory.get(entryHistory.size() - 1);
            trades.add(openPosition.trade().forceClose(
                    lastBar.getEndTime(),
                    BollingerSupport.valueOf(lastBar.getClose()),
                    pendingExit == null ? "FORCED_END" : pendingExit.exitReason()
            ));
        }

        return summarize(entryHistory.size(), trades, signalCounts, config);
    }

    static long resolveIntervalMs(String interval) {
        return BollingerSupport.resolveIntervalMs(interval);
    }

    private List<BinanceKlineDTO> loadHistoricalKlines(String symbol, String interval, long startTime, long endTime) {
        if (klineHistoryService == null) {
            throw new IllegalStateException("Backtest kline history service is unavailable; access_kline_bar cannot be queried.");
        }
        List<BinanceKlineDTO> history = klineHistoryService.loadClosedKlines(symbol, interval, startTime, endTime);
        if (CollectionUtils.isEmpty(history)) {
            throw new IllegalStateException("No backtest klines found in access_kline_bar for symbol="
                    + symbol + ", interval=" + interval + ", startTime=" + startTime + ", endTime=" + endTime);
        }
        log.info("Loaded backtest history from access_kline_bar, symbol={}, interval={}, bars={}", symbol, interval, history.size());
        return history;
    }

    private BacktestReport summarize(int barCount,
                                     List<TradeRecord> trades,
                                     Map<String, Integer> signalCounts,
                                     BacktestConfig config) {
        if (CollectionUtils.isEmpty(trades)) {
            return new BacktestReport(
                    barCount,
                    List.of(),
                    signalCounts,
                    Map.of(),
                    signalCounts.values().stream().mapToInt(Integer::intValue).sum(),
                    0,
                    false,
                    0,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    config
            );
        }

        BigDecimal wins = ZERO;
        BigDecimal lossesAbs = ZERO;
        BigDecimal totalR = ZERO;
        BigDecimal peakR = ZERO;
        BigDecimal cumulativeR = ZERO;
        BigDecimal maxDrawdownR = ZERO;
        int winCount = 0;

        for (TradeRecord trade : trades) {
            BigDecimal realizedR = trade.realizedR();
            totalR = totalR.add(realizedR);
            cumulativeR = cumulativeR.add(realizedR);
            if (cumulativeR.compareTo(peakR) > 0) {
                peakR = cumulativeR;
            }
            BigDecimal drawdown = peakR.subtract(cumulativeR);
            if (drawdown.compareTo(maxDrawdownR) > 0) {
                maxDrawdownR = drawdown;
            }
            if (realizedR.compareTo(ZERO) > 0) {
                wins = wins.add(realizedR);
                winCount++;
            } else if (realizedR.compareTo(ZERO) < 0) {
                lossesAbs = lossesAbs.add(realizedR.abs());
            }
        }

        BigDecimal tradeCount = BigDecimal.valueOf(trades.size());
        BigDecimal winRate = BigDecimal.valueOf(winCount).divide(tradeCount, 8, RoundingMode.HALF_UP);
        BigDecimal averageR = totalR.divide(tradeCount, 8, RoundingMode.HALF_UP);
        BigDecimal profitFactor = lossesAbs.compareTo(ZERO) == 0
                ? wins
                : wins.divide(lossesAbs, 8, RoundingMode.HALF_UP);

        return new BacktestReport(
                barCount,
                List.copyOf(trades),
                signalCounts,
                Map.of(),
                signalCounts.values().stream().mapToInt(Integer::intValue).sum(),
                0,
                false,
                trades.size(),
                winRate,
                averageR,
                totalR,
                profitFactor,
                maxDrawdownR,
                config
        );
    }

    private BacktestReport emptyReport(BacktestConfig config) {
        return new BacktestReport(
                0,
                List.of(),
                Map.of(),
                Map.of(),
                0,
                0,
                false,
                0,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                config
        );
    }

    private BacktestConfig compatibilityConfig() {
        long startTime = parseInstant(backtestStart, Instant.parse("2020-01-01T00:00:00Z")).toEpochMilli();
        long endTime = parseInstant(backtestEnd, Instant.now()).toEpochMilli();
        return new BacktestConfig(
                backtestSymbol,
                entryInterval,
                startTime,
                endTime,
                bollPeriod,
                bollPeriod,
                bollPeriod,
                ZERO,
                ZERO,
                ZERO,
                0,
                ZERO,
                0,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                0L,
                0,
                0,
                0,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                0,
                ZERO,
                ZERO,
                disabledPolicyProfile()
        );
    }

    private CompositeFactorPolicyProfile disabledPolicyProfile() {
        PolicyWeights zeroWeights = new PolicyWeights(ZERO, ZERO, ZERO, ZERO, ZERO);
        return new CompositeFactorPolicyProfile(
                false,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                zeroWeights,
                zeroWeights,
                zeroWeights
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

    private String formatReport(String label, BacktestReport report, CapitalSummary summary) {
        return label
                + " | interval=" + report.config().interval()
                + " range=" + Instant.ofEpochMilli(report.config().startTime()) + " -> " + Instant.ofEpochMilli(report.config().endTime())
                + " trades=" + report.tradeCount()
                + " winRate=" + scalePct(report.winRate())
                + " netProfit=" + scaleMoney(summary.netProfit()) + " USDT"
                + " return=" + scaleMoney(summary.totalReturnPct()) + "%"
                + " finalEquity=" + scaleMoney(summary.finalEquity()) + " USDT"
                + " maxDrawdown=" + scaleMoney(summary.maxDrawdownAmount()) + " USDT"
                + " (" + scaleMoney(summary.maxDrawdownPct()) + "%)"
                + " profitFactor=" + scale(report.profitFactor())
                + " signals=" + report.rawSignalCount()
                + " signalMix=" + report.signalCounts();
    }

    private boolean isCooldownActive(long cooldownUntilTime, long currentSignalTime) {
        return reentryCooldownBars > 0 && cooldownUntilTime > currentSignalTime;
    }

    private boolean hasReachedContextEntryLimit(int entriesOnTrackedContextBar) {
        return maxEntriesPerContextBar > 0 && entriesOnTrackedContextBar >= maxEntriesPerContextBar;
    }

    private long nextCooldownTime(long existingCooldownUntilTime,
                                  TradeRecord trade,
                                  long exitTime,
                                  long entryIntervalMs) {
        if (reentryCooldownBars <= 0 || trade == null || trade.realizedReturnRatio().compareTo(ZERO) > 0) {
            return existingCooldownUntilTime;
        }
        long cooldownDurationMs = Math.max(1, reentryCooldownBars) * entryIntervalMs;
        return Math.max(existingCooldownUntilTime, exitTime + cooldownDurationMs);
    }

    private String exitReasonForSignal(String signalType) {
        if ("EXIT_BOLLINGER_FAST_FAILURE_LONG".equalsIgnoreCase(signalType)) {
            return "FAST_FAILURE";
        }
        return "BOLLINGER_REVERSAL";
    }

    private String scale(BigDecimal value) {
        return value == null ? "-" : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private String scaleMoney(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String scalePct(BigDecimal ratio) {
        return ratio == null ? "-" : ratio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private CapitalSummary simulateCapital(BacktestReport report) {
        BigDecimal equity = initialCapital.max(ZERO);
        BigDecimal peak = equity;
        BigDecimal maxDrawdownAmount = ZERO;
        BigDecimal maxDrawdownPct = ZERO;
        for (TradeRecord trade : report.trades()) {
            BigDecimal pnl = trade.realizedPnl(equity);
            equity = equity.add(pnl);
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            BigDecimal drawdownAmount = peak.subtract(equity);
            BigDecimal drawdownPct = peak.compareTo(ZERO) == 0
                    ? ZERO
                    : drawdownAmount.divide(peak, 8, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            if (drawdownAmount.compareTo(maxDrawdownAmount) > 0) {
                maxDrawdownAmount = drawdownAmount;
            }
            if (drawdownPct.compareTo(maxDrawdownPct) > 0) {
                maxDrawdownPct = drawdownPct;
            }
        }
        BigDecimal netProfit = equity.subtract(initialCapital);
        BigDecimal totalReturnPct = initialCapital.compareTo(ZERO) == 0
                ? ZERO
                : netProfit.divide(initialCapital, 8, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        return new CapitalSummary(equity, netProfit, totalReturnPct, maxDrawdownAmount, maxDrawdownPct);
    }

    private record PendingEntry(String signalType, long signalTime, long contextBarEndTime) {
    }

    private record PendingExit(String exitReason) {
    }

    private record OpenPosition(TradeRecord trade) {
        private BigDecimal stopPrice() {
            return trade.stopPrice();
        }
    }

    private record CapitalSummary(BigDecimal finalEquity,
                                  BigDecimal netProfit,
                                  BigDecimal totalReturnPct,
                                  BigDecimal maxDrawdownAmount,
                                  BigDecimal maxDrawdownPct) {
    }
}
