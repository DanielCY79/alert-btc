package com.mobai.alert.access.capitalflow.dto;

/**
 * Binance 头部交易者多空比对象。
 * 这个结构既可表示头部账户多空比，也可表示头部持仓多空比，
 * 具体含义取决于调用的接口来源。
 */
public class BinanceTopTraderRatioDTO {

    /**
     * 交易对代码。
     */
    private String symbol;

    /**
     * 多空比值。
     */
    private String longShortRatio;

    /**
     * 多头占比或多头账户占比。
     */
    private String longAccount;

    /**
     * 空头占比或空头账户占比。
     */
    private String shortAccount;

    /**
     * 统计时间戳。
     */
    private Long timestamp;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getLongShortRatio() {
        return longShortRatio;
    }

    public void setLongShortRatio(String longShortRatio) {
        this.longShortRatio = longShortRatio;
    }

    public String getLongAccount() {
        return longAccount;
    }

    public void setLongAccount(String longAccount) {
        this.longAccount = longAccount;
    }

    public String getShortAccount() {
        return shortAccount;
    }

    public void setShortAccount(String shortAccount) {
        this.shortAccount = shortAccount;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
