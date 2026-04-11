package com.mobai.alert.feature.extractor;

import com.mobai.alert.access.event.dto.MarketEventDTO;
import com.mobai.alert.feature.model.EventFeatures;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事件特征提取器测试，验证按标的过滤后的事件偏置聚合结果。
 */
class EventFeatureExtractorTests {

    /**
     * 仅应统计与目标标的相关的事件，并输出对应的偏置指标。
     */
    @Test
    void shouldFilterEventsBySymbolAndAggregateBias() {
        EventFeatureExtractor extractor = new EventFeatureExtractor();
        ReflectionTestUtils.setField(extractor, "eventLookbackMs", 6 * 60 * 60 * 1000L);
        ReflectionTestUtils.setField(extractor, "eventHalfLifeMs", 2 * 60 * 60 * 1000L);

        long anchorTime = Instant.parse("2026-04-11T12:00:00Z").toEpochMilli();
        EventFeatures features = extractor.extract("BTCUSDT", anchorTime, List.of(
                event("BTC", "listing", "bullish", "binance_cms", 0.95, 1.0, "2026-04-11T11:30:00Z"),
                event("MARKET", "regulation", "bearish", "social", 0.80, 0.90, "2026-04-11T11:45:00Z"),
                event("ETH", "listing", "bullish", "binance_cms", 0.95, 1.0, "2026-04-11T11:50:00Z")
        ));

        assertThat(features.isHasRelevantEvents()).isTrue();
        assertThat(features.getRelevantEventCount()).isEqualTo(2);
        assertThat(features.getBullishEventCount()).isEqualTo(1);
        assertThat(features.getBearishEventCount()).isEqualTo(1);
        assertThat(features.getLatestEventType()).isEqualTo("regulation");
        assertThat(features.getLatestEventSource()).isEqualTo("social");
        assertThat(features.getLatestEventAgeMs()).isEqualTo(15 * 60 * 1000L);
        assertThat(features.getEventShockScore()).isPositive();
    }

    /**
     * 构造统一的市场事件样本，便于覆盖不同事件组合。
     */
    private MarketEventDTO event(String entity,
                                 String type,
                                 String sentiment,
                                 String source,
                                 double confidence,
                                 double novelty,
                                 String eventTime) {
        MarketEventDTO dto = new MarketEventDTO();
        dto.setEntity(entity);
        dto.setEventType(type);
        dto.setSentiment(sentiment);
        dto.setSource(source);
        dto.setConfidence(confidence);
        dto.setNovelty(novelty);
        dto.setEventTime(Instant.parse(eventTime));
        return dto;
    }
}
