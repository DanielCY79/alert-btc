package com.mobai.alert.strategy;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.state.signal.AlertSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 策略规则评估器测试，覆盖区间假突破、确认突破与回踩场景。
 */
class AlertRuleEvaluatorTests {

    private AlertRuleEvaluator evaluator;

    /**
     * 初始化评估器参数，使测试场景与生产规则保持一致。
     */
    @BeforeEach
    void setUp() {
        evaluator = new AlertRuleEvaluator();
        ReflectionTestUtils.setField(evaluator, "fastPeriod", 20);
        ReflectionTestUtils.setField(evaluator, "slowPeriod", 60);
        ReflectionTestUtils.setField(evaluator, "rangeLookback", 36);
        ReflectionTestUtils.setField(evaluator, "rangeMinWidth", new BigDecimal("0.03"));
        ReflectionTestUtils.setField(evaluator, "rangeMaxWidth", new BigDecimal("0.18"));
        ReflectionTestUtils.setField(evaluator, "rangeEdgeTolerance", new BigDecimal("0.015"));
        ReflectionTestUtils.setField(evaluator, "requiredEdgeTouches", 2);
        ReflectionTestUtils.setField(evaluator, "overlapThreshold", new BigDecimal("0.45"));
        ReflectionTestUtils.setField(evaluator, "minOverlapBars", 12);
        ReflectionTestUtils.setField(evaluator, "maFlatThreshold", new BigDecimal("0.012"));
        ReflectionTestUtils.setField(evaluator, "breakoutCloseBuffer", new BigDecimal("0.003"));
        ReflectionTestUtils.setField(evaluator, "breakoutVolumeMultiplier", new BigDecimal("1.5"));
        ReflectionTestUtils.setField(evaluator, "breakoutBodyRatioThreshold", new BigDecimal("0.45"));
        ReflectionTestUtils.setField(evaluator, "breakoutMaxExtension", new BigDecimal("0.05"));
        ReflectionTestUtils.setField(evaluator, "breakoutFailureBuffer", new BigDecimal("0.008"));
        ReflectionTestUtils.setField(evaluator, "failureProbeBuffer", new BigDecimal("0.003"));
        ReflectionTestUtils.setField(evaluator, "failureReentryBuffer", new BigDecimal("0.001"));
        ReflectionTestUtils.setField(evaluator, "failureMinWickBodyRatio", new BigDecimal("1.20"));
        ReflectionTestUtils.setField(evaluator, "pullbackTouchTolerance", new BigDecimal("0.008"));
        ReflectionTestUtils.setField(evaluator, "pullbackHoldBuffer", new BigDecimal("0.006"));
        ReflectionTestUtils.setField(evaluator, "pullbackMaxVolumeRatio", new BigDecimal("1.10"));
        ReflectionTestUtils.setField(evaluator, "breakoutFollowThroughCloseBuffer", new BigDecimal("0.001"));
        ReflectionTestUtils.setField(evaluator, "breakoutFollowThroughMinBodyRatio", new BigDecimal("0.25"));
        ReflectionTestUtils.setField(evaluator, "breakoutFollowThroughMinCloseLocation", new BigDecimal("0.55"));
        ReflectionTestUtils.setField(evaluator, "breakoutFollowThroughMinVolumeRatio", new BigDecimal("0.80"));
        ReflectionTestUtils.setField(evaluator, "secondEntryLookback", 12);
        ReflectionTestUtils.setField(evaluator, "secondEntryMinPullbackBars", 2);
        ReflectionTestUtils.setField(evaluator, "secondEntryMinBodyRatio", new BigDecimal("0.20"));
        ReflectionTestUtils.setField(evaluator, "secondEntryMinCloseLocation", new BigDecimal("0.55"));
        ReflectionTestUtils.setField(evaluator, "secondEntryInvalidationBuffer", new BigDecimal("0.001"));
    }

    /**
     * 跌破支撑后快速收回区间时，应识别为假跌破做多信号。
     */
    @Test
    void shouldTriggerFailedBreakdownLongAtRangeSupport() {
        List<BinanceKlineDTO> klines = baseRangeScenario();
        klines.add(kline(100.80, 102.40, 99.20, 102.00, "130.0", 67));
        klines.add(kline(102.10, 102.30, 101.90, 102.00, "60.0", 68));

        AlertSignal signal = evaluator.evaluateRangeFailedBreakdownLong(klines).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("RANGE_FAILURE_LONG");
        assertThat(signal.getTriggerPrice()).isEqualByComparingTo("100.00");
    }

    /**
     * 上破阻力后重新跌回区间时，应识别为假突破做空信号。
     */
    @Test
    void shouldTriggerFailedBreakoutShortAtRangeResistance() {
        List<BinanceKlineDTO> klines = baseRangeScenario();
        klines.add(kline(108.20, 110.60, 107.90, 107.95, "128.0", 67));
        klines.add(kline(108.00, 108.20, 107.80, 108.00, "60.0", 68));

        AlertSignal signal = evaluator.evaluateRangeFailedBreakoutShort(klines).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("RANGE_FAILURE_SHORT");
        assertThat(signal.getTriggerPrice()).isEqualByComparingTo("109.00");
    }

    /**
     * 突破区间并完成确认时，应给出趋势突破做多信号。
     */
    @Test
    void shouldTriggerConfirmedBullishBreakoutWhenRangeIsAccepted() {
        List<BinanceKlineDTO> klines = baseRangeScenario();
        klines.add(kline(109.40, 111.20, 108.90, 110.80, "180.0", 67));
        klines.add(kline(110.90, 111.00, 110.70, 110.85, "60.0", 68));

        AlertSignal signal = evaluator.evaluateTrendBreakout(klines).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("CONFIRMED_BREAKOUT_LONG");
        assertThat(signal.getTriggerPrice()).isEqualByComparingTo("109.00");
    }

    /**
     * 强势突破后还需要 follow-through 继续站稳，才应升级为成熟突破背景。
     */
    @Test
    void shouldConfirmBreakoutOnlyAfterFollowThroughBarAppears() {
        List<BinanceKlineDTO> klines = baseRangeScenario();
        klines.add(kline(109.40, 111.20, 108.90, 110.80, "180.0", 67));
        klines.add(kline(110.70, 111.60, 110.40, 111.30, "95.0", 68));
        klines.add(kline(111.10, 111.20, 111.00, 111.05, "60.0", 69));

        AlertSignal signal = evaluator.evaluateBreakoutFollowThrough(
                klines,
                new BigDecimal("109.00"),
                new BigDecimal("108.13"),
                new BigDecimal("118.00"),
                true
        ).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("CONFIRMED_BREAKOUT_LONG");
        assertThat(signal.getInvalidationPrice()).isEqualByComparingTo("108.13");
    }

    /**
     * 突破成立后的缩量回踩，应触发回踩接力信号。
     */
    @Test
    void shouldTriggerBullishPullbackAfterAcceptedBreakout() {
        List<BinanceKlineDTO> klines = baseRangeScenario();
        klines.add(kline(109.40, 111.20, 108.90, 110.80, "180.0", 67));
        klines.add(kline(109.70, 110.50, 109.50, 110.20, "95.0", 68));
        klines.add(kline(110.10, 110.30, 109.90, 110.00, "60.0", 69));

        AlertSignal signal = evaluator.evaluateBreakoutPullback(klines, new BigDecimal("109.00"), true).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("BREAKOUT_PULLBACK_LONG");
        assertThat(signal.getInvalidationPrice()).isEqualByComparingTo("108.35");
    }

    @Test
    void shouldTriggerH1SecondEntryLongInBullTrend() {
        List<BinanceKlineDTO> klines = baseBullTrendScenario();
        klines.add(kline(132.60, 133.00, 131.80, 132.00, "88.0", 67));
        klines.add(kline(132.10, 132.40, 131.20, 131.60, "84.0", 68));
        klines.add(kline(131.70, 132.80, 131.40, 132.60, "92.0", 69));
        klines.add(kline(132.50, 132.70, 132.20, 132.40, "60.0", 70));

        AlertSignal signal = evaluator.evaluateSecondEntryLong(klines, null, null).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("SECOND_ENTRY_H1_LONG");
        assertThat(signal.getTriggerPrice()).isEqualByComparingTo("132.80");
    }

    @Test
    void shouldTriggerH2SecondEntryLongAfterFirstAttemptFails() {
        List<BinanceKlineDTO> klines = baseBullTrendScenario();
        klines.add(kline(132.60, 133.00, 131.80, 132.00, "88.0", 67));
        klines.add(kline(132.10, 133.05, 131.90, 132.70, "94.0", 68));
        klines.add(kline(132.40, 132.50, 131.20, 131.50, "96.0", 69));
        klines.add(kline(131.60, 132.80, 131.30, 132.50, "102.0", 70));
        klines.add(kline(132.40, 132.60, 132.10, 132.30, "60.0", 71));

        AlertSignal signal = evaluator.evaluateSecondEntryLong(klines, null, null).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("SECOND_ENTRY_H2_LONG");
        assertThat(signal.getTriggerPrice()).isEqualByComparingTo("132.80");
    }

    @Test
    void shouldTriggerL1SecondEntryShortInBearTrend() {
        List<BinanceKlineDTO> klines = baseBearTrendScenario();
        klines.add(kline(167.10, 167.90, 166.90, 167.50, "88.0", 67));
        klines.add(kline(167.40, 168.20, 167.10, 167.90, "84.0", 68));
        klines.add(kline(167.80, 168.00, 166.70, 166.90, "92.0", 69));
        klines.add(kline(166.80, 167.10, 166.60, 166.90, "60.0", 70));

        AlertSignal signal = evaluator.evaluateSecondEntryShort(klines, null, null).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("SECOND_ENTRY_L1_SHORT");
        assertThat(signal.getTriggerPrice()).isEqualByComparingTo("166.70");
    }

    @Test
    void shouldTriggerL2SecondEntryShortAfterFirstAttemptFails() {
        List<BinanceKlineDTO> klines = baseBearTrendScenario();
        klines.add(kline(167.10, 167.90, 167.20, 167.50, "88.0", 67));
        klines.add(kline(167.40, 168.00, 167.05, 167.10, "94.0", 68));
        klines.add(kline(167.20, 168.30, 167.15, 167.90, "96.0", 69));
        klines.add(kline(167.80, 168.00, 166.95, 167.00, "102.0", 70));
        klines.add(kline(166.80, 167.00, 166.50, 166.70, "60.0", 71));

        AlertSignal signal = evaluator.evaluateSecondEntryShort(klines, null, null).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("SECOND_ENTRY_L2_SHORT");
        assertThat(signal.getTriggerPrice()).isEqualByComparingTo("166.95");
    }

    /**
     * 生成基础区间盘整行情，用于覆盖不同衍生场景。
     */
    private List<BinanceKlineDTO> baseRangeScenario() {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        double[] closes = {
                104.0, 105.0, 106.0, 107.0, 108.0, 107.0, 106.0, 105.0,
                104.0, 103.0, 102.0, 101.0, 102.0, 103.0, 104.0, 105.0,
                106.0, 107.0, 108.0, 107.0, 106.0, 105.0, 104.0, 103.0
        };

        for (int i = 0; i < 66; i++) {
            double close = closes[i % closes.length];
            double open = close >= 105.0 ? close - 0.30 : close + 0.30;
            double high = close + 1.00;
            double low = close - 1.00;
            klines.add(kline(open, high, low, close, "100.0", i + 1L));
        }
        return klines;
    }

    private List<BinanceKlineDTO> baseBullTrendScenario() {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        double close = 100.0;
        for (int i = 0; i < 66; i++) {
            double open = close - 0.40;
            double high = close + 0.60;
            double low = close - 1.00;
            klines.add(kline(open, high, low, close, "100.0", i + 1L));
            close += 0.50;
        }
        return klines;
    }

    private List<BinanceKlineDTO> baseBearTrendScenario() {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        double close = 200.0;
        for (int i = 0; i < 66; i++) {
            double open = close + 0.40;
            double high = close + 1.00;
            double low = close - 0.60;
            klines.add(kline(open, high, low, close, "100.0", i + 1L));
            close -= 0.50;
        }
        return klines;
    }

    /**
     * 构造单根测试 K 线数据。
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

