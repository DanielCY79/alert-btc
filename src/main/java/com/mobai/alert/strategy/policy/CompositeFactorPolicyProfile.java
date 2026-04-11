package com.mobai.alert.strategy.policy;

import java.math.BigDecimal;

/**
 * 复合因子策略画像。
 * 定义阈值、否决规则以及不同策略类型的权重配置。
 */
public record CompositeFactorPolicyProfile(boolean enabled,
                                           BigDecimal baseScore,
                                           BigDecimal rangeFailureMinScore,
                                           BigDecimal breakoutMinScore,
                                           BigDecimal pullbackMinScore,
                                           BigDecimal maxRegimeRisk,
                                           BigDecimal missingDerivativePenalty,
                                           BigDecimal crowdingExtreme,
                                           BigDecimal negativeBreakoutVeto,
                                           BigDecimal negativeTrendVeto,
                                           BigDecimal negativeEventVeto,
                                           BigDecimal eventRiskGate,
                                           BigDecimal crowdedBreakoutRiskGate,
                                           PolicyWeights rangeFailureWeights,
                                           PolicyWeights breakoutWeights,
                                           PolicyWeights pullbackWeights) {

    /**
     * 返回修改基础分后的新画像。
     */
    public CompositeFactorPolicyProfile withBaseScore(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                value,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改缺失衍生品惩罚的新画像。
     */
    public CompositeFactorPolicyProfile withMissingDerivativePenalty(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                value,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改区间失败最小分的新画像。
     */
    public CompositeFactorPolicyProfile withRangeFailureMinScore(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                value,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改突破最小分的新画像。
     */
    public CompositeFactorPolicyProfile withBreakoutMinScore(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                value,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改回踩最小分的新画像。
     */
    public CompositeFactorPolicyProfile withPullbackMinScore(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                value,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改最大状态风险的新画像。
     */
    public CompositeFactorPolicyProfile withMaxRegimeRisk(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                value,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改拥挤度极值阈值的新画像。
     */
    public CompositeFactorPolicyProfile withCrowdingExtreme(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                value,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改负向突破否决阈值的新画像。
     */
    public CompositeFactorPolicyProfile withNegativeBreakoutVeto(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                value,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改负向趋势否决阈值的新画像。
     */
    public CompositeFactorPolicyProfile withNegativeTrendVeto(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                value,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改负向事件否决阈值的新画像。
     */
    public CompositeFactorPolicyProfile withNegativeEventVeto(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                value,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改事件风险门限的新画像。
     */
    public CompositeFactorPolicyProfile withEventRiskGate(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                value,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改拥挤突破风险门限的新画像。
     */
    public CompositeFactorPolicyProfile withCrowdedBreakoutRiskGate(BigDecimal value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                value,
                rangeFailureWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改区间失败权重的新画像。
     */
    public CompositeFactorPolicyProfile withRangeFailureWeights(PolicyWeights value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                value,
                breakoutWeights,
                pullbackWeights
        );
    }

    /**
     * 返回修改突破权重的新画像。
     */
    public CompositeFactorPolicyProfile withBreakoutWeights(PolicyWeights value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                value,
                pullbackWeights
        );
    }

    /**
     * 返回修改回踩权重的新画像。
     */
    public CompositeFactorPolicyProfile withPullbackWeights(PolicyWeights value) {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                rangeFailureWeights,
                breakoutWeights,
                value
        );
    }
}
