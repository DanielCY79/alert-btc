package com.mobai.alert.control;

import com.mobai.alert.state.backtest.BatchBacktestResult;
import com.mobai.alert.strategy.backtest.StrategyBacktestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "backtest.enabled", havingValue = "true")
public class BacktestRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BacktestRunner.class);

    private final StrategyBacktestService strategyBacktestService;
    private final ConfigurableApplicationContext applicationContext;

    public BacktestRunner(StrategyBacktestService strategyBacktestService,
                          ConfigurableApplicationContext applicationContext) {
        this.strategyBacktestService = strategyBacktestService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("回测任务开始执行");
        BatchBacktestResult result = strategyBacktestService.runDefaultBacktestBatch();
        log.info("回测任务执行完成，结果如下：\n{}", strategyBacktestService.formatBatchResult(result));
        SpringApplication.exit(applicationContext, () -> 0);
    }
}
