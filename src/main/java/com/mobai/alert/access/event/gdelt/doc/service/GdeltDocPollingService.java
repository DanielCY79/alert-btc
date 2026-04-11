package com.mobai.alert.access.event.gdelt.doc.service;

import com.mobai.alert.access.event.dto.MarketEventDTO;
import com.mobai.alert.access.event.dto.SocialEventDTO;
import com.mobai.alert.access.event.service.MarketEventService;
import com.mobai.alert.access.event.gdelt.doc.dto.GdeltArticleDTO;
import com.mobai.alert.access.event.gdelt.doc.rest.GdeltDocRestClient;
import com.mobai.alert.notification.AlertNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GDELT DOC 轮询服务。
 * 定时从 GDELT 拉取与加密市场相关的新闻文章，将其转成社交事件文本，
 * 再交给市场事件服务标准化，最后触发事件通知。
 */
@Service
public class GdeltDocPollingService {

    private static final Logger log = LoggerFactory.getLogger(GdeltDocPollingService.class);

    private final GdeltDocRestClient gdeltDocRestClient;
    private final MarketEventService marketEventService;
    private final AlertNotificationService alertNotificationService;

    /**
     * 已处理文章去重表，值为处理时间。
     */
    private final Map<String, Long> processedArticles = new ConcurrentHashMap<>();

    @Value("${gdelt.doc.enabled:false}")
    private boolean gdeltEnabled;

    @Value("${gdelt.doc.query:(bitcoin OR btc OR ethereum OR eth OR solana OR sol OR binance OR sec OR etf)}")
    private String query;

    @Value("${gdelt.doc.timespan:1h}")
    private String timespan;

    @Value("${gdelt.doc.max-records:25}")
    private int maxRecords;

    @Value("${gdelt.doc.source-quality:0.70}")
    private double sourceQuality;

    @Value("${gdelt.doc.processed-ttl-ms:604800000}")
    private long processedTtlMs;

    public GdeltDocPollingService(GdeltDocRestClient gdeltDocRestClient,
                                  MarketEventService marketEventService,
                                  AlertNotificationService alertNotificationService) {
        this.gdeltDocRestClient = gdeltDocRestClient;
        this.marketEventService = marketEventService;
        this.alertNotificationService = alertNotificationService;
    }

    /**
     * 定时拉取最新文章并转换为市场事件。
     */
    @Scheduled(
            fixedDelayString = "${gdelt.doc.poll-delay-ms:300000}",
            initialDelayString = "${gdelt.doc.initial-delay-ms:30000}"
    )
    public void pollLatestArticles() {
        if (!isFeatureActive()) {
            return;
        }

        List<GdeltArticleDTO> articles = gdeltDocRestClient.searchArticles(query, timespan, maxRecords);
        if (articles.isEmpty()) {
            return;
        }

        List<GdeltArticleDTO> orderedArticles = new ArrayList<>(articles);
        orderedArticles.sort(Comparator.comparing(article -> article.getEventTime() == null ? Instant.EPOCH : article.getEventTime()));

        int ingestedCount = 0;
        for (GdeltArticleDTO article : orderedArticles) {
            if (shouldSkip(article)) {
                continue;
            }
            processedArticles.put(buildArticleKey(article), System.currentTimeMillis());
            MarketEventDTO event = marketEventService.ingestSocialEvent(toSocialEvent(article));
            if (event != null) {
                alertNotificationService.sendMarketEvent(event, article.getTitle(), article.getUrl());
                ingestedCount++;
            }
        }

        if (ingestedCount > 0) {
            log.info("已将 {} 篇 GDELT DOC 文章注入市场事件流，query={}，timespan={}",
                    ingestedCount,
                    query,
                    timespan);
        }
    }

    /**
     * 定期清理过期的文章去重记录。
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    public void cleanupProcessedArticles() {
        long currentTime = System.currentTimeMillis();
        processedArticles.entrySet().removeIf(entry -> currentTime - entry.getValue() > processedTtlMs);
    }

    /**
     * 功能启用条件。
     */
    private boolean isFeatureActive() {
        return gdeltEnabled && StringUtils.hasText(query);
    }

    /**
     * 跳过空文章或已经处理过的文章。
     */
    private boolean shouldSkip(GdeltArticleDTO article) {
        if (article == null || !StringUtils.hasText(article.getTitle())) {
            return true;
        }
        return processedArticles.containsKey(buildArticleKey(article));
    }

    /**
     * 构造文章去重键。
     */
    private String buildArticleKey(GdeltArticleDTO article) {
        String url = normalize(article.getUrl());
        String title = normalize(article.getTitle());
        long eventTime = article.getEventTime() == null ? 0L : article.getEventTime().toEpochMilli();
        return url + "|" + eventTime + "|" + title;
    }

    /**
     * 将新闻文章转成通用社交事件输入。
     * 这里不直接做事件分类，而是复用已有的市场事件标准化逻辑。
     */
    private SocialEventDTO toSocialEvent(GdeltArticleDTO article) {
        SocialEventDTO socialEvent = new SocialEventDTO();
        socialEvent.setEventTime(article.getEventTime());
        socialEvent.setSource("gdelt_doc");
        socialEvent.setSourceQuality(sourceQuality);
        socialEvent.setRawText(join(article.getTitle(), article.getExcerpt(), article.getDomain(), article.getLanguage(), article.getSourceCountry()));
        return socialEvent;
    }

    /**
     * 将若干文本片段拼成一段原始文本。
     */
    private String join(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(" ", parts);
    }

    /**
     * 统一做去重用的小写归一化。
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
