package com.mobai.alert.access.kline.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("access_kline_bar")
public class AccessKlineBarEntity {

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

    @TableField("open_time_ms")
    private Long openTimeMs;

    @TableField("close_time_ms")
    private Long closeTimeMs;

    @TableField("open_price")
    private BigDecimal openPrice;

    @TableField("high_price")
    private BigDecimal highPrice;

    @TableField("low_price")
    private BigDecimal lowPrice;

    @TableField("close_price")
    private BigDecimal closePrice;

    @TableField("base_volume")
    private BigDecimal baseVolume;

    @TableField("quote_volume")
    private BigDecimal quoteVolume;

    @TableField("trade_count")
    private Integer tradeCount;

    @TableField("taker_buy_base_volume")
    private BigDecimal takerBuyBaseVolume;

    @TableField("taker_buy_quote_volume")
    private BigDecimal takerBuyQuoteVolume;

    @TableField("is_closed")
    private Integer isClosed;

    @TableField("data_source")
    private String dataSource;

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

    public Long getOpenTimeMs() {
        return openTimeMs;
    }

    public void setOpenTimeMs(Long openTimeMs) {
        this.openTimeMs = openTimeMs;
    }

    public Long getCloseTimeMs() {
        return closeTimeMs;
    }

    public void setCloseTimeMs(Long closeTimeMs) {
        this.closeTimeMs = closeTimeMs;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public BigDecimal getBaseVolume() {
        return baseVolume;
    }

    public void setBaseVolume(BigDecimal baseVolume) {
        this.baseVolume = baseVolume;
    }

    public BigDecimal getQuoteVolume() {
        return quoteVolume;
    }

    public void setQuoteVolume(BigDecimal quoteVolume) {
        this.quoteVolume = quoteVolume;
    }

    public Integer getTradeCount() {
        return tradeCount;
    }

    public void setTradeCount(Integer tradeCount) {
        this.tradeCount = tradeCount;
    }

    public BigDecimal getTakerBuyBaseVolume() {
        return takerBuyBaseVolume;
    }

    public void setTakerBuyBaseVolume(BigDecimal takerBuyBaseVolume) {
        this.takerBuyBaseVolume = takerBuyBaseVolume;
    }

    public BigDecimal getTakerBuyQuoteVolume() {
        return takerBuyQuoteVolume;
    }

    public void setTakerBuyQuoteVolume(BigDecimal takerBuyQuoteVolume) {
        this.takerBuyQuoteVolume = takerBuyQuoteVolume;
    }

    public Integer getIsClosed() {
        return isClosed;
    }

    public void setIsClosed(Integer isClosed) {
        this.isClosed = isClosed;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
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
