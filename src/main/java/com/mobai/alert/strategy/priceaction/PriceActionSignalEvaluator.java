package com.mobai.alert.strategy.priceaction;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.priceaction.breakout.BreakoutFollowThroughStrategyEvaluator;
import com.mobai.alert.strategy.priceaction.breakout.ConfirmedBreakoutStrategyEvaluator;
import com.mobai.alert.strategy.priceaction.pullback.BreakoutPullbackStrategyEvaluator;
import com.mobai.alert.strategy.priceaction.pullback.SecondEntryStrategyEvaluator;
import com.mobai.alert.strategy.priceaction.range.RangeFailureStrategyEvaluator;
import com.mobai.alert.strategy.priceaction.shared.StrategySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(value = "monitoring.strategy.type", havingValue = "priceaction", matchIfMissing = true)
public class PriceActionSignalEvaluator {
    /*
     * 这是策略层的统一门面类。
     * 它本身不承载所有策略细节，而是负责：
     * 1. 持有当前策略参数。
     * 2. 生成参数快照。
     * 3. 把请求分发到三个独立策略类：
     *    - 区间失败突破
     *    - 确认突破
     *    - 突破后回踩
     *
     * 用通俗的话来说，这套策略的思路是：
     * - 市场在区间里时，优先看边缘的失败突破。
     * - 市场真突破时，不是只看“碰一下边界”，而是看“有没有被接受”。
     * - 市场突破后，更舒服的入场点通常不是第一根突破 K 线，而是第一次回踩确认。
     */

    private final RangeFailureStrategyEvaluator rangeFailureStrategyEvaluator = new RangeFailureStrategyEvaluator();
    private final ConfirmedBreakoutStrategyEvaluator confirmedBreakoutStrategyEvaluator = new ConfirmedBreakoutStrategyEvaluator();
    private final BreakoutFollowThroughStrategyEvaluator breakoutFollowThroughStrategyEvaluator = new BreakoutFollowThroughStrategyEvaluator();
    private final BreakoutPullbackStrategyEvaluator breakoutPullbackStrategyEvaluator = new BreakoutPullbackStrategyEvaluator();
    private final SecondEntryStrategyEvaluator secondEntryStrategyEvaluator = new SecondEntryStrategyEvaluator();

    // 快速均线周期，用来衡量短期价格重心。
    @Value("${monitoring.strategy.trend.fast-period:20}")
    private int fastPeriod;

    // 慢速均线周期，用来确保样本长度和趋势背景足够稳定。
    @Value("${monitoring.strategy.trend.slow-period:60}")
    private int slowPeriod;

    // 区间识别回看长度，决定最近多少根 K 线用于定义支撑、阻力和中轴。
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

    // 相邻 K 线最小重叠比例，重叠越多越符合交易区间的来回交换特征。
    @Value("${monitoring.strategy.range.overlap-threshold:0.45}")
    private BigDecimal overlapThreshold;

    // 至少需要多少根 K 线满足重叠条件，避免把单边推进误判成区间。
    @Value("${monitoring.strategy.range.min-overlap-bars:12}")
    private int minOverlapBars;

    // 快速均线允许的最大漂移比例，超过这个值说明价格重心在明显移动。
    @Value("${monitoring.strategy.range.ma-flat-threshold:0.012}")
    private BigDecimal maFlatThreshold;

    // 确认突破时，收盘必须站上或跌破边界的最小缓冲比例。
    @Value("${monitoring.strategy.breakout.close-buffer:0.003}")
    private BigDecimal breakoutCloseBuffer;

    // 确认突破时需要达到的相对放量倍数。
    @Value("${monitoring.strategy.breakout.volume-multiplier:1.5}")
    private BigDecimal breakoutVolumeMultiplier;

    // 突破 K 线实体占整根 K 线波动的最小比例，用来过滤长影线假突破。
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

    // 回踩确认时，收盘允许跌回或站回突破位另一侧的最大容忍范围。
    @Value("${monitoring.strategy.pullback.hold-buffer:0.006}")
    private BigDecimal pullbackHoldBuffer;

    // 回踩阶段允许的最大相对量能，策略更偏好缩量回踩，而不是再次剧烈对冲。
    @Value("${monitoring.strategy.pullback.max-volume-ratio:1.10}")
    private BigDecimal pullbackMaxVolumeRatio;

    // follow-through 确认时，收盘至少要继续守在突破位之外的缓冲比例。
    @Value("${monitoring.strategy.breakout.follow-through.close-buffer:0.001}")
    private BigDecimal breakoutFollowThroughCloseBuffer;

    // follow-through K 线最小实体占比。
    @Value("${monitoring.strategy.breakout.follow-through.min-body-ratio:0.25}")
    private BigDecimal breakoutFollowThroughMinBodyRatio;

    // follow-through 收盘位置要求，做多看高位收盘，做空看低位收盘。
    @Value("${monitoring.strategy.breakout.follow-through.min-close-location:0.55}")
    private BigDecimal breakoutFollowThroughMinCloseLocation;

    // follow-through 不再要求放量突破，但至少不能完全失速。
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

    /**
     * 对外暴露“区间下破失败做多”策略。
     * 调用方不需要知道内部实现落在哪个子类，只需要从这里拿结果即可。
     */
    public Optional<AlertSignal> evaluateRangeFailedBreakdownLong(List<BinanceKlineDTO> klines) {
        return rangeFailureStrategyEvaluator.evaluateRangeFailedBreakdownLong(klines, currentSettings());
    }

    /**
     * 对外暴露“区间上破失败做空”策略。
     */
    public Optional<AlertSignal> evaluateRangeFailedBreakoutShort(List<BinanceKlineDTO> klines) {
        return rangeFailureStrategyEvaluator.evaluateRangeFailedBreakoutShort(klines, currentSettings());
    }

    /**
     * 对外暴露“区间确认突破做多”策略。
     */
    public Optional<AlertSignal> evaluateTrendBreakout(List<BinanceKlineDTO> klines) {
        return confirmedBreakoutStrategyEvaluator.evaluateTrendBreakout(klines, currentSettings());
    }

    /**
     * 对外暴露“区间确认跌破做空”策略。
     */
    public Optional<AlertSignal> evaluateTrendBreakdown(List<BinanceKlineDTO> klines) {
        return confirmedBreakoutStrategyEvaluator.evaluateTrendBreakdown(klines, currentSettings());
    }

    /**
     * 对外暴露“突破后的 follow-through 接受确认”。
     */
    public Optional<AlertSignal> evaluateBreakoutFollowThrough(List<BinanceKlineDTO> klines,
                                                               BigDecimal breakoutLevel,
                                                               BigDecimal invalidationPrice,
                                                               BigDecimal targetPrice,
                                                               boolean bullishBreakout) {
        return breakoutFollowThroughStrategyEvaluator.evaluateFollowThrough(
                klines,
                breakoutLevel,
                invalidationPrice,
                targetPrice,
                bullishBreakout,
                currentSettings()
        );
    }

    /**
     * 向上突破场景的简化调用，默认不额外传入目标位。
     */
    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel) {
        return evaluateBreakoutPullback(klines, breakoutLevel, null, true);
    }

    /**
     * 突破后回踩策略的简化调用。
     * `bullishBreakout=true` 表示向上突破后的回踩做多。
     * `bullishBreakout=false` 表示向下跌破后的反抽做空。
     */
    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel,
                                                          boolean bullishBreakout) {
        return evaluateBreakoutPullback(klines, breakoutLevel, null, bullishBreakout);
    }

    /**
     * 对外暴露“突破后回踩确认”策略的完整调用形式。
     * 这是三类策略里最偏“顺势二次入场”的一种。
     */
    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel,
                                                          BigDecimal targetPrice,
                                                          boolean bullishBreakout) {
        return breakoutPullbackStrategyEvaluator.evaluateBreakoutPullback(
                klines,
                breakoutLevel,
                targetPrice,
                bullishBreakout,
                currentSettings()
        );
    }

    public Optional<AlertSignal> evaluateSecondEntryLong(List<BinanceKlineDTO> klines,
                                                         BigDecimal referenceLevel,
                                                         BigDecimal targetPrice) {
        return secondEntryStrategyEvaluator.evaluateLongSecondEntry(
                klines,
                referenceLevel,
                targetPrice,
                currentSettings()
        );
    }

    public Optional<AlertSignal> evaluateSecondEntryShort(List<BinanceKlineDTO> klines,
                                                          BigDecimal referenceLevel,
                                                          BigDecimal targetPrice) {
        return secondEntryStrategyEvaluator.evaluateShortSecondEntry(
                klines,
                referenceLevel,
                targetPrice,
                currentSettings()
        );
    }

    private StrategySettings currentSettings() {
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
}

