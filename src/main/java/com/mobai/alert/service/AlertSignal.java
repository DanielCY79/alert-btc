package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceKlineDTO;

import java.math.BigDecimal;

public class AlertSignal {
    private final String title;
    private final BinanceKlineDTO kline;
    private final String type;
    private final String summary;
    private final BigDecimal triggerPrice;
    private final BigDecimal invalidationPrice;
    private final BigDecimal volumeRatio;

    public AlertSignal(String title,
                       BinanceKlineDTO kline,
                       String type,
                       String summary,
                       BigDecimal triggerPrice,
                       BigDecimal invalidationPrice,
                       BigDecimal volumeRatio) {
        this.title = title;
        this.kline = kline;
        this.type = type;
        this.summary = summary;
        this.triggerPrice = triggerPrice;
        this.invalidationPrice = invalidationPrice;
        this.volumeRatio = volumeRatio;
    }

    public String getTitle() {
        return title;
    }

    public BinanceKlineDTO getKline() {
        return kline;
    }

    public String getType() {
        return type;
    }

    public String getSummary() {
        return summary;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public BigDecimal getInvalidationPrice() {
        return invalidationPrice;
    }

    public BigDecimal getVolumeRatio() {
        return volumeRatio;
    }
}
