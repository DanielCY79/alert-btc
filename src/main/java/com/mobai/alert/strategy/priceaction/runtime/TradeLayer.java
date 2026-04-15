package com.mobai.alert.strategy.priceaction.runtime;

/**
 * 仓位层级。
 * PROBE 表示试错层，小仓、快证伪；
 * PROFIT 表示利润层，确认后沿趋势持有。
 */
public enum TradeLayer {
    PROBE,
    PROFIT
}
