package com.mobai.alert.state.signal;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;

import java.math.BigDecimal;

/**
 * 策略输出的标准信号对象。
 * 封装方向、信号类型、触发位、失效位、目标位以及上下文评分等关键信息。
 */
public class AlertSignal {
    /**
     * 信号方向。
     */
    private final TradeDirection direction;
    /**
     * 对外展示标题。
     */
    private final String title;
    /**
     * 触发信号时关联的 K 线。
     */
    private final BinanceKlineDTO kline;
    /**
     * 信号类别标识。
     */
    private final String type;
    /**
     * 简要策略说明。
     */
    private final String summary;
    /**
     * 触发价。
     */
    private final BigDecimal triggerPrice;
    /**
     * 失效价。
     */
    private final BigDecimal invalidationPrice;
    /**
     * 目标价。
     */
    private final BigDecimal targetPrice;
    /**
     * 量能倍率或相关比值。
     */
    private final BigDecimal volumeRatio;
    /**
     * 因子策略打分。
     */
    private final BigDecimal contextScore;
    /**
     * 因子策略生成的上下文说明。
     */
    private final String contextComment;

    /**
     * 创建基础信号对象，不附带上下文评分。
     */
    public AlertSignal(TradeDirection direction,
                       String title,
                       BinanceKlineDTO kline,
                       String type,
                       String summary,
                       BigDecimal triggerPrice,
                       BigDecimal invalidationPrice,
                       BigDecimal targetPrice,
                       BigDecimal volumeRatio) {
        this(direction, title, kline, type, summary, triggerPrice, invalidationPrice, targetPrice, volumeRatio, null, null);
    }

    /**
     * 创建完整信号对象。
     */
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

    /**
     * 复制当前信号，并附加新的上下文评分与说明。
     */
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
                nextContextComment
        );
    }
}

