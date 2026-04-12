package com.mobai.alert.strategy.priceaction.shared;

import java.math.BigDecimal;

/**
 * 策略参数快照。
 *
 * 这个类的作用，是把当前生效中的所有策略参数收拢成一个只读对象，
 * 再统一传给各个具体策略类使用。
 *
 * 这样做的好处有三个：
 * 1. 具体策略类不需要直接依赖 Spring 配置注入，职责更纯粹；
 * 2. 回测、实时监控、单元测试都能复用同一套参数结构；
 * 3. 当策略类拆分以后，参数仍然可以通过一个对象整体传递，避免方法签名越来越长。
 *
 * 你可以把它理解成：
 * “当前这一次策略计算所使用的全部规则参数集合”。
 */
public record StrategySettings(int fastPeriod,
                               int slowPeriod,
                               int rangeLookback,
                               BigDecimal rangeMinWidth,
                               BigDecimal rangeMaxWidth,
                               BigDecimal rangeEdgeTolerance,
                               int requiredEdgeTouches,
                               BigDecimal overlapThreshold,
                               int minOverlapBars,
                               BigDecimal maFlatThreshold,
                               BigDecimal breakoutCloseBuffer,
                               BigDecimal breakoutVolumeMultiplier,
                               BigDecimal breakoutBodyRatioThreshold,
                               BigDecimal breakoutMaxExtension,
                               BigDecimal breakoutFailureBuffer,
                               BigDecimal failureProbeBuffer,
                               BigDecimal failureReentryBuffer,
                               BigDecimal failureMinWickBodyRatio,
                               BigDecimal pullbackTouchTolerance,
                               BigDecimal pullbackHoldBuffer,
                               BigDecimal pullbackMaxVolumeRatio,
                               BigDecimal breakoutFollowThroughCloseBuffer,
                               BigDecimal breakoutFollowThroughMinBodyRatio,
                               BigDecimal breakoutFollowThroughMinCloseLocation,
                               BigDecimal breakoutFollowThroughMinVolumeRatio,
                               int secondEntryLookback,
                               int secondEntryMinPullbackBars,
                               BigDecimal secondEntryMinBodyRatio,
                               BigDecimal secondEntryMinCloseLocation,
                               BigDecimal secondEntryInvalidationBuffer) {
    /*
     * 字段大体可分为五组：
     * 1. trend：趋势背景判断
     * 2. range：区间识别
     * 3. breakout：确认突破
     * 4. failure：失败突破
     * 5. pullback：突破后回踩
     *
     * 具体解释已经在 PriceActionSignalEvaluator 的属性区写了详细中文注释，
     * 这里不重复逐项展开，保持这个参数对象本身简洁。
     */
}
