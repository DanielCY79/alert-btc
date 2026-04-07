package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class AlertRuleEvaluator {

    private static final String BREAKOUT_SIGNAL_TYPE = "TREND_BREAKOUT";
    private static final String PULLBACK_SIGNAL_TYPE = "BREAKOUT_PULLBACK";

    @Value("${monitoring.strategy.trend.fast-period:20}")
    private int fastPeriod;

    @Value("${monitoring.strategy.trend.slow-period:60}")
    private int slowPeriod;

    @Value("${monitoring.strategy.breakout.lookback:20}")
    private int breakoutLookback;

    @Value("${monitoring.strategy.breakout.max-range:0.05}")
    private BigDecimal breakoutMaxRange;

    @Value("${monitoring.strategy.breakout.close-buffer:0.0015}")
    private BigDecimal breakoutCloseBuffer;

    @Value("${monitoring.strategy.breakout.volume-multiplier:1.8}")
    private BigDecimal breakoutVolumeMultiplier;

    @Value("${monitoring.strategy.breakout.max-extension:0.04}")
    private BigDecimal breakoutMaxExtension;

    @Value("${monitoring.strategy.breakout.failure-buffer:0.008}")
    private BigDecimal breakoutFailureBuffer;

    @Value("${monitoring.strategy.pullback.touch-tolerance:0.008}")
    private BigDecimal pullbackTouchTolerance;

    @Value("${monitoring.strategy.pullback.hold-buffer:0.006}")
    private BigDecimal pullbackHoldBuffer;

    @Value("${monitoring.strategy.pullback.max-volume-ratio:1.10}")
    private BigDecimal pullbackMaxVolumeRatio;

    public Optional<AlertSignal> evaluateTrendBreakout(List<BinanceKlineDTO> klines) {
        List<BinanceKlineDTO> closedKlines = closedKlines(klines);
        if (!hasEnoughBars(closedKlines, slowPeriod + breakoutLookback + 2)) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = last(closedKlines);
        if (!isBullish(latest)) {
            return Optional.empty();
        }

        TrendSnapshot trendSnapshot = buildTrendSnapshot(closedKlines);
        if (!trendSnapshot.uptrend()) {
            return Optional.empty();
        }

        List<BinanceKlineDTO> baseWindow = trailingWindow(closedKlines, breakoutLookback, 1);
        BigDecimal resistance = highestHigh(baseWindow);
        BigDecimal support = lowestLow(baseWindow);
        BigDecimal baseRange = percentageDistance(resistance, support);
        if (baseRange.compareTo(breakoutMaxRange) > 0) {
            return Optional.empty();
        }

        BigDecimal latestClose = valueOf(latest.getClose());
        BigDecimal breakoutThreshold = resistance.multiply(BigDecimal.ONE.add(breakoutCloseBuffer));
        if (latestClose.compareTo(breakoutThreshold) <= 0) {
            return Optional.empty();
        }

        BigDecimal averageVolume = averageVolume(baseWindow);
        BigDecimal volumeRatio = ratio(volumeOf(latest), averageVolume);
        if (volumeRatio.compareTo(breakoutVolumeMultiplier) < 0) {
            return Optional.empty();
        }

        BigDecimal maxChasePrice = trendSnapshot.fastMa().multiply(BigDecimal.ONE.add(breakoutMaxExtension));
        if (latestClose.compareTo(maxChasePrice) > 0) {
            return Optional.empty();
        }

        BigDecimal invalidationPrice = resistance.multiply(BigDecimal.ONE.subtract(breakoutFailureBuffer));
        String summary = String.format(
                "上升趋势保持完好，价格有效突破最近 %d 根K线的平台高点，且成交额放大到均量的 %.2f 倍。",
                breakoutLookback,
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        );

        return Optional.of(new AlertSignal(
                "BTC 趋势突破",
                latest,
                BREAKOUT_SIGNAL_TYPE,
                summary,
                resistance.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines, BigDecimal breakoutLevel) {
        List<BinanceKlineDTO> closedKlines = closedKlines(klines);
        if (!hasEnoughBars(closedKlines, slowPeriod + breakoutLookback + 2)) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = last(closedKlines);
        TrendSnapshot trendSnapshot = buildTrendSnapshot(closedKlines);
        if (!trendSnapshot.uptrend()) {
            return Optional.empty();
        }

        BigDecimal latestLow = valueOf(latest.getLow());
        BigDecimal latestClose = valueOf(latest.getClose());
        BigDecimal latestOpen = valueOf(latest.getOpen());

        BigDecimal touchCeiling = breakoutLevel.multiply(BigDecimal.ONE.add(pullbackTouchTolerance));
        BigDecimal holdFloor = breakoutLevel.multiply(BigDecimal.ONE.subtract(pullbackHoldBuffer));
        if (latestLow.compareTo(touchCeiling) > 0) {
            return Optional.empty();
        }
        if (latestClose.compareTo(holdFloor) < 0 || latestClose.compareTo(latestOpen) <= 0) {
            return Optional.empty();
        }
        if (latestClose.compareTo(trendSnapshot.fastMa()) < 0) {
            return Optional.empty();
        }

        List<BinanceKlineDTO> recentWindow = trailingWindow(closedKlines, Math.min(10, breakoutLookback), 1);
        BigDecimal averageVolume = averageVolume(recentWindow);
        BigDecimal volumeRatio = ratio(volumeOf(latest), averageVolume);
        if (volumeRatio.compareTo(pullbackMaxVolumeRatio) > 0) {
            return Optional.empty();
        }

        BigDecimal invalidationPrice = breakoutLevel.multiply(BigDecimal.ONE.subtract(pullbackHoldBuffer));
        String summary = String.format(
                "突破后回踩前高附近并重新企稳，回踩成交额仅为近期均量的 %.2f 倍。",
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        );

        return Optional.of(new AlertSignal(
                "BTC 突破回踩",
                latest,
                PULLBACK_SIGNAL_TYPE,
                summary,
                breakoutLevel.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    private boolean hasEnoughBars(List<BinanceKlineDTO> closedKlines, int minimumSize) {
        return !CollectionUtils.isEmpty(closedKlines) && closedKlines.size() >= minimumSize;
    }

    private List<BinanceKlineDTO> closedKlines(List<BinanceKlineDTO> klines) {
        if (CollectionUtils.isEmpty(klines) || klines.size() < 2) {
            return List.of();
        }
        return klines.subList(0, klines.size() - 1);
    }

    private TrendSnapshot buildTrendSnapshot(List<BinanceKlineDTO> closedKlines) {
        BigDecimal fastMa = movingAverage(closedKlines, fastPeriod, 0);
        BigDecimal previousFastMa = movingAverage(closedKlines, fastPeriod, 1);
        BigDecimal slowMa = movingAverage(closedKlines, slowPeriod, 0);
        BigDecimal previousSlowMa = movingAverage(closedKlines, slowPeriod, 1);
        BigDecimal latestClose = valueOf(last(closedKlines).getClose());

        boolean uptrend = fastMa.compareTo(slowMa) > 0
                && fastMa.compareTo(previousFastMa) > 0
                && slowMa.compareTo(previousSlowMa) >= 0
                && latestClose.compareTo(fastMa) >= 0;
        return new TrendSnapshot(fastMa, slowMa, uptrend);
    }

    private List<BinanceKlineDTO> trailingWindow(List<BinanceKlineDTO> klines, int size, int excludeLastBars) {
        int endExclusive = klines.size() - excludeLastBars;
        int startInclusive = Math.max(0, endExclusive - size);
        return klines.subList(startInclusive, endExclusive);
    }

    private BinanceKlineDTO last(List<BinanceKlineDTO> klines) {
        return klines.get(klines.size() - 1);
    }

    private BigDecimal movingAverage(List<BinanceKlineDTO> klines, int period, int offset) {
        int endExclusive = klines.size() - offset;
        int startInclusive = endExclusive - period;
        BigDecimal total = BigDecimal.ZERO;
        for (int i = startInclusive; i < endExclusive; i++) {
            total = total.add(valueOf(klines.get(i).getClose()));
        }
        return total.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal highestHigh(List<BinanceKlineDTO> klines) {
        BigDecimal highest = BigDecimal.ZERO;
        for (BinanceKlineDTO kline : klines) {
            BigDecimal candidate = valueOf(kline.getHigh());
            if (candidate.compareTo(highest) > 0) {
                highest = candidate;
            }
        }
        return highest;
    }

    private BigDecimal lowestLow(List<BinanceKlineDTO> klines) {
        BigDecimal lowest = valueOf(klines.get(0).getLow());
        for (BinanceKlineDTO kline : klines) {
            BigDecimal candidate = valueOf(kline.getLow());
            if (candidate.compareTo(lowest) < 0) {
                lowest = candidate;
            }
        }
        return lowest;
    }

    private BigDecimal averageVolume(List<BinanceKlineDTO> klines) {
        BigDecimal total = BigDecimal.ZERO;
        for (BinanceKlineDTO kline : klines) {
            total = total.add(volumeOf(kline));
        }
        return total.divide(BigDecimal.valueOf(klines.size()), 8, RoundingMode.HALF_UP);
    }

    private boolean isBullish(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).compareTo(valueOf(kline.getOpen())) > 0;
    }

    private BigDecimal percentageDistance(BigDecimal high, BigDecimal low) {
        return high.subtract(low).divide(low, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal volumeOf(BinanceKlineDTO kline) {
        return valueOf(kline.getVolume());
    }

    private BigDecimal valueOf(String value) {
        return new BigDecimal(value);
    }

    private record TrendSnapshot(BigDecimal fastMa, BigDecimal slowMa, boolean uptrend) {
    }
}
