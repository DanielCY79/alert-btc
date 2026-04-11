package com.mobai.alert.state.signal;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;

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
    private final BigDecimal contextScore;
    private final String contextComment;
    private final BigDecimal referenceEntryPrice;
    private final BigDecimal referenceStopPrice;

    public AlertSignal(TradeDirection direction,
                       String title,
                       BinanceKlineDTO kline,
                       String type,
                       String summary,
                       BigDecimal triggerPrice,
                       BigDecimal invalidationPrice,
                       BigDecimal targetPrice,
                       BigDecimal volumeRatio) {
        this(direction, title, kline, type, summary, triggerPrice, invalidationPrice, targetPrice, volumeRatio, null, null, null, null);
    }

    public AlertSignal(TradeDirection direction,
                       String title,
                       BinanceKlineDTO kline,
                       String type,
                       String summary,
                       BigDecimal triggerPrice,
                       BigDecimal invalidationPrice,
                       BigDecimal targetPrice,
                       BigDecimal volumeRatio,
                       BigDecimal contextScore,
                       String contextComment) {
        this(direction, title, kline, type, summary, triggerPrice, invalidationPrice, targetPrice, volumeRatio, contextScore, contextComment, null, null);
    }

    public AlertSignal(TradeDirection direction,
                       String title,
                       BinanceKlineDTO kline,
                       String type,
                       String summary,
                       BigDecimal triggerPrice,
                       BigDecimal invalidationPrice,
                       BigDecimal targetPrice,
                       BigDecimal volumeRatio,
                       BigDecimal contextScore,
                       String contextComment,
                       BigDecimal referenceEntryPrice,
                       BigDecimal referenceStopPrice) {
        this.direction = direction;
        this.title = title;
        this.kline = kline;
        this.type = type;
        this.summary = summary;
        this.triggerPrice = triggerPrice;
        this.invalidationPrice = invalidationPrice;
        this.targetPrice = targetPrice;
        this.volumeRatio = volumeRatio;
        this.contextScore = contextScore;
        this.contextComment = contextComment;
        this.referenceEntryPrice = referenceEntryPrice;
        this.referenceStopPrice = referenceStopPrice;
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

    public BigDecimal getContextScore() {
        return contextScore;
    }

    public String getContextComment() {
        return contextComment;
    }

    public BigDecimal getReferenceEntryPrice() {
        return referenceEntryPrice;
    }

    public BigDecimal getReferenceStopPrice() {
        return referenceStopPrice;
    }

    public AlertSignal withContext(BigDecimal nextContextScore, String nextContextComment) {
        return new AlertSignal(
                direction,
                title,
                kline,
                type,
                summary,
                triggerPrice,
                invalidationPrice,
                targetPrice,
                volumeRatio,
                nextContextScore,
                nextContextComment,
                referenceEntryPrice,
                referenceStopPrice
        );
    }
}
