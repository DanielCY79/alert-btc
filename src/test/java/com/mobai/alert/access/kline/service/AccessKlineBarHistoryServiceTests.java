package com.mobai.alert.access.kline.service;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessKlineBarHistoryServiceTests {

    @Test
    void shouldMapClosedBarsFromAccessTableIntoBinanceDto() throws SQLException {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AccessKlineBarHistoryService service = new AccessKlineBarHistoryService(jdbcTemplate, "BINANCE", "USDT_PERPETUAL");
        ResultSet resultSet = mock(ResultSet.class);

        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("BINANCE"), eq("USDT_PERPETUAL"), eq("BTCUSDT"), eq("1m"), eq(1000L), eq(2000L)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.jdbc.core.RowMapper<BinanceKlineDTO> rowMapper =
                            (org.springframework.jdbc.core.RowMapper<BinanceKlineDTO>) invocation.getArgument(1);
                    when(resultSet.getString("symbol")).thenReturn("BTCUSDT");
                    when(resultSet.getString("interval_code")).thenReturn("1m");
                    when(resultSet.getLong("open_time_ms")).thenReturn(1200L);
                    when(resultSet.getLong("close_time_ms")).thenReturn(1259L);
                    when(resultSet.getBigDecimal("open_price")).thenReturn(new BigDecimal("100.10"));
                    when(resultSet.getBigDecimal("high_price")).thenReturn(new BigDecimal("101.20"));
                    when(resultSet.getBigDecimal("low_price")).thenReturn(new BigDecimal("99.80"));
                    when(resultSet.getBigDecimal("close_price")).thenReturn(new BigDecimal("100.90"));
                    when(resultSet.getBigDecimal("base_volume")).thenReturn(new BigDecimal("12.34"));
                    when(resultSet.getBigDecimal("quote_volume")).thenReturn(new BigDecimal("1234.56"));
                    return List.of(rowMapper.mapRow(resultSet, 0));
                });

        List<BinanceKlineDTO> history = service.loadClosedKlines("BTCUSDT", "1m", 1000L, 2000L);

        assertThat(history).hasSize(1);
        BinanceKlineDTO dto = history.get(0);
        assertThat(dto.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(dto.getInterval()).isEqualTo("1m");
        assertThat(dto.getStartTime()).isEqualTo(1200L);
        assertThat(dto.getEndTime()).isEqualTo(1259L);
        assertThat(dto.getOpen()).isEqualTo("100.10");
        assertThat(dto.getHigh()).isEqualTo("101.20");
        assertThat(dto.getLow()).isEqualTo("99.80");
        assertThat(dto.getClose()).isEqualTo("100.90");
        assertThat(dto.getAmount()).isEqualTo("12.34");
        assertThat(dto.getVolume()).isEqualTo("1234.56");
    }
}
