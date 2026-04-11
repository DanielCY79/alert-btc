package com.mobai.alert.access.event.dto;

import java.time.Instant;

/**
 * 标准化市场事件对象。
 * 无论事件来源于 Binance 公告还是外部社交源，都会被整理成统一结构，
 * 便于后续做事件驱动分析、通知下发或与交易信号联动。
 */
public class MarketEventDTO {

    /**
     * 事件发生或发布时间。
     */
    private Instant eventTime;

    /**
     * 事件来源标识，例如 {@code binance_cms}、{@code social_event}。
     */
    private String source;

    /**
     * 事件涉及的主体或币种，例如 {@code BTC}、{@code ETH}。
     */
    private String entity;

    /**
     * 识别出的事件类型，例如上币、下币、监管、攻击等。
     */
    private String eventType;

    /**
     * 规范化后的原始文本内容。
     */
    private String rawText;

    /**
     * 识别出的情绪倾向，例如利多、利空或中性。
     */
    private String sentiment;

    /**
     * 新颖度评分。
     * 值越高表示与最近事件越不重复。
     */
    private Double novelty;

    /**
     * 事件识别置信度。
     */
    private Double confidence;

    /**
     * 来源质量评分。
     * 一般由上游源头可靠性或接入策略给出。
     */
    private Double sourceQuality;

    /**
     * 事件提及速度。
     * 当前实现中表现为相同实体/类型/来源组合在短期内累计出现的次数。
     */
    private Double mentionVelocity;

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }

    public Double getNovelty() {
        return novelty;
    }

    public void setNovelty(Double novelty) {
        this.novelty = novelty;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Double getSourceQuality() {
        return sourceQuality;
    }

    public void setSourceQuality(Double sourceQuality) {
        this.sourceQuality = sourceQuality;
    }

    public Double getMentionVelocity() {
        return mentionVelocity;
    }

    public void setMentionVelocity(Double mentionVelocity) {
        this.mentionVelocity = mentionVelocity;
    }
}
