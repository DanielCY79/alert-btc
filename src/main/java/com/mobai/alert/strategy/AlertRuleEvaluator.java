package com.mobai.alert.strategy;

import com.mobai.alert.access.dto.BinanceKlineDTO;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class AlertRuleEvaluator {
    /*
     * 这是一套“区间优先”的 BTC 价格行为策略评估器。
     * 核心思想是：
     * 1. 先识别市场是否处于可交易区间。
     * 2. 区间边缘优先找失败突破，而不是追第一下突破。
     * 3. 真正有效的突破，需要实体、收盘位置和量能共同确认。
     * 4. 真突破之后，优先等待第一次回踩确认，而不是直接追高追低。
     */

    private static final String RANGE_FAILURE_LONG_TYPE = "RANGE_FAILURE_LONG";
    private static final String RANGE_FAILURE_SHORT_TYPE = "RANGE_FAILURE_SHORT";
    private static final String BREAKOUT_LONG_TYPE = "CONFIRMED_BREAKOUT_LONG";
    private static final String BREAKOUT_SHORT_TYPE = "CONFIRMED_BREAKOUT_SHORT";
    private static final String PULLBACK_LONG_TYPE = "BREAKOUT_PULLBACK_LONG";
    private static final String PULLBACK_SHORT_TYPE = "BREAKOUT_PULLBACK_SHORT";

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TWO = new BigDecimal("2");

    // 快速均线周期，用来衡量短期价格重心。
    @Value("${monitoring.strategy.trend.fast-period:20}")
    private int fastPeriod;

    // 慢速均线周期，用来确保样本长度和趋势背景足够稳定。
    @Value("${monitoring.strategy.trend.slow-period:60}")
    private int slowPeriod;

    // 区间识别回看长度，决定最近多少根K线用于定义支撑、阻力和中轴。
    @Value("${monitoring.strategy.range.lookback:36}")
    private int rangeLookback;

    // 可接受的最小区间宽度，过窄通常只是噪声整理，不具备交易价值。
    @Value("${monitoring.strategy.range.min-width:0.03}")
    private BigDecimal rangeMinWidth;

    // 可接受的最大区间宽度，过宽则更像大幅震荡，不适合作为紧凑区间处理。
    @Value("${monitoring.strategy.range.max-width:0.18}")
    private BigDecimal rangeMaxWidth;

    // 边缘容忍度，用来判断价格是否“足够接近”区间上沿或下沿。
    @Value("${monitoring.strategy.range.edge-tolerance:0.015}")
    private BigDecimal rangeEdgeTolerance;

    // 上下边缘最少被测试的次数，确保区间边界不是偶然出现。
    @Value("${monitoring.strategy.range.required-edge-touches:2}")
    private int requiredEdgeTouches;

    // 相邻K线最小重叠比例，重叠越多越符合交易区间的来回交换特征。
    @Value("${monitoring.strategy.range.overlap-threshold:0.45}")
    private BigDecimal overlapThreshold;

    // 至少需要多少根K线满足重叠条件，避免把单边推进误判成区间。
    @Value("${monitoring.strategy.range.min-overlap-bars:12}")
    private int minOverlapBars;

    // 快速均线允许的最大漂移比例，超过这个值说明价格重心在明显移动。
    @Value("${monitoring.strategy.range.ma-flat-threshold:0.012}")
    private BigDecimal maFlatThreshold;

    // 确认突破时，收盘必须站上/跌破边界的最小缓冲比例。
    @Value("${monitoring.strategy.breakout.close-buffer:0.003}")
    private BigDecimal breakoutCloseBuffer;

    // 确认突破时需要达到的相对放量倍数。
    @Value("${monitoring.strategy.breakout.volume-multiplier:1.5}")
    private BigDecimal breakoutVolumeMultiplier;

    // 突破K线实体占整根K线波动的最小比例，用来过滤长影线假突破。
    @Value("${monitoring.strategy.breakout.body-ratio-threshold:0.45}")
    private BigDecimal breakoutBodyRatioThreshold;

    // 突破后允许距离边界的最大延伸比例，过度拉开通常不适合追价。
    @Value("${monitoring.strategy.breakout.max-extension:0.05}")
    private BigDecimal breakoutMaxExtension;

    // 突破失效缓冲，用于设置确认突破后的失效位。
    @Value("${monitoring.strategy.breakout.failure-buffer:0.008}")
    private BigDecimal breakoutFailureBuffer;

    // 假突破时，价格至少要向边界外探出的缓冲比例。
    @Value("${monitoring.strategy.failure.probe-buffer:0.003}")
    private BigDecimal failureProbeBuffer;

    // 假突破后重新回到区间内时，需要回收的最小缓冲比例。
    @Value("${monitoring.strategy.failure.reentry-buffer:0.001}")
    private BigDecimal failureReentryBuffer;

    // 影线与实体的最小倍数关系，用来确认“拒绝感”是否足够明显。
    @Value("${monitoring.strategy.failure.min-wick-body-ratio:1.20}")
    private BigDecimal failureMinWickBodyRatio;

    // 回踩确认时，价格触碰突破位附近所允许的误差范围。
    @Value("${monitoring.strategy.pullback.touch-tolerance:0.008}")
    private BigDecimal pullbackTouchTolerance;

    // 回踩确认时，收盘允许跌回/站回突破位另一侧的最大容忍范围。
    @Value("${monitoring.strategy.pullback.hold-buffer:0.006}")
    private BigDecimal pullbackHoldBuffer;

    // 回踩阶段允许的最大相对量能，策略更偏好缩量回踩而不是再次剧烈对冲。
    @Value("${monitoring.strategy.pullback.max-volume-ratio:1.10}")
    private BigDecimal pullbackMaxVolumeRatio;

    /**
     * 区间下沿假跌破后重新收回。
     * 这是典型的“扫流动性后回到区间”的做多信号。
     */
    public Optional<AlertSignal> evaluateRangeFailedBreakdownLong(List<BinanceKlineDTO> klines) {
        List<BinanceKlineDTO> closedKlines = closedKlines(klines);
        if (!hasEnoughBars(closedKlines, minimumBarsRequired())) {
            return Optional.empty();
        }

        RangeContext range = buildRangeContext(closedKlines);
        if (range == null) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = last(closedKlines);
        BigDecimal low = valueOf(latest.getLow());
        BigDecimal close = valueOf(latest.getClose());
        BigDecimal support = range.support();
        // 先要求价格真实刺穿支撑，而不是只在支撑附近晃一下。
        if (low.compareTo(support.multiply(ONE.subtract(failureProbeBuffer))) > 0) {
            return Optional.empty();
        }
        // 再要求收盘重新站回区间，证明下破没有被市场接受。
        if (close.compareTo(support.multiply(ONE.add(failureReentryBuffer))) < 0) {
            return Optional.empty();
        }
        // 做多方向要求收阳，并且收盘尽量靠近K线上半部，说明买盘接管。
        if (!isBullish(latest) || closeLocation(latest).compareTo(new BigDecimal("0.60")) < 0) {
            return Optional.empty();
        }
        // 下影线要明显长于实体，体现“下破失败”的拒绝感。
        if (lowerWick(latest).compareTo(bodySize(latest).multiply(failureMinWickBodyRatio)) < 0) {
            return Optional.empty();
        }
        // 如果已经直接收到区间中轴上方，盈亏比会变差，因此放弃。
        if (close.compareTo(range.midpoint()) > 0) {
            return Optional.empty();
        }

        BigDecimal volumeRatio = ratio(volumeOf(latest), averageVolume(range.window()));
        BigDecimal invalidationPrice = low.multiply(ONE.subtract(failureReentryBuffer));
        String summary = String.format(
                "Established range support was swept and reclaimed. Price closed back inside the range with a strong rejection wick."
        );
        return Optional.of(new AlertSignal(
                TradeDirection.LONG,
                "BTC Range Failed Breakdown",
                latest,
                RANGE_FAILURE_LONG_TYPE,
                summary,
                support.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                range.midpoint().setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    /**
     * 区间上沿假突破后重新跌回区间。
     * 这是与假跌破对称的做空信号。
     */
    public Optional<AlertSignal> evaluateRangeFailedBreakoutShort(List<BinanceKlineDTO> klines) {
        List<BinanceKlineDTO> closedKlines = closedKlines(klines);
        if (!hasEnoughBars(closedKlines, minimumBarsRequired())) {
            return Optional.empty();
        }

        RangeContext range = buildRangeContext(closedKlines);
        if (range == null) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = last(closedKlines);
        BigDecimal high = valueOf(latest.getHigh());
        BigDecimal close = valueOf(latest.getClose());
        BigDecimal resistance = range.resistance();
        // 先要求价格刺穿阻力，确认出现了“向上扫单”。
        if (high.compareTo(resistance.multiply(ONE.add(failureProbeBuffer))) < 0) {
            return Optional.empty();
        }
        // 收盘重新落回阻力下方，说明上破没有被接受。
        if (close.compareTo(resistance.multiply(ONE.subtract(failureReentryBuffer))) > 0) {
            return Optional.empty();
        }
        // 做空方向要求收阴，并且收盘尽量靠近K线下半部。
        if (!isBearish(latest) || closeLocation(latest).compareTo(new BigDecimal("0.40")) > 0) {
            return Optional.empty();
        }
        // 上影线越长，越说明上破失败后的抛压明显。
        if (upperWick(latest).compareTo(bodySize(latest).multiply(failureMinWickBodyRatio)) < 0) {
            return Optional.empty();
        }
        // 如果已经回到中轴下方过深，信号性价比反而下降。
        if (close.compareTo(range.midpoint()) < 0) {
            return Optional.empty();
        }

        BigDecimal volumeRatio = ratio(volumeOf(latest), averageVolume(range.window()));
        BigDecimal invalidationPrice = high.multiply(ONE.add(failureReentryBuffer));
        String summary = String.format(
                "Established range resistance was swept and rejected. Price closed back inside the range with a strong upper wick."
        );
        return Optional.of(new AlertSignal(
                TradeDirection.SHORT,
                "BTC Range Failed Breakout",
                latest,
                RANGE_FAILURE_SHORT_TYPE,
                summary,
                resistance.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                range.midpoint().setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    public Optional<AlertSignal> evaluateTrendBreakout(List<BinanceKlineDTO> klines) {
        return evaluateConfirmedBreakout(klines, true);
    }

    public Optional<AlertSignal> evaluateTrendBreakdown(List<BinanceKlineDTO> klines) {
        return evaluateConfirmedBreakout(klines, false);
    }

    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel) {
        return evaluateBreakoutPullback(klines, breakoutLevel, null, true);
    }

    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel,
                                                          boolean bullishBreakout) {
        return evaluateBreakoutPullback(klines, breakoutLevel, null, bullishBreakout);
    }

    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel,
                                                          BigDecimal targetPrice,
                                                          boolean bullishBreakout) {
        List<BinanceKlineDTO> closedKlines = closedKlines(klines);
        if (!hasEnoughBars(closedKlines, minimumBarsRequired())) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = last(closedKlines);
        BigDecimal latestLow = valueOf(latest.getLow());
        BigDecimal latestHigh = valueOf(latest.getHigh());
        BigDecimal latestClose = valueOf(latest.getClose());
        BigDecimal latestOpen = valueOf(latest.getOpen());

        BigDecimal averageVolume = averageVolume(trailingWindow(closedKlines, Math.min(10, rangeLookback), 1));
        BigDecimal volumeRatio = ratio(volumeOf(latest), averageVolume);
        // 回踩阶段更希望看到“缩量确认”，而不是再次放量剧烈对冲。
        if (volumeRatio.compareTo(pullbackMaxVolumeRatio) > 0) {
            return Optional.empty();
        }

        if (bullishBreakout) {
            BigDecimal touchCeiling = breakoutLevel.multiply(ONE.add(pullbackTouchTolerance));
            BigDecimal holdFloor = breakoutLevel.multiply(ONE.subtract(pullbackHoldBuffer));
            // 必须真的回踩到突破位附近，否则不算“回踩确认”。
            if (latestLow.compareTo(touchCeiling) > 0) {
                return Optional.empty();
            }
            // 回踩之后要守住突破位下方的容忍区间，并重新收强。
            if (latestClose.compareTo(holdFloor) < 0 || latestClose.compareTo(latestOpen) <= 0) {
                return Optional.empty();
            }

            return Optional.of(new AlertSignal(
                    TradeDirection.LONG,
                    "BTC Breakout Pullback Long",
                    latest,
                    PULLBACK_LONG_TYPE,
                    "Price retested the accepted breakout area and held above it. This is the preferred post-breakout long setup.",
                    breakoutLevel.setScale(2, RoundingMode.HALF_UP),
                    holdFloor.setScale(2, RoundingMode.HALF_UP),
                    scaleOrNull(targetPrice),
                    volumeRatio.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        BigDecimal touchFloor = breakoutLevel.multiply(ONE.subtract(pullbackTouchTolerance));
        BigDecimal holdCeiling = breakoutLevel.multiply(ONE.add(pullbackHoldBuffer));
        // 空头回踩则要求反抽到跌破位附近。
        if (latestHigh.compareTo(touchFloor) < 0) {
            return Optional.empty();
        }
        // 反抽不能有效站回跌破位上方，并且最好重新收弱。
        if (latestClose.compareTo(holdCeiling) > 0 || latestClose.compareTo(latestOpen) >= 0) {
            return Optional.empty();
        }

        return Optional.of(new AlertSignal(
                TradeDirection.SHORT,
                "BTC Breakout Pullback Short",
                latest,
                PULLBACK_SHORT_TYPE,
                "Price retested the accepted breakdown area and failed to reclaim it. This is the preferred post-breakdown short setup.",
                breakoutLevel.setScale(2, RoundingMode.HALF_UP),
                holdCeiling.setScale(2, RoundingMode.HALF_UP),
                scaleOrNull(targetPrice),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    private Optional<AlertSignal> evaluateConfirmedBreakout(List<BinanceKlineDTO> klines, boolean bullishBreakout) {
        List<BinanceKlineDTO> closedKlines = closedKlines(klines);
        if (!hasEnoughBars(closedKlines, minimumBarsRequired())) {
            return Optional.empty();
        }

        RangeContext range = buildRangeContext(closedKlines);
        if (range == null) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = last(closedKlines);
        BinanceKlineDTO previous = closedKlines.get(closedKlines.size() - 2);
        BigDecimal close = valueOf(latest.getClose());
        BigDecimal previousClose = valueOf(previous.getClose());
        BigDecimal boundary = bullishBreakout ? range.resistance() : range.support();
        BigDecimal closeThreshold = bullishBreakout
                ? boundary.multiply(ONE.add(breakoutCloseBuffer))
                : boundary.multiply(ONE.subtract(breakoutCloseBuffer));
        BigDecimal previousThreshold = bullishBreakout
                ? boundary.multiply(ONE.add(breakoutCloseBuffer))
                : boundary.multiply(ONE.subtract(breakoutCloseBuffer));

        // 当前K线必须有效收在边界之外，说明突破被市场接受。
        if (bullishBreakout && close.compareTo(closeThreshold) <= 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && close.compareTo(closeThreshold) >= 0) {
            return Optional.empty();
        }
        // 前一根不能已经提前站稳边界外，否则这里更像“突破后的延续”，不是首次确认。
        if (bullishBreakout && previousClose.compareTo(previousThreshold) > 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && previousClose.compareTo(previousThreshold) < 0) {
            return Optional.empty();
        }
        // 真突破需要较大的实体占比，减少长影线假突破。
        if (bodyRatio(latest).compareTo(breakoutBodyRatioThreshold) < 0) {
            return Optional.empty();
        }
        // 收盘位置也要靠近突破方向一侧，避免“看起来突破，实际收得很虚”。
        if (bullishBreakout && closeLocation(latest).compareTo(new BigDecimal("0.65")) < 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && closeLocation(latest).compareTo(new BigDecimal("0.35")) > 0) {
            return Optional.empty();
        }
        if (bullishBreakout && !isBullish(latest)) {
            return Optional.empty();
        }
        if (!bullishBreakout && !isBearish(latest)) {
            return Optional.empty();
        }

        BigDecimal averageVolume = averageVolume(range.window());
        BigDecimal volumeRatio = ratio(volumeOf(latest), averageVolume);
        // 放量是“被接受突破”的重要确认条件。
        if (volumeRatio.compareTo(breakoutVolumeMultiplier) < 0) {
            return Optional.empty();
        }

        BigDecimal extensionCap = bullishBreakout
                ? boundary.multiply(ONE.add(breakoutMaxExtension))
                : boundary.multiply(ONE.subtract(breakoutMaxExtension));
        // 过度远离边界的K线容易是情绪释放，追进去的性价比不高。
        if (bullishBreakout && close.compareTo(extensionCap) > 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && close.compareTo(extensionCap) < 0) {
            return Optional.empty();
        }

        BigDecimal invalidationPrice = bullishBreakout
                ? boundary.multiply(ONE.subtract(breakoutFailureBuffer))
                : boundary.multiply(ONE.add(breakoutFailureBuffer));
        BigDecimal measuredMoveTarget = bullishBreakout
                ? range.resistance().add(range.resistance().subtract(range.support()))
                : range.support().subtract(range.resistance().subtract(range.support()));
        String title = bullishBreakout ? "BTC Confirmed Range Breakout" : "BTC Confirmed Range Breakdown";
        String type = bullishBreakout ? BREAKOUT_LONG_TYPE : BREAKOUT_SHORT_TYPE;
        String summary = bullishBreakout
                ? String.format("An established range was accepted to the upside with strong body quality and %.2fx relative volume.", volumeRatio.setScale(2, RoundingMode.HALF_UP))
                : String.format("An established range was accepted to the downside with strong body quality and %.2fx relative volume.", volumeRatio.setScale(2, RoundingMode.HALF_UP));

        return Optional.of(new AlertSignal(
                bullishBreakout ? TradeDirection.LONG : TradeDirection.SHORT,
                title,
                latest,
                type,
                summary,
                boundary.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                measuredMoveTarget.setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    /**
     * 区间识别是整套策略的前提。
     * 这里不是简单看“横盘”，而是从四个角度过滤：
     * 1. 区间宽度要合适；
     * 2. 上下边缘都要被反复测试；
     * 3. K线之间要有足够重叠；
     * 4. 均线漂移不能太大，否则更像趋势而不是区间。
     */
    private RangeContext buildRangeContext(List<BinanceKlineDTO> closedKlines) {
        if (!hasEnoughBars(closedKlines, minimumBarsRequired())) {
            return null;
        }

        // 区间判断固定排除最后一根未参与确认的K线，避免用当前信号K线定义区间本身。
        List<BinanceKlineDTO> window = trailingWindow(closedKlines, rangeLookback, 1);
        if (CollectionUtils.isEmpty(window) || window.size() < rangeLookback) {
            return null;
        }

        BigDecimal resistance = highestHigh(window);
        BigDecimal support = lowestLow(window);
        BigDecimal width = percentageDistance(resistance, support);
        if (width.compareTo(rangeMinWidth) < 0 || width.compareTo(rangeMaxWidth) > 0) {
            return null;
        }

        // 边缘触达次数不足，说明上下边界还没有被市场反复确认。
        int topTouches = countTopTouches(window, resistance);
        int bottomTouches = countBottomTouches(window, support);
        if (topTouches < requiredEdgeTouches || bottomTouches < requiredEdgeTouches) {
            return null;
        }

        // 重叠不够通常意味着波动具有单边推进特征，不适合按区间处理。
        int overlappingBars = countOverlappingBars(window);
        if (overlappingBars < minOverlapBars) {
            return null;
        }

        // 均线漂移过大说明价格重心在移动，更像趋势中的整理而不是成熟区间。
        BigDecimal fastMa = movingAverage(closedKlines, fastPeriod, 0);
        BigDecimal laggedFastMa = movingAverage(closedKlines, fastPeriod, Math.min(5, closedKlines.size() - fastPeriod));
        BigDecimal maDrift = ratio(fastMa.subtract(laggedFastMa).abs(), laggedFastMa);
        if (maDrift.compareTo(maFlatThreshold) > 0) {
            return null;
        }

        BigDecimal midpoint = resistance.add(support).divide(TWO, 8, RoundingMode.HALF_UP);
        return new RangeContext(window, support, resistance, midpoint, width);
    }

    private int minimumBarsRequired() {
        return Math.max(slowPeriod + 6, rangeLookback + fastPeriod + 6);
    }

    private boolean hasEnoughBars(List<BinanceKlineDTO> closedKlines, int minimumSize) {
        return !CollectionUtils.isEmpty(closedKlines) && closedKlines.size() >= minimumSize;
    }

    private List<BinanceKlineDTO> closedKlines(List<BinanceKlineDTO> klines) {
        // 统一只使用已收盘K线，避免未收盘K线在盘中反复变化导致假信号。
        if (CollectionUtils.isEmpty(klines) || klines.size() < 2) {
            return List.of();
        }
        return klines.subList(0, klines.size() - 1);
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
        int safeOffset = Math.max(0, offset);
        int endExclusive = klines.size() - safeOffset;
        int startInclusive = endExclusive - period;
        BigDecimal total = ZERO;
        for (int i = startInclusive; i < endExclusive; i++) {
            total = total.add(valueOf(klines.get(i).getClose()));
        }
        return total.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal highestHigh(List<BinanceKlineDTO> klines) {
        BigDecimal highest = ZERO;
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
        BigDecimal total = ZERO;
        for (BinanceKlineDTO kline : klines) {
            total = total.add(volumeOf(kline));
        }
        return total.divide(BigDecimal.valueOf(klines.size()), 8, RoundingMode.HALF_UP);
    }

    private int countTopTouches(List<BinanceKlineDTO> window, BigDecimal resistance) {
        int count = 0;
        BigDecimal floor = resistance.multiply(ONE.subtract(rangeEdgeTolerance));
        for (BinanceKlineDTO kline : window) {
            if (valueOf(kline.getHigh()).compareTo(floor) >= 0) {
                count++;
            }
        }
        return count;
    }

    private int countBottomTouches(List<BinanceKlineDTO> window, BigDecimal support) {
        int count = 0;
        BigDecimal ceiling = support.multiply(ONE.add(rangeEdgeTolerance));
        for (BinanceKlineDTO kline : window) {
            if (valueOf(kline.getLow()).compareTo(ceiling) <= 0) {
                count++;
            }
        }
        return count;
    }

    private int countOverlappingBars(List<BinanceKlineDTO> window) {
        int count = 0;
        for (int i = 1; i < window.size(); i++) {
            // 相邻K线重叠越多，越符合“多空反复交换主导权”的区间特征。
            if (overlapRatio(window.get(i - 1), window.get(i)).compareTo(overlapThreshold) >= 0) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal overlapRatio(BinanceKlineDTO left, BinanceKlineDTO right) {
        BigDecimal leftHigh = valueOf(left.getHigh());
        BigDecimal leftLow = valueOf(left.getLow());
        BigDecimal rightHigh = valueOf(right.getHigh());
        BigDecimal rightLow = valueOf(right.getLow());

        BigDecimal overlapHigh = leftHigh.min(rightHigh);
        BigDecimal overlapLow = leftLow.max(rightLow);
        if (overlapHigh.compareTo(overlapLow) <= 0) {
            return ZERO;
        }

        BigDecimal overlap = overlapHigh.subtract(overlapLow);
        BigDecimal leftRange = leftHigh.subtract(leftLow);
        BigDecimal rightRange = rightHigh.subtract(rightLow);
        BigDecimal smallerRange = leftRange.min(rightRange);
        if (smallerRange.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return overlap.divide(smallerRange, 8, RoundingMode.HALF_UP);
    }

    private boolean isBullish(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).compareTo(valueOf(kline.getOpen())) > 0;
    }

    private boolean isBearish(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).compareTo(valueOf(kline.getOpen())) < 0;
    }

    private BigDecimal percentageDistance(BigDecimal high, BigDecimal low) {
        return high.subtract(low).divide(low, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal volumeOf(BinanceKlineDTO kline) {
        return valueOf(kline.getVolume());
    }

    private BigDecimal bodySize(BinanceKlineDTO kline) {
        return valueOf(kline.getClose()).subtract(valueOf(kline.getOpen())).abs();
    }

    private BigDecimal barRange(BinanceKlineDTO kline) {
        return valueOf(kline.getHigh()).subtract(valueOf(kline.getLow()));
    }

    private BigDecimal bodyRatio(BinanceKlineDTO kline) {
        BigDecimal range = barRange(kline);
        if (range.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return bodySize(kline).divide(range, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal closeLocation(BinanceKlineDTO kline) {
        BigDecimal range = barRange(kline);
        if (range.compareTo(ZERO) == 0) {
            return new BigDecimal("0.50");
        }
        return valueOf(kline.getClose()).subtract(valueOf(kline.getLow()))
                .divide(range, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal lowerWick(BinanceKlineDTO kline) {
        BigDecimal open = valueOf(kline.getOpen());
        BigDecimal close = valueOf(kline.getClose());
        BigDecimal low = valueOf(kline.getLow());
        return open.min(close).subtract(low).max(ZERO);
    }

    private BigDecimal upperWick(BinanceKlineDTO kline) {
        BigDecimal open = valueOf(kline.getOpen());
        BigDecimal close = valueOf(kline.getClose());
        BigDecimal high = valueOf(kline.getHigh());
        return high.subtract(open.max(close)).max(ZERO);
    }

    private BigDecimal valueOf(String value) {
        return new BigDecimal(value);
    }

    private BigDecimal scaleOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record RangeContext(List<BinanceKlineDTO> window,
                                BigDecimal support,
                                BigDecimal resistance,
                                BigDecimal midpoint,
                                BigDecimal width) {
        // window 是用于定义区间的历史观察窗口；
        // support / resistance / midpoint 则是后续信号判定直接依赖的关键价格区域。
    }
}
