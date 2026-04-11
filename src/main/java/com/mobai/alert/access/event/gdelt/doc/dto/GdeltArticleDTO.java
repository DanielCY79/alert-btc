package com.mobai.alert.access.event.gdelt.doc.dto;

import java.time.Instant;

/**
 * GDELT DOC 文章对象。
 * 用于承接 GDELT 新闻检索接口返回的文章摘要信息，
 * 后续会被转换为通用社交事件并送入市场事件标准化流程。
 */
public class GdeltArticleDTO {

    /**
     * 文章对应的事件时间或发布时间。
     */
    private Instant eventTime;

    /**
     * 文章标题。
     */
    private String title;

    /**
     * 原文链接。
     */
    private String url;

    /**
     * 来源站点域名。
     */
    private String domain;

    /**
     * 文章语言。
     */
    private String language;

    /**
     * 来源国家。
     */
    private String sourceCountry;

    /**
     * 社交媒体预览图地址。
     */
    private String socialImage;

    /**
     * 文章摘要或摘录。
     */
    private String excerpt;

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceCountry() {
        return sourceCountry;
    }

    public void setSourceCountry(String sourceCountry) {
        this.sourceCountry = sourceCountry;
    }

    public String getSocialImage() {
        return socialImage;
    }

    public void setSocialImage(String socialImage) {
        this.socialImage = socialImage;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }
}
