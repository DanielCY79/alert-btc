package com.mobai.alert.access.kline.service;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 从 access_kline_bar 读取回测所需的闭合 K 线。
 */
@Service
@ConditionalOnProperty(value = "backtest.enabled", havingValue = "true")
public class AccessKlineBarHistoryService {

    private static final Logger log = LoggerFactory.getLogger(AccessKlineBarHistoryService.class);

    private static final String LOAD_CLOSED_BARS_SQL = """
            SELECT symbol,
                   interval_code,
                   open_time_ms,
                   close_time_ms,
                   open_price,
                   high_price,
                   low_price,
                   close_price,
                   base_volume,
                   quote_volume
            FROM access_kline_bar
            WHERE exchange = ?
              AND market_type = ?
              AND symbol = ?
              AND interval_code = ?
              AND is_closed = 1
              AND open_time_ms >= ?
              AND close_time_ms <= ?
            ORDER BY open_time_ms ASC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String exchange;
    private final String marketType;

    public AccessKlineBarHistoryService(
            @Qualifier("backtestKlineJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Value("${kline.sync.exchange:BINANCE}") String exchange,
            @Value("${kline.sync.market-type:USDT_PERPETUAL}") String marketType) {
        this.jdbcTemplate = jdbcTemplate;
        this.exchange = exchange;
        this.marketType = marketType;
    }

    public List<BinanceKlineDTO> loadClosedKlines(String symbol, String interval, long startTime, long endTime) {
        if (!StringUtils.hasText(symbol) || !StringUtils.hasText(interval) || endTime <= startTime) {
            return List.of();
        }
        List<BinanceKlineDTO> history = jdbcTemplate.query(
                LOAD_CLOSED_BARS_SQL,
                this::mapRow,
                exchange,
                marketType,
                symbol,
                interval,
                startTime,
                endTime
        );
        log.info("Loaded backtest klines from access_kline_bar, exchange={}, marketType={}, symbol={}, interval={}, start={}, end={}, bars={}",
                exchange,
                marketType,
                symbol,
                interval,
                startTime,
                endTime,
                history.size());
        return history;
    }

    private BinanceKlineDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol(rs.getString("symbol"));
        dto.setInterval(rs.getString("interval_code"));
        dto.setStartTime(rs.getLong("open_time_ms"));
        dto.setEndTime(rs.getLong("close_time_ms"));
        dto.setOpen(rs.getBigDecimal("open_price").toPlainString());
        dto.setHigh(rs.getBigDecimal("high_price").toPlainString());
        dto.setLow(rs.getBigDecimal("low_price").toPlainString());
        dto.setClose(rs.getBigDecimal("close_price").toPlainString());
        dto.setAmount(rs.getBigDecimal("base_volume").toPlainString());
        dto.setVolume(rs.getBigDecimal("quote_volume").toPlainString());
        return dto;
    }
}
