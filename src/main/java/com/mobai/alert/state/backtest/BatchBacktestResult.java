package com.mobai.alert.state.backtest;

import java.util.List;

public record BatchBacktestResult(int barCount,
                                  BacktestReport baseline,
                                  List<SensitivityResult> variants) {
}
