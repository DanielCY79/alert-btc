package com.mobai.alert.strategy.pullback;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.shared.StrategySettings;
import com.mobai.alert.strategy.shared.StrategySupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * H1/H2 与 L1/L2 顺势二次入场识别器。
 * 在已有趋势或突破背景里，识别回调/反抽后的第一次与第二次顺势触发。
 */
public class SecondEntryStrategyEvaluator {

    private static final String H1_LONG_TYPE = "SECOND_ENTRY_H1_LONG";
    private static final String H2_LONG_TYPE = "SECOND_ENTRY_H2_LONG";
    private static final String L1_SHORT_TYPE = "SECOND_ENTRY_L1_SHORT";
    private static final String L2_SHORT_TYPE = "SECOND_ENTRY_L2_SHORT";

    public Optional<AlertSignal> evaluateLongSecondEntry(List<BinanceKlineDTO> klines,
                                                         BigDecimal referenceLevel,
                                                         BigDecimal targetPrice,
                                                         StrategySettings settings) {
        return evaluateDirectionalSecondEntry(klines, referenceLevel, targetPrice, true, settings);
    }

    public Optional<AlertSignal> evaluateShortSecondEntry(List<BinanceKlineDTO> klines,
                                                          BigDecimal referenceLevel,
                                                          BigDecimal targetPrice,
                                                          StrategySettings settings) {
        return evaluateDirectionalSecondEntry(klines, referenceLevel, targetPrice, false, settings);
    }

    private Optional<AlertSignal> evaluateDirectionalSecondEntry(List<BinanceKlineDTO> klines,
                                                                 BigDecimal referenceLevel,
                                                                 BigDecimal targetPrice,
                                                                 boolean bullishTrend,
                                                                 StrategySettings settings) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        if (!StrategySupport.hasEnoughBars(closedKlines, StrategySupport.minimumBarsRequired(settings))) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = StrategySupport.last(closedKlines);
        if (!isSignalBarQualified(latest, bullishTrend, settings)) {
            return Optional.empty();
        }

        BigDecimal fastMa = StrategySupport.movingAverage(closedKlines, settings.fastPeriod(), 0);
        BigDecimal slowMa = StrategySupport.movingAverage(closedKlines, settings.slowPeriod(), 0);
        BigDecimal latestClose = StrategySupport.valueOf(latest.getClose());
        if (!isTrendAligned(latestClose, fastMa, slowMa, bullishTrend)) {
            return Optional.empty();
        }

        int windowSize = Math.min(settings.secondEntryLookback(), closedKlines.size());
        List<BinanceKlineDTO> window = StrategySupport.trailingWindow(closedKlines, windowSize, 0);
        int latestIndex = window.size() - 1;
        if (latestIndex < settings.secondEntryMinPullbackBars()) {
            return Optional.empty();
        }

        int pivotIndex = bullishTrend
                ? mostRecentHighestHighIndex(window, latestIndex - 1)
                : mostRecentLowestLowIndex(window, latestIndex - 1);
        if (pivotIndex < 0 || latestIndex - pivotIndex < settings.secondEntryMinPullbackBars()) {
            return Optional.empty();
        }

        List<BinanceKlineDTO> pullback = window.subList(pivotIndex + 1, latestIndex + 1);
        if (pullback.size() < settings.secondEntryMinPullbackBars()) {
            return Optional.empty();
        }
        if (!hasCounterTrendPressure(pullback, bullishTrend)) {
            return Optional.empty();
        }
        if (!holdsReferenceLevel(pullback, referenceLevel, bullishTrend, settings)) {
            return Optional.empty();
        }

        List<Integer> attemptIndices = attemptIndices(window, pivotIndex + 1, latestIndex, bullishTrend);
        if (attemptIndices.isEmpty() || attemptIndices.get(attemptIndices.size() - 1) != latestIndex) {
            return Optional.empty();
        }

        EntryLabel entryLabel = entryLabel(window, attemptIndices, bullishTrend);
        if (entryLabel == null) {
            return Optional.empty();
        }

        BigDecimal signalHigh = StrategySupport.valueOf(latest.getHigh());
        BigDecimal signalLow = StrategySupport.valueOf(latest.getLow());
        BigDecimal triggerPrice = bullishTrend ? signalHigh : signalLow;
        BigDecimal invalidationPrice = bullishTrend
                ? StrategySupport.lowestLow(pullback).multiply(StrategySupport.ONE.subtract(settings.secondEntryInvalidationBuffer()))
                : StrategySupport.highestHigh(pullback).multiply(StrategySupport.ONE.add(settings.secondEntryInvalidationBuffer()));
        BigDecimal effectiveTarget = targetPrice != null ? targetPrice : measuredMoveTarget(window, pivotIndex, pullback, bullishTrend);

        BigDecimal averageVolume = StrategySupport.averageVolume(
                StrategySupport.trailingWindow(closedKlines, Math.min(10, settings.rangeLookback()), 1)
        );
        BigDecimal volumeRatio = StrategySupport.ratio(StrategySupport.volumeOf(latest), averageVolume);

        String summary = bullishTrend
                ? entryLabel.label() + " 出现在回调后的顺势重启位，信号 K 线重新向上触发，量能约为 "
                + volumeRatio.setScale(2, RoundingMode.HALF_UP) + "x。"
                : entryLabel.label() + " 出现在反抽后的顺势重启位，信号 K 线重新向下触发，量能约为 "
                + volumeRatio.setScale(2, RoundingMode.HALF_UP) + "x。";
        return Optional.of(new AlertSignal(
                bullishTrend ? TradeDirection.LONG : TradeDirection.SHORT,
                bullishTrend ? "BTC " + entryLabel.label() + " 顺势做多信号" : "BTC " + entryLabel.label() + " 顺势做空信号",
                latest,
                entryLabel.type(),
                summary,
                triggerPrice.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                StrategySupport.scaleOrNull(effectiveTarget),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    private boolean isSignalBarQualified(BinanceKlineDTO latest,
                                         boolean bullishTrend,
                                         StrategySettings settings) {
        if (StrategySupport.bodyRatio(latest).compareTo(settings.secondEntryMinBodyRatio()) < 0) {
            return false;
        }
        if (bullishTrend) {
            return StrategySupport.isBullish(latest)
                    && StrategySupport.closeLocation(latest).compareTo(settings.secondEntryMinCloseLocation()) >= 0;
        }
        BigDecimal ceiling = StrategySupport.ONE.subtract(settings.secondEntryMinCloseLocation());
        return StrategySupport.isBearish(latest)
                && StrategySupport.closeLocation(latest).compareTo(ceiling) <= 0;
    }

    private boolean isTrendAligned(BigDecimal latestClose,
                                   BigDecimal fastMa,
                                   BigDecimal slowMa,
                                   boolean bullishTrend) {
        if (bullishTrend) {
            return fastMa.compareTo(slowMa) >= 0 && latestClose.compareTo(fastMa) >= 0;
        }
        return fastMa.compareTo(slowMa) <= 0 && latestClose.compareTo(fastMa) <= 0;
    }

    private boolean holdsReferenceLevel(List<BinanceKlineDTO> pullback,
                                        BigDecimal referenceLevel,
                                        boolean bullishTrend,
                                        StrategySettings settings) {
        if (referenceLevel == null) {
            return true;
        }
        return bullishTrend
                ? StrategySupport.lowestLow(pullback)
                .compareTo(referenceLevel.multiply(StrategySupport.ONE.subtract(settings.pullbackHoldBuffer()))) >= 0
                : StrategySupport.highestHigh(pullback)
                .compareTo(referenceLevel.multiply(StrategySupport.ONE.add(settings.pullbackHoldBuffer()))) <= 0;
    }

    private boolean hasCounterTrendPressure(List<BinanceKlineDTO> pullback, boolean bullishTrend) {
        for (int i = 0; i < pullback.size() - 1; i++) {
            BinanceKlineDTO bar = pullback.get(i);
            if (bullishTrend && StrategySupport.isBearish(bar)) {
                return true;
            }
            if (!bullishTrend && StrategySupport.isBullish(bar)) {
                return true;
            }
            if (i == 0) {
                continue;
            }
            BinanceKlineDTO previous = pullback.get(i - 1);
            BigDecimal currentLow = StrategySupport.valueOf(bar.getLow());
            BigDecimal previousLow = StrategySupport.valueOf(previous.getLow());
            BigDecimal currentHigh = StrategySupport.valueOf(bar.getHigh());
            BigDecimal previousHigh = StrategySupport.valueOf(previous.getHigh());
            if (bullishTrend && currentLow.compareTo(previousLow) < 0) {
                return true;
            }
            if (!bullishTrend && currentHigh.compareTo(previousHigh) > 0) {
                return true;
            }
        }
        return false;
    }

    private List<Integer> attemptIndices(List<BinanceKlineDTO> window,
                                         int fromIndex,
                                         int latestIndex,
                                         boolean bullishTrend) {
        List<Integer> attempts = new ArrayList<>();
        for (int i = fromIndex; i <= latestIndex; i++) {
            BinanceKlineDTO current = window.get(i);
            BinanceKlineDTO previous = window.get(i - 1);
            BigDecimal currentExtreme = bullishTrend
                    ? StrategySupport.valueOf(current.getHigh())
                    : StrategySupport.valueOf(current.getLow());
            BigDecimal previousExtreme = bullishTrend
                    ? StrategySupport.valueOf(previous.getHigh())
                    : StrategySupport.valueOf(previous.getLow());
            boolean triggered = bullishTrend
                    ? currentExtreme.compareTo(previousExtreme) > 0
                    : currentExtreme.compareTo(previousExtreme) < 0;
            if (triggered) {
                attempts.add(i);
            }
        }
        return attempts;
    }

    private EntryLabel entryLabel(List<BinanceKlineDTO> window,
                                  List<Integer> attemptIndices,
                                  boolean bullishTrend) {
        if (attemptIndices.size() == 1) {
            return bullishTrend
                    ? new EntryLabel("H1", H1_LONG_TYPE)
                    : new EntryLabel("L1", L1_SHORT_TYPE);
        }
        if (attemptIndices.size() != 2) {
            return null;
        }

        int firstAttemptIndex = attemptIndices.get(0);
        int latestIndex = attemptIndices.get(1);
        if (latestIndex - firstAttemptIndex < 2) {
            return null;
        }

        List<BinanceKlineDTO> betweenAttempts = window.subList(firstAttemptIndex + 1, latestIndex);
        BinanceKlineDTO firstAttempt = window.get(firstAttemptIndex);
        boolean failedFirstAttempt = bullishTrend
                ? StrategySupport.lowestLow(betweenAttempts).compareTo(StrategySupport.valueOf(firstAttempt.getLow())) < 0
                : StrategySupport.highestHigh(betweenAttempts).compareTo(StrategySupport.valueOf(firstAttempt.getHigh())) > 0;
        if (!failedFirstAttempt) {
            return null;
        }
        return bullishTrend
                ? new EntryLabel("H2", H2_LONG_TYPE)
                : new EntryLabel("L2", L2_SHORT_TYPE);
    }

    private BigDecimal measuredMoveTarget(List<BinanceKlineDTO> window,
                                          int pivotIndex,
                                          List<BinanceKlineDTO> pullback,
                                          boolean bullishTrend) {
        if (bullishTrend) {
            BigDecimal pivotHigh = StrategySupport.valueOf(window.get(pivotIndex).getHigh());
            BigDecimal pullbackLow = StrategySupport.lowestLow(pullback);
            return pivotHigh.add(pivotHigh.subtract(pullbackLow));
        }
        BigDecimal pivotLow = StrategySupport.valueOf(window.get(pivotIndex).getLow());
        BigDecimal pullbackHigh = StrategySupport.highestHigh(pullback);
        return pivotLow.subtract(pullbackHigh.subtract(pivotLow));
    }

    private int mostRecentHighestHighIndex(List<BinanceKlineDTO> window, int lastIndex) {
        BigDecimal highest = null;
        int index = -1;
        for (int i = 0; i <= lastIndex; i++) {
            BigDecimal candidate = StrategySupport.valueOf(window.get(i).getHigh());
            if (highest == null || candidate.compareTo(highest) >= 0) {
                highest = candidate;
                index = i;
            }
        }
        return index;
    }

    private int mostRecentLowestLowIndex(List<BinanceKlineDTO> window, int lastIndex) {
        BigDecimal lowest = null;
        int index = -1;
        for (int i = 0; i <= lastIndex; i++) {
            BigDecimal candidate = StrategySupport.valueOf(window.get(i).getLow());
            if (lowest == null || candidate.compareTo(lowest) <= 0) {
                lowest = candidate;
                index = i;
            }
        }
        return index;
    }

    private record EntryLabel(String label, String type) {
    }
}
