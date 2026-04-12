package com.mobai.alert.strategy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(
        value = "classpath:strategy/${monitoring.strategy.type:priceaction}.properties",
        encoding = "UTF-8",
        ignoreResourceNotFound = false
)
public class StrategyPropertySourceConfig {
}
