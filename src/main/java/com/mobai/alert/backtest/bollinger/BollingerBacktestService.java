package com.mobai.alert.backtest.bollinger;

import com.mobai.alert.access.BinanceApi;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(value = "backtest.strategy.type", havingValue = "bollinger")
public class BollingerBacktestService implements BacktestStrategyRunner {

    private static final Logger log = LoggerFactory.getLogger(BollingerBacktestService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int PAGE_LIMIT = 1500;

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

    private final BinanceApi binanceApi;
    private final BollingerSignalEvaluator signalEvaluator;

    public BollingerBacktestService(BinanceApi binanceApi,
                                    BollingerSignalEvaluator signalEvaluator) {
        this.binanceApi = binanceApi;
        this.signalEvaluator = signalEvaluator;
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
        StringBuilder builder = new StringBuilder();
        builder.append(formatReport("Raw Baseline", result.baseline())).append("\n");
        builder.append(formatReport("Policy Baseline", result.policyFilteredBaseline())).append("\n");
        builder.append("Comparison | tradeDelta=")
                .append(result.policyFilteredBaseline().tradeCount() - result.baseline().tradeCount())
                .append(" totalRDelta=")
                .append(scale(result.policyFilteredBaseline().totalR().subtract(result.baseline().totalR())))
                .append(" blockedSignals=")
                .append(result.policyFilteredBaseline().blockedSignalCount());
        if (!CollectionUtils.isEmpty(result.variants())) {
            builder.append("\nPolicy Sensitivity");
            for (SensitivityResult variant : result.variants()) {
                builder.append("\n- ")
                        .append(variant.label())
                        .append(" | trades=").append(variant.report().tradeCount())
                        .append(" totalR=").append(scale(variant.report().totalR()));
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
        int contextCursor = 0;

        for (int i = 0; i < entryHistory.size(); i++) {
            BinanceKlineDTO currentBar = entryHistory.get(i);
            while (contextCursor < contextHistory.size()
                    && contextHistory.get(contextCursor).getEndTime() <= currentBar.getEndTime()) {
                contextCursor++;
            }

            if (pendingExit != null && openPosition != null) {
                trades.add(openPosition.trade().close(currentBar.getStartTime(), BollingerSupport.valueOf(currentBar.getOpen()), pendingExit.exitReason()));
                openPosition = null;
                pendingExit = null;
            }

            if (pendingEntry != null && openPosition == null) {
                BigDecimal entryPrice = BollingerSupport.valueOf(currentBar.getOpen());
                BigDecimal stopPrice = entryPrice.multiply(BigDecimal.ONE.subtract(stopLossPct)).setScale(8, RoundingMode.HALF_UP);
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
                    trades.add(openPosition.trade().close(currentBar.getStartTime(), barOpen, "GAP_STOP"));
                    openPosition = null;
                    pendingExit = null;
                    continue;
                }
                if (barLow.compareTo(openPosition.stopPrice()) <= 0) {
                    trades.add(openPosition.trade().close(currentBar.getEndTime(), openPosition.stopPrice(), "STOP_LOSS"));
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
                Optional<AlertSignal> signal = signalEvaluator.evaluateLongEntry(closedEntryKlines, closedContextKlines);
                if (signal.isPresent()) {
                    signalCounts.merge(signal.get().getType(), 1, Integer::sum);
                    pendingEntry = new PendingEntry(signal.get().getType(), currentBar.getEndTime());
                }
                continue;
            }

            Optional<AlertSignal> exitSignal = signalEvaluator.evaluateLongExit(
                    closedEntryKlines,
                    closedContextKlines,
                    openPosition.trade().entryPrice(),
                    openPosition.stopPrice()
            );
            if (exitSignal.isPresent()) {
                if (i + 1 < entryHistory.size()) {
                    pendingExit = new PendingExit("BOLLINGER_REVERSAL");
                } else {
                    trades.add(openPosition.trade().close(currentBar.getEndTime(), BollingerSupport.valueOf(currentBar.getClose()), "BOLLINGER_REVERSAL"));
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
        List<BinanceKlineDTO> all = new ArrayList<>();
        long cursor = startTime;
        while (cursor < endTime) {
            BinanceKlineDTO request = new BinanceKlineDTO();
            request.setSymbol(symbol);
            request.setInterval(interval);
            request.setLimit(PAGE_LIMIT);
            request.setStartTime(cursor);
            request.setEndTime(endTime);
            List<BinanceKlineDTO> page = binanceApi.listKline(request);
            if (CollectionUtils.isEmpty(page)) {
                break;
            }
            all.addAll(page);
            long nextCursor = page.get(page.size() - 1).getStartTime() + resolveIntervalMs(interval);
            if (nextCursor <= cursor) {
                break;
            }
            cursor = nextCursor;
            if (page.size() < PAGE_LIMIT) {
                break;
            }
        }
        log.info("Loaded backtest history, symbol={}, interval={}, bars={}", symbol, interval, all.size());
        return all.stream()
                .sorted(Comparator.comparingLong(BinanceKlineDTO::getStartTime))
                .toList();
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

    private String formatReport(String label, BacktestReport report) {
        return label
                + " | interval=" + report.config().interval()
                + " range=" + Instant.ofEpochMilli(report.config().startTime()) + " -> " + Instant.ofEpochMilli(report.config().endTime())
                + " trades=" + report.tradeCount()
                + " winRate=" + scale(report.winRate())
                + " avgR=" + scale(report.averageR())
                + " totalR=" + scale(report.totalR())
                + " profitFactor=" + scale(report.profitFactor())
                + " maxDrawdownR=" + scale(report.maxDrawdownR())
                + " signals=" + report.rawSignalCount()
                + " signalMix=" + report.signalCounts();
    }

    private String scale(BigDecimal value) {
        return value == null ? "-" : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private record PendingEntry(String signalType, long signalTime) {
    }

    private record PendingExit(String exitReason) {
    }

    private record OpenPosition(TradeRecord trade) {
        private BigDecimal stopPrice() {
            return trade.stopPrice();
        }
    }
}
