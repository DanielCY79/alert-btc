package com.mobai.alert.access.kline.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * 回测模式下提供只读的 K 线历史数据连接。
 * 这里复用 access_kline_bar 所在数据库，但仅在 backtest.enabled=true 时启用。
 */
@Configuration
@ConditionalOnProperty(value = "backtest.enabled", havingValue = "true")
public class BacktestKlineStoreConfig {

    @Bean(name = "backtestKlineDataSource", destroyMethod = "close")
    public DataSource backtestKlineDataSource(
            @Value("${spring.datasource.url:${kline.sync.db.url:}}") String url,
            @Value("${spring.datasource.username:${kline.sync.db.username:}}") String username,
            @Value("${spring.datasource.password:${kline.sync.db.password:}}") String password,
            @Value("${spring.datasource.driver-class-name:${kline.sync.db.driver-class-name:}}") String driverClassName,
            @Value("${spring.datasource.hikari.maximum-pool-size:${kline.sync.db.max-pool-size:4}}") int maxPoolSize,
            @Value("${spring.datasource.hikari.connection-timeout:${kline.sync.db.connection-timeout-ms:30000}}") long connectionTimeoutMs) {
        Assert.hasText(url, "spring.datasource.url or kline.sync.db.url must not be blank in backtest mode");
        Assert.hasText(username, "spring.datasource.username or kline.sync.db.username must not be blank in backtest mode");

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        if (StringUtils.hasText(driverClassName)) {
            dataSource.setDriverClassName(driverClassName);
        }
        dataSource.setMaximumPoolSize(Math.max(1, maxPoolSize));
        dataSource.setConnectionTimeout(connectionTimeoutMs);
        dataSource.setPoolName("backtest-kline-store");
        dataSource.setReadOnly(true);
        return dataSource;
    }

    @Bean(name = "backtestKlineJdbcTemplate")
    public JdbcTemplate backtestKlineJdbcTemplate(@Qualifier("backtestKlineDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
