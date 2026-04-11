package com.mobai.alert.feature.model;

import java.math.BigDecimal;

/**
 * 衍生品特征对象。
 * 统一保存资金费率、持仓变化、主动买卖量和强平压力等指标。
 */
public class DerivativeFeatures {
    /**
     * 特征时间戳。
     */
    private Long asOfTime;
    /**
     * 近 5 分钟持仓变化。
     */
    private BigDecimal oiDelta5m;
    /**
     * 资金费率 Z 分数。
     */
    private BigDecimal fundingZscore;
    /**
     * 主动买卖失衡值。
     */
    private BigDecimal takerBuySellImbalance;
    /**
     * 头部账户多空比变化。
     */
    private BigDecimal topTraderAccountRatioChange;
    /**
     * 头部持仓多空比变化。
     */
    private BigDecimal topTraderPositionRatioChange;
    /**
     * 强平簇强度。
     */
    private BigDecimal liquidationClusterIntensity;
    /**
     * 主动资金流方向分数。
     */
    private BigDecimal takerFlowScore;
    /**
     * 多空拥挤度分数。
     */
    private BigDecimal longShortCrowdingScore;
    /**
     * 爆仓压力分数。
     */
    private BigDecimal liquidationStressScore;

    public Long getAsOfTime() {
        return asOfTime;
    }

    public void setAsOfTime(Long asOfTime) {
        this.asOfTime = asOfTime;
    }

    public BigDecimal getOiDelta5m() {
        return oiDelta5m;
    }

    public void setOiDelta5m(BigDecimal oiDelta5m) {
        this.oiDelta5m = oiDelta5m;
    }

    public BigDecimal getFundingZscore() {
        return fundingZscore;
    }

    public void setFundingZscore(BigDecimal fundingZscore) {
        this.fundingZscore = fundingZscore;
    }

    public BigDecimal getTakerBuySellImbalance() {
        return takerBuySellImbalance;
    }

    public void setTakerBuySellImbalance(BigDecimal takerBuySellImbalance) {
        this.takerBuySellImbalance = takerBuySellImbalance;
    }

    public BigDecimal getTopTraderAccountRatioChange() {
        return topTraderAccountRatioChange;
    }

    public void setTopTraderAccountRatioChange(BigDecimal topTraderAccountRatioChange) {
        this.topTraderAccountRatioChange = topTraderAccountRatioChange;
    }

    public BigDecimal getTopTraderPositionRatioChange() {
        return topTraderPositionRatioChange;
    }

    public void setTopTraderPositionRatioChange(BigDecimal topTraderPositionRatioChange) {
        this.topTraderPositionRatioChange = topTraderPositionRatioChange;
    }

    public BigDecimal getLiquidationClusterIntensity() {
        return liquidationClusterIntensity;
    }

    public void setLiquidationClusterIntensity(BigDecimal liquidationClusterIntensity) {
        this.liquidationClusterIntensity = liquidationClusterIntensity;
    }

    public BigDecimal getTakerFlowScore() {
        return takerFlowScore;
    }

    public void setTakerFlowScore(BigDecimal takerFlowScore) {
        this.takerFlowScore = takerFlowScore;
    }

    public BigDecimal getLongShortCrowdingScore() {
        return longShortCrowdingScore;
    }

    public void setLongShortCrowdingScore(BigDecimal longShortCrowdingScore) {
        this.longShortCrowdingScore = longShortCrowdingScore;
    }

    public BigDecimal getLiquidationStressScore() {
        return liquidationStressScore;
    }

    public void setLiquidationStressScore(BigDecimal liquidationStressScore) {
        this.liquidationStressScore = liquidationStressScore;
    }
}
