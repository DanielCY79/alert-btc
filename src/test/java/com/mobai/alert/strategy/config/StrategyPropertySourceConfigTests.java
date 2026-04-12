package com.mobai.alert.strategy.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyPropertySourceConfigTests {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(StrategyPropertySourceConfig.class);

    @Test
    void shouldLoadBaseStrategyDefaults() {
        contextRunner
                .withPropertyValues("monitoring.strategy.type=priceaction")
                .run(context -> assertThat(context.getEnvironment().getProperty("backtest.holding-bars.breakout"))
                        .isEqualTo("24"));
    }

    @Test
    void shouldLoadSignalDefaultsFromPriceActionConfig() {
        contextRunner
                .withPropertyValues("monitoring.strategy.type=priceaction")
                .run(context -> assertThat(context.getEnvironment().getProperty("monitoring.strategy.breakout.close-buffer"))
                        .isEqualTo("0.003"));
    }

    @Test
    void shouldLoadBollingerDefaultsFromStrategyConfig() {
        contextRunner
                .withPropertyValues("monitoring.strategy.type=bollinger")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("monitoring.strategy.boll.entry-interval"))
                            .isEqualTo("1m");
                    assertThat(context.getEnvironment().getProperty("monitoring.strategy.default-id"))
                            .isEqualTo("bollinger-1m-4h");
                });
    }
}
