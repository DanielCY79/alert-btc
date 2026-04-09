package com.mobai.alert.access.dto;

public class BinanceKlineDTO {
    /**
     * 交易对
     */
    private String symbol;

    /**
     * 时间间隔
     */
    private String interval;

    /**
     * 起始时间 毫秒时间戳
     */
    private Long startTime;

    /**
     * 结束时间 毫秒时间戳
     */
    private Long endTime;

    /**
     * 时区
     */
    private String timeZone;

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * 数据量限制 默认值500，最大1000
     */
    private Integer limit;

    /**
     * 最低价
     */
    private String low;

    /**
     * 最高价
     */
    private String high;

    /**
     * 开盘价
     */
    private String open;

    /**
     * 收盘价
     */
    private String close;

    public String getOpen() {
        return open;
    }

    public void setOpen(String open) {
        this.open = open;
    }

    public String getClose() {
        return close;
    }

    public void setClose(String close) {
        this.close = close;
    }

    /**
     * 成交量
     */
    private String amount;

    /**
     * 成交额
     */
    private String volume;

    /**
     * 开盘时间
     */
    private String openTime;

    /**
     * 收盘时间
     */
    private String closeTime;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getLow() {
        return low;
    }

    public void setLow(String low) {
        this.low = low;
    }

    public String getHigh() {
        return high;
    }

    public void setHigh(String high) {
        this.high = high;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public String getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(String closeTime) {
        this.closeTime = closeTime;
    }
}
