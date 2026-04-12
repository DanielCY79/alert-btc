package com.mobai.alert.feature.service;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.extractor.CompositeFactorCalculator;
import com.mobai.alert.feature.extractor.PriceFeatureExtractor;
import com.mobai.alert.feature.model.CompositeFactors;
import com.mobai.alert.feature.model.DerivativeFeatures;
import com.mobai.alert.feature.model.EventFeatures;
import com.mobai.alert.feature.model.FeatureQuality;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.model.PriceFeatures;
import com.mobai.alert.strategy.priceaction.shared.StrategySupport;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回测专用特征快照服务。
 * 在缺少实时衍生品和事件数据时，只基于价格特征构造可用于回测的统一快照。
 */
@Service
public class BacktestFeatureSnapshotService {

    private final PriceFeatureExtractor priceFeatureExtractor;
    private final CompositeFactorCalculator compositeFactorCalculator;

    public BacktestFeatureSnapshotService(PriceFeatureExtractor priceFeatureExtractor,
                                          CompositeFactorCalculator compositeFactorCalculator) {
        this.priceFeatureExtractor = priceFeatureExtractor;
        this.compositeFactorCalculator = compositeFactorCalculator;
    }

    /**
     * 为回测场景构建精简版特征快照。
     */
    public FeatureSnapshot buildSnapshot(String symbol, String interval, List<BinanceKlineDTO> klines) {
        PriceFeatures priceFeatures = priceFeatureExtractor.extract(symbol, interval, klines);

        EventFeatures eventFeatures = new EventFeatures();
        eventFeatures.setRelevantEventCount(0);
        eventFeatures.setBullishEventCount(0);
        eventFeatures.setBearishEventCount(0);
        eventFeatures.setNeutralEventCount(0);

        DerivativeFeatures derivativeFeatures = new DerivativeFeatures();
        CompositeFactors compositeFactors = compositeFactorCalculator.calculate(priceFeatures, derivativeFeatures, eventFeatures);

        FeatureQuality quality = new FeatureQuality();
        quality.setRawKlineCount(klines == null ? 0 : klines.size());
        quality.setClosedKlineCount(StrategySupport.closedKlines(klines).size());
        quality.setPriceReady(priceFeatures.getAsOfTime() != null);
        quality.setDerivativeReady(false);
        quality.setEventReady(false);
        quality.setRelevantEventCount(0);
        quality.setCompleteSnapshot(quality.isPriceReady());

        FeatureSnapshot snapshot = new FeatureSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setInterval(interval);
        snapshot.setAsOfTime(priceFeatures.getAsOfTime());
        snapshot.setGeneratedAt(System.currentTimeMillis());
        snapshot.setPriceFeatures(priceFeatures);
        snapshot.setDerivativeFeatures(derivativeFeatures);
        snapshot.setEventFeatures(eventFeatures);
        snapshot.setCompositeFactors(compositeFactors);
        snapshot.setQuality(quality);
        return snapshot;
    }
}
