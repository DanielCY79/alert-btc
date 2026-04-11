package com.mobai.alert.access.kline.dto;

/**
 * Binance K 线统一传输对象。
 * 这个类同时承担两类职责：
 * 1. 作为 REST / WebSocket 请求参数载体，描述想拉取哪一个交易对、哪个周期、多少根 K 线。
 * 2. 作为标准化后的 K 线结果对象，把不同来源的原始字段整理成策略层可直接使用的结构。
 */
public class BinanceKlineDTO {

    /**
     * 交易对代码，例如 {@code BTCUSDT}。
     */
    private String symbol;

    /**
     * K 线周期，例如 {@code 5m}、{@code 15m}、{@code 4h}。
     */
    private String interval;

    /**
     * K 线开始时间，毫秒时间戳。
     * 作为请求参数时表示查询窗口起点；作为结果字段时表示当前 K 线的开盘时间。
     */
    private Long startTime;

    /**
     * K 线结束时间，毫秒时间戳。
     * 作为请求参数时表示查询窗口终点；作为结果字段时表示当前 K 线的收盘时间。
     */
    private Long endTime;

    /**
     * 查询时区参数。
     * 当前主要用于 REST 拉取历史 K 线时指定 Binance 接口的时区偏移。
     */
    private String timeZone;

    /**
     * 期望返回的 K 线数量上限。
     */
    private Integer limit;

    /**
     * 当前 K 线最低价。
     */
    private String low;

    /**
     * 当前 K 线最高价。
     */
    private String high;

    /**
     * 当前 K 线开盘价。
     */
    private String open;

    /**
     * 当前 K 线收盘价。
     */
    private String close;

    /**
     * 基础资产成交量。
     * 在 Binance K 线接口中通常对应成交数量，例如 BTC 张数或币数。
     */
    private String amount;

    /**
     * 报价资产成交额。
     * 在 Binance K 线接口中通常对应以计价货币表示的成交额，例如 USDT 成交额。
     */
    private String volume;

    /**
     * 预留的开盘时间文本字段。
     * 当前主流程未直接使用，保留给需要字符串化展示时间的调用方。
     */
    private String openTime;

    /**
     * 预留的收盘时间文本字段。
     * 当前主流程未直接使用，保留给需要字符串化展示时间的调用方。
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
