package com.mobai.alert.backtest;

import com.mobai.alert.backtest.model.BatchBacktestResult;
import org.springframework.stereotype.Service;

/**
 * 回测领域门面。
 * 控制层只依赖这个入口，不直接耦合具体策略实现。
 */
@Service
public class BacktestService {

    private final BacktestStrategyRunner strategyRunner;

    public BacktestService(BacktestStrategyRunner strategyRunner) {
        this.strategyRunner = strategyRunner;
    }

    public BatchBacktestResult runDefaultBacktestBatch() {
        return strategyRunner.runDefaultBacktestBatch();
    }

    public String formatBatchResult(BatchBacktestResult result) {
        return strategyRunner.formatBatchResult(result);
    }
}
