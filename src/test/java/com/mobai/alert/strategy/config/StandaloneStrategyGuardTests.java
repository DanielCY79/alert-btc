package com.mobai.alert.strategy.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandaloneStrategyGuardTests {

    @Test
    void shouldAllowStandaloneStrategy() {
        StandaloneStrategyGuard guard = new StandaloneStrategyGuard(
                new StrategyMetadata("priceaction-3m-4h", "Price Action 3m/4h", true)
        );

        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectContextOnlyStrategy() {
        StandaloneStrategyGuard guard = new StandaloneStrategyGuard(
                new StrategyMetadata("priceaction-context-only", "Price Action Context", false)
        );

        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot run standalone")
                .hasMessageContaining("priceaction-context-only");
    }
}
