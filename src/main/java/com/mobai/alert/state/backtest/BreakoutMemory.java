package com.mobai.alert.state.backtest;

import java.math.BigDecimal;

public record BreakoutMemory(BigDecimal breakoutLevel, BigDecimal targetPrice, long signalTime) {
}
