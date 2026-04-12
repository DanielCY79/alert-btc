package com.mobai.alert.access.kline.sync;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(KlineSyncProperties.class)
public class KlineSyncPersistenceConfig {

    @Bean
    public DataSource klineSyncDataSource(
            @Value("${spring.datasource.url:${kline.sync.db.url:}}") String url,
            @Value("${spring.datasource.username:${kline.sync.db.username:}}") String username,
            @Value("${spring.datasource.password:${kline.sync.db.password:}}") String password,
            @Value("${spring.datasource.driver-class-name:${kline.sync.db.driver-class-name:}}") String driverClassName,
            @Value("${spring.datasource.hikari.maximum-pool-size:${kline.sync.db.max-pool-size:4}}") int maxPoolSize,
            @Value("${spring.datasource.hikari.connection-timeout:${kline.sync.db.connection-timeout-ms:30000}}") long connectionTimeoutMs) {
        Assert.hasText(url, "spring.datasource.url or kline.sync.db.url must not be blank");
        Assert.hasText(username, "spring.datasource.username or kline.sync.db.username must not be blank");

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        if (StringUtils.hasText(driverClassName)) {
            dataSource.setDriverClassName(driverClassName);
        }
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setConnectionTimeout(connectionTimeoutMs);
        dataSource.setPoolName("kline-sync-pool");
        return dataSource;
    }
}
