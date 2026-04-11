package com.mobai.alert.state.runtime;

import java.math.BigDecimal;

/**
 * 实时监控阶段缓存的突破记忆。
 * 用于在后续回踩策略里判断是否仍然存在有效突破背景。
 */
public record BreakoutRecord(BigDecimal breakoutLevel, BigDecimal targetPrice, long timestamp) {
}
