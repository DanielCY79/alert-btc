package com.mobai.alert.backtest;

import com.mobai.alert.backtest.model.BatchBacktestResult;

/**
 * 单个策略的回测执行器。
 * 当前工程先接入一套策略，后续新增策略时可继续实现这个接口。
 */
public interface BacktestStrategyRunner {

    BatchBacktestResult runDefaultBacktestBatch();

    String formatBatchResult(BatchBacktestResult result);
}
