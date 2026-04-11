package com.mobai.alert.feature.extractor;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.model.PriceFeatures;
import com.mobai.alert.strategy.shared.RangeContext;
import com.mobai.alert.strategy.shared.StrategySettings;
import com.mobai.alert.strategy.shared.StrategySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 价格特征提取器。
 * 从最近 K 线里提取趋势、波动、量能和区间位置等价格维度特征。
 */
@Component
public class PriceFeatureExtractor {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HALF = new BigDecimal("0.50");
    private static final BigDecimal TWO = new BigDecimal("2");

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

    @Value("${monitoring.feature.price.volume-lookback:20}")
    private int volumeLookback;

    @Value("${monitoring.feature.price.atr-period:14}")
    private int atrPeriod;

    /**
     * 基于最近 K 线提取价格特征。
     */
    public PriceFeatures extract(String symbol, String interval, List<BinanceKlineDTO> klines) {
        PriceFeatures features = new PriceFeatures();
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        if (CollectionUtils.isEmpty(closedKlines)) {
            return features;
        }

        BinanceKlineDTO latest = StrategySupport.last(closedKlines);
        features.setAsOfTime(latest.getEndTime());
        features.setClosePrice(StrategySupport.valueOf(latest.getClose()));
        features.setReturn1Bar(relativeCloseChange(closedKlines, 1));
        features.setReturn3Bar(relativeCloseChange(closedKlines, 3));
        features.setReturn12Bar(relativeCloseChange(closedKlines, 12));
        features.setFastMa(movingAverageIfReady(closedKlines, fastPeriod));
        features.setSlowMa(movingAverageIfReady(closedKlines, slowPeriod));
        if (features.getFastMa() != null && features.getSlowMa() != null) {
            features.setMaSpreadPct(StrategySupport.ratio(
                    features.getFastMa().subtract(features.getSlowMa()),
                    features.getSlowMa()
            ));
        }

        features.setAtrPct(calculateAtrPct(closedKlines));
        features.setVolumeRatio(calculateVolumeRatio(closedKlines));
        features.setBodyRatio(StrategySupport.bodyRatio(latest));
        features.setUpperWickRatio(wickRatio(StrategySupport.upperWick(latest), latest));
        features.setLowerWickRatio(wickRatio(StrategySupport.lowerWick(latest), latest));
        features.setCloseLocation(StrategySupport.closeLocation(latest));

        RangeContext rangeContext = StrategySupport.buildRangeContext(closedKlines, strategySettings());
        if (rangeContext != null) {
            features.setRangeWidthPct(rangeContext.width());
            BigDecimal closePrice = features.getClosePrice();
            BigDecimal range = rangeContext.resistance().subtract(rangeContext.support());
            if (range.compareTo(ZERO) > 0) {
                features.setRangePosition(
                        closePrice.subtract(rangeContext.support()).divide(range, 8, RoundingMode.HALF_UP)
                );
            }
            features.setInsideRange(closePrice.compareTo(rangeContext.support()) >= 0
                    && closePrice.compareTo(rangeContext.resistance()) <= 0);
        }

        features.setBreakoutStrengthScore(calculateBreakoutStrength(features));
        return features;
    }

    /**
     * 把当前配置拼装成策略参数对象，方便复用已有策略工具方法。
     */
    private StrategySettings strategySettings() {
        return new StrategySettings(
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
                secondEntryLookback,
                secondEntryMinPullbackBars,
                secondEntryMinBodyRatio,
                secondEntryMinCloseLocation,
                secondEntryInvalidationBuffer
        );
    }

    /**
     * 计算距离若干根 K 线前的收盘涨跌幅。
     */
    private BigDecimal relativeCloseChange(List<BinanceKlineDTO> closedKlines, int barsBack) {
        if (closedKlines.size() <= barsBack) {
            return null;
        }
        BigDecimal latestClose = StrategySupport.valueOf(StrategySupport.last(closedKlines).getClose());
        BigDecimal baselineClose = StrategySupport.valueOf(closedKlines.get(closedKlines.size() - 1 - barsBack).getClose());
        return StrategySupport.ratio(latestClose.subtract(baselineClose), baselineClose);
    }

    /**
     * 当样本足够时计算简单均线，否则返回空值。
     */
    private BigDecimal movingAverageIfReady(List<BinanceKlineDTO> closedKlines, int period) {
        if (closedKlines.size() < period) {
            return null;
        }
        return StrategySupport.movingAverage(closedKlines, period, 0);
    }

    /**
     * 用最近一根成交量与历史均量比较，得到量比。
     */
    private BigDecimal calculateVolumeRatio(List<BinanceKlineDTO> closedKlines) {
        if (closedKlines.size() < 2) {
            return null;
        }

        int baselineBars = Math.min(volumeLookback, closedKlines.size() - 1);
        if (baselineBars <= 0) {
            return null;
        }

        int endExclusive = closedKlines.size() - 1;
        int startInclusive = endExclusive - baselineBars;
        List<BinanceKlineDTO> baselineWindow = closedKlines.subList(startInclusive, endExclusive);
        BigDecimal averageVolume = StrategySupport.averageVolume(baselineWindow);
        return StrategySupport.ratio(StrategySupport.volumeOf(StrategySupport.last(closedKlines)), averageVolume);
    }

    /**
     * 计算 ATR 相对收盘价的比例。
     */
    private BigDecimal calculateAtrPct(List<BinanceKlineDTO> closedKlines) {
        if (closedKlines.size() < 2) {
            return null;
        }

        int sampleSize = Math.min(atrPeriod, closedKlines.size() - 1);
        if (sampleSize <= 0) {
            return null;
        }

        int startInclusive = closedKlines.size() - sampleSize;
        BigDecimal total = ZERO;
        for (int i = startInclusive; i < closedKlines.size(); i++) {
            BinanceKlineDTO current = closedKlines.get(i);
            BinanceKlineDTO previous = closedKlines.get(i - 1);
            total = total.add(trueRange(current, previous));
        }

        BigDecimal atr = total.divide(BigDecimal.valueOf(sampleSize), 8, RoundingMode.HALF_UP);
        BigDecimal latestClose = StrategySupport.valueOf(StrategySupport.last(closedKlines).getClose());
        return StrategySupport.ratio(atr, latestClose);
    }

    /**
     * 计算单根 K 线的真实波幅。
     */
    private BigDecimal trueRange(BinanceKlineDTO current, BinanceKlineDTO previous) {
        BigDecimal high = StrategySupport.valueOf(current.getHigh());
        BigDecimal low = StrategySupport.valueOf(current.getLow());
        BigDecimal previousClose = StrategySupport.valueOf(previous.getClose());

        BigDecimal intrabarRange = high.subtract(low).abs();
        BigDecimal gapUp = high.subtract(previousClose).abs();
        BigDecimal gapDown = low.subtract(previousClose).abs();
        return intrabarRange.max(gapUp).max(gapDown);
    }

    /**
     * 把影线长度换算成整根 K 线范围内的占比。
     */
    private BigDecimal wickRatio(BigDecimal wick, BinanceKlineDTO kline) {
        BigDecimal range = StrategySupport.barRange(kline);
        if (range.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return wick.divide(range, 8, RoundingMode.HALF_UP);
    }

    /**
     * 聚合实体、收盘位置和量比，形成突破强度分数。
     */
    private BigDecimal calculateBreakoutStrength(PriceFeatures features) {
        List<BigDecimal> components = new ArrayList<>();
        components.add(clampZeroToOne(features.getBodyRatio()));

        if (features.getCloseLocation() != null) {
            BigDecimal edgeScore = features.getCloseLocation().subtract(HALF).abs().multiply(TWO);
            components.add(clampZeroToOne(edgeScore));
        }

        if (features.getVolumeRatio() != null) {
            BigDecimal denominator = breakoutVolumeMultiplier.subtract(ONE);
            if (denominator.compareTo(ZERO) <= 0) {
                denominator = ONE;
            }
            BigDecimal volumeScore = features.getVolumeRatio().subtract(ONE).divide(denominator, 8, RoundingMode.HALF_UP);
            components.add(clampZeroToOne(volumeScore));
        }

        if (components.isEmpty()) {
            return null;
        }

        BigDecimal total = ZERO;
        for (BigDecimal component : components) {
            total = total.add(component);
        }
        return total.divide(BigDecimal.valueOf(components.size()), 8, RoundingMode.HALF_UP);
    }

    /**
     * 把数值限制在 0 到 1。
     */
    private BigDecimal clampZeroToOne(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (value.compareTo(ONE) > 0) {
            return ONE;
        }
        return value;
    }
}
