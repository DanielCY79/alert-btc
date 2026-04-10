package com.mobai.alert.state.signal;

import com.mobai.alert.access.binance.kline.dto.BinanceKlineDTO;

import java.math.BigDecimal;

public class AlertSignal {
    private final TradeDirection direction;
    private final String title;
    private final BinanceKlineDTO kline;
    private final String type;
    private final String summary;
    private final BigDecimal triggerPrice;
    private final BigDecimal invalidationPrice;
    private final BigDecimal targetPrice;
    private final BigDecimal volumeRatio;

    public AlertSignal(TradeDirection direction,
                       String title,
                       BinanceKlineDTO kline,
                       String type,
                       String summary,
                       BigDecimal triggerPrice,
                       BigDecimal invalidationPrice,
                       BigDecimal targetPrice,
                       BigDecimal volumeRatio) {
        this.direction = direction;
        this.title = title;
        this.kline = kline;
        this.type = type;
        this.summary = summary;
        this.triggerPrice = triggerPrice;
        this.invalidationPrice = invalidationPrice;
        this.targetPrice = targetPrice;
        this.volumeRatio = volumeRatio;
    }

    public TradeDirection getDirection() {
        return direction;
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

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public BigDecimal getVolumeRatio() {
        return volumeRatio;
    }
}

