package com.mobai.alert.strategy.bollinger.shared;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.bollinger.BollingerBandLevels;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class BollingerSupport {

    public static final BigDecimal ZERO = BigDecimal.ZERO;

    private BollingerSupport() {
    }

    public static List<BinanceKlineDTO> closedKlines(List<BinanceKlineDTO> klines) {
        if (CollectionUtils.isEmpty(klines) || klines.size() < 2) {
            return List.of();
        }
        return klines.subList(0, klines.size() - 1);
    }

    public static BigDecimal valueOf(String value) {
        return new BigDecimal(value);
    }

    public static BigDecimal scaleOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BinanceKlineDTO last(List<BinanceKlineDTO> klines) {
        return klines.get(klines.size() - 1);
    }

    public static BollingerBandLevels calculateBands(List<BinanceKlineDTO> closedKlines,
                                                     int period,
                                                     BigDecimal stddevMultiplier) {
        if (CollectionUtils.isEmpty(closedKlines) || closedKlines.size() < period) {
            return null;
        }
        List<BinanceKlineDTO> window = closedKlines.subList(closedKlines.size() - period, closedKlines.size());
        BigDecimal sum = ZERO;
        for (BinanceKlineDTO kline : window) {
            sum = sum.add(valueOf(kline.getClose()));
        }
        BigDecimal middle = sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        double variance = 0D;
        for (BinanceKlineDTO kline : window) {
            double delta = valueOf(kline.getClose()).subtract(middle).doubleValue();
            variance += delta * delta;
        }
        variance = variance / period;
        BigDecimal standardDeviation = BigDecimal.valueOf(Math.sqrt(variance));
        BigDecimal offset = standardDeviation.multiply(stddevMultiplier);
        return new BollingerBandLevels(
                middle,
                middle.add(offset),
                middle.subtract(offset)
        );
    }

    public static BigDecimal volumeRatio(List<BinanceKlineDTO> closedKlines, int lookback) {
        if (CollectionUtils.isEmpty(closedKlines) || closedKlines.size() < 2) {
            return null;
        }
        int baselineSize = Math.min(Math.max(1, lookback), closedKlines.size() - 1);
        List<BinanceKlineDTO> baseline = closedKlines.subList(closedKlines.size() - 1 - baselineSize, closedKlines.size() - 1);
        BigDecimal sum = ZERO;
        for (BinanceKlineDTO kline : baseline) {
            sum = sum.add(valueOf(kline.getVolume()));
        }
        BigDecimal average = sum.divide(BigDecimal.valueOf(baseline.size()), 8, RoundingMode.HALF_UP);
        if (average.compareTo(ZERO) == 0) {
            return null;
        }
        return valueOf(last(closedKlines).getVolume()).divide(average, 8, RoundingMode.HALF_UP);
    }

    public static long resolveIntervalMs(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "3m" -> 180_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "1h" -> 3_600_000L;
            case "4h" -> 14_400_000L;
            case "1d" -> 86_400_000L;
            default -> 60_000L;
        };
    }
}
