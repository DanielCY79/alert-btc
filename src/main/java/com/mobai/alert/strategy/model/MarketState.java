package com.mobai.alert.strategy.model;

/**
 * 显式市场状态枚举。
 */
public enum MarketState {
    UNKNOWN("未定"),
    RANGE("区间"),
    BREAKOUT("突破"),
    PULLBACK("回踩"),
    TREND("趋势");

    private final String label;

    MarketState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
