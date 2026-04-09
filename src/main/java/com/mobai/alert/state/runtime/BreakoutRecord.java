package com.mobai.alert.state.runtime;

import java.math.BigDecimal;

public record BreakoutRecord(BigDecimal breakoutLevel, BigDecimal targetPrice, long timestamp) {
}
