package com.mobai.alert.access.capitalflow.dto;

import java.math.BigDecimal;

/**
 * Binance 衍生品特征聚合结果对象。
 * 该 DTO 不是 Binance 原始返回结构，而是项目根据多类衍生品接口和强平流，
 * 聚合出的策略辅助特征快照。
 */
public class BinanceDerivativeFeaturesDTO {

    /**
     * 特征所属交易对。
     */
    private String symbol;

    /**
     * 特征快照生成时间，毫秒时间戳。
     */
    private Long asOfTime;

    /**
     * 最近一个开窗周期内的持仓量变化值。
     */
    private BigDecimal oiDelta5m;

    /**
     * 资金费率的 Z-Score，衡量当前资金费率相对近期分布的偏离程度。
     */
    private BigDecimal fundingZscore;

    /**
     * 主动买卖量失衡值。
     * 一般用来描述主动买盘与主动卖盘的相对强弱。
     */
    private BigDecimal takerBuySellImbalance;

    /**
     * 头部账户多空比变化值。
     */
    private BigDecimal topTraderAccountRatioChange;

    /**
     * 头部持仓多空比变化值。
     */
    private BigDecimal topTraderPositionRatioChange;

    /**
     * 指定时间窗内的强平聚集强度。
     * 当前实现近似为价格与累计成交量乘积后的总和。
     */
    private BigDecimal liquidationClusterIntensity;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

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
}
