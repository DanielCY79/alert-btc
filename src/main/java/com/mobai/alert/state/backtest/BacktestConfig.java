package com.mobai.alert.state.backtest;

import com.mobai.alert.strategy.policy.CompositeFactorPolicyProfile;
import com.mobai.alert.strategy.policy.PolicyWeights;

import java.math.BigDecimal;

/**
 * 回测配置对象。
 * 聚合策略参数、持仓规则和复合因子过滤配置，方便构造不同的敏感性测试变体。
 */
public record BacktestConfig(String symbol,
                             String interval,
                             long startTime,
                             long endTime,
                             int fastPeriod,
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
                             long breakoutRecordTtlMs,
                             int rangeHoldingBars,
                             int breakoutHoldingBars,
                             int pullbackHoldingBars,
                             BigDecimal fallbackTargetMultiple,
                             BigDecimal scaleOutTriggerR,
                             BigDecimal scaleOutFraction,
                             BigDecimal trailingActivationR,
                             BigDecimal trailingDistanceR,
                             int pyramidMaxAdds,
                             BigDecimal pyramidTriggerR,
                             BigDecimal pyramidAddFraction,
                             CompositeFactorPolicyProfile policyProfile) {

    /**
     * 返回修改过区间回看长度的新配置。
     */
    public BacktestConfig withRangeLookback(int value) {
        return new BacktestConfig(symbol, interval, startTime, endTime, fastPeriod, slowPeriod, value, rangeMinWidth, rangeMaxWidth,
                rangeEdgeTolerance, requiredEdgeTouches, overlapThreshold, minOverlapBars, maFlatThreshold, breakoutCloseBuffer,
                breakoutVolumeMultiplier, breakoutBodyRatioThreshold, breakoutMaxExtension, breakoutFailureBuffer, failureProbeBuffer,
                failureReentryBuffer, failureMinWickBodyRatio, pullbackTouchTolerance, pullbackHoldBuffer, pullbackMaxVolumeRatio,
                breakoutFollowThroughCloseBuffer, breakoutFollowThroughMinBodyRatio, breakoutFollowThroughMinCloseLocation,
                breakoutFollowThroughMinVolumeRatio,
                breakoutRecordTtlMs, rangeHoldingBars, breakoutHoldingBars, pullbackHoldingBars, fallbackTargetMultiple,
                scaleOutTriggerR, scaleOutFraction, trailingActivationR, trailingDistanceR, pyramidMaxAdds, pyramidTriggerR,
                pyramidAddFraction, policyProfile);
    }

    /**
     * 返回修改过突破量比要求的新配置。
     */
    public BacktestConfig withBreakoutVolumeMultiplier(BigDecimal value) {
        return new BacktestConfig(symbol, interval, startTime, endTime, fastPeriod, slowPeriod, rangeLookback, rangeMinWidth, rangeMaxWidth,
                rangeEdgeTolerance, requiredEdgeTouches, overlapThreshold, minOverlapBars, maFlatThreshold, breakoutCloseBuffer,
                value, breakoutBodyRatioThreshold, breakoutMaxExtension, breakoutFailureBuffer, failureProbeBuffer,
                failureReentryBuffer, failureMinWickBodyRatio, pullbackTouchTolerance, pullbackHoldBuffer, pullbackMaxVolumeRatio,
                breakoutFollowThroughCloseBuffer, breakoutFollowThroughMinBodyRatio, breakoutFollowThroughMinCloseLocation,
                breakoutFollowThroughMinVolumeRatio,
                breakoutRecordTtlMs, rangeHoldingBars, breakoutHoldingBars, pullbackHoldingBars, fallbackTargetMultiple,
                scaleOutTriggerR, scaleOutFraction, trailingActivationR, trailingDistanceR, pyramidMaxAdds, pyramidTriggerR,
                pyramidAddFraction, policyProfile);
    }

    /**
     * 返回修改过突破收盘缓冲的新配置。
     */
    public BacktestConfig withBreakoutCloseBuffer(BigDecimal value) {
        return new BacktestConfig(symbol, interval, startTime, endTime, fastPeriod, slowPeriod, rangeLookback, rangeMinWidth, rangeMaxWidth,
                rangeEdgeTolerance, requiredEdgeTouches, overlapThreshold, minOverlapBars, maFlatThreshold, value,
                breakoutVolumeMultiplier, breakoutBodyRatioThreshold, breakoutMaxExtension, breakoutFailureBuffer, failureProbeBuffer,
                failureReentryBuffer, failureMinWickBodyRatio, pullbackTouchTolerance, pullbackHoldBuffer, pullbackMaxVolumeRatio,
                breakoutFollowThroughCloseBuffer, breakoutFollowThroughMinBodyRatio, breakoutFollowThroughMinCloseLocation,
                breakoutFollowThroughMinVolumeRatio,
                breakoutRecordTtlMs, rangeHoldingBars, breakoutHoldingBars, pullbackHoldingBars, fallbackTargetMultiple,
                scaleOutTriggerR, scaleOutFraction, trailingActivationR, trailingDistanceR, pyramidMaxAdds, pyramidTriggerR,
                pyramidAddFraction, policyProfile);
    }

    /**
     * 返回替换策略画像后的新配置。
     */
    public BacktestConfig withPolicyProfile(CompositeFactorPolicyProfile value) {
        return new BacktestConfig(symbol, interval, startTime, endTime, fastPeriod, slowPeriod, rangeLookback, rangeMinWidth, rangeMaxWidth,
                rangeEdgeTolerance, requiredEdgeTouches, overlapThreshold, minOverlapBars, maFlatThreshold, breakoutCloseBuffer,
                breakoutVolumeMultiplier, breakoutBodyRatioThreshold, breakoutMaxExtension, breakoutFailureBuffer, failureProbeBuffer,
                failureReentryBuffer, failureMinWickBodyRatio, pullbackTouchTolerance, pullbackHoldBuffer, pullbackMaxVolumeRatio,
                breakoutFollowThroughCloseBuffer, breakoutFollowThroughMinBodyRatio, breakoutFollowThroughMinCloseLocation,
                breakoutFollowThroughMinVolumeRatio,
                breakoutRecordTtlMs, rangeHoldingBars, breakoutHoldingBars, pullbackHoldingBars, fallbackTargetMultiple,
                scaleOutTriggerR, scaleOutFraction, trailingActivationR, trailingDistanceR, pyramidMaxAdds, pyramidTriggerR,
                pyramidAddFraction, value);
    }

    public BacktestConfig withPolicyBaseScore(BigDecimal value) {
        return withPolicyProfile(policyProfile.withBaseScore(value));
    }

    public BacktestConfig withPolicyRangeFailureMinScore(BigDecimal value) {
        return withPolicyProfile(policyProfile.withRangeFailureMinScore(value));
    }

    public BacktestConfig withPolicyBreakoutMinScore(BigDecimal value) {
        return withPolicyProfile(policyProfile.withBreakoutMinScore(value));
    }

    public BacktestConfig withPolicyPullbackMinScore(BigDecimal value) {
        return withPolicyProfile(policyProfile.withPullbackMinScore(value));
    }

    public BacktestConfig withPolicyMaxRegimeRisk(BigDecimal value) {
        return withPolicyProfile(policyProfile.withMaxRegimeRisk(value));
    }

    public BacktestConfig withPolicyCrowdingExtreme(BigDecimal value) {
        return withPolicyProfile(policyProfile.withCrowdingExtreme(value));
    }

    public BacktestConfig withPolicyNegativeBreakoutVeto(BigDecimal value) {
        return withPolicyProfile(policyProfile.withNegativeBreakoutVeto(value));
    }

    public BacktestConfig withPolicyNegativeTrendVeto(BigDecimal value) {
        return withPolicyProfile(policyProfile.withNegativeTrendVeto(value));
    }

    public BacktestConfig withPolicyNegativeEventVeto(BigDecimal value) {
        return withPolicyProfile(policyProfile.withNegativeEventVeto(value));
    }

    public BacktestConfig withPolicyEventRiskGate(BigDecimal value) {
        return withPolicyProfile(policyProfile.withEventRiskGate(value));
    }

    public BacktestConfig withPolicyCrowdedBreakoutRiskGate(BigDecimal value) {
        return withPolicyProfile(policyProfile.withCrowdedBreakoutRiskGate(value));
    }

    public BacktestConfig withPolicyMissingDerivativePenalty(BigDecimal value) {
        return withPolicyProfile(policyProfile.withMissingDerivativePenalty(value));
    }

    public BacktestConfig withPolicyRangeFailureWeights(PolicyWeights value) {
        return withPolicyProfile(policyProfile.withRangeFailureWeights(value));
    }

    public BacktestConfig withPolicyBreakoutWeights(PolicyWeights value) {
        return withPolicyProfile(policyProfile.withBreakoutWeights(value));
    }

    public BacktestConfig withPolicyPullbackWeights(PolicyWeights value) {
        return withPolicyProfile(policyProfile.withPullbackWeights(value));
    }
}
