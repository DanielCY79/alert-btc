package com.mobai.alert.strategy.priceaction.policy;

import com.mobai.alert.strategy.model.MarketState;

/**
 * 市场状态机的输出结果。
 */
public record MarketStateDecision(MarketState state,
                                  String comment) {
}
