package com.mobai.alert.access.binance.kline.dto;

/**
 * Binance K 绾?DTO銆? */
public class BinanceKlineDTO {

    /**
     * 浜ゆ槗瀵癸紝渚嬪 BTCUSDT銆?     */
    private String symbol;

    /**
     * K 绾垮懆鏈燂紝渚嬪 5m / 15m / 4h銆?     */
    private String interval;

    /**
     * 璧峰鏃堕棿锛屾绉掓椂闂存埑銆?     */
    private Long startTime;

    /**
     * 缁撴潫鏃堕棿锛屾绉掓椂闂存埑銆?     */
    private Long endTime;

    /**
     * 璇锋眰鏃跺尯銆?     */
    private String timeZone;

    /**
     * 鎷夊彇鏁伴噺闄愬埗銆?     */
    private Integer limit;

    /**
     * 鏈€浣庝环銆?     */
    private String low;

    /**
     * 鏈€楂樹环銆?     */
    private String high;

    /**
     * 寮€鐩樹环銆?     */
    private String open;

    /**
     * 鏀剁洏浠枫€?     */
    private String close;

    /**
     * 鎴愪氦閲忋€?     */
    private String amount;

    /**
     * 鎴愪氦棰濄€?     */
    private String volume;

    private String openTime;
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

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
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

