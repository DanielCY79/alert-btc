package com.mobai.alert.access.capitalflow.dto;

/**
 * Binance 强平订单事件对象。
 * 来自合约市场的强平 WebSocket 推送，
 * 主要用于估算短期强平簇强度以及观察极端波动时的被动止损压力。
 */
public class BinanceForceOrderDTO {

    /**
     * 交易对代码。
     */
    private String symbol;

    /**
     * 订单方向，例如 BUY 或 SELL。
     */
    private String side;

    /**
     * 订单类型。
     */
    private String orderType;

    /**
     * 订单有效方式，例如 GTC。
     */
    private String timeInForce;

    /**
     * 原始下单数量。
     */
    private String originalQuantity;

    /**
     * 委托价格。
     */
    private String price;

    /**
     * 平均成交价格。
     */
    private String averagePrice;

    /**
     * 订单状态。
     */
    private String orderStatus;

    /**
     * 最近一次成交数量。
     */
    private String lastFilledQuantity;

    /**
     * 累计成交数量。
     */
    private String accumulatedFilledQuantity;

    /**
     * 交易时间，毫秒时间戳。
     */
    private Long tradeTime;

    /**
     * 事件推送时间，毫秒时间戳。
     */
    private Long eventTime;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public String getOriginalQuantity() {
        return originalQuantity;
    }

    public void setOriginalQuantity(String originalQuantity) {
        this.originalQuantity = originalQuantity;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(String averagePrice) {
        this.averagePrice = averagePrice;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getLastFilledQuantity() {
        return lastFilledQuantity;
    }

    public void setLastFilledQuantity(String lastFilledQuantity) {
        this.lastFilledQuantity = lastFilledQuantity;
    }

    public String getAccumulatedFilledQuantity() {
        return accumulatedFilledQuantity;
    }

    public void setAccumulatedFilledQuantity(String accumulatedFilledQuantity) {
        this.accumulatedFilledQuantity = accumulatedFilledQuantity;
    }

    public Long getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(Long tradeTime) {
        this.tradeTime = tradeTime;
    }

    public Long getEventTime() {
        return eventTime;
    }

    public void setEventTime(Long eventTime) {
        this.eventTime = eventTime;
    }
}
