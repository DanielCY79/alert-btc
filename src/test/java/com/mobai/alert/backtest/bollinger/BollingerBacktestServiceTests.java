package com.mobai.alert.backtest.bollinger;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.backtest.model.BacktestConfig;
import com.mobai.alert.backtest.model.BacktestReport;
import com.mobai.alert.strategy.bollinger.BollingerSignalEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

        BollingerBacktestService service = new BollingerBacktestService((BinanceApi) null, evaluator);
        ReflectionTestUtils.setField(service, "bollPeriod", 20);
        ReflectionTestUtils.setField(service, "entryVolumeLookback", 20);
        ReflectionTestUtils.setField(service, "stopLossPct", new BigDecimal("0.10"));

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
