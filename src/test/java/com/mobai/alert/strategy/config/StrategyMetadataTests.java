package com.mobai.alert.strategy.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyMetadataTests {

    @Test
    void shouldUseStrategySpecificDefaultsWhenLegacyPriceActionMetadataStillConfigured() {
        StrategyMetadata metadata = new StrategyMetadata(
                "bollinger",
                "priceaction-3m-4h",
                "Price Action 3m/4h",
                "bollinger-1m-4h",
                "Bollinger 1m/4h",
                true
        );

        assertThat(metadata.id()).isEqualTo("bollinger-1m-4h");
        assertThat(metadata.label()).isEqualTo("Bollinger 1m/4h");
    }
}
