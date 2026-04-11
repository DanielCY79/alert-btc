package com.mobai.alert.feature.extractor;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.model.PriceFeatures;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 价格特征提取器测试，覆盖均线、波动率与突破强度等核心指标。
 */
class PriceFeatureExtractorTests {

    /**
     * 关闭中的最后一根 K 线不应参与计算，特征应基于已收盘数据生成。
     */
    @Test
    void shouldExtractCorePriceFeaturesFromClosedBars() {
        PriceFeatureExtractor extractor = new PriceFeatureExtractor();
        configure(extractor);

        List<BinanceKlineDTO> klines = new ArrayList<>();
        klines.add(kline(99, 101, 98, 100, "10", 1));
        klines.add(kline(100, 103, 99, 102, "10", 2));
        klines.add(kline(102, 103, 100, 101, "10", 3));
        klines.add(kline(101, 104, 100, 103, "12", 4));
        klines.add(kline(103, 105, 102, 104, "12", 5));
        klines.add(kline(104, 107, 103, 106, "18", 6));
        klines.add(kline(106, 109, 105, 108, "30", 7));
        klines.add(kline(108, 110, 107, 109, "8", 8));

        PriceFeatures features = extractor.extract("BTCUSDT", "4h", klines);

        assertThat(features.getAsOfTime()).isEqualTo(7L);
        assertThat(features.getClosePrice()).isEqualByComparingTo("108.00");
        assertThat(features.getReturn1Bar()).isEqualByComparingTo("0.01886792");
        assertThat(features.getReturn3Bar()).isEqualByComparingTo("0.04854369");
        assertThat(features.getFastMa()).isEqualByComparingTo("106.00000000");
        assertThat(features.getSlowMa()).isEqualByComparingTo("104.40000000");
        assertThat(features.getVolumeRatio()).isEqualByComparingTo("2.14285714");
        assertThat(features.getCloseLocation()).isEqualByComparingTo("0.75000000");
        assertThat(features.getBreakoutStrengthScore()).isEqualByComparingTo("0.66666667");
        assertThat(features.getAtrPct()).isNotNull();
    }

    /**
     * 注入测试所需的参数阈值，保持提取逻辑可重复。
     */
    private void configure(PriceFeatureExtractor extractor) {
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
        ReflectionTestUtils.setField(extractor, "secondEntryLookback", 8);
        ReflectionTestUtils.setField(extractor, "secondEntryMinPullbackBars", 2);
        ReflectionTestUtils.setField(extractor, "secondEntryMinBodyRatio", new BigDecimal("0.20"));
        ReflectionTestUtils.setField(extractor, "secondEntryMinCloseLocation", new BigDecimal("0.55"));
        ReflectionTestUtils.setField(extractor, "secondEntryInvalidationBuffer", new BigDecimal("0.001"));
        ReflectionTestUtils.setField(extractor, "volumeLookback", 3);
        ReflectionTestUtils.setField(extractor, "atrPeriod", 3);
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
