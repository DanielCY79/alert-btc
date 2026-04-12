package com.mobai.alert.feature.service;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.capitalflow.dto.BinanceDerivativeFeaturesDTO;
import com.mobai.alert.access.event.dto.MarketEventDTO;
import com.mobai.alert.access.event.service.MarketEventService;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.extractor.CompositeFactorCalculator;
import com.mobai.alert.feature.extractor.DerivativeFeatureExtractor;
import com.mobai.alert.feature.extractor.EventFeatureExtractor;
import com.mobai.alert.feature.extractor.PriceFeatureExtractor;
import com.mobai.alert.feature.model.FeatureSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 市场特征快照服务测试，验证价格、事件和衍生品数据的统一装配。
 */
class MarketFeatureSnapshotServiceTests {

    /**
     * 构建快照后，应同时写入缓存并暴露完整质量状态。
     */
    @Test
    void shouldBuildAndCacheUnifiedFeatureSnapshot() {
        BinanceApi binanceApi = mock(BinanceApi.class);
        MarketEventService marketEventService = mock(MarketEventService.class);

        PriceFeatureExtractor priceFeatureExtractor = new PriceFeatureExtractor();
        configurePrice(priceFeatureExtractor);
        EventFeatureExtractor eventFeatureExtractor = new EventFeatureExtractor();
        ReflectionTestUtils.setField(eventFeatureExtractor, "eventLookbackMs", 6 * 60 * 60 * 1000L);
        ReflectionTestUtils.setField(eventFeatureExtractor, "eventHalfLifeMs", 2 * 60 * 60 * 1000L);

        MarketFeatureSnapshotService service = new MarketFeatureSnapshotService(
                binanceApi,
                marketEventService,
                priceFeatureExtractor,
                new DerivativeFeatureExtractor(),
                eventFeatureExtractor,
                new CompositeFactorCalculator()
        );

        BinanceDerivativeFeaturesDTO derivativeFeaturesDTO = new BinanceDerivativeFeaturesDTO();
        derivativeFeaturesDTO.setAsOfTime(Instant.parse("2026-04-11T11:00:00Z").toEpochMilli());
        derivativeFeaturesDTO.setOiDelta5m(new BigDecimal("25"));
        derivativeFeaturesDTO.setFundingZscore(new BigDecimal("1.80"));
        derivativeFeaturesDTO.setTakerBuySellImbalance(new BigDecimal("0.30"));
        derivativeFeaturesDTO.setTopTraderAccountRatioChange(new BigDecimal("0.10"));
        derivativeFeaturesDTO.setTopTraderPositionRatioChange(new BigDecimal("0.08"));
        derivativeFeaturesDTO.setLiquidationClusterIntensity(new BigDecimal("250000"));

        when(binanceApi.buildDerivativeFeatures("BTCUSDT")).thenReturn(derivativeFeaturesDTO);
        when(marketEventService.getRecentEvents()).thenReturn(List.of(
                event("BTC", "listing", "bullish", "binance_cms", 0.95, 1.0, "2026-04-11T11:30:00Z")
        ));

        FeatureSnapshot snapshot = service.buildSnapshot("BTCUSDT", "4h", klines());
        service.rememberLatestSnapshot("test-strategy", snapshot);

        assertThat(snapshot.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(snapshot.getPriceFeatures()).isNotNull();
        assertThat(snapshot.getDerivativeFeatures().getFundingZscore()).isEqualByComparingTo("1.80");
        assertThat(snapshot.getEventFeatures().getRelevantEventCount()).isEqualTo(1);
        assertThat(snapshot.getCompositeFactors().getCrowdingScore()).isNotNull();
        assertThat(snapshot.getQuality().isPriceReady()).isTrue();
        assertThat(snapshot.getQuality().isDerivativeReady()).isTrue();
        assertThat(service.getLatestSnapshot("test-strategy", "BTCUSDT")).isSameAs(snapshot);
        assertThat(service.getLatestSnapshot("test-strategy", "BTCUSDT", "4h")).isSameAs(snapshot);
    }

    /**
     * 配置价格特征提取器，使测试结果保持稳定。
     */
    private void configurePrice(PriceFeatureExtractor extractor) {
        ReflectionTestUtils.setField(extractor, "fastPeriod", 3);
        ReflectionTestUtils.setField(extractor, "slowPeriod", 5);
        ReflectionTestUtils.setField(extractor, "rangeLookback", 4);
        ReflectionTestUtils.setField(extractor, "rangeMinWidth", new BigDecimal("0.01"));
        ReflectionTestUtils.setField(extractor, "rangeMaxWidth", new BigDecimal("0.30"));
        ReflectionTestUtils.setField(extractor, "rangeEdgeTolerance", new BigDecimal("0.02"));
        ReflectionTestUtils.setField(extractor, "requiredEdgeTouches", 1);
        ReflectionTestUtils.setField(extractor, "overlapThreshold", new BigDecimal("0.20"));
        ReflectionTestUtils.setField(extractor, "minOverlapBars", 1);
        ReflectionTestUtils.setField(extractor, "maFlatThreshold", new BigDecimal("0.50"));
        ReflectionTestUtils.setField(extractor, "breakoutCloseBuffer", new BigDecimal("0.003"));
        ReflectionTestUtils.setField(extractor, "breakoutVolumeMultiplier", new BigDecimal("1.5"));
        ReflectionTestUtils.setField(extractor, "breakoutBodyRatioThreshold", new BigDecimal("0.45"));
        ReflectionTestUtils.setField(extractor, "breakoutMaxExtension", new BigDecimal("0.05"));
        ReflectionTestUtils.setField(extractor, "breakoutFailureBuffer", new BigDecimal("0.008"));
        ReflectionTestUtils.setField(extractor, "failureProbeBuffer", new BigDecimal("0.003"));
        ReflectionTestUtils.setField(extractor, "failureReentryBuffer", new BigDecimal("0.001"));
        ReflectionTestUtils.setField(extractor, "failureMinWickBodyRatio", new BigDecimal("1.20"));
        ReflectionTestUtils.setField(extractor, "pullbackTouchTolerance", new BigDecimal("0.008"));
        ReflectionTestUtils.setField(extractor, "pullbackHoldBuffer", new BigDecimal("0.006"));
        ReflectionTestUtils.setField(extractor, "pullbackMaxVolumeRatio", new BigDecimal("1.10"));
        ReflectionTestUtils.setField(extractor, "volumeLookback", 3);
        ReflectionTestUtils.setField(extractor, "atrPeriod", 3);
    }

    /**
     * 生成一组用于快照测试的 K 线序列。
     */
    private List<BinanceKlineDTO> klines() {
        long baseTime = Instant.parse("2026-04-11T05:00:00Z").toEpochMilli();
        List<BinanceKlineDTO> klines = new ArrayList<>();
        klines.add(kline(99, 101, 98, 100, "10", baseTime));
        klines.add(kline(100, 103, 99, 102, "10", baseTime + 60_000L));
        klines.add(kline(102, 103, 100, 101, "10", baseTime + 120_000L));
        klines.add(kline(101, 104, 100, 103, "12", baseTime + 180_000L));
        klines.add(kline(103, 105, 102, 104, "12", baseTime + 240_000L));
        klines.add(kline(104, 107, 103, 106, "18", baseTime + 300_000L));
        klines.add(kline(106, 109, 105, 108, "30", Instant.parse("2026-04-11T12:00:00Z").toEpochMilli()));
        klines.add(kline(108, 110, 107, 109, "8", Instant.parse("2026-04-11T12:05:00Z").toEpochMilli()));
        return klines;
    }

    /**
     * 构造市场事件样本。
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

    /**
     * 构造单根测试 K 线样本。
     */
    private BinanceKlineDTO kline(double open,
                                  double high,
                                  double low,
                                  double close,
                                  String volume,
                                  long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol("BTCUSDT");
        dto.setInterval("4h");
        dto.setOpen(String.format("%.2f", open));
        dto.setHigh(String.format("%.2f", high));
        dto.setLow(String.format("%.2f", low));
        dto.setClose(String.format("%.2f", close));
        dto.setVolume(volume);
        dto.setEndTime(endTime);
        return dto;
    }
}
