package com.mobai.alert.access.capitalflow.dto;

/**
 * Binance 资金费率对象。
 * 用于描述某个时间点的资金费率以及对应标记价格，
 * 后续会被用来计算资金费率的相对偏离程度。
 */
public class BinanceFundingRateDTO {

    /**
     * 交易对代码。
     */
    private String symbol;

    /**
     * 资金费率值。
     */
    private String fundingRate;

    /**
     * 对应时刻的标记价格。
     */
    private String markPrice;

    /**
     * 资金费率结算时间，毫秒时间戳。
     */
    private Long fundingTime;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getFundingRate() {
        return fundingRate;
    }

    public void setFundingRate(String fundingRate) {
        this.fundingRate = fundingRate;
    }

    public String getMarkPrice() {
        return markPrice;
    }

    public void setMarkPrice(String markPrice) {
        this.markPrice = markPrice;
    }

    public Long getFundingTime() {
        return fundingTime;
    }

    public void setFundingTime(Long fundingTime) {
        this.fundingTime = fundingTime;
    }
}
