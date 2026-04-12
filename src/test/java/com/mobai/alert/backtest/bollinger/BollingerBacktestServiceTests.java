package com.mobai.alert.backtest.bollinger;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.backtest.model.BacktestConfig;
import com.mobai.alert.backtest.model.BacktestReport;
import com.mobai.alert.backtest.model.BatchBacktestResult;
import com.mobai.alert.strategy.bollinger.BollingerSignalEvaluator;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BollingerBacktestServiceTests {

    @Test
    void shouldResolveOneMinuteIntervalForBacktests() {
        assertThat(BollingerBacktestService.resolveIntervalMs("1m")).isEqualTo(60_000L);
        assertThat(BollingerBacktestService.resolveIntervalMs("4h")).isEqualTo(14_400_000L);
    }

    @Test
    void shouldCloseTradeOnTrendReversal() {
        BollingerSignalEvaluator evaluator = new BollingerSignalEvaluator();
        ReflectionTestUtils.setField(evaluator, "bollPeriod", 20);
        ReflectionTestUtils.setField(evaluator, "stddevMultiplier", new BigDecimal("2.0"));
        ReflectionTestUtils.setField(evaluator, "stopLossPct", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(evaluator, "entryVolumeLookback", 20);
        ReflectionTestUtils.setField(evaluator, "contextTrendLookbackBars", 3);
        ReflectionTestUtils.setField(evaluator, "contextMinMiddleRisePct", new BigDecimal("0.0020"));
        ReflectionTestUtils.setField(evaluator, "contextMinCloseBufferPct", new BigDecimal("0.0015"));
        ReflectionTestUtils.setField(evaluator, "contextBandwidthLookbackBars", 0);
        ReflectionTestUtils.setField(evaluator, "contextMinBandwidthExpansionPct", BigDecimal.ZERO);
        ReflectionTestUtils.setField(evaluator, "fastFailureMaxBars", 1);
        ReflectionTestUtils.setField(evaluator, "fastFailureLossPct", new BigDecimal("0.0035"));

        BollingerBacktestService service = new BollingerBacktestService((BinanceApi) null, evaluator);
        ReflectionTestUtils.setField(service, "bollPeriod", 20);
        ReflectionTestUtils.setField(service, "entryVolumeLookback", 20);
        ReflectionTestUtils.setField(service, "stopLossPct", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(service, "reentryCooldownBars", 240);
        ReflectionTestUtils.setField(service, "maxEntriesPerContextBar", 1);

        List<BinanceKlineDTO> entryHistory = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double close = 100.0 + 0.10 * i;
            entryHistory.add(kline("1m", close - 0.08, close + 0.12, close - 0.18, close, i, i + 1));
        }
        entryHistory.add(kline("1m", 103.00, 103.20, 102.90, 103.10, 30, 31));
        entryHistory.add(kline("1m", 103.10, 103.20, 102.80, 103.00, 31, 32));
        entryHistory.add(kline("1m", 103.00, 103.10, 100.00, 100.10, 32, 33));
        entryHistory.add(kline("1m", 99.90, 100.00, 99.60, 99.80, 33, 34));

        List<BinanceKlineDTO> contextHistory = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double close = 100.0 + 0.90 * i;
            contextHistory.add(kline("4h", close - 0.30, close + 0.40, close - 0.50, close, i, i + 1));
        }
        contextHistory.add(kline("4h", 127.00, 127.20, 126.80, 127.10, 30, 31));
        contextHistory.add(kline("4h", 127.10, 127.20, 126.90, 127.00, 31, 32));
        contextHistory.add(kline("4h", 127.00, 127.10, 110.00, 110.20, 32, 33));
        contextHistory.add(kline("4h", 110.20, 110.50, 109.80, 110.10, 33, 34));

        BacktestConfig config = new BacktestConfig(
                "BTCUSDT",
                "1m",
                20L,
                34L,
                20,
                20,
                20,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L,
                0,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );

        BacktestReport report = service.runBacktest(entryHistory, contextHistory, config);

        assertThat(report.tradeCount()).isEqualTo(1);
        assertThat(report.trades().get(0).exitReason()).isEqualTo("BOLLINGER_REVERSAL");
    }

    @Test
    void shouldCloseTradeOnFastFailure() {
        BollingerSignalEvaluator evaluator = new BollingerSignalEvaluator();
        ReflectionTestUtils.setField(evaluator, "bollPeriod", 20);
        ReflectionTestUtils.setField(evaluator, "stddevMultiplier", new BigDecimal("2.0"));
        ReflectionTestUtils.setField(evaluator, "stopLossPct", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(evaluator, "entryVolumeLookback", 20);
        ReflectionTestUtils.setField(evaluator, "contextTrendLookbackBars", 3);
        ReflectionTestUtils.setField(evaluator, "contextMinMiddleRisePct", new BigDecimal("0.0020"));
        ReflectionTestUtils.setField(evaluator, "contextMinCloseBufferPct", new BigDecimal("0.0015"));
        ReflectionTestUtils.setField(evaluator, "contextBandwidthLookbackBars", 0);
        ReflectionTestUtils.setField(evaluator, "contextMinBandwidthExpansionPct", BigDecimal.ZERO);
        ReflectionTestUtils.setField(evaluator, "fastFailureMaxBars", 10);
        ReflectionTestUtils.setField(evaluator, "fastFailureLossPct", new BigDecimal("0.0035"));

        BollingerBacktestService service = new BollingerBacktestService((BinanceApi) null, evaluator);
        ReflectionTestUtils.setField(service, "bollPeriod", 20);
        ReflectionTestUtils.setField(service, "entryVolumeLookback", 20);
        ReflectionTestUtils.setField(service, "stopLossPct", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(service, "reentryCooldownBars", 240);
        ReflectionTestUtils.setField(service, "maxEntriesPerContextBar", 1);

        List<BinanceKlineDTO> entryHistory = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double close = 100.0 + 0.10 * i;
            entryHistory.add(kline("1m", close - 0.08, close + 0.12, close - 0.18, close, i, i + 1));
        }
        entryHistory.add(kline("1m", 103.00, 103.10, 101.00, 101.20, 30, 31));
        entryHistory.add(kline("1m", 101.10, 101.20, 100.90, 101.00, 31, 32));

        List<BinanceKlineDTO> contextHistory = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            double close = 100.0 + 0.90 * i;
            contextHistory.add(kline("4h", close - 0.30, close + 0.40, close - 0.50, close, i, i + 1));
        }

        BacktestReport report = service.runBacktest(entryHistory, contextHistory, minimalConfig(20L, 32L));

        assertThat(report.tradeCount()).isEqualTo(1);
        assertThat(report.trades().get(0).exitReason()).isEqualTo("FAST_FAILURE");
    }

    @Test
    void shouldLimitEntriesToOnePerContextBar() {
        BinanceKlineDTO signalBar = kline("1m", 102.70, 102.90, 102.60, 102.80, 30, 31);
        AlertSignal entrySignal = new AlertSignal(
                TradeDirection.LONG,
                "4h/1m Bollinger Long",
                signalBar,
                "BOLLINGER_LONG_ENTRY",
                "mock entry",
                new BigDecimal("102.80"),
                new BigDecimal("92.52"),
                null,
                BigDecimal.ONE,
                null,
                "mock",
                new BigDecimal("102.80"),
                new BigDecimal("92.52")
        );
        AlertSignal fastFailureSignal = new AlertSignal(
                TradeDirection.LONG,
                "4h/1m Bollinger Fast Failure Exit",
                signalBar,
                "EXIT_BOLLINGER_FAST_FAILURE_LONG",
                "mock fast failure",
                new BigDecimal("101.10"),
                new BigDecimal("92.52"),
                null,
                BigDecimal.ONE,
                null,
                "mock",
                new BigDecimal("102.80"),
                new BigDecimal("92.52")
        );

        List<BinanceKlineDTO> entryHistory = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double close = 100.0 + 0.05 * i;
            entryHistory.add(kline("1m", close - 0.05, close + 0.10, close - 0.10, close, i, i + 1));
        }

        List<BinanceKlineDTO> contextHistory = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            double close = 100.0 + 0.50 * i;
            contextHistory.add(kline("4h", close - 0.20, close + 0.30, close - 0.30, close, i, i + 1));
        }
        contextHistory.add(kline("4h", 109.80, 110.20, 109.50, 110.00, 20, 300));

        BollingerSignalEvaluator unlimitedEvaluator = scriptedEvaluator(entrySignal, fastFailureSignal);
        BollingerBacktestService unlimitedService = new BollingerBacktestService((BinanceApi) null, unlimitedEvaluator);
        ReflectionTestUtils.setField(unlimitedService, "bollPeriod", 20);
        ReflectionTestUtils.setField(unlimitedService, "entryVolumeLookback", 20);
        ReflectionTestUtils.setField(unlimitedService, "stopLossPct", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(unlimitedService, "reentryCooldownBars", 0);
        ReflectionTestUtils.setField(unlimitedService, "maxEntriesPerContextBar", 0);

        BollingerSignalEvaluator limitedEvaluator = scriptedEvaluator(entrySignal, fastFailureSignal);
        BollingerBacktestService limitedService = new BollingerBacktestService((BinanceApi) null, limitedEvaluator);
        ReflectionTestUtils.setField(limitedService, "bollPeriod", 20);
        ReflectionTestUtils.setField(limitedService, "entryVolumeLookback", 20);
        ReflectionTestUtils.setField(limitedService, "stopLossPct", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(limitedService, "reentryCooldownBars", 0);
        ReflectionTestUtils.setField(limitedService, "maxEntriesPerContextBar", 1);

        BacktestReport unlimitedReport = unlimitedService.runBacktest(entryHistory, contextHistory, minimalConfig(20L, 35L));
        BacktestReport limitedReport = limitedService.runBacktest(entryHistory, contextHistory, minimalConfig(20L, 35L));

        assertThat(unlimitedReport.tradeCount()).isEqualTo(2);
        assertThat(limitedReport.tradeCount()).isEqualTo(1);
    }

    private BollingerSignalEvaluator scriptedEvaluator(AlertSignal entrySignal, AlertSignal exitSignal) {
        BollingerSignalEvaluator evaluator = mock(BollingerSignalEvaluator.class);
        AtomicInteger entryCalls = new AtomicInteger();
        AtomicInteger exitCalls = new AtomicInteger();
        when(evaluator.evaluateLongEntry(any(), any())).thenAnswer(invocation -> {
            int call = entryCalls.incrementAndGet();
            return call <= 2 ? Optional.of(entrySignal) : Optional.empty();
        });
        when(evaluator.evaluateLongExit(any(), any(), any(), any(), any(Integer.class))).thenAnswer(invocation -> {
            int call = exitCalls.incrementAndGet();
            return call <= 2 ? Optional.of(exitSignal) : Optional.empty();
        });
        return evaluator;
    }

    @Test
    void shouldFormatBollingerReportWithCapitalMetrics() {
        BollingerSignalEvaluator evaluator = new BollingerSignalEvaluator();
        BollingerBacktestService service = new BollingerBacktestService((BinanceApi) null, evaluator);
        ReflectionTestUtils.setField(service, "initialCapital", new BigDecimal("10000"));

        BacktestConfig config = new BacktestConfig(
                "BTCUSDT",
                "1m",
                0L,
                10L,
                20,
                20,
                20,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L,
                0,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
        BacktestReport report = new BacktestReport(
                10,
                List.of(new com.mobai.alert.backtest.model.TradeRecord(
                        "BOLLINGER_LONG_ENTRY",
                        com.mobai.alert.strategy.model.TradeDirection.LONG,
                        1L,
                        2L,
                        1,
                        new BigDecimal("100"),
                        new BigDecimal("90"),
                        null,
                        new BigDecimal("10"),
                        0,
                        3L,
                        new BigDecimal("110"),
                        "BOLLINGER_REVERSAL",
                        null
                )),
                java.util.Map.of("BOLLINGER_LONG_ENTRY", 1),
                java.util.Map.of(),
                1,
                0,
                false,
                1,
                new BigDecimal("1.0"),
                new BigDecimal("1.0"),
                new BigDecimal("1.0"),
                new BigDecimal("1.0"),
                BigDecimal.ZERO,
                config
        );

        String formatted = service.formatBatchResult(new BatchBacktestResult(10, report, report, List.of()));

        assertThat(formatted).contains("netProfit=1000.00 USDT");
        assertThat(formatted).contains("finalEquity=11000.00 USDT");
        assertThat(formatted).doesNotContain("totalR=");
    }

    private BacktestConfig minimalConfig(long startTime, long endTime) {
        return new BacktestConfig(
                "BTCUSDT",
                "1m",
                startTime,
                endTime,
                20,
                20,
                20,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L,
                0,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );
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
