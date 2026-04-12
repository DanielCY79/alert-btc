package com.mobai.alert.strategy.bollinger;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BollingerSignalEvaluatorTests {

    private final BollingerSignalEvaluator evaluator = evaluator();

    @Test
    void shouldCreateLongSignalWhen4hIsAboveMiddleAnd1mSitsBetweenMiddleAndUpper() {
        List<BinanceKlineDTO> entryKlines = trendingKlines("1m", 100.0, 0.10, 30);
        List<BinanceKlineDTO> contextKlines = trendingKlines("4h", 100.0, 0.90, 30);

        AlertSignal signal = evaluator.evaluateLongEntry(entryKlines, contextKlines).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("BOLLINGER_LONG_ENTRY");
        assertThat(signal.getDirection()).isEqualTo(TradeDirection.LONG);
        assertThat(signal.getInvalidationPrice()).isEqualTo(new BigDecimal("92.61"));
    }

    @Test
    void shouldCreateExitSignalWhen4hFallsBelowMiddleAnd1mTouchesLowerBand() {
        List<BinanceKlineDTO> entryKlines = new ArrayList<>(trendingKlines("1m", 100.0, 0.10, 29));
        entryKlines.add(kline("1m", 102.90, 103.10, 100.00, 100.20, 29, 30));
        List<BinanceKlineDTO> contextKlines = new ArrayList<>(trendingKlines("4h", 100.0, 0.90, 29));
        contextKlines.add(kline("4h", 124.90, 125.10, 110.00, 110.20, 29, 30));

        AlertSignal signal = evaluator.evaluateLongExit(
                entryKlines,
                contextKlines,
                new BigDecimal("102.90"),
                new BigDecimal("92.61"),
                400
        ).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("EXIT_BOLLINGER_REVERSAL_LONG");
        assertThat(signal.getReferenceEntryPrice()).isEqualTo(new BigDecimal("102.90"));
        assertThat(signal.getReferenceStopPrice()).isEqualTo(new BigDecimal("92.61"));
    }

    @Test
    void shouldRejectLongSignalWhen4hTrendFilterIsTooWeak() {
        BollingerSignalEvaluator strictEvaluator = evaluator();
        ReflectionTestUtils.setField(strictEvaluator, "contextMinCloseBufferPct", new BigDecimal("0.0050"));

        List<BinanceKlineDTO> entryKlines = trendingKlines("1m", 100.0, 0.10, 30);
        List<BinanceKlineDTO> contextKlines = trendingKlines("4h", 100.0, 0.05, 30);

        assertThat(strictEvaluator.evaluateLongEntry(entryKlines, contextKlines)).isEmpty();
    }

    @Test
    void shouldRejectLongSignalWhen4hBandwidthIsNotExpanding() {
        BollingerSignalEvaluator strictEvaluator = evaluator();
        ReflectionTestUtils.setField(strictEvaluator, "contextBandwidthLookbackBars", 3);
        ReflectionTestUtils.setField(strictEvaluator, "contextMinBandwidthExpansionPct", new BigDecimal("0.1000"));

        List<BinanceKlineDTO> entryKlines = trendingKlines("1m", 100.0, 0.10, 30);
        List<BinanceKlineDTO> contextKlines = trendingKlines("4h", 100.0, 0.12, 30);

        assertThat(strictEvaluator.evaluateLongEntry(entryKlines, contextKlines)).isEmpty();
    }

    @Test
    void shouldCreateFastFailureExitWhen1mLosesMiddleQuickly() {
        List<BinanceKlineDTO> entryKlines = new ArrayList<>(trendingKlines("1m", 100.0, 0.10, 29));
        entryKlines.add(kline("1m", 102.70, 102.80, 101.00, 101.20, 29, 30));
        List<BinanceKlineDTO> contextKlines = trendingKlines("4h", 100.0, 0.90, 30);

        AlertSignal signal = evaluator.evaluateLongExit(
                entryKlines,
                contextKlines,
                new BigDecimal("102.90"),
                new BigDecimal("92.61"),
                3
        ).orElse(null);

        assertThat(signal).isNotNull();
        assertThat(signal.getType()).isEqualTo("EXIT_BOLLINGER_FAST_FAILURE_LONG");
        assertThat(signal.getReferenceEntryPrice()).isEqualTo(new BigDecimal("102.90"));
    }

    private BollingerSignalEvaluator evaluator() {
        BollingerSignalEvaluator signalEvaluator = new BollingerSignalEvaluator();
        ReflectionTestUtils.setField(signalEvaluator, "bollPeriod", 20);
        ReflectionTestUtils.setField(signalEvaluator, "stddevMultiplier", new BigDecimal("2.0"));
        ReflectionTestUtils.setField(signalEvaluator, "stopLossPct", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(signalEvaluator, "entryVolumeLookback", 20);
        ReflectionTestUtils.setField(signalEvaluator, "contextTrendLookbackBars", 3);
        ReflectionTestUtils.setField(signalEvaluator, "contextMinMiddleRisePct", new BigDecimal("0.0020"));
        ReflectionTestUtils.setField(signalEvaluator, "contextMinCloseBufferPct", new BigDecimal("0.0015"));
        ReflectionTestUtils.setField(signalEvaluator, "contextBandwidthLookbackBars", 0);
        ReflectionTestUtils.setField(signalEvaluator, "contextMinBandwidthExpansionPct", BigDecimal.ZERO);
        ReflectionTestUtils.setField(signalEvaluator, "fastFailureMaxBars", 360);
        ReflectionTestUtils.setField(signalEvaluator, "fastFailureLossPct", new BigDecimal("0.0035"));
        return signalEvaluator;
    }

    private List<BinanceKlineDTO> trendingKlines(String interval, double startClose, double step, int count) {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double close = startClose + step * i;
            double open = close - 0.08;
            double high = close + 0.12;
            double low = open - 0.10;
            klines.add(kline(interval, open, high, low, close, i, i + 1));
        }
        return klines;
    }

    private BinanceKlineDTO kline(String interval,
                                  double open,
                                  double high,
                                  double low,
                                  double close,
                                  long startTime,
                                  long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol("BTCUSDT");
        dto.setInterval(interval);
        dto.setOpen(String.format("%.2f", open));
        dto.setHigh(String.format("%.2f", high));
        dto.setLow(String.format("%.2f", low));
        dto.setClose(String.format("%.2f", close));
        dto.setVolume("1000.00");
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        return dto;
    }
}
