package com.mobai.alert.feature.service;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.capitalflow.dto.BinanceDerivativeFeaturesDTO;
import com.mobai.alert.access.event.service.MarketEventService;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.extractor.CompositeFactorCalculator;
import com.mobai.alert.feature.extractor.DerivativeFeatureExtractor;
import com.mobai.alert.feature.extractor.EventFeatureExtractor;
import com.mobai.alert.feature.extractor.PriceFeatureExtractor;
import com.mobai.alert.feature.model.CompositeFactors;
import com.mobai.alert.feature.model.DerivativeFeatures;
import com.mobai.alert.feature.model.EventFeatures;
import com.mobai.alert.feature.model.FeatureQuality;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.model.PriceFeatures;
import com.mobai.alert.strategy.shared.StrategySupport;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实盘特征快照服务。
 * 负责整合价格、衍生品和事件数据，并缓存最近一次统一快照。
 */
@Service
public class MarketFeatureSnapshotService {

    private final BinanceApi binanceApi;
    private final MarketEventService marketEventService;
    private final PriceFeatureExtractor priceFeatureExtractor;
    private final DerivativeFeatureExtractor derivativeFeatureExtractor;
    private final EventFeatureExtractor eventFeatureExtractor;
    private final CompositeFactorCalculator compositeFactorCalculator;
    private final Map<String, FeatureSnapshot> latestSnapshots = new ConcurrentHashMap<>();

    public MarketFeatureSnapshotService(BinanceApi binanceApi,
                                        MarketEventService marketEventService,
                                        PriceFeatureExtractor priceFeatureExtractor,
                                        DerivativeFeatureExtractor derivativeFeatureExtractor,
                                        EventFeatureExtractor eventFeatureExtractor,
                                        CompositeFactorCalculator compositeFactorCalculator) {
        this.binanceApi = binanceApi;
        this.marketEventService = marketEventService;
        this.priceFeatureExtractor = priceFeatureExtractor;
        this.derivativeFeatureExtractor = derivativeFeatureExtractor;
        this.eventFeatureExtractor = eventFeatureExtractor;
        this.compositeFactorCalculator = compositeFactorCalculator;
    }

    /**
     * 构建并缓存指定交易对的统一特征快照。
     */
    public FeatureSnapshot buildSnapshot(String symbol, String interval, List<BinanceKlineDTO> klines) {
        PriceFeatures priceFeatures = priceFeatureExtractor.extract(symbol, interval, klines);
        BinanceDerivativeFeaturesDTO rawDerivativeFeatures = binanceApi.buildDerivativeFeatures(symbol);
        DerivativeFeatures derivativeFeatures = derivativeFeatureExtractor.extract(rawDerivativeFeatures);
        long anchorTime = priceFeatures.getAsOfTime() == null ? System.currentTimeMillis() : priceFeatures.getAsOfTime();
        EventFeatures eventFeatures = eventFeatureExtractor.extract(symbol, anchorTime, marketEventService.getRecentEvents());
        CompositeFactors compositeFactors = compositeFactorCalculator.calculate(priceFeatures, derivativeFeatures, eventFeatures);

        FeatureSnapshot snapshot = new FeatureSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setInterval(interval);
        snapshot.setAsOfTime(anchorTime);
        snapshot.setGeneratedAt(System.currentTimeMillis());
        snapshot.setPriceFeatures(priceFeatures);
        snapshot.setDerivativeFeatures(derivativeFeatures);
        snapshot.setEventFeatures(eventFeatures);
        snapshot.setCompositeFactors(compositeFactors);
        snapshot.setQuality(buildQuality(klines, priceFeatures, derivativeFeatures, eventFeatures));
        latestSnapshots.put(symbol, snapshot);
        return snapshot;
    }

    /**
     * 返回缓存中的最近一次快照。
     */
    public FeatureSnapshot getLatestSnapshot(String symbol) {
        return latestSnapshots.get(symbol);
    }

    /**
     * 评估当前快照的数据完整性与新鲜度。
     */
    private FeatureQuality buildQuality(List<BinanceKlineDTO> klines,
                                        PriceFeatures priceFeatures,
                                        DerivativeFeatures derivativeFeatures,
                                        EventFeatures eventFeatures) {
        FeatureQuality quality = new FeatureQuality();
        quality.setRawKlineCount(klines == null ? 0 : klines.size());
        quality.setClosedKlineCount(StrategySupport.closedKlines(klines).size());
        quality.setPriceReady(priceFeatures.getAsOfTime() != null);
        quality.setDerivativeReady(hasDerivativeData(derivativeFeatures));
        quality.setEventReady(true);
        quality.setRelevantEventCount(eventFeatures.getRelevantEventCount() == null ? 0 : eventFeatures.getRelevantEventCount());
        long now = System.currentTimeMillis();
        if (priceFeatures.getAsOfTime() != null) {
            quality.setPriceAgeMs(Math.max(0L, now - priceFeatures.getAsOfTime()));
        }
        if (derivativeFeatures.getAsOfTime() != null) {
            quality.setDerivativeAgeMs(Math.max(0L, now - derivativeFeatures.getAsOfTime()));
        }
        quality.setLatestRelevantEventAgeMs(eventFeatures.getLatestEventAgeMs());
        quality.setCompleteSnapshot(quality.isPriceReady() && quality.isDerivativeReady());
        return quality;
    }

    /**
     * 判断衍生品特征是否至少有一部分真实数据到位。
     */
    private boolean hasDerivativeData(DerivativeFeatures derivativeFeatures) {
        return derivativeFeatures.getAsOfTime() != null
                || derivativeFeatures.getOiDelta5m() != null
                || derivativeFeatures.getFundingZscore() != null
                || derivativeFeatures.getTakerBuySellImbalance() != null
                || derivativeFeatures.getTopTraderAccountRatioChange() != null
                || derivativeFeatures.getTopTraderPositionRatioChange() != null
                || derivativeFeatures.getLiquidationClusterIntensity() != null;
    }
}
