package com.mobai.alert.feature.extractor;

import com.mobai.alert.access.event.dto.MarketEventDTO;
import com.mobai.alert.feature.model.EventFeatures;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 事件特征提取器。
 * 根据最近事件的时效性、置信度和情绪方向，生成事件偏置与冲击强度。
 */
@Component
public class EventFeatureExtractor {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Value("${monitoring.feature.event.lookback-ms:21600000}")
    private long eventLookbackMs;

    @Value("${monitoring.feature.event.half-life-ms:7200000}")
    private long eventHalfLifeMs;

    /**
     * 根据标的和锚定时间，从最近事件中提取有效的事件特征。
     */
    public EventFeatures extract(String symbol, Long anchorTime, List<MarketEventDTO> recentEvents) {
        EventFeatures features = new EventFeatures();
        features.setRelevantEventCount(0);
        features.setBullishEventCount(0);
        features.setBearishEventCount(0);
        features.setNeutralEventCount(0);
        features.setBullishScore(ZERO);
        features.setBearishScore(ZERO);
        features.setEventBiasScore(ZERO);
        features.setEventShockScore(ZERO);

        if (CollectionUtils.isEmpty(recentEvents)) {
            return features;
        }

        long effectiveAnchorTime = anchorTime == null ? System.currentTimeMillis() : anchorTime;
        String baseAsset = inferBaseAsset(symbol);
        List<MarketEventDTO> relevantEvents = new ArrayList<>();
        for (MarketEventDTO event : recentEvents) {
            if (event == null || event.getEventTime() == null) {
                continue;
            }

            long ageMs = effectiveAnchorTime - event.getEventTime().toEpochMilli();
            if (ageMs < 0 || ageMs > eventLookbackMs) {
                continue;
            }
            if (!isRelevantEntity(baseAsset, event.getEntity())) {
                continue;
            }
            relevantEvents.add(event);
        }

        relevantEvents.sort(Comparator.comparing(MarketEventDTO::getEventTime).reversed());
        if (relevantEvents.isEmpty()) {
            return features;
        }

        features.setHasRelevantEvents(true);
        features.setRelevantEventCount(relevantEvents.size());

        MarketEventDTO latestEvent = relevantEvents.get(0);
        features.setLatestEventType(latestEvent.getEventType());
        features.setLatestEventSentiment(latestEvent.getSentiment());
        features.setLatestEventSource(latestEvent.getSource());
        features.setLatestEventConfidence(latestEvent.getConfidence());
        features.setLatestEventAgeMs(Math.max(0L, effectiveAnchorTime - latestEvent.getEventTime().toEpochMilli()));

        BigDecimal bullishScore = ZERO;
        BigDecimal bearishScore = ZERO;
        BigDecimal shockScore = ZERO;

        for (MarketEventDTO event : relevantEvents) {
            long ageMs = effectiveAnchorTime - event.getEventTime().toEpochMilli();
            double weight = scoreWeight(event, ageMs);
            BigDecimal weightValue = BigDecimal.valueOf(weight);
            shockScore = shockScore.add(weightValue);

            String sentiment = normalize(event.getSentiment());
            if ("bullish".equals(sentiment)) {
                bullishScore = bullishScore.add(weightValue);
                features.setBullishEventCount(features.getBullishEventCount() + 1);
            } else if ("bearish".equals(sentiment)) {
                bearishScore = bearishScore.add(weightValue);
                features.setBearishEventCount(features.getBearishEventCount() + 1);
            } else {
                features.setNeutralEventCount(features.getNeutralEventCount() + 1);
            }
        }

        features.setBullishScore(bullishScore);
        features.setBearishScore(bearishScore);
        features.setEventBiasScore(bullishScore.subtract(bearishScore));
        features.setEventShockScore(shockScore);
        return features;
    }

    /**
     * 结合置信度、新颖度和时间衰减计算单条事件权重。
     */
    private double scoreWeight(MarketEventDTO event, long ageMs) {
        double confidence = safe(event.getConfidence(), 0.60d);
        double novelty = safe(event.getNovelty(), 1.0d);
        double decay = Math.exp(-(double) ageMs / Math.max(1L, eventHalfLifeMs));
        return confidence * novelty * decay * eventTypeMultiplier(event.getEventType());
    }

    /**
     * 为不同事件类型分配不同的冲击系数。
     */
    private double eventTypeMultiplier(String eventType) {
        return switch (normalize(eventType)) {
            case "delisting", "exploit" -> 1.30d;
            case "listing", "regulation" -> 1.10d;
            default -> 1.0d;
        };
    }

    /**
     * 判断事件实体是否与当前标的相关。
     */
    private boolean isRelevantEntity(String baseAsset, String entity) {
        String normalizedEntity = normalize(entity);
        if (!StringUtils.hasText(baseAsset)) {
            return "market".equals(normalizedEntity);
        }
        return baseAsset.equals(normalizedEntity) || "market".equals(normalizedEntity);
    }

    /**
     * 从交易对符号中推断基础资产代码。
     */
    private String inferBaseAsset(String symbol) {
        String normalized = normalize(symbol);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }

        for (String suffix : List.of("usdt", "busd", "usdc", "fdusd", "tusd", "btc", "eth")) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }
        return normalized;
    }

    /**
     * 为空值提供默认数值。
     */
    private double safe(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    /**
     * 统一做小写归一化。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
