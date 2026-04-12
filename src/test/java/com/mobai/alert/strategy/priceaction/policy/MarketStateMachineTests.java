package com.mobai.alert.strategy.priceaction.policy;

import com.mobai.alert.feature.model.CompositeFactors;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.model.PriceFeatures;
import com.mobai.alert.strategy.model.MarketState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketStateMachineTests {

    private final MarketStateMachine marketStateMachine = new MarketStateMachine();

    @Test
    void shouldClassifyRangeWhenInsideRangeAndBiasesAreFlat() {
        FeatureSnapshot snapshot = snapshot(
                true,
                new BigDecimal("0.12"),
                new BigDecimal("0.18"),
                new BigDecimal("0.52"),
                new BigDecimal("0.32"),
                new BigDecimal("0.90"),
                new BigDecimal("0.20"),
                new BigDecimal("0.008")
        );

        MarketStateDecision decision = marketStateMachine.evaluate(snapshot, MarketState.UNKNOWN);

        assertEquals(MarketState.RANGE, decision.state());
        assertTrue(decision.comment().contains("区间条件成立"));
    }

    @Test
    void shouldTransitionFromRangeToBreakoutOnStrongAcceptance() {
        FeatureSnapshot snapshot = snapshot(
                false,
                new BigDecimal("0.48"),
                new BigDecimal("0.70"),
                null,
                new BigDecimal("0.72"),
                new BigDecimal("1.80"),
                new BigDecimal("0.62"),
                new BigDecimal("0.015")
        );

        MarketStateDecision decision = marketStateMachine.evaluate(snapshot, MarketState.RANGE);

        assertEquals(MarketState.BREAKOUT, decision.state());
        assertTrue(decision.comment().contains("突破接受度较强"));
    }

    @Test
    void shouldClassifyPullbackAfterBreakoutLosesDriveNearRangeEdge() {
        FeatureSnapshot snapshot = snapshot(
                true,
                new BigDecimal("0.28"),
                new BigDecimal("0.18"),
                new BigDecimal("0.72"),
                new BigDecimal("0.42"),
                new BigDecimal("0.95"),
                new BigDecimal("0.38"),
                new BigDecimal("0.012")
        );

        MarketStateDecision decision = marketStateMachine.evaluate(snapshot, MarketState.BREAKOUT);

        assertEquals(MarketState.PULLBACK, decision.state());
        assertTrue(decision.comment().contains("突破后回踩/趋势中整理"));
    }

    @Test
    void shouldClassifyTrendWhenOutsideRangeAndTrendBiasDominates() {
        FeatureSnapshot snapshot = snapshot(
                false,
                new BigDecimal("0.65"),
                new BigDecimal("0.22"),
                null,
                new BigDecimal("0.35"),
                new BigDecimal("1.10"),
                new BigDecimal("0.40"),
                new BigDecimal("0.018")
        );

        MarketStateDecision decision = marketStateMachine.evaluate(snapshot, MarketState.PULLBACK);

        assertEquals(MarketState.TREND, decision.state());
        assertTrue(decision.comment().contains("趋势延续条件更强"));
    }

    private FeatureSnapshot snapshot(boolean insideRange,
                                     BigDecimal trendBias,
                                     BigDecimal breakoutConfirmation,
                                     BigDecimal rangePosition,
                                     BigDecimal breakoutStrength,
                                     BigDecimal volumeRatio,
                                     BigDecimal bodyRatio,
                                     BigDecimal maSpreadPct) {
        PriceFeatures priceFeatures = new PriceFeatures();
        priceFeatures.setInsideRange(insideRange);
        priceFeatures.setRangeWidthPct(new BigDecimal("0.08"));
        priceFeatures.setRangePosition(rangePosition);
        priceFeatures.setBreakoutStrengthScore(breakoutStrength);
        priceFeatures.setVolumeRatio(volumeRatio);
        priceFeatures.setBodyRatio(bodyRatio);
        priceFeatures.setMaSpreadPct(maSpreadPct);

        CompositeFactors compositeFactors = new CompositeFactors();
        compositeFactors.setTrendBiasScore(trendBias);
        compositeFactors.setBreakoutConfirmationScore(breakoutConfirmation);

        FeatureSnapshot snapshot = new FeatureSnapshot();
        snapshot.setPriceFeatures(priceFeatures);
        snapshot.setCompositeFactors(compositeFactors);
        return snapshot;
    }
}
