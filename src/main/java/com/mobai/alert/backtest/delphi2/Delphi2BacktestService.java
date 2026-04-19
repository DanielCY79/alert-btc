package com.mobai.alert.backtest.delphi2;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.kline.service.AccessKlineBarHistoryService;
import com.mobai.alert.backtest.BacktestStrategyRunner;
import com.mobai.alert.backtest.model.BacktestConfig;
import com.mobai.alert.backtest.model.BacktestReport;
import com.mobai.alert.backtest.model.BatchBacktestResult;
import com.mobai.alert.backtest.model.TradeRecord;
import com.mobai.alert.strategy.delphi2.Delphi2SignalEvaluator;
import com.mobai.alert.strategy.delphi2.shared.Delphi2Support;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
@ConditionalOnProperty(value = "backtest.strategy.type", havingValue = "delphi2")
public class Delphi2BacktestService implements BacktestStrategyRunner {

    private static final Logger log = LoggerFactory.getLogger(Delphi2BacktestService.class);
    private static final int BINANCE_MAX_FETCH_LIMIT = 1000;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Value("${backtest.symbol:${monitoring.target-symbol:BTCUSDT}}")
    private String backtestSymbol;

    @Value("${backtest.start:2020-01-01T00:00:00Z}")
    private String backtestStart;

    @Value("${backtest.end:}")
    private String backtestEnd;

    @Value("${monitoring.strategy.delphi.entry-interval:1h}")
    private String entryInterval;

    @Value("${monitoring.strategy.delphi.trend-interval:1d}")
    private String trendInterval;

    @Value("${monitoring.strategy.delphi.trend-fast-period:20}")
    private int trendFastPeriod;

    @Value("${monitoring.strategy.delphi.trend-slow-period:50}")
    private int trendSlowPeriod;

    @Value("${monitoring.strategy.delphi.entry-breakout-lookback:10}")
    private int entryBreakoutLookback;

    @Value("${monitoring.strategy.delphi.entry-atr-period:20}")
    private int entryAtrPeriod;

    @Value("${monitoring.strategy.delphi.stop-loss-atr-multiplier:2.0}")
    private BigDecimal stopLossAtrMultiplier = new BigDecimal("2.0");

    @Value("${monitoring.strategy.delphi.trailing.activation-atr-multiple:4.0}")
    private BigDecimal trailingActivationAtrMultiple = new BigDecimal("4.0");

    @Value("${monitoring.strategy.delphi.trailing.distance-atr-multiple:1.5}")
    private BigDecimal trailingDistanceAtrMultiple = new BigDecimal("1.5");

    @Value("${backtest.export.initial-capital:10000}")
    private BigDecimal initialCapital;

    @Value("${backtest.history.remote-fetch-limit:${kline.sync.fetch-limit:1000}}")
    private int remoteFetchLimit;

    private final BinanceApi binanceApi;
    private final Delphi2SignalEvaluator signalEvaluator;
    private final AccessKlineBarHistoryService klineHistoryService;

    @Autowired
    public Delphi2BacktestService(BinanceApi binanceApi,
                                  Delphi2SignalEvaluator signalEvaluator,
                                  ObjectProvider<AccessKlineBarHistoryService> klineHistoryServiceProvider) {
        this.binanceApi = binanceApi;
        this.signalEvaluator = signalEvaluator;
        this.klineHistoryService = klineHistoryServiceProvider.getIfAvailable();
    }

    Delphi2BacktestService(BinanceApi binanceApi,
                           Delphi2SignalEvaluator signalEvaluator) {
        this.binanceApi = binanceApi;
        this.signalEvaluator = signalEvaluator;
        this.klineHistoryService = null;
    }

    @Override
    public BatchBacktestResult runDefaultBacktestBatch() {
        BacktestConfig config = compatibilityConfig();
        long entryWarmupStart = Math.max(
                0L,
                config.startTime() - (long) (entryAtrPeriod + entryBreakoutLookback + 4) * Delphi2Support.resolveIntervalMs(entryInterval)
        );
        long trendWarmupStart = Math.max(
                0L,
                config.startTime() - (long) (trendSlowPeriod + 4) * Delphi2Support.resolveIntervalMs(trendInterval)
        );
        List<BinanceKlineDTO> entryHistory = loadHistoricalKlines(config.symbol(), entryInterval, entryWarmupStart, config.endTime());
        List<BinanceKlineDTO> trendHistory = loadHistoricalKlines(config.symbol(), trendInterval, trendWarmupStart, config.endTime());
        BacktestReport report = runBacktest(entryHistory, trendHistory, config);
        return new BatchBacktestResult(report.barCount(), report, report, List.of());
    }

    @Override
    public String formatBatchResult(BatchBacktestResult result) {
        if (result == null) {
            return "No backtest result.";
        }
        CapitalSummary summary = simulateCapital(result.baseline());
        return "Delphi II Aggressive"
                + " | interval=" + result.baseline().config().interval()
                + " range=" + Instant.ofEpochMilli(result.baseline().config().startTime())
                + " -> " + Instant.ofEpochMilli(result.baseline().config().endTime())
                + " trades=" + result.baseline().tradeCount()
                + " winRate=" + scalePct(result.baseline().winRate())
                + " netProfit=" + scaleMoney(summary.netProfit()) + " USDT"
                + " return=" + scaleMoney(summary.totalReturnPct()) + "%"
                + " finalEquity=" + scaleMoney(summary.finalEquity()) + " USDT"
                + " maxDrawdown=" + scaleMoney(summary.maxDrawdownAmount()) + " USDT"
                + " (" + scaleMoney(summary.maxDrawdownPct()) + "%)"
                + " profitFactor=" + scale(result.baseline().profitFactor())
                + " totalR=" + scale(result.baseline().totalR())
                + " signalMix=" + result.baseline().signalCounts();
    }

    BacktestReport runBacktest(List<BinanceKlineDTO> entryHistory,
                               List<BinanceKlineDTO> trendHistory,
                               BacktestConfig config) {
        Map<String, Integer> signalCounts = new LinkedHashMap<>();
        List<TradeRecord> trades = new ArrayList<>();
        if (CollectionUtils.isEmpty(entryHistory) || CollectionUtils.isEmpty(trendHistory)) {
            return emptyReport(config);
        }

        PendingEntry pendingEntry = null;
        PendingExit pendingExit = null;
        OpenPosition openPosition = null;
        int trendCursor = 0;

        for (int i = 0; i < entryHistory.size(); i++) {
            BinanceKlineDTO currentBar = entryHistory.get(i);
            while (trendCursor < trendHistory.size()
                    && trendHistory.get(trendCursor).getEndTime() <= currentBar.getEndTime()) {
                trendCursor++;
            }

            if (pendingExit != null && openPosition != null) {
                trades.add(openPosition.trade().close(
                        currentBar.getStartTime(),
                        Delphi2Support.valueOf(currentBar.getOpen()),
                        pendingExit.exitReason()
                ));
                openPosition = null;
                pendingExit = null;
            }

            if (pendingEntry != null && openPosition == null) {
                BigDecimal entryPrice = Delphi2Support.valueOf(currentBar.getOpen());
                BigDecimal stopPrice = pendingEntry.direction() == TradeDirection.LONG
                        ? entryPrice.subtract(pendingEntry.signalAtr().multiply(stopLossAtrMultiplier))
                        : entryPrice.add(pendingEntry.signalAtr().multiply(stopLossAtrMultiplier));
                BigDecimal riskPerUnit = entryPrice.subtract(stopPrice).abs();
                if (riskPerUnit.compareTo(ZERO) > 0) {
                    openPosition = new OpenPosition(new TradeRecord(
                            pendingEntry.signalType(),
                            pendingEntry.direction(),
                            pendingEntry.signalTime(),
                            currentBar.getStartTime(),
                            i,
                            entryPrice,
                            stopPrice,
                            null,
                            riskPerUnit,
                            0
                    ), pendingEntry.signalAtr());
                }
                pendingEntry = null;
            }

            if (currentBar.getEndTime() < config.startTime()) {
                continue;
            }
            if (currentBar.getEndTime() > config.endTime()) {
                break;
            }

            List<BinanceKlineDTO> visibleEntry = entryHistory.subList(0, i + 1);
            List<BinanceKlineDTO> visibleTrend = trendHistory.subList(0, trendCursor);

            if (openPosition != null) {
                BigDecimal open = Delphi2Support.valueOf(currentBar.getOpen());
                BigDecimal high = Delphi2Support.valueOf(currentBar.getHigh());
                BigDecimal low = Delphi2Support.valueOf(currentBar.getLow());
                if (openPosition.direction() == TradeDirection.LONG) {
                    if (open.compareTo(openPosition.stopPrice()) <= 0) {
                        trades.add(openPosition.trade().close(currentBar.getStartTime(), open, "GAP_STOP"));
                        openPosition = null;
                        pendingExit = null;
                        continue;
                    }
                    if (low.compareTo(openPosition.stopPrice()) <= 0) {
                        trades.add(openPosition.trade().close(
                                currentBar.getEndTime(),
                                openPosition.stopPrice(),
                                openPosition.trailingActive() ? "TRAILING_STOP" : "STOP_LOSS"
                        ));
                        openPosition = null;
                        pendingExit = null;
                        continue;
                    }
                } else {
                    if (open.compareTo(openPosition.stopPrice()) >= 0) {
                        trades.add(openPosition.trade().close(currentBar.getStartTime(), open, "GAP_STOP"));
                        openPosition = null;
                        pendingExit = null;
                        continue;
                    }
                    if (high.compareTo(openPosition.stopPrice()) >= 0) {
                        trades.add(openPosition.trade().close(
                                currentBar.getEndTime(),
                                openPosition.stopPrice(),
                                openPosition.trailingActive() ? "TRAILING_STOP" : "STOP_LOSS"
                        ));
                        openPosition = null;
                        pendingExit = null;
                        continue;
                    }
                }

                BigDecimal currentAtr = Delphi2Support.atr(visibleEntry, entryAtrPeriod, 0);
                openPosition.updateAfterClosedBar(currentBar, currentAtr, trailingActivationAtrMultiple, trailingDistanceAtrMultiple);
                if (signalEvaluator.hasTrendReversed(visibleTrend, openPosition.direction())) {
                    String exitReason = openPosition.direction() == TradeDirection.LONG ? "DAILY_REVERSAL_LONG" : "DAILY_REVERSAL_SHORT";
                    if (i + 1 < entryHistory.size()) {
                        pendingExit = new PendingExit(exitReason);
                    } else {
                        trades.add(openPosition.trade().close(
                                currentBar.getEndTime(),
                                Delphi2Support.valueOf(currentBar.getClose()),
                                exitReason
                        ));
                        openPosition = null;
                    }
                }
                continue;
            }

            if (i + 1 >= entryHistory.size()) {
                continue;
            }
            Optional<AlertSignal> signal = signalEvaluator.evaluateEntry(visibleEntry, visibleTrend);
            if (signal.isPresent()) {
                signalCounts.merge(signal.get().getType(), 1, Integer::sum);
                BigDecimal signalAtr = Delphi2Support.atr(visibleEntry, entryAtrPeriod, 0);
                if (signalAtr != null && signalAtr.compareTo(ZERO) > 0) {
                    pendingEntry = new PendingEntry(
                            signal.get().getType(),
                            signal.get().getDirection(),
                            signal.get().getKline().getEndTime(),
                            signalAtr
                    );
                }
            }
        }

        if (openPosition != null) {
            BinanceKlineDTO lastBar = entryHistory.get(entryHistory.size() - 1);
            trades.add(openPosition.trade().forceClose(
                    lastBar.getEndTime(),
                    Delphi2Support.valueOf(lastBar.getClose()),
                    "FORCED_END"
            ));
        }
        return summarize(entryHistory.size(), trades, signalCounts, config);
    }

    static long resolveIntervalMs(String interval) {
        return Delphi2Support.resolveIntervalMs(interval);
    }

    private List<BinanceKlineDTO> loadHistoricalKlines(String symbol, String interval, long startTime, long endTime) {
        if (klineHistoryService != null) {
            try {
                List<BinanceKlineDTO> history = klineHistoryService.loadClosedKlines(symbol, interval, startTime, endTime);
                if (!CollectionUtils.isEmpty(history)) {
                    log.info("Loaded Delphi II backtest history from access_kline_bar, symbol={}, interval={}, bars={}",
                            symbol,
                            interval,
                            history.size());
                    return history;
                }
                log.warn("No Delphi II backtest klines found in access_kline_bar, falling back to Binance REST, symbol={}, interval={}, startTime={}, endTime={}",
                        symbol,
                        interval,
                        startTime,
                        endTime);
            } catch (Exception ex) {
                log.warn("Failed to load Delphi II backtest history from access_kline_bar, falling back to Binance REST, symbol={}, interval={}, startTime={}, endTime={}",
                        symbol,
                        interval,
                        startTime,
                        endTime,
                        ex);
            }
        }
        if (binanceApi == null) {
            throw new IllegalStateException("Backtest kline history service is unavailable and BinanceApi fallback cannot be queried.");
        }
        long intervalMs = Delphi2Support.resolveIntervalMs(interval);
        long effectiveEndTime = Math.min(endTime, System.currentTimeMillis());
        int fetchLimit = Math.max(1, Math.min(BINANCE_MAX_FETCH_LIMIT, remoteFetchLimit));
        List<BinanceKlineDTO> history = new ArrayList<>();
        long nextStartTime = startTime;
        while (nextStartTime < effectiveEndTime) {
            BinanceKlineDTO request = new BinanceKlineDTO();
            request.setSymbol(symbol);
            request.setInterval(interval);
            request.setLimit(fetchLimit);
            request.setStartTime(nextStartTime);
            request.setEndTime(effectiveEndTime);

            List<BinanceKlineDTO> page = binanceApi.listKline(request);
            if (CollectionUtils.isEmpty(page)) {
                break;
            }
            for (BinanceKlineDTO bar : page) {
                if (bar.getStartTime() == null || bar.getEndTime() == null || bar.getEndTime() > effectiveEndTime) {
                    continue;
                }
                if (!history.isEmpty() && bar.getStartTime() <= history.get(history.size() - 1).getStartTime()) {
                    continue;
                }
                history.add(bar);
            }
            BinanceKlineDTO lastBar = page.get(page.size() - 1);
            if (lastBar.getStartTime() == null) {
                break;
            }
            long candidateNextStartTime = lastBar.getStartTime() + intervalMs;
            if (candidateNextStartTime <= nextStartTime) {
                break;
            }
            nextStartTime = candidateNextStartTime;
            if (page.size() < fetchLimit) {
                break;
            }
        }
        if (CollectionUtils.isEmpty(history)) {
            throw new IllegalStateException("No Delphi II backtest klines found from access_kline_bar or Binance REST for symbol="
                    + symbol + ", interval=" + interval + ", startTime=" + startTime + ", endTime=" + endTime);
        }
        log.info("Loaded Delphi II backtest history from Binance REST, symbol={}, interval={}, bars={}",
                symbol,
                interval,
                history.size());
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
                trendFastPeriod,
                trendSlowPeriod,
                entryBreakoutLookback,
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
                null
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

    private String scale(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String scaleMoney(BigDecimal value) {
        return scale(value);
    }

    private String scalePct(BigDecimal ratio) {
        return ratio == null ? "-" : ratio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private record PendingEntry(String signalType,
                                TradeDirection direction,
                                long signalTime,
                                BigDecimal signalAtr) {
    }

    private record PendingExit(String exitReason) {
    }

    private static final class OpenPosition {
        private final TradeRecord trade;
        private final BigDecimal entryAtr;
        private BigDecimal stopPrice;
        private BigDecimal highestHigh;
        private BigDecimal lowestLow;
        private boolean trailingActive;

        private OpenPosition(TradeRecord trade, BigDecimal entryAtr) {
            this.trade = trade;
            this.entryAtr = entryAtr;
            this.stopPrice = trade.stopPrice();
            this.highestHigh = trade.entryPrice();
            this.lowestLow = trade.entryPrice();
            this.trailingActive = false;
        }

        private void updateAfterClosedBar(BinanceKlineDTO closedBar,
                                          BigDecimal currentAtr,
                                          BigDecimal trailingActivationAtrMultiple,
                                          BigDecimal trailingDistanceAtrMultiple) {
            BigDecimal high = Delphi2Support.valueOf(closedBar.getHigh());
            BigDecimal low = Delphi2Support.valueOf(closedBar.getLow());
            if (high.compareTo(highestHigh) > 0) {
                highestHigh = high;
            }
            if (low.compareTo(lowestLow) < 0) {
                lowestLow = low;
            }
            BigDecimal favorableMove = direction() == TradeDirection.LONG
                    ? highestHigh.subtract(trade.entryPrice())
                    : trade.entryPrice().subtract(lowestLow);
            if (!trailingActive
                    && entryAtr != null
                    && favorableMove.compareTo(entryAtr.multiply(trailingActivationAtrMultiple)) >= 0) {
                trailingActive = true;
            }
            if (!trailingActive || currentAtr == null || currentAtr.compareTo(ZERO) <= 0) {
                return;
            }
            BigDecimal candidate = direction() == TradeDirection.LONG
                    ? highestHigh.subtract(currentAtr.multiply(trailingDistanceAtrMultiple))
                    : lowestLow.add(currentAtr.multiply(trailingDistanceAtrMultiple));
            if (direction() == TradeDirection.LONG && candidate.compareTo(stopPrice) > 0) {
                stopPrice = candidate;
            }
            if (direction() == TradeDirection.SHORT && candidate.compareTo(stopPrice) < 0) {
                stopPrice = candidate;
            }
        }

        private TradeRecord trade() {
            return trade;
        }

        private TradeDirection direction() {
            return trade.direction();
        }

        private BigDecimal stopPrice() {
            return stopPrice;
        }

        private boolean trailingActive() {
            return trailingActive;
        }
    }

    private record CapitalSummary(BigDecimal finalEquity,
                                  BigDecimal netProfit,
                                  BigDecimal totalReturnPct,
                                  BigDecimal maxDrawdownAmount,
                                  BigDecimal maxDrawdownPct) {
    }
}
