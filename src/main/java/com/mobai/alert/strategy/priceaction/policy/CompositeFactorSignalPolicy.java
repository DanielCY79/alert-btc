package com.mobai.alert.strategy.priceaction.policy;

import com.mobai.alert.feature.model.CompositeFactors;
import com.mobai.alert.feature.model.FeatureQuality;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.strategy.model.MarketState;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import com.mobai.alert.strategy.priceaction.shared.MultiTimeframeDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 复合因子信号过滤器。
 * 先做显式市场状态准入，再根据多维特征分数对原始信号加权、否决和补充上下文。
 */
@Service
public class CompositeFactorSignalPolicy {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Value("${monitoring.feature.policy.enabled:true}")
    private boolean enabled;

    @Value("${monitoring.feature.policy.base-score:0.50}")
    private BigDecimal baseScore;

    @Value("${monitoring.feature.policy.range-failure.min-score:0.50}")
    private BigDecimal rangeFailureMinScore;

    @Value("${monitoring.feature.policy.breakout.min-score:0.55}")
    private BigDecimal breakoutMinScore;

    @Value("${monitoring.feature.policy.pullback.min-score:0.53}")
    private BigDecimal pullbackMinScore;

    @Value("${monitoring.feature.policy.max-regime-risk:0.88}")
    private BigDecimal maxRegimeRisk;

    @Value("${monitoring.feature.policy.missing-derivative-penalty:0.03}")
    private BigDecimal missingDerivativePenalty;

    @Value("${monitoring.feature.policy.veto.crowding-extreme:0.80}")
    private BigDecimal crowdingExtreme;

    @Value("${monitoring.feature.policy.veto.breakout-confirmation:-0.20}")
    private BigDecimal negativeBreakoutVeto;

    @Value("${monitoring.feature.policy.veto.trend-bias:-0.35}")
    private BigDecimal negativeTrendVeto;

    @Value("${monitoring.feature.policy.veto.event-bias:-0.60}")
    private BigDecimal negativeEventVeto;

    @Value("${monitoring.feature.policy.veto.event-risk-gate:0.45}")
    private BigDecimal eventRiskGate;

    @Value("${monitoring.feature.policy.veto.crowded-breakout-risk-gate:0.70}")
    private BigDecimal crowdedBreakoutRiskGate;

    @Value("${monitoring.feature.policy.weights.range-failure.trend:0.18}")
    private BigDecimal rangeFailureTrendWeight;

    @Value("${monitoring.feature.policy.weights.range-failure.breakout:0.10}")
    private BigDecimal rangeFailureBreakoutWeight;

    @Value("${monitoring.feature.policy.weights.range-failure.event:0.12}")
    private BigDecimal rangeFailureEventWeight;

    @Value("${monitoring.feature.policy.weights.range-failure.crowding:0.12}")
    private BigDecimal rangeFailureCrowdingWeight;

    @Value("${monitoring.feature.policy.weights.range-failure.regime-risk:0.18}")
    private BigDecimal rangeFailureRegimeRiskWeight;

    @Value("${monitoring.feature.policy.weights.breakout.trend:0.18}")
    private BigDecimal breakoutTrendWeight;

    @Value("${monitoring.feature.policy.weights.breakout.breakout:0.26}")
    private BigDecimal breakoutBreakoutWeight;

    @Value("${monitoring.feature.policy.weights.breakout.event:0.10}")
    private BigDecimal breakoutEventWeight;

    @Value("${monitoring.feature.policy.weights.breakout.crowding:0.08}")
    private BigDecimal breakoutCrowdingWeight;

    @Value("${monitoring.feature.policy.weights.breakout.regime-risk:0.22}")
    private BigDecimal breakoutRegimeRiskWeight;

    @Value("${monitoring.feature.policy.weights.pullback.trend:0.24}")
    private BigDecimal pullbackTrendWeight;

    @Value("${monitoring.feature.policy.weights.pullback.breakout:0.18}")
    private BigDecimal pullbackBreakoutWeight;

    @Value("${monitoring.feature.policy.weights.pullback.event:0.10}")
    private BigDecimal pullbackEventWeight;

    @Value("${monitoring.feature.policy.weights.pullback.crowding:0.06}")
    private BigDecimal pullbackCrowdingWeight;

    @Value("${monitoring.feature.policy.weights.pullback.regime-risk:0.18}")
    private BigDecimal pullbackRegimeRiskWeight;

    private String multiTimeframeConflictPolicy = MultiTimeframeDefaults.CONFLICT_POLICY;

    private boolean allowCountertrendEntry = MultiTimeframeDefaults.ALLOW_COUNTERTREND_ENTRY;

    private boolean requireExecutionContext = MultiTimeframeDefaults.REQUIRE_EXECUTION_CONTEXT;

    private long executionContextGracePeriodMs = MultiTimeframeDefaults.EXECUTION_CONTEXT_GRACE_PERIOD_MS;

    private long policyStartedAt = System.currentTimeMillis();

    public CompositeFactorPolicyProfile currentProfile() {
        return new CompositeFactorPolicyProfile(
                enabled,
                baseScore,
                rangeFailureMinScore,
                breakoutMinScore,
                pullbackMinScore,
                maxRegimeRisk,
                missingDerivativePenalty,
                crowdingExtreme,
                negativeBreakoutVeto,
                negativeTrendVeto,
                negativeEventVeto,
                eventRiskGate,
                crowdedBreakoutRiskGate,
                new PolicyWeights(
                        rangeFailureTrendWeight,
                        rangeFailureBreakoutWeight,
                        rangeFailureEventWeight,
                        rangeFailureCrowdingWeight,
                        rangeFailureRegimeRiskWeight
                ),
                new PolicyWeights(
                        breakoutTrendWeight,
                        breakoutBreakoutWeight,
                        breakoutEventWeight,
                        breakoutCrowdingWeight,
                        breakoutRegimeRiskWeight
                ),
                new PolicyWeights(
                        pullbackTrendWeight,
                        pullbackBreakoutWeight,
                        pullbackEventWeight,
                        pullbackCrowdingWeight,
                        pullbackRegimeRiskWeight
                )
        );
    }

    public SignalPolicyDecision evaluate(AlertSignal signal, FeatureSnapshot snapshot) {
        return evaluate(signal, snapshot, currentProfile());
    }

    public SignalPolicyDecision evaluate(AlertSignal signal,
                                         FeatureSnapshot snapshot,
                                         CompositeFactorPolicyProfile profile) {
        if (signal == null) {
            return new SignalPolicyDecision(false, null, null, List.of("空信号"));
        }

        List<String> reasons = new ArrayList<>();
        if (snapshot != null && snapshot.getMarketState() != null) {
            MarketState marketState = snapshot.getMarketState();
            reasons.add("市场状态=" + marketState.label());
            if (marketState != MarketState.UNKNOWN && !isCompatibleWithMarketState(signal, marketState)) {
                reasons.add(stateMismatchReason(signal, marketState));
                String contextComment = buildContextComment(null, reasons);
                return new SignalPolicyDecision(false, signal.withContext(null, contextComment), null, List.copyOf(reasons));
            }
        }

        ContextAlignmentDecision contextDecision = evaluateHigherTimeframeContext(signal, snapshot);
        reasons.addAll(contextDecision.reasons());
        if (!contextDecision.allowed()) {
            String contextComment = buildContextComment(null, reasons);
            return new SignalPolicyDecision(false, signal.withContext(null, contextComment), null, List.copyOf(reasons));
        }

        if (profile == null || !profile.enabled() || snapshot == null || snapshot.getCompositeFactors() == null) {
            AlertSignal enrichedSignal = reasons.isEmpty()
                    ? signal
                    : signal.withContext(null, buildContextComment(null, reasons));
            return new SignalPolicyDecision(true, enrichedSignal, null, List.copyOf(reasons));
        }

        CompositeFactors factors = snapshot.getCompositeFactors();
        FeatureQuality quality = snapshot.getQuality();
        BigDecimal score = profile.baseScore();
        BigDecimal directedTrend = directed(signal, factors.getTrendBiasScore());
        BigDecimal directedBreakout = directed(signal, factors.getBreakoutConfirmationScore());
        BigDecimal directedEvent = directed(signal, factors.getEventBiasScore());
        BigDecimal directedCrowding = directed(signal, factors.getCrowdingScore());
        BigDecimal regimeRisk = safePositive(factors.getRegimeRiskScore());

        PolicyWeights weights = weightsFor(signal, profile);
        if (isRangeFailure(signal)) {
            score = score
                    .add(weight(directedTrend, weights.trendWeight()))
                    .add(weight(directedBreakout, weights.breakoutWeight()))
                    .add(weight(directedEvent, weights.eventWeight()))
                    .add(weight(opposite(directedCrowding), weights.crowdingWeight()))
                    .subtract(weight(regimeRisk, weights.regimeRiskWeight()));
        } else {
            score = score
                    .add(weight(directedTrend, weights.trendWeight()))
                    .add(weight(directedBreakout, weights.breakoutWeight()))
                    .add(weight(directedEvent, weights.eventWeight()))
                    .add(weight(directedCrowding, weights.crowdingWeight()))
                    .subtract(weight(regimeRisk, weights.regimeRiskWeight()));
        }

        if (quality != null && !quality.isDerivativeReady() && profile.missingDerivativePenalty() != null) {
            score = score.subtract(profile.missingDerivativePenalty());
            if (profile.missingDerivativePenalty().compareTo(ZERO) > 0) {
                reasons.add("衍生品快照缺失");
            }
        }

        if (quality != null && quality.getRelevantEventCount() != null && quality.getRelevantEventCount() > 0 && directedEvent != null) {
            reasons.add("事件偏置=" + scale(directedEvent));
        }
        if (directedTrend != null) {
            reasons.add("趋势偏置=" + scale(directedTrend));
        }
        if (directedBreakout != null) {
            reasons.add("突破确认=" + scale(directedBreakout));
        }
        if (regimeRisk != null && regimeRisk.compareTo(ZERO) > 0) {
            reasons.add("环境风险=" + scale(regimeRisk));
        }

        boolean blocked = false;
        if (regimeRisk != null && regimeRisk.compareTo(profile.maxRegimeRisk()) > 0) {
            blocked = true;
            reasons.add("拦截：环境风险过高");
        }
        if (!isRangeFailure(signal) && directedBreakout != null && directedBreakout.compareTo(profile.negativeBreakoutVeto()) < 0) {
            blocked = true;
            reasons.add("拦截：突破确认与信号方向冲突");
        }
        if (directedTrend != null && directedTrend.compareTo(profile.negativeTrendVeto()) < 0) {
            blocked = true;
            reasons.add("拦截：趋势偏置与信号方向冲突");
        }
        if (directedEvent != null && directedEvent.compareTo(profile.negativeEventVeto()) < 0
                && regimeRisk != null && regimeRisk.compareTo(profile.eventRiskGate()) > 0) {
            blocked = true;
            reasons.add("拦截：利空事件压力叠加高风险");
        }
        if (isBreakout(signal) && directedCrowding != null && directedCrowding.compareTo(profile.crowdingExtreme()) > 0
                && regimeRisk != null && regimeRisk.compareTo(profile.crowdedBreakoutRiskGate()) > 0) {
            blocked = true;
            reasons.add("拦截：拥挤突破叠加不稳定环境");
        }

        BigDecimal normalizedScore = clamp01(score);
        if (normalizedScore.compareTo(minScore(signal, profile)) < 0) {
            blocked = true;
            reasons.add("拦截：环境分低于阈值");
        }

        String contextComment = buildContextComment(normalizedScore, reasons);
        AlertSignal enrichedSignal = signal.withContext(normalizedScore, contextComment);
        return new SignalPolicyDecision(!blocked, enrichedSignal, normalizedScore, List.copyOf(reasons));
    }

    private PolicyWeights weightsFor(AlertSignal signal, CompositeFactorPolicyProfile profile) {
        if (isRangeFailure(signal)) {
            return profile.rangeFailureWeights();
        }
        if (isPullback(signal)) {
            return profile.pullbackWeights();
        }
        return profile.breakoutWeights();
    }

    private BigDecimal minScore(AlertSignal signal, CompositeFactorPolicyProfile profile) {
        if (isRangeFailure(signal)) {
            return profile.rangeFailureMinScore();
        }
        if (isPullback(signal)) {
            return profile.pullbackMinScore();
        }
        return profile.breakoutMinScore();
    }

    private boolean isRangeFailure(AlertSignal signal) {
        return signal.getType() != null && signal.getType().startsWith("RANGE_FAILURE");
    }

    private boolean isProbe(AlertSignal signal) {
        return signal.getType() != null && signal.getType().startsWith("PROBE_TREND");
    }

    private boolean isProfit(AlertSignal signal) {
        return signal.getType() != null && signal.getType().startsWith("PROFIT_TREND");
    }

    private boolean isBreakout(AlertSignal signal) {
        return signal.getType() != null
                && (signal.getType().startsWith("CONFIRMED_BREAKOUT") || isProbe(signal));
    }

    private boolean isPullback(AlertSignal signal) {
        return signal.getType() != null
                && (signal.getType().startsWith("BREAKOUT_PULLBACK")
                || signal.getType().startsWith("SECOND_ENTRY")
                || isProfit(signal));
    }

    private boolean isCompatibleWithMarketState(AlertSignal signal, MarketState marketState) {
        return switch (marketState) {
            case UNKNOWN -> true;
            case RANGE -> isRangeFailure(signal) || isProbe(signal);
            case BREAKOUT -> isProbe(signal) || isProfit(signal);
            case PULLBACK -> isProfit(signal);
            case TREND -> isProfit(signal);
        };
    }

    private String stateMismatchReason(AlertSignal signal, MarketState marketState) {
        return "拦截：当前市场状态=" + marketState.label() + "，不接受" + signalFamilyLabel(signal);
    }

    private String signalFamilyLabel(AlertSignal signal) {
        if (isRangeFailure(signal)) {
            return "区间失败反转";
        }
        if (isPullback(signal)) {
            if (signal.getType() != null && signal.getType().startsWith("SECOND_ENTRY")) {
                return "二次入场";
            }
            if (isProfit(signal)) {
                return "利润层续势";
            }
            return "突破回踩";
        }
        if (isBreakout(signal)) {
            if (isProbe(signal)) {
                return "试错层扩张";
            }
            return "确认突破";
        }
        return "该信号";
    }

    private ContextAlignmentDecision evaluateHigherTimeframeContext(AlertSignal signal, FeatureSnapshot snapshot) {
        if (!isExecutionRole() || !isContextFirstPolicy()) {
            return ContextAlignmentDecision.allowed(List.of());
        }

        FeatureSnapshot contextSnapshot = snapshot == null ? null : snapshot.getContextSnapshot();
        if (contextSnapshot == null) {
            if (requireExecutionContext) {
                if (isWithinExecutionContextGracePeriod()) {
                    return ContextAlignmentDecision.allowed(List.of("降级：启动宽限期内允许缺少高周期上下文"));
                }
                return ContextAlignmentDecision.blocked(List.of("Block: missing higher-timeframe context"));
            }
            return ContextAlignmentDecision.allowed(List.of());
        }

        List<String> reasons = new ArrayList<>();
        String interval = contextSnapshot.getInterval() == null ? "HTF" : contextSnapshot.getInterval();
        MarketState contextState = contextSnapshot.getMarketState() == null ? MarketState.UNKNOWN : contextSnapshot.getMarketState();
        reasons.add("HigherTF(" + interval + ")=" + contextState.label());

        TradeDirection contextDirection = inferContextDirection(contextSnapshot);
        if (contextDirection != null) {
            reasons.add("HigherTF bias=" + contextDirection.name());
        }

        if (allowCountertrendEntry) {
            return ContextAlignmentDecision.allowed(List.copyOf(reasons));
        }
        if (!isCompatibleWithHigherTimeframe(signal, contextState, contextDirection)) {
            reasons.add(higherTimeframeMismatchReason(signal, interval, contextState, contextDirection));
            return ContextAlignmentDecision.blocked(List.copyOf(reasons));
        }
        return ContextAlignmentDecision.allowed(List.copyOf(reasons));
    }

    private boolean isExecutionRole() {
        return true;
    }

    private boolean isWithinExecutionContextGracePeriod() {
        if (!requireExecutionContext || executionContextGracePeriodMs <= 0) {
            return false;
        }
        return System.currentTimeMillis() - policyStartedAt <= executionContextGracePeriodMs;
    }

    private boolean isContextFirstPolicy() {
        return multiTimeframeConflictPolicy == null
                || multiTimeframeConflictPolicy.isBlank()
                || "context-first".equalsIgnoreCase(multiTimeframeConflictPolicy);
    }

    private boolean isCompatibleWithHigherTimeframe(AlertSignal signal,
                                                    MarketState contextState,
                                                    TradeDirection contextDirection) {
        if (contextState == null || contextState == MarketState.UNKNOWN) {
            return true;
        }
        if (contextState == MarketState.RANGE) {
            return isRangeFailure(signal) || isProbe(signal);
        }
        if (isRangeFailure(signal)) {
            return false;
        }
        if (contextDirection == null || signal == null || signal.getDirection() == null) {
            return true;
        }
        return signal.getDirection() == contextDirection;
    }

    private TradeDirection inferContextDirection(FeatureSnapshot contextSnapshot) {
        if (contextSnapshot == null || contextSnapshot.getCompositeFactors() == null) {
            return null;
        }
        CompositeFactors factors = contextSnapshot.getCompositeFactors();
        BigDecimal dominantBias = dominantBias(factors.getTrendBiasScore(), factors.getBreakoutConfirmationScore());
        if (dominantBias == null || dominantBias.compareTo(ZERO) == 0) {
            return null;
        }
        return dominantBias.compareTo(ZERO) > 0 ? TradeDirection.LONG : TradeDirection.SHORT;
    }

    private BigDecimal dominantBias(BigDecimal primary, BigDecimal secondary) {
        BigDecimal normalizedPrimary = primary == null ? ZERO : primary;
        BigDecimal normalizedSecondary = secondary == null ? ZERO : secondary;
        return normalizedPrimary.abs().compareTo(normalizedSecondary.abs()) >= 0
                ? normalizedPrimary
                : normalizedSecondary;
    }

    private String higherTimeframeMismatchReason(AlertSignal signal,
                                                 String interval,
                                                 MarketState contextState,
                                                 TradeDirection contextDirection) {
        if (contextState == MarketState.RANGE) {
            return "Block: " + interval + " range context only accepts range-failure entries";
        }
        if (contextDirection == null) {
            return "Block: " + interval + " context rejects " + signalFamilyLabel(signal);
        }
        return "Block: " + interval + " bias " + contextDirection.name()
                + " rejects " + signal.getDirection() + " " + signalFamilyLabel(signal);
    }

    private BigDecimal directed(AlertSignal signal, BigDecimal factor) {
        if (factor == null) {
            return null;
        }
        return signal.getDirection() == TradeDirection.LONG ? factor : factor.negate();
    }

    private BigDecimal opposite(BigDecimal value) {
        return value == null ? null : value.negate();
    }

    private BigDecimal weight(BigDecimal value, BigDecimal weight) {
        if (value == null || weight == null) {
            return ZERO;
        }
        return value.multiply(weight);
    }

    private BigDecimal safePositive(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        if (value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        return value;
    }

    private BigDecimal clamp01(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (value.compareTo(ONE) > 0) {
            return ONE;
        }
        return value;
    }

    private String buildContextComment(BigDecimal score, List<String> reasons) {
        StringBuilder builder = new StringBuilder();
        builder.append("综合环境分 ").append(scale(score));
        if (!reasons.isEmpty()) {
            builder.append(" | ").append(String.join("，", reasons));
        }
        return builder.toString();
    }

    private String scale(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record ContextAlignmentDecision(boolean allowed,
                                            List<String> reasons) {
        private static ContextAlignmentDecision allowed(List<String> reasons) {
            return new ContextAlignmentDecision(true, reasons);
        }

        private static ContextAlignmentDecision blocked(List<String> reasons) {
            return new ContextAlignmentDecision(false, reasons);
        }
    }
}
