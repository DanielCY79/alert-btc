package com.mobai.alert.strategy.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 阻止上下文专用配置被当成可独立运行策略直接启动。
 */
@Component
public class StandaloneStrategyGuard implements InitializingBean {

    private final StrategyMetadata strategyMetadata;

    public StandaloneStrategyGuard(StrategyMetadata strategyMetadata) {
        this.strategyMetadata = strategyMetadata;
    }

    @Override
    public void afterPropertiesSet() {
        if (strategyMetadata.standaloneEnabled()) {
            return;
        }
        throw new IllegalStateException(
                "Strategy '" + strategyMetadata.id()
                        + "' is context-only and cannot run standalone. "
                        + "Start an execution strategy profile instead."
        );
    }
}
