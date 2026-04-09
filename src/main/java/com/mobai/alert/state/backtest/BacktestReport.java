package com.mobai.alert.state.backtest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record BacktestReport(int barCount,
                             List<TradeRecord> trades,
                             Map<String, Integer> signalCounts,
                             int tradeCount,
                             BigDecimal winRate,
                             BigDecimal averageR,
                             BigDecimal totalR,
                             BigDecimal profitFactor,
                             BigDecimal maxDrawdownR,
                             BacktestConfig config) {
}
