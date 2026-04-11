package com.mobai.alert.feature.model;

import java.math.BigDecimal;

/**
 * 复合因子结果对象。
 * 保存策略过滤阶段使用的核心上下文分数。
 */
public class CompositeFactors {
    /**
     * 趋势偏置分数。
     */
    private BigDecimal trendBiasScore;
    /**
     * 突破确认分数。
     */
    private BigDecimal breakoutConfirmationScore;
    /**
     * 拥挤度分数。
     */
    private BigDecimal crowdingScore;
    /**
     * 事件偏置分数。
     */
    private BigDecimal eventBiasScore;
    /**
     * 市场状态风险分数。
     */
    private BigDecimal regimeRiskScore;

    public BigDecimal getTrendBiasScore() {
        return trendBiasScore;
    }

    public void setTrendBiasScore(BigDecimal trendBiasScore) {
        this.trendBiasScore = trendBiasScore;
    }

    public BigDecimal getBreakoutConfirmationScore() {
        return breakoutConfirmationScore;
    }

    public void setBreakoutConfirmationScore(BigDecimal breakoutConfirmationScore) {
        this.breakoutConfirmationScore = breakoutConfirmationScore;
    }

    public BigDecimal getCrowdingScore() {
        return crowdingScore;
    }

    public void setCrowdingScore(BigDecimal crowdingScore) {
        this.crowdingScore = crowdingScore;
    }

    public BigDecimal getEventBiasScore() {
        return eventBiasScore;
    }

    public void setEventBiasScore(BigDecimal eventBiasScore) {
        this.eventBiasScore = eventBiasScore;
    }

    public BigDecimal getRegimeRiskScore() {
        return regimeRiskScore;
    }

    public void setRegimeRiskScore(BigDecimal regimeRiskScore) {
        this.regimeRiskScore = regimeRiskScore;
    }
}
