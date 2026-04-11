package com.mobai.alert.strategy.policy;

import java.math.BigDecimal;

/**
 * 单类策略的因子权重配置。
 * 分别描述趋势、突破、事件、拥挤度和风险的加权比例。
 */
public record PolicyWeights(BigDecimal trendWeight,
                            BigDecimal breakoutWeight,
                            BigDecimal eventWeight,
                            BigDecimal crowdingWeight,
                            BigDecimal regimeRiskWeight) {
}
