package com.mobai.alert.strategy.delphi2;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Delphi2SignalEvaluatorTests {

    private final Delphi2SignalEvaluator evaluator = evaluator();

    @Test
    void shouldCreateLongEntryWhenDailyTrendIsUpAndHourBreaksUpperBand() {
        List<BinanceKlineDTO> trendKlines = risingKlines("1d", 100.0, 1.0, 80, 1200.0);
        List<BinanceKlineDTO> entryKlines = new ArrayList<>(flatKlines("1h", 100.0, 30, 800.0));
        entryKlines.add(kline("1h", 100.60, 103.20, 100.10, 102.90, 30, 31, 1800.0));

        AlertSignal signal = evaluator.evaluateEntry(entryKlines, trendKlines).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("DELPHI2_AGGRESSIVE_LONG_ENTRY");
        assertThat(signal.getDirection()).isEqualTo(TradeDirection.LONG);
        assertThat(signal.getInvalidationPrice()).isLessThan(signal.getTriggerPrice());
    }

    @Test
    void shouldCreateShortEntryWhenDailyTrendIsDownAndHourBreaksLowerBand() {
        List<BinanceKlineDTO> trendKlines = fallingKlines("1d", 180.0, 1.1, 80, 1200.0);
        List<BinanceKlineDTO> entryKlines = new ArrayList<>(flatKlines("1h", 150.0, 30, 800.0));
        entryKlines.add(kline("1h", 149.40, 149.80, 146.30, 146.60, 30, 31, 1800.0));

        AlertSignal signal = evaluator.evaluateEntry(entryKlines, trendKlines).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("DELPHI2_AGGRESSIVE_SHORT_ENTRY");
        assertThat(signal.getDirection()).isEqualTo(TradeDirection.SHORT);
        assertThat(signal.getInvalidationPrice()).isGreaterThan(signal.getTriggerPrice());
    }

    @Test
    void shouldDetectDailyReverseCrossForLongExit() {
        Delphi2SignalEvaluator reversalEvaluator = new Delphi2SignalEvaluator();
        ReflectionTestUtils.setField(reversalEvaluator, "trendFastPeriod", 1);
        ReflectionTestUtils.setField(reversalEvaluator, "trendSlowPeriod", 2);
        List<BinanceKlineDTO> trendKlines = new ArrayList<>();
        trendKlines.add(kline("1d", 99.0, 101.0, 98.0, 100.0, 0, 1, 1200.0));
        trendKlines.add(kline("1d", 109.0, 111.0, 108.0, 110.0, 1, 2, 1200.0));
        trendKlines.add(kline("1d", 119.0, 121.0, 118.0, 120.0, 2, 3, 1200.0));
        trendKlines.add(kline("1d", 129.0, 131.0, 128.0, 130.0, 3, 4, 1200.0));
        trendKlines.add(kline("1d", 89.0, 91.0, 88.0, 90.0, 4, 5, 1200.0));

        assertThat(reversalEvaluator.hasTrendReversed(trendKlines, TradeDirection.LONG)).isTrue();
    }

    private Delphi2SignalEvaluator evaluator() {
        Delphi2SignalEvaluator signalEvaluator = new Delphi2SignalEvaluator();
        ReflectionTestUtils.setField(signalEvaluator, "trendFastPeriod", 20);
        ReflectionTestUtils.setField(signalEvaluator, "trendSlowPeriod", 50);
        ReflectionTestUtils.setField(signalEvaluator, "entryBreakoutLookback", 10);
        ReflectionTestUtils.setField(signalEvaluator, "entryAtrPeriod", 20);
        ReflectionTestUtils.setField(signalEvaluator, "entryChannelFactor", new BigDecimal("0.50"));
        ReflectionTestUtils.setField(signalEvaluator, "stopLossAtrMultiplier", new BigDecimal("2.0"));
        ReflectionTestUtils.setField(signalEvaluator, "trailingActivationAtrMultiple", new BigDecimal("4.0"));
        ReflectionTestUtils.setField(signalEvaluator, "trailingDistanceAtrMultiple", new BigDecimal("1.5"));
        ReflectionTestUtils.setField(signalEvaluator, "volumeLookback", 20);
        return signalEvaluator;
    }

    private List<BinanceKlineDTO> risingKlines(String interval,
                                               double startClose,
                                               double step,
                                               int count,
                                               double volume) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = startClose + step * i;
            klines.add(kline(interval, close - 0.4, close + 0.8, close - 0.8, close, i, i + 1, volume));
        }
        return klines;
    }

    private List<BinanceKlineDTO> fallingKlines(String interval,
                                                double startClose,
                                                double step,
                                                int count,
                                                double volume) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = startClose - step * i;
            klines.add(kline(interval, close + 0.4, close + 0.8, close - 0.8, close, i, i + 1, volume));
        }
        return klines;
    }

    private List<BinanceKlineDTO> flatKlines(String interval,
                                             double center,
                                             int count,
                                             double volume) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = center + ((i % 4) - 1.5) * 0.15;
            klines.add(kline(interval, close - 0.1, close + 0.6, close - 0.6, close, i, i + 1, volume));
        }
        return klines;
    }

    private BinanceKlineDTO kline(String interval,
                                  double open,
                                  double high,
                                  double low,
                                  double close,
                                  long startTime,
                                  long endTime,
                                  double volume) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol("BTCUSDT");
        dto.setInterval(interval);
        dto.setOpen(String.format("%.2f", open));
        dto.setHigh(String.format("%.2f", high));
        dto.setLow(String.format("%.2f", low));
        dto.setClose(String.format("%.2f", close));
        dto.setVolume(String.format("%.2f", volume));
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        return dto;
    }
}
