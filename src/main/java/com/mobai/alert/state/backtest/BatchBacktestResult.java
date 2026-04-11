package com.mobai.alert.state.backtest;

import java.util.List;

/**
 * 一批回测结果的聚合对象。
 * 包含原始基线、策略过滤基线以及若干敏感性测试结果。
 */
public record BatchBacktestResult(int barCount,
                                  BacktestReport baseline,
                                  BacktestReport policyFilteredBaseline,
                                  List<SensitivityResult> variants) {
}
