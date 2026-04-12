package com.mobai.alert.strategy.priceaction.policy;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.model.CompositeFactors;
import com.mobai.alert.feature.model.FeatureQuality;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.strategy.model.MarketState;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeFactorSignalPolicyTests {

    @Test
    void shouldApproveSupportiveBullishBreakout() {
        CompositeFactorSignalPolicy policy = createPolicy();
        AlertSignal signal = breakoutLongSignal();
        FeatureSnapshot snapshot = snapshot(
                new BigDecimal("0.70"),
                new BigDecimal("0.65"),
                new BigDecimal("0.35"),
                new BigDecimal("0.25"),
                new BigDecimal("0.20"),
                true,
                1,
                MarketState.BREAKOUT
        );

        SignalPolicyDecision decision = policy.evaluate(signal, snapshot);

        assertTrue(decision.allowed());
        assertNotNull(decision.signal());
        assertNotNull(decision.score());
        assertTrue(decision.score().compareTo(new BigDecimal("0.55")) >= 0);
        assertTrue(decision.signal().getContextComment().contains("综合环境分"));
        assertTrue(decision.signal().getContextComment().contains("市场状态=突破"));
        assertTrue(decision.signal().getContextComment().contains("趋势偏置=0.70"));
        assertTrue(decision.signal().getContextComment().contains("突破确认=0.65"));
        assertEquals(decision.score(), decision.signal().getContextScore());
    }

    @Test
    void shouldBlockBreakoutAgainstTrendAndConfirmation() {
        CompositeFactorSignalPolicy policy = createPolicy();
        AlertSignal signal = breakoutLongSignal();
        FeatureSnapshot snapshot = snapshot(
                new BigDecimal("-0.50"),
                new BigDecimal("-0.30"),
                new BigDecimal("0.10"),
                new BigDecimal("-0.20"),
                new BigDecimal("0.20"),
                true,
                0,
                MarketState.BREAKOUT
        );

        SignalPolicyDecision decision = policy.evaluate(signal, snapshot);

        assertFalse(decision.allowed());
        assertNotNull(decision.signal());
        assertTrue(decision.reasons().contains("拦截：突破确认与信号方向冲突"));
        assertTrue(decision.reasons().contains("拦截：趋势偏置与信号方向冲突"));
        assertTrue(decision.reasons().contains("拦截：环境分低于阈值"));
        assertTrue(decision.signal().getContextComment().contains("拦截"));
    }

    @Test
    void shouldPenalizeMissingDerivativeSnapshot() {
        CompositeFactorSignalPolicy policy = createPolicy();
        AlertSignal signal = breakoutLongSignal();
        FeatureSnapshot derivativeReadySnapshot = snapshot(
                new BigDecimal("0.20"),
                new BigDecimal("0.10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                0,
                MarketState.BREAKOUT
        );
        FeatureSnapshot derivativeMissingSnapshot = snapshot(
                new BigDecimal("0.20"),
                new BigDecimal("0.10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                0,
                MarketState.BREAKOUT
        );

        SignalPolicyDecision readyDecision = policy.evaluate(signal, derivativeReadySnapshot);
        SignalPolicyDecision missingDecision = policy.evaluate(signal, derivativeMissingSnapshot);

        assertTrue(readyDecision.allowed());
        assertFalse(missingDecision.allowed());
        assertTrue(missingDecision.reasons().contains("衍生品快照缺失"));
        assertTrue(missingDecision.reasons().contains("拦截：环境分低于阈值"));
        assertTrue(readyDecision.score().compareTo(missingDecision.score()) > 0);
    }

    @Test
    void shouldBlockRangeFailureOutsideRangeState() {
        CompositeFactorSignalPolicy policy = createPolicy();
        AlertSignal signal = rangeFailureLongSignal();
        FeatureSnapshot snapshot = snapshot(
                new BigDecimal("0.45"),
                new BigDecimal("0.20"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.10"),
                true,
                0,
                MarketState.TREND
        );

        SignalPolicyDecision decision = policy.evaluate(signal, snapshot);

        assertFalse(decision.allowed());
        assertTrue(decision.reasons().contains("市场状态=趋势"));
        assertTrue(decision.reasons().contains("拦截：当前市场状态=趋势，不接受区间失败反转"));
        assertTrue(decision.signal().getContextComment().contains("市场状态=趋势"));
    }

    @Test
    void shouldAllowAlignedExecutionSignalWithHigherTimeframeTrend() {
        CompositeFactorSignalPolicy policy = createExecutionPolicy();
        AlertSignal signal = pullbackLongSignal();
        FeatureSnapshot snapshot = snapshot(
                new BigDecimal("0.35"),
                new BigDecimal("0.28"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.10"),
                true,
                0,
                MarketState.PULLBACK
        );
        snapshot.setContextSnapshot(contextSnapshot("4h", MarketState.TREND, new BigDecimal("0.70"), new BigDecimal("0.20")));

        SignalPolicyDecision decision = policy.evaluate(signal, snapshot);

        assertTrue(decision.allowed());
        assertTrue(decision.reasons().stream().anyMatch(reason -> reason.startsWith("HigherTF(4h)=")));
        assertTrue(decision.reasons().contains("HigherTF bias=LONG"));
    }

    @Test
    void shouldBlockCounterTrendExecutionSignalAgainstHigherTimeframeBias() {
        CompositeFactorSignalPolicy policy = createExecutionPolicy();
        AlertSignal signal = breakoutShortSignal();
        FeatureSnapshot snapshot = snapshot(
                new BigDecimal("-0.20"),
                new BigDecimal("0.10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.10"),
                true,
                0,
                MarketState.BREAKOUT
        );
        snapshot.setContextSnapshot(contextSnapshot("4h", MarketState.TREND, new BigDecimal("0.75"), new BigDecimal("0.30")));

        SignalPolicyDecision decision = policy.evaluate(signal, snapshot);

        assertFalse(decision.allowed());
        assertTrue(decision.reasons().stream().anyMatch(reason -> reason.startsWith("HigherTF(4h)=")));
        assertTrue(decision.reasons().contains("HigherTF bias=LONG"));
        assertTrue(decision.reasons().stream().anyMatch(reason -> reason.contains("bias LONG rejects SHORT")));
    }

    @Test
    void shouldRequireHigherTimeframeContextForExecutionProfile() {
        CompositeFactorSignalPolicy policy = createExecutionPolicy();
        ReflectionTestUtils.setField(policy, "executionContextGracePeriodMs", 1L);
        ReflectionTestUtils.setField(policy, "policyStartedAt", System.currentTimeMillis() - 10_000L);
        AlertSignal signal = breakoutLongSignal();
        FeatureSnapshot snapshot = snapshot(
                new BigDecimal("0.40"),
                new BigDecimal("0.35"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.10"),
                true,
                0,
                MarketState.BREAKOUT
        );

        SignalPolicyDecision decision = policy.evaluate(signal, snapshot);

        assertFalse(decision.allowed());
        assertTrue(decision.reasons().contains("Block: missing higher-timeframe context"));
    }

    @Test
    void shouldAllowMissingHigherTimeframeContextDuringStartupGracePeriod() {
        CompositeFactorSignalPolicy policy = createExecutionPolicy();
        ReflectionTestUtils.setField(policy, "executionContextGracePeriodMs", 60_000L);

        AlertSignal signal = breakoutLongSignal();
        FeatureSnapshot snapshot = snapshot(
                new BigDecimal("0.40"),
                new BigDecimal("0.35"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.10"),
                true,
                0,
                MarketState.BREAKOUT
        );

        SignalPolicyDecision decision = policy.evaluate(signal, snapshot);

        assertTrue(decision.allowed());
        assertTrue(decision.reasons().contains("降级：启动宽限期内允许缺少高周期上下文"));
    }

    private CompositeFactorSignalPolicy createPolicy() {
        CompositeFactorSignalPolicy policy = new CompositeFactorSignalPolicy();
        ReflectionTestUtils.setField(policy, "enabled", true);
        ReflectionTestUtils.setField(policy, "baseScore", new BigDecimal("0.50"));
        ReflectionTestUtils.setField(policy, "rangeFailureMinScore", new BigDecimal("0.50"));
        ReflectionTestUtils.setField(policy, "breakoutMinScore", new BigDecimal("0.55"));
        ReflectionTestUtils.setField(policy, "pullbackMinScore", new BigDecimal("0.53"));
        ReflectionTestUtils.setField(policy, "maxRegimeRisk", new BigDecimal("0.88"));
        ReflectionTestUtils.setField(policy, "missingDerivativePenalty", new BigDecimal("0.03"));
        ReflectionTestUtils.setField(policy, "crowdingExtreme", new BigDecimal("0.80"));
        ReflectionTestUtils.setField(policy, "negativeBreakoutVeto", new BigDecimal("-0.20"));
        ReflectionTestUtils.setField(policy, "negativeTrendVeto", new BigDecimal("-0.35"));
        ReflectionTestUtils.setField(policy, "negativeEventVeto", new BigDecimal("-0.60"));
        ReflectionTestUtils.setField(policy, "eventRiskGate", new BigDecimal("0.45"));
        ReflectionTestUtils.setField(policy, "crowdedBreakoutRiskGate", new BigDecimal("0.70"));
        ReflectionTestUtils.setField(policy, "rangeFailureTrendWeight", new BigDecimal("0.18"));
        ReflectionTestUtils.setField(policy, "rangeFailureBreakoutWeight", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(policy, "rangeFailureEventWeight", new BigDecimal("0.12"));
        ReflectionTestUtils.setField(policy, "rangeFailureCrowdingWeight", new BigDecimal("0.12"));
        ReflectionTestUtils.setField(policy, "rangeFailureRegimeRiskWeight", new BigDecimal("0.18"));
        ReflectionTestUtils.setField(policy, "breakoutTrendWeight", new BigDecimal("0.18"));
        ReflectionTestUtils.setField(policy, "breakoutBreakoutWeight", new BigDecimal("0.26"));
        ReflectionTestUtils.setField(policy, "breakoutEventWeight", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(policy, "breakoutCrowdingWeight", new BigDecimal("0.08"));
        ReflectionTestUtils.setField(policy, "breakoutRegimeRiskWeight", new BigDecimal("0.22"));
        ReflectionTestUtils.setField(policy, "pullbackTrendWeight", new BigDecimal("0.24"));
        ReflectionTestUtils.setField(policy, "pullbackBreakoutWeight", new BigDecimal("0.18"));
        ReflectionTestUtils.setField(policy, "pullbackEventWeight", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(policy, "pullbackCrowdingWeight", new BigDecimal("0.06"));
        ReflectionTestUtils.setField(policy, "pullbackRegimeRiskWeight", new BigDecimal("0.18"));
        return policy;
    }

    private CompositeFactorSignalPolicy createExecutionPolicy() {
        CompositeFactorSignalPolicy policy = createPolicy();
        ReflectionTestUtils.setField(policy, "multiTimeframeConflictPolicy", "context-first");
        ReflectionTestUtils.setField(policy, "allowCountertrendEntry", false);
        ReflectionTestUtils.setField(policy, "requireExecutionContext", true);
        return policy;
    }

    private AlertSignal breakoutLongSignal() {
        BinanceKlineDTO kline = baseKline();
        return new AlertSignal(
                TradeDirection.LONG,
                "BTC 确认突破做多信号",
                kline,
                "CONFIRMED_BREAKOUT_LONG",
                "价格向上有效突破区间，属于确认型突破。",
                new BigDecimal("109.00"),
                new BigDecimal("104.00"),
                new BigDecimal("118.00"),
                new BigDecimal("1.80")
        );
    }

    private AlertSignal rangeFailureLongSignal() {
        BinanceKlineDTO kline = baseKline();
        return new AlertSignal(
                TradeDirection.LONG,
                "BTC 区间假跌破做多信号",
                kline,
                "RANGE_FAILURE_LONG",
                "价格跌破后重新收回区间，属于区间失败反转。",
                new BigDecimal("101.00"),
                new BigDecimal("98.00"),
                new BigDecimal("106.00"),
                new BigDecimal("1.10")
        );
    }

    private AlertSignal pullbackLongSignal() {
        BinanceKlineDTO kline = baseKline();
        return new AlertSignal(
                TradeDirection.LONG,
                "BTC breakout pullback long",
                kline,
                "BREAKOUT_PULLBACK_LONG",
                "pullback retest in trend",
                new BigDecimal("107.00"),
                new BigDecimal("104.00"),
                new BigDecimal("114.00"),
                new BigDecimal("1.20")
        );
    }

    private AlertSignal breakoutShortSignal() {
        BinanceKlineDTO kline = baseKline();
        return new AlertSignal(
                TradeDirection.SHORT,
                "BTC confirmed breakout short",
                kline,
                "CONFIRMED_BREAKOUT_SHORT",
                "breakdown short",
                new BigDecimal("99.00"),
                new BigDecimal("103.00"),
                new BigDecimal("92.00"),
                new BigDecimal("1.30")
        );
    }

    private BinanceKlineDTO baseKline() {
        BinanceKlineDTO kline = new BinanceKlineDTO();
        kline.setSymbol("BTCUSDT");
        kline.setInterval("4h");
        kline.setOpen("100.00");
        kline.setHigh("110.00");
        kline.setLow("99.00");
        kline.setClose("108.00");
        kline.setVolume("100000");
        kline.setEndTime(System.currentTimeMillis());
        return kline;
    }

    private FeatureSnapshot snapshot(BigDecimal trendBias,
                                     BigDecimal breakoutConfirmation,
                                     BigDecimal crowding,
                                     BigDecimal eventBias,
                                     BigDecimal regimeRisk,
                                     boolean derivativeReady,
                                     int relevantEventCount,
                                     MarketState marketState) {
        CompositeFactors factors = new CompositeFactors();
        factors.setTrendBiasScore(trendBias);
        factors.setBreakoutConfirmationScore(breakoutConfirmation);
        factors.setCrowdingScore(crowding);
        factors.setEventBiasScore(eventBias);
        factors.setRegimeRiskScore(regimeRisk);

        FeatureQuality quality = new FeatureQuality();
        quality.setPriceReady(true);
        quality.setDerivativeReady(derivativeReady);
        quality.setEventReady(true);
        quality.setRelevantEventCount(relevantEventCount);
        quality.setCompleteSnapshot(true);

        FeatureSnapshot snapshot = new FeatureSnapshot();
        snapshot.setSymbol("BTCUSDT");
        snapshot.setInterval("4h");
        snapshot.setAsOfTime(System.currentTimeMillis());
        snapshot.setGeneratedAt(System.currentTimeMillis());
        snapshot.setCompositeFactors(factors);
        snapshot.setQuality(quality);
        snapshot.setMarketState(marketState);
        snapshot.setMarketStateComment("测试状态");
        return snapshot;
    }
    private FeatureSnapshot contextSnapshot(String interval,
                                            MarketState marketState,
                                            BigDecimal trendBias,
                                            BigDecimal breakoutConfirmation) {
        FeatureSnapshot snapshot = snapshot(
                trendBias,
                breakoutConfirmation,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.10"),
                true,
                0,
                marketState
        );
        snapshot.setInterval(interval);
        return snapshot;
    }
}
