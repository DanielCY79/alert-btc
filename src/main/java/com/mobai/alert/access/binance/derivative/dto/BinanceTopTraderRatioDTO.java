package com.mobai.alert.access.binance.derivative.dto;

/**
 * 澶撮儴璐︽埛 / 澶撮儴浠撲綅澶氱┖姣?DTO銆? */
public class BinanceTopTraderRatioDTO {

    private String symbol;
    private String longShortRatio;
    private String longAccount;
    private String shortAccount;
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

