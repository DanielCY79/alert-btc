package com.mobai.alert.access.binance.derivative.dto;

import java.math.BigDecimal;

/**
 * 琛嶇敓鍝佽仛鍚堢壒寰?DTO銆? */
public class BinanceDerivativeFeaturesDTO {

    private String symbol;
    private Long asOfTime;
    private BigDecimal oiDelta5m;
    private BigDecimal fundingZscore;
    private BigDecimal takerBuySellImbalance;
    private BigDecimal topTraderAccountRatioChange;
    private BigDecimal topTraderPositionRatioChange;
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

