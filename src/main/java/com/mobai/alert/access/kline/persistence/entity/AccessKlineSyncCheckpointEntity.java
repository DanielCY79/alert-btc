package com.mobai.alert.access.kline.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("access_kline_sync_checkpoint")
public class AccessKlineSyncCheckpointEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("exchange")
    private String exchange;

    @TableField("market_type")
    private String marketType;

    @TableField("symbol")
    private String symbol;

    @TableField("interval_code")
    private String intervalCode;

    @TableField("last_open_time_ms")
    private Long lastOpenTimeMs;

    @TableField("last_close_time_ms")
    private Long lastCloseTimeMs;

    @TableField("sync_status")
    private String syncStatus;

    @TableField("last_error_message")
    private String lastErrorMessage;

    @TableField("create_time")
    private Long createTime;

    @TableField("update_time")
    private Long updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getIntervalCode() {
        return intervalCode;
    }

    public void setIntervalCode(String intervalCode) {
        this.intervalCode = intervalCode;
    }

    public Long getLastOpenTimeMs() {
        return lastOpenTimeMs;
    }

    public void setLastOpenTimeMs(Long lastOpenTimeMs) {
        this.lastOpenTimeMs = lastOpenTimeMs;
    }

    public Long getLastCloseTimeMs() {
        return lastCloseTimeMs;
    }

    public void setLastCloseTimeMs(Long lastCloseTimeMs) {
        this.lastCloseTimeMs = lastCloseTimeMs;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
}
