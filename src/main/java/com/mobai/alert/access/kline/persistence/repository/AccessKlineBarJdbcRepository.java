package com.mobai.alert.access.kline.persistence.repository;

import com.mobai.alert.access.kline.persistence.entity.AccessKlineBarEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

@Repository
public class AccessKlineBarJdbcRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO access_kline_bar (
                exchange,
                market_type,
                symbol,
                interval_code,
                open_time_ms,
                close_time_ms,
                open_price,
                high_price,
                low_price,
                close_price,
                base_volume,
                quote_volume,
                trade_count,
                taker_buy_base_volume,
                taker_buy_quote_volume,
                is_closed,
                data_source,
                create_time,
                update_time
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                close_time_ms = VALUES(close_time_ms),
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                close_price = VALUES(close_price),
                base_volume = VALUES(base_volume),
                quote_volume = VALUES(quote_volume),
                trade_count = VALUES(trade_count),
                taker_buy_base_volume = VALUES(taker_buy_base_volume),
                taker_buy_quote_volume = VALUES(taker_buy_quote_volume),
                is_closed = VALUES(is_closed),
                data_source = VALUES(data_source),
                update_time = VALUES(update_time)
            """;

    private final JdbcTemplate jdbcTemplate;

    public AccessKlineBarJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void batchUpsert(List<AccessKlineBarEntity> bars) {
        if (CollectionUtils.isEmpty(bars)) {
            return;
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                AccessKlineBarEntity bar = bars.get(i);
                ps.setString(1, bar.getExchange());
                ps.setString(2, bar.getMarketType());
                ps.setString(3, bar.getSymbol());
                ps.setString(4, bar.getIntervalCode());
                ps.setLong(5, bar.getOpenTimeMs());
                ps.setLong(6, bar.getCloseTimeMs());
                setBigDecimal(ps, 7, bar.getOpenPrice());
                setBigDecimal(ps, 8, bar.getHighPrice());
                setBigDecimal(ps, 9, bar.getLowPrice());
                setBigDecimal(ps, 10, bar.getClosePrice());
                setBigDecimal(ps, 11, bar.getBaseVolume());
                setBigDecimal(ps, 12, bar.getQuoteVolume());
                if (bar.getTradeCount() == null) {
                    ps.setNull(13, Types.INTEGER);
                } else {
                    ps.setInt(13, bar.getTradeCount());
                }
                setBigDecimal(ps, 14, bar.getTakerBuyBaseVolume());
                setBigDecimal(ps, 15, bar.getTakerBuyQuoteVolume());
                ps.setInt(16, bar.getIsClosed() == null ? 0 : bar.getIsClosed());
                ps.setString(17, bar.getDataSource());
                ps.setLong(18, bar.getCreateTime());
                ps.setLong(19, bar.getUpdateTime());
            }

            @Override
            public int getBatchSize() {
                return bars.size();
            }
        });
    }

    private void setBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DECIMAL);
            return;
        }
        ps.setBigDecimal(index, value);
    }
}
