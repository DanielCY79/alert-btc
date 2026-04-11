package com.mobai.alert.access.capitalflow.dto;

/**
 * Binance 持仓量对象。
 * 描述某一时刻指定合约的未平仓总量，
 * 常用于判断市场杠杆参与度是否在快速扩张或收缩。
 */
public class BinanceOpenInterestDTO {

    /**
     * 交易对代码。
     */
    private String symbol;

    /**
     * 未平仓总量。
     */
    private String openInterest;

    /**
     * 数据时间，毫秒时间戳。
     */
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
