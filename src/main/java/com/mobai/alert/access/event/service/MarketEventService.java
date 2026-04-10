package com.mobai.alert.access.event.service;

import com.mobai.alert.access.binance.cms.dto.BinanceAnnouncementDTO;
import com.mobai.alert.access.event.dto.MarketEventDTO;
import com.mobai.alert.access.event.dto.SocialEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 缁熶竴浜嬩欢绠￠亾銆? * 鐢ㄤ簬鎶?CMS 鍏憡鍜屾湭鏉ュ叾浠?social_event 鏁版嵁婧愭暣鐞嗘垚缁熶竴浜嬩欢缁撴瀯銆? */
@Service
public class MarketEventService {

    private static final Logger log = LoggerFactory.getLogger(MarketEventService.class);
    private static final Pattern CASHTAG_PATTERN = Pattern.compile("\\$([A-Za-z]{2,10})");
    private static final Pattern ENTITY_TOKEN_PATTERN = Pattern.compile("\\b(BTC|BITCOIN|ETH|ETHEREUM|SOL|SOLANA|BNB|BINANCE|SEC|ETF)\\b", Pattern.CASE_INSENSITIVE);
    private static final long EVENT_RETENTION_MS = 7L * 24 * 60 * 60 * 1000;

    private final Object monitor = new Object();
    private final Deque<MarketEventDTO> recentEvents = new ArrayDeque<>();
    private final Map<String, Integer> mentionCounts = new ConcurrentHashMap<>();

    public MarketEventDTO ingestBinanceAnnouncement(BinanceAnnouncementDTO announcement) {
        if (announcement == null) {
            return null;
        }

        String rawText = join(
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getDisclaimer()
        );

        MarketEventDTO event = buildEvent(
                announcement.getPublishDate() == null ? Instant.now() : Instant.ofEpochMilli(announcement.getPublishDate()),
                "binance_cms",
                rawText,
                0.95
        );
        store(event);
        return event;
    }

    public MarketEventDTO ingestSocialEvent(SocialEventDTO socialEvent) {
        if (socialEvent == null || !StringUtils.hasText(socialEvent.getRawText())) {
            return null;
        }

        double sourceQuality = socialEvent.getSourceQuality() == null ? 0.60 : socialEvent.getSourceQuality();
        MarketEventDTO event = buildEvent(
                socialEvent.getEventTime(),
                StringUtils.hasText(socialEvent.getSource()) ? socialEvent.getSource() : "social_event",
                socialEvent.getRawText(),
                sourceQuality
        );
        store(event);
        return event;
    }

    public List<MarketEventDTO> getRecentEvents() {
        synchronized (monitor) {
            cleanupExpiredLocked();
            return List.copyOf(recentEvents);
        }
    }

    private MarketEventDTO buildEvent(Instant eventTime, String source, String rawText, double sourceQuality) {
        String normalizedText = normalize(rawText);
        String entity = detectEntity(normalizedText);
        String eventType = detectEventType(normalizedText);
        String sentiment = detectSentiment(normalizedText, eventType);
        String key = normalize(entity) + "|" + normalize(eventType) + "|" + normalize(source);
        int mentionVelocity = mentionCounts.merge(key, 1, Integer::sum);

        MarketEventDTO event = new MarketEventDTO();
        event.setEventTime(eventTime == null ? Instant.now() : eventTime);
        event.setSource(source);
        event.setEntity(entity);
        event.setEventType(eventType);
        event.setRawText(normalizedText);
        event.setSentiment(sentiment);
        event.setMentionVelocity((double) mentionVelocity);
        event.setSourceQuality(sourceQuality);
        event.setNovelty(calculateNovelty(normalizedText));
        event.setConfidence(calculateConfidence(entity, eventType, sentiment, sourceQuality));
        return event;
    }

    private void store(MarketEventDTO event) {
        if (event == null) {
            return;
        }
        synchronized (monitor) {
            cleanupExpiredLocked();
            recentEvents.addLast(event);
        }
        log.info("甯傚満浜嬩欢鍏ュ簱锛宼ime={}锛宻ource={}锛宔ntity={}锛宔ventType={}锛宻entiment={}锛宯ovelty={}锛宑onfidence={}锛宺awText={}",
                event.getEventTime(),
                event.getSource(),
                event.getEntity(),
                event.getEventType(),
                event.getSentiment(),
                event.getNovelty(),
                event.getConfidence(),
                event.getRawText());
    }

    private void cleanupExpiredLocked() {
        Instant threshold = Instant.now().minusMillis(EVENT_RETENTION_MS);
        while (!recentEvents.isEmpty() && recentEvents.peekFirst().getEventTime().isBefore(threshold)) {
            recentEvents.removeFirst();
        }
    }

    private String detectEntity(String rawText) {
        Matcher cashtagMatcher = CASHTAG_PATTERN.matcher(rawText);
        if (cashtagMatcher.find()) {
            return cashtagMatcher.group(1).toUpperCase(Locale.ROOT);
        }

        for (String preferredAsset : List.of("BTC", "BITCOIN", "ETH", "ETHEREUM", "SOL", "SOLANA", "BNB")) {
            Pattern pattern = Pattern.compile("\\b" + preferredAsset + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(rawText);
            if (matcher.find()) {
                String token = matcher.group().toUpperCase(Locale.ROOT);
                return switch (token) {
                    case "BITCOIN" -> "BTC";
                    case "ETHEREUM" -> "ETH";
                    case "SOLANA" -> "SOL";
                    default -> token;
                };
            }
        }

        Matcher matcher = ENTITY_TOKEN_PATTERN.matcher(rawText);
        if (matcher.find()) {
            String token = matcher.group(1).toUpperCase(Locale.ROOT);
            return switch (token) {
                case "BITCOIN" -> "BTC";
                case "ETHEREUM" -> "ETH";
                case "SOLANA" -> "SOL";
                default -> token;
            };
        }

        return "MARKET";
    }

    private String detectEventType(String rawText) {
        String text = normalize(rawText);
        if (containsAny(text, "delist", "delisting", "remove")) {
            return "delisting";
        }
        if (containsAny(text, "list", "listing", "launchpool", "launch")) {
            return "listing";
        }
        if (containsAny(text, "exploit", "hack", "breach", "drain")) {
            return "exploit";
        }
        if (containsAny(text, "unlock", "vesting")) {
            return "unlock";
        }
        if (containsAny(text, "partnership", "partner", "collaboration", "integrat")) {
            return "partnership";
        }
        if (containsAny(text, "sec", "regulat", "approval", "lawsuit", "etf")) {
            return "regulation";
        }
        return "news";
    }

    private String detectSentiment(String rawText, String eventType) {
        String text = normalize(rawText);
        if ("listing".equals(eventType) || "partnership".equals(eventType)) {
            return "bullish";
        }
        if ("delisting".equals(eventType) || "exploit".equals(eventType)) {
            return "bearish";
        }
        if ("unlock".equals(eventType) && containsAny(text, "large", "massive", "cliff")) {
            return "bearish";
        }
        if ("regulation".equals(eventType) && containsAny(text, "approve", "approval", "greenlight")) {
            return "bullish";
        }
        if ("regulation".equals(eventType) && containsAny(text, "ban", "charge", "sue", "lawsuit")) {
            return "bearish";
        }
        return "neutral";
    }

    private double calculateNovelty(String rawText) {
        String fingerprint = fingerprint(rawText);
        int similarCount = 0;
        synchronized (monitor) {
            for (MarketEventDTO event : recentEvents) {
                if (fingerprint(event.getRawText()).equals(fingerprint)) {
                    similarCount++;
                }
            }
        }
        return similarCount == 0 ? 1.0 : 1.0 / (similarCount + 1);
    }

    private double calculateConfidence(String entity, String eventType, String sentiment, double sourceQuality) {
        double confidence = sourceQuality;
        if (!"MARKET".equals(entity)) {
            confidence += 0.10;
        }
        if (!"news".equals(eventType)) {
            confidence += 0.10;
        }
        if (!"neutral".equals(sentiment)) {
            confidence += 0.05;
        }
        return Math.min(1.0, confidence);
    }

    private String fingerprint(String rawText) {
        String normalized = normalize(rawText)
                .replaceAll("https?://\\S+", "")
                .replaceAll("[^a-z0-9$ ]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= 140) {
            return normalized;
        }
        return normalized.substring(0, 140);
    }

    private boolean containsAny(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }
}

