package com.mobai.alert.access.kline.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "kline.sync")
public class KlineSyncProperties {

    private String exchange = "BINANCE";
    private String marketType = "USDT_PERPETUAL";
    private String symbol = "BTCUSDT";
    private List<String> intervals = new ArrayList<>(List.of("1m", "3m", "15m", "1h", "4h", "1d"));
    private int lookbackDays = 365;
    private int fetchLimit = 1000;
    private int writeBatchSize = 500;
    private boolean forceFullSync = false;
    private boolean resetCheckpoint = false;
    private final Db db = new Db();

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getMarketType() {
        return marketType;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public List<String> getIntervals() {
        return intervals;
    }

    public void setIntervals(List<String> intervals) {
        this.intervals = intervals;
    }

    public int getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(int lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    public int getFetchLimit() {
        return fetchLimit;
    }

    public void setFetchLimit(int fetchLimit) {
        this.fetchLimit = fetchLimit;
    }

    public int getWriteBatchSize() {
        return writeBatchSize;
    }

    public void setWriteBatchSize(int writeBatchSize) {
        this.writeBatchSize = writeBatchSize;
    }

    public boolean isForceFullSync() {
        return forceFullSync;
    }

    public void setForceFullSync(boolean forceFullSync) {
        this.forceFullSync = forceFullSync;
    }

    public boolean isResetCheckpoint() {
        return resetCheckpoint;
    }

    public void setResetCheckpoint(boolean resetCheckpoint) {
        this.resetCheckpoint = resetCheckpoint;
    }

    public Db getDb() {
        return db;
    }

    public static class Db {

        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private int maxPoolSize = 4;
        private long connectionTimeoutMs = 30000L;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }
    }
}
