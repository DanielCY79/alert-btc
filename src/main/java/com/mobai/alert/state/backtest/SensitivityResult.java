package com.mobai.alert.state.backtest;

import java.math.BigDecimal;

public record SensitivityResult(String label,
                                BacktestReport report,
                                BigDecimal baselineTotalR) {
}
