package com.mobai.alert.state.backtest;

import java.math.BigDecimal;

/**
 * 回测阶段缓存的突破记忆。
 * 用于在后续回踩检测时保留突破价、目标价和信号时间。
 */
public record BreakoutMemory(BigDecimal breakoutLevel, BigDecimal targetPrice, long signalTime) {
}
