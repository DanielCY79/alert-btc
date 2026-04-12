package com.mobai.alert.strategy.priceaction.policy;

import com.mobai.alert.feature.model.CompositeFactors;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.model.PriceFeatures;
import com.mobai.alert.strategy.model.MarketState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 显式市场状态机。
 *
 * <p>先把当前结构归类成区间、突破、回踩或趋势，再让信号在匹配语境里通过。</p>
 */
@Service
public class MarketStateMachine {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Value("${monitoring.market-state.range.max-trend-bias:0.35}")
    private BigDecimal rangeMaxTrendBias = new BigDecimal("0.35");

    @Value("${monitoring.market-state.range.max-breakout-confirmation:0.30}")
    private BigDecimal rangeMaxBreakoutConfirmation = new BigDecimal("0.30");

    @Value("${monitoring.market-state.breakout.min-confirmation:0.55}")
    private BigDecimal breakoutMinConfirmation = new BigDecimal("0.55");

    @Value("${monitoring.market-state.breakout.min-strength:0.60}")
    private BigDecimal breakoutMinStrength = new BigDecimal("0.60");

    @Value("${monitoring.market-state.breakout.min-body-ratio:0.45}")
    private BigDecimal breakoutMinBodyRatio = new BigDecimal("0.45");

    @Value("${monitoring.market-state.breakout.continuation-confirmation:0.25}")
    private BigDecimal breakoutContinuationConfirmation = new BigDecimal("0.25");

    @Value("${monitoring.market-state.pullback.min-trend-bias:0.20}")
    private BigDecimal pullbackMinTrendBias = new BigDecimal("0.20");

    @Value("${monitoring.market-state.pullback.max-confirmation:0.45}")
    private BigDecimal pullbackMaxConfirmation = new BigDecimal("0.45");

    @Value("${monitoring.market-state.pullback.max-strength:0.55}")
    private BigDecimal pullbackMaxStrength = new BigDecimal("0.55");

    @Value("${monitoring.market-state.pullback.max-volume-ratio:1.20}")
    private BigDecimal pullbackMaxVolumeRatio = new BigDecimal("1.20");

    @Value("${monitoring.market-state.pullback.edge-zone:0.35}")
    private BigDecimal pullbackEdgeZone = new BigDecimal("0.35");

    @Value("${monitoring.market-state.trend.min-trend-bias:0.40}")
    private BigDecimal trendMinBias = new BigDecimal("0.40");

    @Value("${monitoring.market-state.trend.min-ma-spread:0.010}")
    private BigDecimal trendMinMaSpread = new BigDecimal("0.010");

    public MarketStateDecision evaluate(FeatureSnapshot snapshot, MarketState previousState) {
        MarketState prior = previousState == null ? MarketState.UNKNOWN : previousState;
        if (snapshot == null || snapshot.getPriceFeatures() == null || snapshot.getCompositeFactors() == null) {
            return new MarketStateDecision(prior, "缺少价格或综合因子快照，沿用" + prior.label());
        }

        PriceFeatures price = snapshot.getPriceFeatures();
        CompositeFactors factors = snapshot.getCompositeFactors();
        if (isBreakout(price, factors, prior)) {
            return decide(MarketState.BREAKOUT, price, factors, "突破接受度较强");
        }
        if (isTrend(price, factors)) {
            return decide(MarketState.TREND, price, factors, "趋势延续条件更强");
        }
        if (isPullback(price, factors, prior)) {
            return decide(MarketState.PULLBACK, price, factors, "突破后回踩/趋势中整理");
        }
        if (isRange(price, factors)) {
            return decide(MarketState.RANGE, price, factors, "区间条件成立");
        }

        if (prior != MarketState.UNKNOWN) {
            return decide(prior, price, factors, "结构不够清晰，状态延续");
        }
        return decide(MarketState.UNKNOWN, price, factors, "结构不够清晰");
    }

    private boolean isRange(PriceFeatures price, CompositeFactors factors) {
        if (!Boolean.TRUE.equals(price.getInsideRange()) || price.getRangeWidthPct() == null) {
            return false;
        }
        return abs(factors.getTrendBiasScore()).compareTo(rangeMaxTrendBias) <= 0
                && abs(factors.getBreakoutConfirmationScore()).compareTo(rangeMaxBreakoutConfirmation) <= 0;
    }

    private boolean isBreakout(PriceFeatures price, CompositeFactors factors, MarketState prior) {
        if (Boolean.TRUE.equals(price.getInsideRange())) {
            return false;
        }

        BigDecimal confirmationAbs = abs(factors.getBreakoutConfirmationScore());
        BigDecimal strength = positive(price.getBreakoutStrengthScore());
        BigDecimal bodyRatio = positive(price.getBodyRatio());
        if (confirmationAbs.compareTo(breakoutMinConfirmation) >= 0
                && strength.compareTo(breakoutMinStrength) >= 0
                && bodyRatio.compareTo(breakoutMinBodyRatio) >= 0) {
            return true;
        }

        return prior == MarketState.BREAKOUT
                && confirmationAbs.compareTo(breakoutContinuationConfirmation) >= 0
                && bodyRatio.compareTo(breakoutMinBodyRatio) >= 0;
    }

    private boolean isPullback(PriceFeatures price, CompositeFactors factors, MarketState prior) {
        if (prior != MarketState.BREAKOUT && prior != MarketState.TREND && prior != MarketState.PULLBACK) {
            return false;
        }
        if (abs(factors.getTrendBiasScore()).compareTo(pullbackMinTrendBias) < 0) {
            return false;
        }
        if (abs(factors.getBreakoutConfirmationScore()).compareTo(pullbackMaxConfirmation) > 0) {
            return false;
        }
        if (positive(price.getBreakoutStrengthScore()).compareTo(pullbackMaxStrength) > 0) {
            return false;
        }
        if (positive(price.getVolumeRatio()).compareTo(pullbackMaxVolumeRatio) > 0) {
            return false;
        }
        if (Boolean.TRUE.equals(price.getInsideRange())) {
            return isNearRangeEdge(price.getRangePosition());
        }
        return true;
    }

    private boolean isTrend(PriceFeatures price, CompositeFactors factors) {
        if (Boolean.TRUE.equals(price.getInsideRange())) {
            return false;
        }
        return abs(factors.getTrendBiasScore()).compareTo(trendMinBias) >= 0
                || absValue(price.getMaSpreadPct()).compareTo(trendMinMaSpread) >= 0;
    }

    private boolean isNearRangeEdge(BigDecimal rangePosition) {
        if (rangePosition == null) {
            return false;
        }
        BigDecimal distanceToNearestEdge = rangePosition.min(ONE.subtract(rangePosition));
        return distanceToNearestEdge.compareTo(pullbackEdgeZone) <= 0;
    }

    private MarketStateDecision decide(MarketState state,
                                       PriceFeatures price,
                                       CompositeFactors factors,
                                       String reason) {
        String comment = reason
                + " | insideRange=" + Boolean.TRUE.equals(price.getInsideRange())
                + " trendBias=" + scale(factors.getTrendBiasScore())
                + " breakout=" + scale(factors.getBreakoutConfirmationScore())
                + " rangePos=" + scale(price.getRangePosition())
                + " strength=" + scale(price.getBreakoutStrengthScore())
                + " volume=" + scale(price.getVolumeRatio());
        return new MarketStateDecision(state, comment);
    }

    private BigDecimal abs(BigDecimal value) {
        return value == null ? ZERO : value.abs();
    }

    private BigDecimal absValue(BigDecimal value) {
        return value == null ? ZERO : value.abs();
    }

    private BigDecimal positive(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        return value;
    }

    private String scale(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
