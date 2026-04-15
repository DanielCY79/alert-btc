package com.mobai.alert.strategy.priceaction;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import com.mobai.alert.strategy.priceaction.shared.StrategySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Price action 策略统一入口。
 *
 * 当前版本不再沿用“确认突破 / 回踩 / 二次入场”的旧分类，
 * 而是直接改成更贴近实盘交易习惯的两层：
 * 1. PROBE_TREND: 小仓试方向，要求扩张 K 线直接打出结构。
 * 2. PROFIT_TREND: 趋势确认后的回踩续涨/续跌，负责拿大段利润。
 */
@Service
@ConditionalOnProperty(value = "monitoring.strategy.type", havingValue = "priceaction", matchIfMissing = true)
public class PriceActionSignalEvaluator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Value("${monitoring.strategy.trend.fast-period:20}")
    private int fastPeriod;

    @Value("${monitoring.strategy.trend.slow-period:60}")
    private int slowPeriod;

    @Value("${monitoring.strategy.trend.bias-buffer:0.002}")
    private BigDecimal trendBiasBuffer = new BigDecimal("0.002");

    @Value("${monitoring.strategy.range.lookback:48}")
    private int rangeLookback;

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

    @Value("${monitoring.strategy.breakout.follow-through.min-close-location:0.55}")
    private BigDecimal breakoutFollowThroughMinCloseLocation;

    @Value("${monitoring.strategy.probe.stop-lookback:4}")
    private int probeStopLookback = 4;

    @Value("${monitoring.strategy.pullback.touch-tolerance:0.008}")
    private BigDecimal pullbackTouchTolerance;

    @Value("${monitoring.strategy.pullback.hold-buffer:0.006}")
    private BigDecimal pullbackHoldBuffer;

    @Value("${monitoring.strategy.pullback.max-volume-ratio:1.10}")
    private BigDecimal pullbackMaxVolumeRatio;

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

    @Value("${monitoring.strategy.profit.pullback-lookback:4}")
    private int profitPullbackLookback = 4;

    @Value("${monitoring.strategy.profit.min-pullback-bars:1}")
    private int profitMinPullbackBars = 1;

    @Value("${monitoring.strategy.profit.min-retrace:0.18}")
    private BigDecimal profitMinRetrace = new BigDecimal("0.18");

    @Value("${monitoring.strategy.profit.max-retrace:0.55}")
    private BigDecimal profitMaxRetrace = new BigDecimal("0.55");

    @Value("${monitoring.strategy.profit.min-volume-ratio:0.90}")
    private BigDecimal profitMinVolumeRatio = new BigDecimal("0.90");

    @Value("${monitoring.strategy.profit.reclaim-buffer:0.001}")
    private BigDecimal profitReclaimBuffer = new BigDecimal("0.001");

    @Value("${monitoring.strategy.profit.min-body-ratio:0.12}")
    private BigDecimal profitMinBodyRatio = new BigDecimal("0.12");

    @Value("${monitoring.strategy.profit.min-close-location:0.50}")
    private BigDecimal profitMinCloseLocation = new BigDecimal("0.50");

    @Value("${monitoring.strategy.profit.max-extension:0.08}")
    private BigDecimal profitMaxExtension = new BigDecimal("0.08");

    @Value("${monitoring.strategy.profit.pullback-max-volume-ratio:1.35}")
    private BigDecimal profitPullbackMaxVolumeRatio = new BigDecimal("1.35");

    @Value("${monitoring.strategy.profit.ma-hold-buffer:0.015}")
    private BigDecimal profitMaHoldBuffer = new BigDecimal("0.015");

    @Value("${monitoring.strategy.profit.touch-tolerance:0.002}")
    private BigDecimal profitTouchTolerance = new BigDecimal("0.002");

    public Optional<AlertSignal> evaluateProbeLong(List<BinanceKlineDTO> klines) {
        return evaluateProbe(klines, TradeDirection.LONG);
    }

    public Optional<AlertSignal> evaluateProbeShort(List<BinanceKlineDTO> klines) {
        return evaluateProbe(klines, TradeDirection.SHORT);
    }

    public Optional<AlertSignal> evaluateProfitLong(List<BinanceKlineDTO> klines) {
        return evaluateProfit(klines, TradeDirection.LONG);
    }

    public Optional<AlertSignal> evaluateProfitShort(List<BinanceKlineDTO> klines) {
        return evaluateProfit(klines, TradeDirection.SHORT);
    }

    /**
     * 旧接口保留空壳，避免策略外层编译受影响。
     */
    public Optional<AlertSignal> evaluateRangeFailedBreakdownLong(List<BinanceKlineDTO> klines) {
        return Optional.empty();
    }

    public Optional<AlertSignal> evaluateRangeFailedBreakoutShort(List<BinanceKlineDTO> klines) {
        return Optional.empty();
    }

    public Optional<AlertSignal> evaluateTrendBreakout(List<BinanceKlineDTO> klines) {
        return evaluateProbeLong(klines);
    }

    public Optional<AlertSignal> evaluateTrendBreakdown(List<BinanceKlineDTO> klines) {
        return evaluateProbeShort(klines);
    }

    public Optional<AlertSignal> evaluateBreakoutFollowThrough(List<BinanceKlineDTO> klines,
                                                               BigDecimal breakoutLevel,
                                                               BigDecimal invalidationPrice,
                                                               BigDecimal targetPrice,
                                                               boolean bullishBreakout) {
        return Optional.empty();
    }

    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel) {
        return evaluateProfitLong(klines);
    }

    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel,
                                                          boolean bullishBreakout) {
        return bullishBreakout ? evaluateProfitLong(klines) : evaluateProfitShort(klines);
    }

    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel,
                                                          BigDecimal targetPrice,
                                                          boolean bullishBreakout) {
        return bullishBreakout ? evaluateProfitLong(klines) : evaluateProfitShort(klines);
    }

    public Optional<AlertSignal> evaluateSecondEntryLong(List<BinanceKlineDTO> klines,
                                                         BigDecimal referenceLevel,
                                                         BigDecimal targetPrice) {
        return evaluateProfitLong(klines);
    }

    public Optional<AlertSignal> evaluateSecondEntryShort(List<BinanceKlineDTO> klines,
                                                          BigDecimal referenceLevel,
                                                          BigDecimal targetPrice) {
        return evaluateProfitShort(klines);
    }

    private Optional<AlertSignal> evaluateProbe(List<BinanceKlineDTO> klines, TradeDirection direction) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        int minimumBars = Math.max(slowPeriod + 6, rangeLookback + probeStopLookback + 6);
        if (!StrategySupport.hasEnoughBars(closedKlines, minimumBars)) {
            return Optional.empty();
        }

        BinanceKlineDTO signalBar = StrategySupport.last(closedKlines);
        List<BinanceKlineDTO> structureWindow = StrategySupport.trailingWindow(closedKlines, rangeLookback, 1);
        if (CollectionUtils.isEmpty(structureWindow)) {
            return Optional.empty();
        }

        BigDecimal latestClose = StrategySupport.valueOf(signalBar.getClose());
        BigDecimal fastMa = StrategySupport.movingAverage(closedKlines, fastPeriod, 0);
        BigDecimal slowMa = StrategySupport.movingAverage(closedKlines, slowPeriod, 0);
        BigDecimal previousFastMa = StrategySupport.movingAverage(closedKlines, fastPeriod, 1);
        BigDecimal previousSlowMa = StrategySupport.movingAverage(closedKlines, slowPeriod, 1);
        if (!isTrendAligned(direction, latestClose, fastMa, slowMa, previousFastMa, previousSlowMa)) {
            return Optional.empty();
        }

        BigDecimal structureHigh = StrategySupport.highestHigh(structureWindow);
        BigDecimal structureLow = StrategySupport.lowestLow(structureWindow);
        BigDecimal structureRange = structureHigh.subtract(structureLow);
        if (structureRange.compareTo(ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal bodyRatio = StrategySupport.bodyRatio(signalBar);
        BigDecimal closeLocation = StrategySupport.closeLocation(signalBar);
        BigDecimal volumeRatio = latestVolumeRatio(closedKlines, signalBar);
        if (volumeRatio == null) {
            return Optional.empty();
        }

        if (direction == TradeDirection.LONG) {
            BigDecimal triggerFloor = structureHigh.multiply(ONE.add(breakoutCloseBuffer));
            BigDecimal extensionRatio = ratio(latestClose.subtract(structureHigh), structureHigh);
            if (!StrategySupport.isBullish(signalBar)
                    || latestClose.compareTo(triggerFloor) <= 0
                    || bodyRatio.compareTo(breakoutBodyRatioThreshold) < 0
                    || closeLocation.compareTo(breakoutFollowThroughMinCloseLocation) < 0
                    || volumeRatio.compareTo(breakoutVolumeMultiplier) < 0
                    || extensionRatio.compareTo(breakoutMaxExtension) > 0) {
                return Optional.empty();
            }
            List<BinanceKlineDTO> stopWindow = StrategySupport.trailingWindow(closedKlines, probeStopLookback, 0);
            BigDecimal stopPrice = StrategySupport.lowestLow(stopWindow).multiply(ONE.subtract(breakoutFailureBuffer));
            if (stopPrice.compareTo(latestClose) >= 0) {
                return Optional.empty();
            }
            BigDecimal targetPrice = latestClose.add(structureRange);
            return Optional.of(buildSignal(
                    TradeDirection.LONG,
                    "趋势试错做多",
                    signalBar,
                    "PROBE_TREND_LONG",
                    "最新一根 K 线放量突破最近结构高点，先用试错层跟随方向，若后续给出更顺畅的续涨结构，再考虑切到利润层。",
                    latestClose,
                    stopPrice,
                    targetPrice,
                    volumeRatio
            ));
        }

        BigDecimal triggerCeiling = structureLow.multiply(ONE.subtract(breakoutCloseBuffer));
        BigDecimal extensionRatio = ratio(structureLow.subtract(latestClose), structureLow);
        if (!StrategySupport.isBearish(signalBar)
                || latestClose.compareTo(triggerCeiling) >= 0
                || bodyRatio.compareTo(breakoutBodyRatioThreshold) < 0
                || closeLocation.compareTo(ONE.subtract(breakoutFollowThroughMinCloseLocation)) > 0
                || volumeRatio.compareTo(breakoutVolumeMultiplier) < 0
                || extensionRatio.compareTo(breakoutMaxExtension) > 0) {
            return Optional.empty();
        }
        List<BinanceKlineDTO> stopWindow = StrategySupport.trailingWindow(closedKlines, probeStopLookback, 0);
        BigDecimal stopPrice = StrategySupport.highestHigh(stopWindow).multiply(ONE.add(breakoutFailureBuffer));
        if (stopPrice.compareTo(latestClose) <= 0) {
            return Optional.empty();
        }
        BigDecimal targetPrice = latestClose.subtract(structureRange);
        return Optional.of(buildSignal(
                TradeDirection.SHORT,
                "趋势试错做空",
                signalBar,
                "PROBE_TREND_SHORT",
                "最新一根 K 线放量跌破最近结构低点，先用试错层跟随方向，若后续给出更顺畅的续跌结构，再考虑切到利润层。",
                latestClose,
                stopPrice,
                targetPrice,
                volumeRatio
        ));
    }

    private Optional<AlertSignal> evaluateProfit(List<BinanceKlineDTO> klines, TradeDirection direction) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        int minimumBars = Math.max(slowPeriod + 6, secondEntryLookback + profitPullbackLookback + 8);
        if (!StrategySupport.hasEnoughBars(closedKlines, minimumBars)) {
            return Optional.empty();
        }

        BinanceKlineDTO signalBar = StrategySupport.last(closedKlines);
        BigDecimal latestClose = StrategySupport.valueOf(signalBar.getClose());
        BigDecimal fastMa = StrategySupport.movingAverage(closedKlines, fastPeriod, 0);
        BigDecimal slowMa = StrategySupport.movingAverage(closedKlines, slowPeriod, 0);
        BigDecimal previousFastMa = StrategySupport.movingAverage(closedKlines, fastPeriod, 1);
        BigDecimal previousSlowMa = StrategySupport.movingAverage(closedKlines, slowPeriod, 1);
        if (!isTrendAligned(direction, latestClose, fastMa, slowMa, previousFastMa, previousSlowMa)) {
            return Optional.empty();
        }

        int impulseExclude = profitPullbackLookback + 1;
        if (closedKlines.size() <= secondEntryLookback + impulseExclude) {
            return Optional.empty();
        }

        List<BinanceKlineDTO> pullbackWindow = StrategySupport.trailingWindow(closedKlines, profitPullbackLookback, 1);
        List<BinanceKlineDTO> impulseWindow = StrategySupport.trailingWindow(closedKlines, secondEntryLookback, impulseExclude);
        if (CollectionUtils.isEmpty(pullbackWindow) || CollectionUtils.isEmpty(impulseWindow)) {
            return Optional.empty();
        }

        BigDecimal impulseHigh = StrategySupport.highestHigh(impulseWindow);
        BigDecimal impulseLow = StrategySupport.lowestLow(impulseWindow);
        BigDecimal impulseRange = impulseHigh.subtract(impulseLow);
        if (impulseRange.compareTo(ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal pullbackHigh = StrategySupport.highestHigh(pullbackWindow);
        BigDecimal pullbackLow = StrategySupport.lowestLow(pullbackWindow);
        BigDecimal pullbackAverageVolume = StrategySupport.averageVolume(pullbackWindow);
        BigDecimal impulseAverageVolume = StrategySupport.averageVolume(impulseWindow);
        BigDecimal latestVolumeRatio = latestVolumeRatio(closedKlines, signalBar);
        if (latestVolumeRatio == null || impulseAverageVolume.compareTo(ZERO) <= 0) {
            return Optional.empty();
        }

        if (direction == TradeDirection.LONG) {
            BigDecimal retraceRatio = ratio(impulseHigh.subtract(pullbackLow), impulseRange);
            if (!StrategySupport.isBullish(signalBar)
                    || latestClose.compareTo(pullbackHigh.multiply(ONE.add(profitReclaimBuffer))) <= 0
                    || latestClose.compareTo(impulseHigh.multiply(ONE.add(profitMaxExtension))) > 0
                    || StrategySupport.bodyRatio(signalBar).compareTo(profitMinBodyRatio) < 0
                    || StrategySupport.closeLocation(signalBar).compareTo(profitMinCloseLocation) < 0
                    || latestVolumeRatio.compareTo(profitMinVolumeRatio) < 0
                    || retraceRatio.compareTo(profitMinRetrace) < 0
                    || retraceRatio.compareTo(profitMaxRetrace) > 0
                    || countOpposingBars(pullbackWindow, TradeDirection.LONG) < profitMinPullbackBars
                    || ratio(pullbackAverageVolume, impulseAverageVolume).compareTo(profitPullbackMaxVolumeRatio) > 0
                    || pullbackLow.compareTo(slowMa.multiply(ONE.subtract(profitMaHoldBuffer))) < 0
                    || ratio(impulseHigh.subtract(pullbackLow), latestClose).compareTo(profitTouchTolerance) <= 0) {
                return Optional.empty();
            }
            BigDecimal stopPrice = pullbackLow.multiply(ONE.subtract(secondEntryInvalidationBuffer));
            if (stopPrice.compareTo(latestClose) >= 0) {
                return Optional.empty();
            }
            BigDecimal targetPrice = latestClose.add(impulseRange);
            return Optional.of(buildSignal(
                    TradeDirection.LONG,
                    "趋势利润层做多",
                    signalBar,
                    "PROFIT_TREND_LONG",
                    "前一段上涨已经打出方向，最近几根 K 线完成温和回踩后重新放量上攻，当前更适合按利润层的节奏去拿趋势延伸。",
                    latestClose,
                    stopPrice,
                    targetPrice,
                    latestVolumeRatio
            ));
        }

        BigDecimal retraceRatio = ratio(pullbackHigh.subtract(impulseLow), impulseRange);
        if (!StrategySupport.isBearish(signalBar)
                || latestClose.compareTo(pullbackLow.multiply(ONE.subtract(profitReclaimBuffer))) >= 0
                || latestClose.compareTo(impulseLow.multiply(ONE.subtract(profitMaxExtension))) < 0
                || StrategySupport.bodyRatio(signalBar).compareTo(profitMinBodyRatio) < 0
                || StrategySupport.closeLocation(signalBar).compareTo(ONE.subtract(profitMinCloseLocation)) > 0
                || latestVolumeRatio.compareTo(profitMinVolumeRatio) < 0
                || retraceRatio.compareTo(profitMinRetrace) < 0
                || retraceRatio.compareTo(profitMaxRetrace) > 0
                || countOpposingBars(pullbackWindow, TradeDirection.SHORT) < profitMinPullbackBars
                || ratio(pullbackAverageVolume, impulseAverageVolume).compareTo(profitPullbackMaxVolumeRatio) > 0
                || pullbackHigh.compareTo(slowMa.multiply(ONE.add(profitMaHoldBuffer))) > 0
                || ratio(pullbackHigh.subtract(impulseLow), latestClose).compareTo(profitTouchTolerance) <= 0) {
            return Optional.empty();
        }
        BigDecimal stopPrice = pullbackHigh.multiply(ONE.add(secondEntryInvalidationBuffer));
        if (stopPrice.compareTo(latestClose) <= 0) {
            return Optional.empty();
        }
        BigDecimal targetPrice = latestClose.subtract(impulseRange);
        return Optional.of(buildSignal(
                TradeDirection.SHORT,
                "趋势利润层做空",
                signalBar,
                "PROFIT_TREND_SHORT",
                "前一段下跌已经打出方向，最近几根 K 线完成温和反抽后重新放量下压，当前更适合按利润层的节奏去拿趋势延伸。",
                latestClose,
                stopPrice,
                targetPrice,
                latestVolumeRatio
        ));
    }

    private boolean isTrendAligned(TradeDirection direction,
                                   BigDecimal latestClose,
                                   BigDecimal fastMa,
                                   BigDecimal slowMa,
                                   BigDecimal previousFastMa,
                                   BigDecimal previousSlowMa) {
        if (direction == TradeDirection.LONG) {
            return fastMa.compareTo(slowMa.multiply(ONE.add(trendBiasBuffer))) > 0
                    && fastMa.compareTo(previousFastMa) > 0
                    && slowMa.compareTo(previousSlowMa) >= 0
                    && latestClose.compareTo(fastMa) >= 0;
        }
        return fastMa.compareTo(slowMa.multiply(ONE.subtract(trendBiasBuffer))) < 0
                && fastMa.compareTo(previousFastMa) < 0
                && slowMa.compareTo(previousSlowMa) <= 0
                && latestClose.compareTo(fastMa) <= 0;
    }

    private int countOpposingBars(List<BinanceKlineDTO> klines, TradeDirection direction) {
        int count = 0;
        for (BinanceKlineDTO kline : klines) {
            if (direction == TradeDirection.LONG && StrategySupport.isBearish(kline)) {
                count++;
            }
            if (direction == TradeDirection.SHORT && StrategySupport.isBullish(kline)) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal latestVolumeRatio(List<BinanceKlineDTO> closedKlines, BinanceKlineDTO signalBar) {
        if (CollectionUtils.isEmpty(closedKlines) || signalBar == null) {
            return null;
        }
        int baselineSize = Math.min(10, closedKlines.size() - 1);
        if (baselineSize <= 0) {
            return null;
        }
        List<BinanceKlineDTO> baseline = StrategySupport.trailingWindow(closedKlines, baselineSize, 1);
        if (CollectionUtils.isEmpty(baseline)) {
            return null;
        }
        return StrategySupport.ratio(
                StrategySupport.volumeOf(signalBar),
                StrategySupport.averageVolume(baseline)
        );
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    private AlertSignal buildSignal(TradeDirection direction,
                                    String title,
                                    BinanceKlineDTO bar,
                                    String type,
                                    String summary,
                                    BigDecimal triggerPrice,
                                    BigDecimal invalidationPrice,
                                    BigDecimal targetPrice,
                                    BigDecimal volumeRatio) {
        return new AlertSignal(
                direction,
                title,
                bar,
                type,
                summary,
                StrategySupport.scaleOrNull(triggerPrice),
                StrategySupport.scaleOrNull(invalidationPrice),
                StrategySupport.scaleOrNull(targetPrice),
                StrategySupport.scaleOrNull(volumeRatio)
        );
    }
}
