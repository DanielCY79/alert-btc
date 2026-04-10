package com.mobai.alert.access.binance.derivative.dto;

/**
 * 鎸佷粨閲?DTO銆? */
public class BinanceOpenInterestDTO {

    private String symbol;
    private String openInterest;
    private Long time;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(String openInterest) {
        this.openInterest = openInterest;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }
}

