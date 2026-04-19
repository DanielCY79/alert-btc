package com.mobai.alert.strategy.delphi2.shared;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class Delphi2Support {

    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal ONE = BigDecimal.ONE;

    private Delphi2Support() {
    }

    public static List<BinanceKlineDTO> closedKlines(List<BinanceKlineDTO> klines) {
        if (CollectionUtils.isEmpty(klines) || klines.size() < 2) {
            return List.of();
        }
        return klines.subList(0, klines.size() - 1);
    }

    public static List<BinanceKlineDTO> trailingWindow(List<BinanceKlineDTO> klines, int size, int excludeLastBars) {
        int normalizedSize = Math.max(1, size);
        int endExclusive = Math.max(0, klines.size() - Math.max(0, excludeLastBars));
        int startInclusive = Math.max(0, endExclusive - normalizedSize);
        if (startInclusive >= endExclusive) {
            return List.of();
        }
        return klines.subList(startInclusive, endExclusive);
    }

    public static BinanceKlineDTO last(List<BinanceKlineDTO> klines) {
        return klines.get(klines.size() - 1);
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

    public static BigDecimal ema(List<BinanceKlineDTO> klines, int period, int endOffsetBars) {
        int normalizedPeriod = Math.max(1, period);
        int endExclusive = klines.size() - Math.max(0, endOffsetBars);
        if (endExclusive < normalizedPeriod) {
            return null;
        }
        List<BinanceKlineDTO> visible = klines.subList(0, endExclusive);
        BigDecimal seed = simpleAverage(visible.subList(0, normalizedPeriod));
        BigDecimal multiplier = BigDecimal.valueOf(2D / (normalizedPeriod + 1D));
        BigDecimal ema = seed;
        for (int i = normalizedPeriod; i < visible.size(); i++) {
            BigDecimal close = valueOf(visible.get(i).getClose());
            ema = close.multiply(multiplier)
                    .add(ema.multiply(ONE.subtract(multiplier)))
                    .setScale(8, RoundingMode.HALF_UP);
        }
        return ema;
    }

    public static BigDecimal atr(List<BinanceKlineDTO> klines, int period, int endOffsetBars) {
        int normalizedPeriod = Math.max(1, period);
        int endExclusive = klines.size() - Math.max(0, endOffsetBars);
        if (endExclusive < normalizedPeriod) {
            return null;
        }
        List<BinanceKlineDTO> visible = klines.subList(0, endExclusive);
        BigDecimal atr = ZERO;
        for (int i = 0; i < visible.size(); i++) {
            BigDecimal tr = trueRange(visible, i);
            if (i < normalizedPeriod) {
                atr = atr.add(tr);
                if (i == normalizedPeriod - 1) {
                    atr = atr.divide(BigDecimal.valueOf(normalizedPeriod), 8, RoundingMode.HALF_UP);
                }
            } else {
                atr = atr.multiply(BigDecimal.valueOf(normalizedPeriod - 1L))
                        .add(tr)
                        .divide(BigDecimal.valueOf(normalizedPeriod), 8, RoundingMode.HALF_UP);
            }
        }
        return atr;
    }

    public static BigDecimal highestHigh(List<BinanceKlineDTO> klines) {
        BigDecimal highest = valueOf(klines.get(0).getHigh());
        for (BinanceKlineDTO kline : klines) {
            BigDecimal candidate = valueOf(kline.getHigh());
            if (candidate.compareTo(highest) > 0) {
                highest = candidate;
            }
        }
        return highest;
    }

    public static BigDecimal lowestLow(List<BinanceKlineDTO> klines) {
        BigDecimal lowest = valueOf(klines.get(0).getLow());
        for (BinanceKlineDTO kline : klines) {
            BigDecimal candidate = valueOf(kline.getLow());
            if (candidate.compareTo(lowest) < 0) {
                lowest = candidate;
            }
        }
        return lowest;
    }

    public static BigDecimal volumeRatio(List<BinanceKlineDTO> closedKlines, int lookback) {
        if (CollectionUtils.isEmpty(closedKlines) || closedKlines.size() < 2) {
            return null;
        }
        int baselineSize = Math.min(Math.max(1, lookback), closedKlines.size() - 1);
        List<BinanceKlineDTO> baseline = closedKlines.subList(
                closedKlines.size() - 1 - baselineSize,
                closedKlines.size() - 1
        );
        BigDecimal average = averageVolume(baseline);
        if (average.compareTo(ZERO) <= 0) {
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
            default -> 3_600_000L;
        };
    }

    private static BigDecimal trueRange(List<BinanceKlineDTO> klines, int index) {
        BinanceKlineDTO current = klines.get(index);
        BigDecimal high = valueOf(current.getHigh());
        BigDecimal low = valueOf(current.getLow());
        if (index == 0) {
            return high.subtract(low).abs();
        }
        BigDecimal previousClose = valueOf(klines.get(index - 1).getClose());
        BigDecimal highLow = high.subtract(low).abs();
        BigDecimal highClose = high.subtract(previousClose).abs();
        BigDecimal lowClose = low.subtract(previousClose).abs();
        return highLow.max(highClose).max(lowClose);
    }

    private static BigDecimal simpleAverage(List<BinanceKlineDTO> klines) {
        BigDecimal total = ZERO;
        for (BinanceKlineDTO kline : klines) {
            total = total.add(valueOf(kline.getClose()));
        }
        return total.divide(BigDecimal.valueOf(klines.size()), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageVolume(List<BinanceKlineDTO> klines) {
        BigDecimal total = ZERO;
        for (BinanceKlineDTO kline : klines) {
            total = total.add(valueOf(kline.getVolume()));
        }
        return total.divide(BigDecimal.valueOf(klines.size()), 8, RoundingMode.HALF_UP);
    }
}
