package com.mobai.alert.strategy.policy;

import com.mobai.alert.state.runtime.MarketState;

/**
 * 市场状态机的输出结果。
 */
public record MarketStateDecision(MarketState state,
                                  String comment) {
}
