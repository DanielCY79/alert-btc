package com.mobai.alert.control;

import com.mobai.alert.state.backtest.BatchBacktestResult;
import com.mobai.alert.strategy.backtest.BacktestExcelExportService;
import com.mobai.alert.strategy.backtest.StrategyBacktestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 回测模式下的应用启动执行器。
 * 应用启动后立即运行默认回测批次，并在结束后主动退出。
 */
@Component
@ConditionalOnProperty(value = "backtest.enabled", havingValue = "true")
public class BacktestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BacktestRunner.class);

    private final StrategyBacktestService strategyBacktestService;
    private final BacktestExcelExportService backtestExcelExportService;
    private final ConfigurableApplicationContext applicationContext;

    public BacktestRunner(StrategyBacktestService strategyBacktestService,
                          BacktestExcelExportService backtestExcelExportService,
                          ConfigurableApplicationContext applicationContext) {
        this.strategyBacktestService = strategyBacktestService;
        this.backtestExcelExportService = backtestExcelExportService;
        this.applicationContext = applicationContext;
    }

    /**
     * 启动默认回测并打印结果。
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Backtest job started");
        BatchBacktestResult result = strategyBacktestService.runDefaultBacktestBatch();
        backtestExcelExportService.exportIfEnabled(result).ifPresent(outcome ->
                log.info("Backtest capital summary: rawFinalEquity={} USDT, policyFinalEquity={} USDT, policyReturn={}%, policyMaxDrawdown={} USDT ({}%), workbook={}",
                        outcome.rawSimulation().finalEquity().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                        outcome.policySimulation().finalEquity().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                        outcome.policySimulation().totalReturnPct().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                        outcome.policySimulation().maxDrawdownAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                        outcome.policySimulation().maxDrawdownPct().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                        outcome.outputPath()));
        log.info("Backtest job finished:\n{}", strategyBacktestService.formatBatchResult(result));
        SpringApplication.exit(applicationContext, () -> 0);
    }
}
