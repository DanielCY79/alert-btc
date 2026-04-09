package com.mobai.alert.strategy;

import com.mobai.alert.access.dto.BinanceKlineDTO;
import com.mobai.alert.state.signal.AlertSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleEvaluatorTests {

    private AlertRuleEvaluator evaluator;

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
    }

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
