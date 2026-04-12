package com.mobai.alert.backtest.export;

import com.mobai.alert.backtest.model.BacktestConfig;
import com.mobai.alert.backtest.model.BacktestReport;
import com.mobai.alert.backtest.model.BatchBacktestResult;
import com.mobai.alert.backtest.model.TradeRecord;
import com.mobai.alert.strategy.config.StrategyMetadata;
import com.mobai.alert.strategy.model.TradeDirection;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestExcelExportServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldExportTradeDetailsSheets() throws IOException {
        BacktestExcelExportService exportService = new BacktestExcelExportService(
                new StrategyMetadata("bollinger", "bollinger-1m-4h", "Bollinger 1m/4h", "", "", true)
        );
        ReflectionTestUtils.setField(exportService, "exportEnabled", true);
        ReflectionTestUtils.setField(exportService, "exportPath", tempDir.toString());
        ReflectionTestUtils.setField(exportService, "initialCapital", new BigDecimal("10000"));
        ReflectionTestUtils.setField(exportService, "exportZoneId", "UTC");

        BacktestReport report = new BacktestReport(
                10,
                List.of(new TradeRecord(
                        "BOLLINGER_LONG_ENTRY",
                        TradeDirection.LONG,
                        1_000L,
                        2_000L,
                        1,
                        new BigDecimal("100"),
                        new BigDecimal("90"),
                        new BigDecimal("120"),
                        new BigDecimal("10"),
                        240,
                        3_000L,
                        new BigDecimal("110"),
                        "BOLLINGER_REVERSAL",
                        null
                )),
                Map.of("BOLLINGER_LONG_ENTRY", 1),
                Map.of(),
                1,
                0,
                false,
                1,
                new BigDecimal("1.0"),
                new BigDecimal("1.0"),
                new BigDecimal("1.0"),
                new BigDecimal("1.0"),
                BigDecimal.ZERO,
                backtestConfig()
        );

        BatchBacktestResult result = new BatchBacktestResult(10, report, report, List.of());

        BacktestExcelExportService.ExportOutcome exportOutcome = exportService.exportIfEnabled(result).orElseThrow();

        assertThat(exportOutcome.outputPath()).exists();

        try (InputStream inputStream = Files.newInputStream(exportOutcome.outputPath());
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);

            Sheet rawTrades = workbook.getSheet("Raw Trades");
            Sheet policyTrades = workbook.getSheet("Policy Trades");
            assertThat(rawTrades).isNotNull();
            assertThat(policyTrades).isNotNull();
            assertThat(rawTrades.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(policyTrades.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(rawTrades.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(1.0d);
            assertThat(rawTrades.getRow(1).getCell(16).getNumericCellValue()).isEqualTo(1000.0d);
            assertThat(policyTrades.getRow(1).getCell(17).getNumericCellValue()).isEqualTo(11000.0d);
        }
    }

    private BacktestConfig backtestConfig() {
        return new BacktestConfig(
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
    }
}
