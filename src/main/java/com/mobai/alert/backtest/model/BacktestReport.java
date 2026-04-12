package com.mobai.alert.backtest.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 单次回测结果报告。
 * 包含交易明细、信号分布和收益回撤等核心统计结果。
 */
public record BacktestReport(int barCount,
                             List<TradeRecord> trades,
                             Map<String, Integer> signalCounts,
                             Map<String, Integer> blockedSignalCounts,
                             int rawSignalCount,
                             int blockedSignalCount,
                             boolean compositePolicyApplied,
                             int tradeCount,
                             BigDecimal winRate,
                             BigDecimal averageR,
                             BigDecimal totalR,
                             BigDecimal profitFactor,
                             BigDecimal maxDrawdownR,
                             BacktestConfig config) {
}
