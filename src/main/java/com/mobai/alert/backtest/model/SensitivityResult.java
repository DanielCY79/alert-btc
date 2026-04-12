package com.mobai.alert.backtest.model;

import java.math.BigDecimal;

/**
 * 单个敏感性测试结果。
 * 记录变体标签、回测报告以及相对基线收益的参考值。
 */
public record SensitivityResult(String label,
                                BacktestReport report,
                                BigDecimal baselineTotalR) {
}
