CREATE TABLE IF NOT EXISTS access_kline_bar (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    exchange VARCHAR(16) NOT NULL DEFAULT 'BINANCE' COMMENT 'exchange code',
    market_type VARCHAR(20) NOT NULL DEFAULT 'USDT_PERPETUAL' COMMENT 'market type',
    symbol VARCHAR(20) NOT NULL COMMENT 'symbol',
    interval_code VARCHAR(8) NOT NULL COMMENT 'interval code',
    open_time_ms BIGINT NOT NULL COMMENT 'bar open time in milliseconds',
    close_time_ms BIGINT NOT NULL COMMENT 'bar close time in milliseconds',
    open_price DECIMAL(20,8) NOT NULL COMMENT 'open price',
    high_price DECIMAL(20,8) NOT NULL COMMENT 'high price',
    low_price DECIMAL(20,8) NOT NULL COMMENT 'low price',
    close_price DECIMAL(20,8) NOT NULL COMMENT 'close price',
    base_volume DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT 'base asset volume',
    quote_volume DECIMAL(28,8) NOT NULL DEFAULT 0 COMMENT 'quote asset volume',
    trade_count INT UNSIGNED DEFAULT NULL COMMENT 'trade count',
    taker_buy_base_volume DECIMAL(24,8) DEFAULT NULL COMMENT 'taker buy base volume',
    taker_buy_quote_volume DECIMAL(28,8) DEFAULT NULL COMMENT 'taker buy quote volume',
    is_closed TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0 open bar, 1 closed bar',
    data_source VARCHAR(16) NOT NULL DEFAULT 'REST' COMMENT 'REST or WEBSOCKET',
    create_time BIGINT NOT NULL COMMENT 'create time in milliseconds',
    update_time BIGINT NOT NULL COMMENT 'update time in milliseconds',
    PRIMARY KEY (id),
    UNIQUE KEY uk_kline_bar (exchange, market_type, symbol, interval_code, open_time_ms),
    KEY idx_kline_symbol_interval_open_time (symbol, interval_code, open_time_ms DESC),
    KEY idx_kline_interval_open_time (interval_code, open_time_ms DESC),
    KEY idx_kline_closed_interval_close_time (is_closed, interval_code, close_time_ms DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_bin
  COMMENT='kline history table';

CREATE TABLE IF NOT EXISTS access_kline_sync_checkpoint (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    exchange VARCHAR(16) NOT NULL DEFAULT 'BINANCE' COMMENT 'exchange code',
    market_type VARCHAR(20) NOT NULL DEFAULT 'USDT_PERPETUAL' COMMENT 'market type',
    symbol VARCHAR(20) NOT NULL COMMENT 'symbol',
    interval_code VARCHAR(8) NOT NULL COMMENT 'interval code',
    last_open_time_ms BIGINT DEFAULT NULL COMMENT 'last synced open time in milliseconds',
    last_close_time_ms BIGINT DEFAULT NULL COMMENT 'last synced close time in milliseconds',
    sync_status VARCHAR(16) NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE RUNNING FAILED',
    last_error_message VARCHAR(500) DEFAULT NULL COMMENT 'last error message',
    create_time BIGINT NOT NULL COMMENT 'create time in milliseconds',
    update_time BIGINT NOT NULL COMMENT 'update time in milliseconds',
    PRIMARY KEY (id),
    UNIQUE KEY uk_kline_sync_checkpoint (exchange, market_type, symbol, interval_code)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_bin
  COMMENT='kline sync checkpoint table';
