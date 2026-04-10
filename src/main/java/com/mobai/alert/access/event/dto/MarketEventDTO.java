package com.mobai.alert.access.event.dto;

import java.time.Instant;

/**
 * 标准市场事件 DTO。
 */
public class MarketEventDTO {

    private Instant eventTime;
    private String source;
    private String entity;
    private String eventType;
    private String rawText;
    private String sentiment;
    private Double novelty;
    private Double confidence;
    private Double sourceQuality;
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
