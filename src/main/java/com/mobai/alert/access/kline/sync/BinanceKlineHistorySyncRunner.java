package com.mobai.alert.access.kline.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class BinanceKlineHistorySyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BinanceKlineHistorySyncRunner.class);

    private final KlineSchemaInitializer schemaInitializer;
    private final BinanceKlineHistorySyncService syncService;
    private final ConfigurableApplicationContext applicationContext;

    public BinanceKlineHistorySyncRunner(KlineSchemaInitializer schemaInitializer,
                                         BinanceKlineHistorySyncService syncService,
                                         ConfigurableApplicationContext applicationContext) {
        this.schemaInitializer = schemaInitializer;
        this.syncService = syncService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            log.info("BTCUSDT kline history sync started");
            schemaInitializer.initialize();
            syncService.syncRecentHistory();
            log.info("BTCUSDT kline history sync completed");
        } catch (Exception ex) {
            exitCode = 1;
            log.error("BTCUSDT kline history sync failed", ex);
        } finally {
            int finalExitCode = exitCode;
            SpringApplication.exit(applicationContext, () -> finalExitCode);
        }
    }
}
