package com.mobai.alert.feature.model;

import com.mobai.alert.state.runtime.MarketState;

/**
 * 统一特征快照对象。
 * 聚合价格、衍生品、事件、复合因子以及质量信息。
 */
public class FeatureSnapshot {
    /**
     * 交易对代码。
     */
    private String symbol;
    /**
     * 周期。
     */
    private String interval;
    /**
     * 特征对应的行情时间。
     */
    private Long asOfTime;
    /**
     * 快照生成时间。
     */
    private Long generatedAt;
    /**
     * 价格特征。
     */
    private PriceFeatures priceFeatures;
    /**
     * 衍生品特征。
     */
    private DerivativeFeatures derivativeFeatures;
    /**
     * 事件特征。
     */
    private EventFeatures eventFeatures;
    /**
     * 复合因子。
     */
    private CompositeFactors compositeFactors;
    /**
     * 快照质量信息。
     */
    private FeatureQuality quality;
    private FeatureSnapshot contextSnapshot;
    private MarketState marketState;
    private String marketStateComment;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public Long getAsOfTime() {
        return asOfTime;
    }

    public void setAsOfTime(Long asOfTime) {
        this.asOfTime = asOfTime;
    }

    public Long getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Long generatedAt) {
        this.generatedAt = generatedAt;
    }

    public PriceFeatures getPriceFeatures() {
        return priceFeatures;
    }

    public void setPriceFeatures(PriceFeatures priceFeatures) {
        this.priceFeatures = priceFeatures;
    }

    public DerivativeFeatures getDerivativeFeatures() {
        return derivativeFeatures;
    }

    public void setDerivativeFeatures(DerivativeFeatures derivativeFeatures) {
        this.derivativeFeatures = derivativeFeatures;
    }

    public EventFeatures getEventFeatures() {
        return eventFeatures;
    }

    public void setEventFeatures(EventFeatures eventFeatures) {
        this.eventFeatures = eventFeatures;
    }

    public CompositeFactors getCompositeFactors() {
        return compositeFactors;
    }

    public void setCompositeFactors(CompositeFactors compositeFactors) {
        this.compositeFactors = compositeFactors;
    }

    public FeatureQuality getQuality() {
        return quality;
    }

    public void setQuality(FeatureQuality quality) {
        this.quality = quality;
    }

    public FeatureSnapshot getContextSnapshot() {
        return contextSnapshot;
    }

    public void setContextSnapshot(FeatureSnapshot contextSnapshot) {
        this.contextSnapshot = contextSnapshot;
    }

    public MarketState getMarketState() {
        return marketState;
    }

    public void setMarketState(MarketState marketState) {
        this.marketState = marketState;
    }

    public String getMarketStateComment() {
        return marketStateComment;
    }

    public void setMarketStateComment(String marketStateComment) {
        this.marketStateComment = marketStateComment;
    }
}
