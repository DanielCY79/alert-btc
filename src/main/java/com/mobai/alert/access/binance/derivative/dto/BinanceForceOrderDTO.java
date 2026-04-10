package com.mobai.alert.access.binance.derivative.dto;

/**
 * 寮哄钩浜嬩欢 DTO銆? */
public class BinanceForceOrderDTO {

    private String symbol;
    private String side;
    private String orderType;
    private String timeInForce;
    private String originalQuantity;
    private String price;
    private String averagePrice;
    private String orderStatus;
    private String lastFilledQuantity;
    private String accumulatedFilledQuantity;
    private Long tradeTime;
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

