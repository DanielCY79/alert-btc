package com.mobai.alert.access.event.gdelt.doc.rest;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mobai.alert.access.event.gdelt.doc.dto.GdeltArticleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GDELT DOC REST 客户端。
 * 用于从 GDELT 新闻检索接口拉取指定主题的文章摘要，
 * 并把不同格式的返回字段统一映射为项目内部使用的文章 DTO。
 */
@Component
public class GdeltDocRestClient {

    private static final Logger log = LoggerFactory.getLogger(GdeltDocRestClient.class);
    private static final DateTimeFormatter GDELT_SEEN_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter GDELT_ZULU_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final RestTemplate restTemplate;

    @Value("${gdelt.doc.base-url:https://api.gdeltproject.org/api/v2/doc/doc}")
    private String baseUrl;

    @Value("${gdelt.doc.sort:datedesc}")
    private String defaultSort;

    public GdeltDocRestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 使用默认排序拉取文章列表。
     *
     * @param query 查询表达式
     * @param timespan 查询时间窗
     * @param maxRecords 最大返回条数
     * @return 文章列表
     */
    public List<GdeltArticleDTO> searchArticles(String query, String timespan, Integer maxRecords) {
        return searchArticles(query, timespan, maxRecords, defaultSort);
    }

    /**
     * 拉取文章列表。
     *
     * @param query 查询表达式
     * @param timespan 查询时间窗
     * @param maxRecords 最大返回条数
     * @param sort 排序方式
     * @return 文章列表；失败时返回空列表
     */
    public List<GdeltArticleDTO> searchArticles(String query, String timespan, Integer maxRecords, String sort) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(baseUrl)) {
            return List.of();
        }

        try {
            String requestUrl = UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("query", query)
                    .queryParam("mode", "ArtList")
                    .queryParam("format", "json")
                    .queryParam("sort", StringUtils.hasText(sort) ? sort : "datedesc")
                    .queryParamIfPresent("timespan", StringUtils.hasText(timespan) ? Optional.of(timespan) : Optional.empty())
                    .queryParamIfPresent("maxrecords", maxRecords == null ? Optional.empty() : Optional.of(maxRecords))
                    .build()
                    .encode()
                    .toUriString();
            String body = restTemplate.getForObject(requestUrl, String.class);
            return parseArticles(body);
        } catch (Exception e) {
            log.warn("Failed to fetch GDELT DOC articles, query={}, timespan={}, maxRecords={}",
                    query,
                    timespan,
                    maxRecords,
                    e);
            return List.of();
        }
    }

    /**
     * 将 GDELT 原始 JSON 响应解析为文章 DTO 列表。
     * 这里兼容了不同字段名版本，例如 {@code articles} 和 {@code items}。
     */
    List<GdeltArticleDTO> parseArticles(String body) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }

        JSONObject root = JSON.parseObject(body);
        if (root == null) {
            return List.of();
        }
        JSONArray articles = root.getJSONArray("articles");
        if (articles == null) {
            articles = root.getJSONArray("items");
        }
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }

        List<GdeltArticleDTO> results = new ArrayList<>(articles.size());
        for (int i = 0; i < articles.size(); i++) {
            JSONObject article = articles.getJSONObject(i);
            if (article == null) {
                continue;
            }
            GdeltArticleDTO dto = new GdeltArticleDTO();
            dto.setTitle(readText(article, "title", "name"));
            dto.setUrl(readText(article, "url", "id"));
            dto.setDomain(readText(article, "domain"));
            dto.setLanguage(readText(article, "language"));
            dto.setSourceCountry(readText(article, "sourcecountry", "sourceCountry"));
            dto.setSocialImage(readText(article, "socialimage", "socialImage"));
            dto.setExcerpt(readText(article, "summary", "description", "snippet", "excerpt"));
            dto.setEventTime(parseInstant(readText(article, "seendate", "seenDate", "date_published", "published")));
            results.add(dto);
        }
        return results;
    }

    /**
     * 按候选字段顺序读取第一个非空文本值。
     */
    private String readText(JSONObject article, String... keys) {
        for (String key : keys) {
            String value = article.getString(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 解析 GDELT 可能返回的多种时间格式。
     */
    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed, GDELT_SEEN_DATE_FORMATTER).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed, GDELT_ZULU_DATE_FORMATTER).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }

        if (trimmed.matches("\\d{13}")) {
            return Instant.ofEpochMilli(Long.parseLong(trimmed));
        }
        if (trimmed.matches("\\d{10}")) {
            return Instant.ofEpochSecond(Long.parseLong(trimmed));
        }
        return null;
    }
}
