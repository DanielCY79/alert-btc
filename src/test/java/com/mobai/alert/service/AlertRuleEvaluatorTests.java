package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;
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
        ReflectionTestUtils.setField(evaluator, "breakoutLookback", 20);
        ReflectionTestUtils.setField(evaluator, "breakoutMaxRange", new BigDecimal("0.05"));
        ReflectionTestUtils.setField(evaluator, "breakoutCloseBuffer", new BigDecimal("0.0015"));
        ReflectionTestUtils.setField(evaluator, "breakoutVolumeMultiplier", new BigDecimal("1.8"));
        ReflectionTestUtils.setField(evaluator, "breakoutMaxExtension", new BigDecimal("0.04"));
        ReflectionTestUtils.setField(evaluator, "breakoutFailureBuffer", new BigDecimal("0.008"));
        ReflectionTestUtils.setField(evaluator, "pullbackTouchTolerance", new BigDecimal("0.008"));
        ReflectionTestUtils.setField(evaluator, "pullbackHoldBuffer", new BigDecimal("0.006"));
        ReflectionTestUtils.setField(evaluator, "pullbackMaxVolumeRatio", new BigDecimal("1.10"));
    }

    @Test
    void shouldTriggerBreakoutSignalWhenTrendAndVolumeAlign() {
        List<BinanceKlineDTO> klines = breakoutScenario("220.0");

        AlertSignal signal = evaluator.evaluateTrendBreakout(klines).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("TREND_BREAKOUT");
        assertThat(signal.getTriggerPrice()).isEqualByComparingTo("132.10");
    }

    @Test
    void shouldRejectBreakoutWhenVolumeConfirmationIsMissing() {
        List<BinanceKlineDTO> klines = breakoutScenario("120.0");

        assertThat(evaluator.evaluateTrendBreakout(klines)).isEmpty();
    }

    @Test
    void shouldTriggerPullbackSignalAfterBreakoutRetestsFormerResistance() {
        List<BinanceKlineDTO> klines = pullbackScenario();

        AlertSignal signal = evaluator.evaluateBreakoutPullback(klines, new BigDecimal("132.00")).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("BREAKOUT_PULLBACK");
        assertThat(signal.getInvalidationPrice()).isEqualByComparingTo("131.21");
    }

    private List<BinanceKlineDTO> breakoutScenario(String breakoutVolume) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        long time = 1_700_000_000_000L;

        for (int i = 0; i < 61; i++) {
            double close = 100 + (i * 0.5);
            klines.add(kline(close - 0.3, close + 0.2, close - 0.5, close, "100.0", time));
            time += 900_000L;
        }

        double[] closes = {
                130.8, 131.2, 130.9, 131.3, 131.0,
                131.4, 131.1, 131.5, 131.2, 131.6,
                131.3, 131.7, 131.4, 131.8, 131.5,
                131.9, 131.6, 132.0, 131.7, 131.9
        };
        for (double close : closes) {
            klines.add(kline(close - 0.2, close + 0.1, close - 0.4, close, "100.0", time));
            time += 900_000L;
        }

        klines.add(kline(131.9, 133.5, 131.7, 133.2, breakoutVolume, time));
        time += 900_000L;
        klines.add(kline(133.1, 133.4, 132.9, 133.0, "50.0", time));
        return klines;
    }

    private List<BinanceKlineDTO> pullbackScenario() {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        long time = 1_700_100_000_000L;

        for (int i = 0; i < 61; i++) {
            double close = 100 + (i * 0.5);
            klines.add(kline(close - 0.3, close + 0.2, close - 0.5, close, "100.0", time));
            time += 900_000L;
        }

        double[] closes = {
                130.8, 131.2, 130.9, 131.3, 131.0,
                131.4, 131.1, 131.5, 131.2, 131.6,
                131.3, 131.7, 131.4, 131.8, 131.5,
                131.9, 131.6, 132.0, 131.7
        };
        for (double close : closes) {
            klines.add(kline(close - 0.2, close + 0.1, close - 0.4, close, "100.0", time));
            time += 900_000L;
        }

        klines.add(kline(131.9, 133.6, 131.7, 133.4, "220.0", time));
        time += 900_000L;
        klines.add(kline(132.1, 132.8, 131.95, 132.6, "90.0", time));
        time += 900_000L;
        klines.add(kline(132.5, 132.7, 132.2, 132.4, "50.0", time));
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
        dto.setInterval("15m");
        dto.setOpen(String.format("%.2f", open));
        dto.setHigh(String.format("%.2f", high));
        dto.setLow(String.format("%.2f", low));
        dto.setClose(String.format("%.2f", close));
        dto.setVolume(volume);
        dto.setEndTime(endTime);
        return dto;
    }
}
