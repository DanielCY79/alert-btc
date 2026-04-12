package com.mobai.alert.strategy.bollinger;

import java.math.BigDecimal;

public record BollingerBandLevels(BigDecimal middle,
                                  BigDecimal upper,
                                  BigDecimal lower) {
}
