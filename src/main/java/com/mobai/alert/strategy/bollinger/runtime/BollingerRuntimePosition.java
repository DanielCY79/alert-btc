package com.mobai.alert.strategy.bollinger.runtime;

import java.math.BigDecimal;

public record BollingerRuntimePosition(String signalType,
                                       long signalTime,
                                       BigDecimal entryPrice,
                                       BigDecimal stopPrice) {
}
