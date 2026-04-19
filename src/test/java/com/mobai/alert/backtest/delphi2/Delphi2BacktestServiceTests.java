package com.mobai.alert.backtest.delphi2;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.backtest.model.BacktestConfig;
import com.mobai.alert.backtest.model.BacktestReport;
import com.mobai.alert.backtest.model.BatchBacktestResult;
import com.mobai.alert.backtest.model.TradeRecord;
import com.mobai.alert.strategy.delphi2.Delphi2SignalEvaluator;
import com.mobai.alert.strategy.model.TradeDirection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Delphi2BacktestServiceTests {

    @Test
    void shouldResolveHourlyAndDailyIntervalsForBacktests() {
        assertThat(Delphi2BacktestService.resolveIntervalMs("1h")).isEqualTo(3_600_000L);
        assertThat(Delphi2BacktestService.resolveIntervalMs("1d")).isEqualTo(86_400_000L);
    }

    @Test
    void shouldFormatCapitalMetricsForReport() {
        Delphi2BacktestService service = new Delphi2BacktestService((BinanceApi) null, new Delphi2SignalEvaluator());
        ReflectionTestUtils.setField(service, "initialCapital", new BigDecimal("10000"));

        BacktestConfig config = new BacktestConfig(
                "BTCUSDT",
                "1h",
                0L,
                10L,
                20,
                50,
                10,
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
                List.of(new TradeRecord(
                        "DELPHI2_AGGRESSIVE_LONG_ENTRY",
                        TradeDirection.LONG,
                        1L,
                        2L,
                        1,
                        new BigDecimal("100"),
                        new BigDecimal("95"),
                        null,
                        new BigDecimal("5"),
                        0,
                        3L,
                        new BigDecimal("110"),
                        "DAILY_REVERSAL_LONG",
                        null
                )),
                java.util.Map.of("DELPHI2_AGGRESSIVE_LONG_ENTRY", 1),
                java.util.Map.of(),
                1,
                0,
                false,
                1,
                BigDecimal.ONE,
                new BigDecimal("2.0"),
                new BigDecimal("2.0"),
                new BigDecimal("2.0"),
                BigDecimal.ZERO,
                config
        );

        String formatted = service.formatBatchResult(new BatchBacktestResult(10, report, report, List.of()));

        assertThat(formatted).contains("netProfit=1000.00 USDT");
        assertThat(formatted).contains("finalEquity=11000.00 USDT");
        assertThat(formatted).contains("totalR=2.00");
    }
}
