package com.mobai.alert.strategy.policy;

import com.mobai.alert.state.signal.AlertSignal;

import java.math.BigDecimal;
import java.util.List;

/**
 * 复合因子策略的决策结果。
 * 包含是否放行、富化后的信号、上下文分数和决策原因。
 */
public record SignalPolicyDecision(boolean allowed,
                                   AlertSignal signal,
                                   BigDecimal score,
                                   List<String> reasons) {
}
