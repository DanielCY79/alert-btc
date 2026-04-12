package com.mobai.alert.strategy.backtest;

import com.mobai.alert.state.backtest.BacktestConfig;
import com.mobai.alert.state.backtest.BacktestReport;
import com.mobai.alert.state.backtest.BatchBacktestResult;
import com.mobai.alert.state.backtest.SensitivityResult;
import com.mobai.alert.state.backtest.TradeRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class BacktestExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(BacktestExcelExportService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]+");
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${backtest.export-excel.enabled:false}")
    private boolean exportEnabled;

    @Value("${backtest.export-excel.path:}")
    private String exportPath;

    @Value("${backtest.export.initial-capital:10000}")
    private BigDecimal initialCapital;

    @Value("${backtest.export.risk-per-trade:0.01}")
    private BigDecimal riskPerTrade;

    @Value("${backtest.export.zone-id:Asia/Shanghai}")
    private String exportZoneId;

    @Value("${monitoring.profile.name:default}")
    private String profileName;

    @Value("${monitoring.profile.label:}")
    private String profileLabel;

    public Optional<ExportOutcome> exportIfEnabled(BatchBacktestResult result) {
        if (!exportEnabled) {
            return Optional.empty();
        }

        ReportSimulation rawSimulation = simulate(result.baseline());
        ReportSimulation policySimulation = simulate(result.policyFilteredBaseline());

        Path outputPath = resolveOutputPath(result.policyFilteredBaseline().config());
        try {
            Files.createDirectories(outputPath.getParent());
            try (Workbook workbook = new XSSFWorkbook();
                 OutputStream outputStream = Files.newOutputStream(outputPath)) {
                WorkbookStyles styles = new WorkbookStyles(workbook);
                writeSummarySheet(workbook, styles, result, rawSimulation, policySimulation);
                workbook.write(outputStream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export backtest workbook to " + outputPath, e);
        }

        log.info("Backtest workbook exported, path={}, initialCapital={}, riskPerTrade={}%",
                outputPath,
                scale(initialCapital),
                percentValue(riskPerTrade));
        return Optional.of(new ExportOutcome(outputPath, rawSimulation, policySimulation));
    }

    private void writeSummarySheet(Workbook workbook,
                                   WorkbookStyles styles,
                                   BatchBacktestResult result,
                                   ReportSimulation rawSimulation,
                                   ReportSimulation policySimulation) {
        Sheet sheet = workbook.createSheet("回测结果");
        int rowIndex = 0;
        rowIndex = writeTitle(sheet, rowIndex, "回测结果", styles.title());
        rowIndex = writeKeyValue(sheet, rowIndex, "生成时间", formatInstant(System.currentTimeMillis()), styles);
        rowIndex = writeKeyValue(sheet, rowIndex, "配置档", profileSummary(), styles);
        rowIndex = writeKeyValue(sheet, rowIndex, "交易对", result.policyFilteredBaseline().config().symbol(), styles);
        rowIndex = writeKeyValue(sheet, rowIndex, "周期", result.policyFilteredBaseline().config().interval(), styles);
        rowIndex = writeKeyValue(sheet, rowIndex, "开始时间", formatInstant(result.policyFilteredBaseline().config().startTime()), styles);
        rowIndex = writeKeyValue(sheet, rowIndex, "结束时间", formatInstant(result.policyFilteredBaseline().config().endTime()), styles);
        rowIndex = writeKeyValue(sheet, rowIndex, "初始资金（USDT）", initialCapital, styles);
        rowIndex++;

        rowIndex = writeTitle(sheet, rowIndex, "结果汇总", styles.section());
        Row header = sheet.createRow(rowIndex++);
        String[] headers = {
                "结果类型",
                "盈利或亏损（USDT）",
                "收益率（%）",
                "最大回撤（USDT）",
                "最大回撤（%）",
                "期末权益（USDT）"
        };
        writeHeaderRow(header, headers, styles.header());

        writeSummaryRow(sheet.createRow(rowIndex++), "原始基线", rawSimulation, styles);
        writeSummaryRow(sheet.createRow(rowIndex++), "策略过滤基线", policySimulation, styles);

        autoSize(sheet, 0, headers.length - 1);
    }

    private void writeSummaryRow(Row row,
                                 String label,
                                 ReportSimulation simulation,
                                 WorkbookStyles styles) {
        int col = 0;
        writeCell(row, col++, label, styles.defaultCell());
        writeCell(row, col++, simulation.netProfit(), styles.money());
        writeCell(row, col++, simulation.totalReturnPct(), styles.percent());
        writeCell(row, col++, simulation.maxDrawdownAmount(), styles.money());
        writeCell(row, col++, simulation.maxDrawdownPct(), styles.percent());
        writeCell(row, col++, simulation.finalEquity(), styles.money());
    }

    private void writeTradeSheet(Workbook workbook,
                                 WorkbookStyles styles,
                                 String sheetName,
                                 BacktestReport report,
                                 ReportSimulation simulation) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);
        String[] headers = {
                "序号", "信号类型", "方向", "信号时间", "入场时间", "出场时间",
                "入场价", "出场价", "止损价", "目标价", "每单位风险",
                "最大持仓K线数", "出场原因", "实现R", "交易前权益", "本笔风险金",
                "盈亏（USDT）", "交易后权益", "权益峰值", "回撤（USDT）", "回撤（%）"
        };
        writeHeaderRow(header, headers, styles.header());

        List<TradeRecord> trades = report.trades();
        List<TradeSimulationRow> rows = simulation.rows();
        for (int i = 0; i < trades.size(); i++) {
            TradeRecord trade = trades.get(i);
            TradeSimulationRow simulationRow = rows.get(i);
            Row row = sheet.createRow(rowIndex++);
            int col = 0;
            writeCell(row, col++, i + 1, styles.defaultCell());
            writeCell(row, col++, localizeSignalType(trade.signalType()), styles.defaultCell());
            writeCell(row, col++, localizeDirection(trade.direction().name()), styles.defaultCell());
            writeCell(row, col++, formatInstant(trade.signalTime()), styles.defaultCell());
            writeCell(row, col++, formatInstant(trade.entryTime()), styles.defaultCell());
            writeCell(row, col++, trade.exitTime() == null ? "" : formatInstant(trade.exitTime()), styles.defaultCell());
            writeCell(row, col++, trade.entryPrice(), styles.number());
            writeCell(row, col++, trade.exitPrice(), styles.number());
            writeCell(row, col++, trade.stopPrice(), styles.number());
            writeCell(row, col++, trade.targetPrice(), styles.number());
            writeCell(row, col++, trade.riskPerUnit(), styles.number());
            writeCell(row, col++, trade.maxHoldingBars(), styles.defaultCell());
            writeCell(row, col++, localizeExitReason(trade.exitReason()), styles.defaultCell());
            writeCell(row, col++, trade.realizedR(), styles.number());
            writeCell(row, col++, simulationRow.equityBefore(), styles.money());
            writeCell(row, col++, simulationRow.riskCapital(), styles.money());
            writeCell(row, col++, simulationRow.pnl(), styles.money());
            writeCell(row, col++, simulationRow.equityAfter(), styles.money());
            writeCell(row, col++, simulationRow.peakEquity(), styles.money());
            writeCell(row, col++, simulationRow.drawdownAmount(), styles.money());
            writeCell(row, col++, simulationRow.drawdownPct(), styles.percent());
        }

        autoSize(sheet, 0, headers.length - 1);
    }

    private void writeSensitivitySheet(Workbook workbook,
                                       WorkbookStyles styles,
                                       List<SensitivitySimulation> sensitivitySimulations) {
        Sheet sheet = workbook.createSheet("敏感性分析");
        int rowIndex = 0;
        Row header = sheet.createRow(rowIndex++);
        String[] headers = {
                "变体", "交易笔数", "胜率", "平均R", "总R", "相对基线R变化",
                "盈亏比", "被过滤信号数", "期末权益（USDT）", "净收益（USDT）",
                "收益率（%）", "最大回撤（USDT）", "最大回撤（%）"
        };
        writeHeaderRow(header, headers, styles.header());

        for (SensitivitySimulation sensitivity : sensitivitySimulations) {
            Row row = sheet.createRow(rowIndex++);
            int col = 0;
            BacktestReport report = sensitivity.result().report();
            ReportSimulation simulation = sensitivity.simulation();
            writeCell(row, col++, localizeVariantLabel(sensitivity.result().label()), styles.defaultCell());
            writeCell(row, col++, report.tradeCount(), styles.defaultCell());
            writeCell(row, col++, percentValue(report.winRate()), styles.percent());
            writeCell(row, col++, report.averageR(), styles.number());
            writeCell(row, col++, report.totalR(), styles.number());
            writeCell(row, col++, report.totalR().subtract(sensitivity.result().baselineTotalR()), styles.number());
            writeCell(row, col++, report.profitFactor(), styles.number());
            writeCell(row, col++, report.blockedSignalCount(), styles.defaultCell());
            writeCell(row, col++, simulation.finalEquity(), styles.money());
            writeCell(row, col++, simulation.netProfit(), styles.money());
            writeCell(row, col++, simulation.totalReturnPct(), styles.percent());
            writeCell(row, col++, simulation.maxDrawdownAmount(), styles.money());
            writeCell(row, col++, simulation.maxDrawdownPct(), styles.percent());
        }

        autoSize(sheet, 0, headers.length - 1);
    }

    private ReportSimulation simulate(BacktestReport report) {
        List<TradeSimulationRow> rows = new ArrayList<>();
        BigDecimal equity = initialCapital.max(ZERO);
        BigDecimal peak = equity;
        BigDecimal maxDrawdownAmount = ZERO;
        BigDecimal maxDrawdownPct = ZERO;
        BigDecimal effectiveRiskPerTrade = riskPerTrade.max(ZERO);

        for (TradeRecord trade : report.trades()) {
            BigDecimal equityBefore = equity;
            BigDecimal riskCapital = equityBefore.multiply(effectiveRiskPerTrade);
            BigDecimal pnl = riskCapital.multiply(trade.realizedR());
            BigDecimal equityAfter = equityBefore.add(pnl);
            if (equityAfter.compareTo(peak) > 0) {
                peak = equityAfter;
            }
            BigDecimal drawdownAmount = peak.subtract(equityAfter);
            BigDecimal drawdownPct = peak.compareTo(ZERO) == 0
                    ? ZERO
                    : drawdownAmount.divide(peak, 8, RoundingMode.HALF_UP).multiply(ONE_HUNDRED);
            if (drawdownAmount.compareTo(maxDrawdownAmount) > 0) {
                maxDrawdownAmount = drawdownAmount;
            }
            if (drawdownPct.compareTo(maxDrawdownPct) > 0) {
                maxDrawdownPct = drawdownPct;
            }
            rows.add(new TradeSimulationRow(
                    equityBefore,
                    riskCapital,
                    pnl,
                    equityAfter,
                    peak,
                    drawdownAmount,
                    drawdownPct
            ));
            equity = equityAfter;
        }

        BigDecimal netProfit = equity.subtract(initialCapital);
        BigDecimal totalReturnPct = initialCapital.compareTo(ZERO) == 0
                ? ZERO
                : netProfit.divide(initialCapital, 8, RoundingMode.HALF_UP).multiply(ONE_HUNDRED);
        return new ReportSimulation(rows, equity, netProfit, totalReturnPct, maxDrawdownAmount, maxDrawdownPct);
    }

    private Path resolveOutputPath(BacktestConfig config) {
        if (exportPath != null && !exportPath.isBlank()) {
            Path path = Paths.get(exportPath.trim());
            if (!path.isAbsolute()) {
                return Path.of("").toAbsolutePath().resolve(path).normalize();
            }
            return path.normalize();
        }

        ZoneId zoneId = resolveZoneId();
        String safeProfile = sanitizeFilename(profileName);
        String fileName = "backtest-" + safeProfile + "-" + config.interval() + "-"
                + FILE_TIME_FORMATTER.format(ZonedDateTime.now(zoneId)) + ".xlsx";
        return Path.of("target", "backtest", fileName).toAbsolutePath().normalize();
    }

    private String profileSummary() {
        if (profileLabel == null || profileLabel.isBlank()) {
            return profileName;
        }
        return profileName + "（" + profileLabel + "）";
    }

    private String localizeDirection(String direction) {
        if ("LONG".equalsIgnoreCase(direction)) {
            return "做多";
        }
        if ("SHORT".equalsIgnoreCase(direction)) {
            return "做空";
        }
        return direction;
    }

    private String localizeSignalType(String signalType) {
        if (signalType == null || signalType.isBlank()) {
            return "";
        }
        return signalType
                .replace("RANGE_FAILURE_LONG", "区间失败突破做多")
                .replace("RANGE_FAILURE_SHORT", "区间失败跌破做空")
                .replace("CONFIRMED_BREAKOUT_LONG", "确认突破做多")
                .replace("CONFIRMED_BREAKOUT_SHORT", "确认跌破做空")
                .replace("BREAKOUT_PULLBACK_LONG", "突破回踩做多")
                .replace("BREAKOUT_PULLBACK_SHORT", "跌破回抽做空")
                .replace("SECOND_ENTRY_H1_LONG", "二次入场 H1 做多")
                .replace("SECOND_ENTRY_H2_LONG", "二次入场 H2 做多")
                .replace("SECOND_ENTRY_L1_SHORT", "二次入场 L1 做空")
                .replace("SECOND_ENTRY_L2_SHORT", "二次入场 L2 做空");
    }

    private String localizeExitReason(String exitReason) {
        if (exitReason == null || exitReason.isBlank()) {
            return "";
        }
        return exitReason
                .replace("STOP_LOSS", "止损")
                .replace("TARGET", "目标止盈")
                .replace("TIME_EXIT", "持仓到期")
                .replace("FORCED_END", "区间结束强制平仓")
                .replace("FAILED_FOLLOW_THROUGH", "跟随失败退出")
                .replace("TRAILING_STOP", "移动止损")
                .replace("scaleOut=true", "已分批止盈=是")
                .replace("scaleOut=false", "已分批止盈=否")
                .replace("addOns=", "加仓次数=")
                .replace("finalStop=", "最终止损=")
                .replace(" | ", "；");
    }

    private String localizeVariantLabel(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        return label
                .replace("rangeLookback", "区间回看长度")
                .replace("breakoutVolumeMultiplier", "突破量能倍数")
                .replace("breakoutCloseBuffer", "突破收盘缓冲")
                .replace("policy.baseScore", "策略基础分阈值")
                .replace("policy.rangeFailureMinScore", "区间失败最小分数")
                .replace("policy.breakoutMinScore", "突破最小分数")
                .replace("policy.pullbackMinScore", "回踩最小分数")
                .replace("policy.maxRegimeRisk", "最大市场状态风险")
                .replace("policy.crowdingExtreme", "极端拥挤阈值")
                .replace("policy.breakout.breakoutWeight", "突破因子权重")
                .replace("policy.breakout.trendWeight", "趋势因子权重")
                .replace("policy.pullback.regimeRiskWeight", "回踩状态风险权重");
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return INVALID_FILENAME_CHARS.matcher(value).replaceAll("_");
    }

    private ZoneId resolveZoneId() {
        try {
            return ZoneId.of(exportZoneId);
        } catch (Exception ignored) {
            return ZoneId.systemDefault();
        }
    }

    private String formatInstant(long epochMillis) {
        return DISPLAY_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(resolveZoneId()));
    }

    private int writeTitle(Sheet sheet, int rowIndex, String title, CellStyle style) {
        Row row = sheet.createRow(rowIndex++);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(style);
        return rowIndex;
    }

    private int writeKeyValue(Sheet sheet, int rowIndex, String key, Object value, WorkbookStyles styles) {
        Row row = sheet.createRow(rowIndex++);
        writeCell(row, 0, key, styles.header());
        writeCell(row, 1, value, styles.defaultCell());
        return rowIndex;
    }

    private void writeHeaderRow(Row header, String[] values, CellStyle style) {
        for (int i = 0; i < values.length; i++) {
            writeCell(header, i, values[i], style);
        }
    }

    private void writeCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof BigDecimal bigDecimal) {
            cell.setCellValue(bigDecimal.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    private void autoSize(Sheet sheet, int fromColumn, int toColumn) {
        for (int column = fromColumn; column <= toColumn; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    private String scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String percentValue(BigDecimal ratio) {
        return ratio.multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public record ExportOutcome(Path outputPath,
                                ReportSimulation rawSimulation,
                                ReportSimulation policySimulation) {
    }

    public record ReportSimulation(List<TradeSimulationRow> rows,
                                   BigDecimal finalEquity,
                                   BigDecimal netProfit,
                                   BigDecimal totalReturnPct,
                                   BigDecimal maxDrawdownAmount,
                                   BigDecimal maxDrawdownPct) {
    }

    private record SensitivitySimulation(SensitivityResult result, ReportSimulation simulation) {
    }

    public record TradeSimulationRow(BigDecimal equityBefore,
                                     BigDecimal riskCapital,
                                     BigDecimal pnl,
                                     BigDecimal equityAfter,
                                     BigDecimal peakEquity,
                                     BigDecimal drawdownAmount,
                                     BigDecimal drawdownPct) {
    }

    private static final class WorkbookStyles {
        private final CellStyle title;
        private final CellStyle section;
        private final CellStyle header;
        private final CellStyle defaultCell;
        private final CellStyle number;
        private final CellStyle money;
        private final CellStyle percent;

        private WorkbookStyles(Workbook workbook) {
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            DataFormat dataFormat = workbook.createDataFormat();

            title = workbook.createCellStyle();
            title.setFont(boldFont);

            section = workbook.createCellStyle();
            section.setFont(boldFont);

            header = workbook.createCellStyle();
            header.setFont(boldFont);

            defaultCell = workbook.createCellStyle();

            number = workbook.createCellStyle();
            number.cloneStyleFrom(defaultCell);
            number.setDataFormat(dataFormat.getFormat("0.0000"));

            money = workbook.createCellStyle();
            money.cloneStyleFrom(defaultCell);
            money.setDataFormat(dataFormat.getFormat("0.00"));

            percent = workbook.createCellStyle();
            percent.cloneStyleFrom(defaultCell);
            percent.setDataFormat(dataFormat.getFormat("0.00"));
        }

        private CellStyle title() {
            return title;
        }

        private CellStyle section() {
            return section;
        }

        private CellStyle header() {
            return header;
        }

        private CellStyle defaultCell() {
            return defaultCell;
        }

        private CellStyle number() {
            return number;
        }

        private CellStyle money() {
            return money;
        }

        private CellStyle percent() {
            return percent;
        }
    }
}
