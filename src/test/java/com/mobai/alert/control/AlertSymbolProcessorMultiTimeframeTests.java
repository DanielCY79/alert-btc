package com.mobai.alert.control;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.model.CompositeFactors;
import com.mobai.alert.feature.model.FeatureQuality;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.service.MarketFeatureSnapshotService;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.state.runtime.MarketState;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.AlertRuleEvaluator;
import com.mobai.alert.strategy.policy.CompositeFactorSignalPolicy;
import com.mobai.alert.strategy.policy.MarketStateDecision;
import com.mobai.alert.strategy.policy.MarketStateMachine;
import com.mobai.alert.strategy.policy.SignalPolicyDecision;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertSymbolProcessorMultiTimeframeTests {

    @Test
    void shouldAttachHigherTimeframeSnapshotBeforePolicyEvaluation() {
        BinanceApi binanceApi = mock(BinanceApi.class);
        AlertRuleEvaluator evaluator = mock(AlertRuleEvaluator.class);
        AlertNotificationService notificationService = mock(AlertNotificationService.class);
        MarketFeatureSnapshotService snapshotService = mock(MarketFeatureSnapshotService.class);
        CompositeFactorSignalPolicy policy = mock(CompositeFactorSignalPolicy.class);
        MarketStateMachine marketStateMachine = mock(MarketStateMachine.class);

        AlertSymbolProcessor processor = new AlertSymbolProcessor(
                binanceApi,
                evaluator,
                notificationService,
                snapshotService,
                policy,
                marketStateMachine
        );
        ReflectionTestUtils.setField(processor, "targetSymbol", "BTCUSDT");
        ReflectionTestUtils.setField(processor, "klineInterval", "3m");
        ReflectionTestUtils.setField(processor, "klineLimit", 120);
        ReflectionTestUtils.setField(processor, "multiTimeframeRole", "execution");
        ReflectionTestUtils.setField(processor, "contextInterval", "4h");
        ReflectionTestUtils.setField(processor, "contextKlineLimit", 60);

        List<BinanceKlineDTO> executionKlines = executionKlines();
        List<BinanceKlineDTO> contextKlines = contextKlines();
        when(binanceApi.listKline(any())).thenAnswer(invocation -> {
            BinanceKlineDTO request = invocation.getArgument(0);
            return "4h".equals(request.getInterval()) ? contextKlines : executionKlines;
        });

        FeatureSnapshot executionSnapshot = snapshot("3m", new BigDecimal("0.20"), new BigDecimal("0.15"));
        FeatureSnapshot contextSnapshot = snapshot("4h", new BigDecimal("0.75"), new BigDecimal("0.30"));
        when(snapshotService.buildSnapshot(eq("BTCUSDT"), eq("3m"), anyList())).thenReturn(executionSnapshot);
        when(snapshotService.buildSnapshot(eq("BTCUSDT"), eq("4h"), anyList())).thenReturn(contextSnapshot);
        when(marketStateMachine.evaluate(eq(executionSnapshot), any())).thenReturn(new MarketStateDecision(MarketState.PULLBACK, "3m pullback"));
        when(marketStateMachine.evaluate(eq(contextSnapshot), any())).thenReturn(new MarketStateDecision(MarketState.TREND, "4h trend"));

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "3m pullback long",
                executionKlines.get(executionKlines.size() - 1),
                "BREAKOUT_PULLBACK_LONG",
                "test",
                new BigDecimal("100.50"),
                new BigDecimal("99.20"),
                new BigDecimal("103.50"),
                new BigDecimal("1.10")
        );
        when(evaluator.evaluateRangeFailedBreakdownLong(executionKlines)).thenReturn(java.util.Optional.of(signal));

        AtomicReference<FeatureSnapshot> capturedSnapshot = new AtomicReference<>();
        when(policy.evaluate(eq(signal), any(FeatureSnapshot.class))).thenAnswer(invocation -> {
            FeatureSnapshot snapshot = invocation.getArgument(1);
            capturedSnapshot.set(snapshot);
            return new SignalPolicyDecision(
                    true,
                    signal.withContext(new BigDecimal("0.72"), "ok"),
                    new BigDecimal("0.72"),
                    List.of("ok")
            );
        });

        processor.process("BTCUSDT");

        ArgumentCaptor<BinanceKlineDTO> requestCaptor = ArgumentCaptor.forClass(BinanceKlineDTO.class);
        verify(binanceApi, times(2)).listKline(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues()).extracting(BinanceKlineDTO::getInterval).containsExactly("3m", "4h");

        FeatureSnapshot policySnapshot = capturedSnapshot.get();
        assertThat(policySnapshot).isNotNull();
        assertThat(policySnapshot.getInterval()).isEqualTo("3m");
        assertThat(policySnapshot.getMarketState()).isEqualTo(MarketState.PULLBACK);
        assertThat(policySnapshot.getContextSnapshot()).isNotNull();
        assertThat(policySnapshot.getContextSnapshot().getInterval()).isEqualTo("4h");
        assertThat(policySnapshot.getContextSnapshot().getMarketState()).isEqualTo(MarketState.TREND);
        verify(notificationService).send(any(AlertSignal.class));
        verify(snapshotService).rememberLatestSnapshot(executionSnapshot);
    }

    @Test
    void shouldReusePrewarmedHigherTimeframeSnapshotDuringStartupGracePeriod() {
        BinanceApi binanceApi = mock(BinanceApi.class);
        AlertRuleEvaluator evaluator = mock(AlertRuleEvaluator.class);
        AlertNotificationService notificationService = mock(AlertNotificationService.class);
        MarketFeatureSnapshotService snapshotService = mock(MarketFeatureSnapshotService.class);
        CompositeFactorSignalPolicy policy = mock(CompositeFactorSignalPolicy.class);
        MarketStateMachine marketStateMachine = mock(MarketStateMachine.class);

        AlertSymbolProcessor processor = new AlertSymbolProcessor(
                binanceApi,
                evaluator,
                notificationService,
                snapshotService,
                policy,
                marketStateMachine
        );
        ReflectionTestUtils.setField(processor, "targetSymbol", "BTCUSDT");
        ReflectionTestUtils.setField(processor, "klineInterval", "3m");
        ReflectionTestUtils.setField(processor, "klineLimit", 120);
        ReflectionTestUtils.setField(processor, "multiTimeframeRole", "execution");
        ReflectionTestUtils.setField(processor, "contextInterval", "4h");
        ReflectionTestUtils.setField(processor, "contextKlineLimit", 60);
        ReflectionTestUtils.setField(processor, "requireExecutionContext", true);
        ReflectionTestUtils.setField(processor, "executionContextWarmupEnabled", true);
        ReflectionTestUtils.setField(processor, "executionContextGracePeriodMs", 60_000L);

        List<BinanceKlineDTO> executionKlines = executionKlines();
        List<BinanceKlineDTO> validContextKlines = contextKlines();
        List<BinanceKlineDTO> insufficientContextKlines = validContextKlines.subList(0, 2);
        when(binanceApi.listKline(any())).thenAnswer(invocation -> {
            BinanceKlineDTO request = invocation.getArgument(0);
            if ("4h".equals(request.getInterval())) {
                return ((java.util.Map<?, ?>) ReflectionTestUtils.getField(processor, "contextWarmupReadyAt")).isEmpty()
                        ? validContextKlines
                        : insufficientContextKlines;
            }
            return executionKlines;
        });

        FeatureSnapshot executionSnapshot = snapshot("3m", new BigDecimal("0.20"), new BigDecimal("0.15"));
        FeatureSnapshot contextSnapshot = snapshot("4h", new BigDecimal("0.75"), new BigDecimal("0.30"));
        when(snapshotService.buildSnapshot(eq("BTCUSDT"), eq("3m"), anyList())).thenReturn(executionSnapshot);
        when(snapshotService.buildSnapshot(eq("BTCUSDT"), eq("4h"), anyList())).thenReturn(contextSnapshot);
        when(snapshotService.getLatestSnapshot("BTCUSDT", "4h")).thenReturn(contextSnapshot);
        when(marketStateMachine.evaluate(eq(executionSnapshot), any())).thenReturn(new MarketStateDecision(MarketState.PULLBACK, "3m pullback"));
        when(marketStateMachine.evaluate(eq(contextSnapshot), any())).thenReturn(new MarketStateDecision(MarketState.TREND, "4h trend"));

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "3m pullback long",
                executionKlines.get(executionKlines.size() - 1),
                "BREAKOUT_PULLBACK_LONG",
                "test",
                new BigDecimal("100.50"),
                new BigDecimal("99.20"),
                new BigDecimal("103.50"),
                new BigDecimal("1.10")
        );
        when(evaluator.evaluateRangeFailedBreakdownLong(executionKlines)).thenReturn(java.util.Optional.of(signal));

        AtomicReference<FeatureSnapshot> capturedSnapshot = new AtomicReference<>();
        when(policy.evaluate(eq(signal), any(FeatureSnapshot.class))).thenAnswer(invocation -> {
            FeatureSnapshot snapshot = invocation.getArgument(1);
            capturedSnapshot.set(snapshot);
            return new SignalPolicyDecision(
                    true,
                    signal.withContext(new BigDecimal("0.72"), "ok"),
                    new BigDecimal("0.72"),
                    List.of("ok")
            );
        });

        processor.prepareExecutionContext("BTCUSDT");
        processor.process("BTCUSDT");

        FeatureSnapshot policySnapshot = capturedSnapshot.get();
        assertThat(policySnapshot).isNotNull();
        assertThat(policySnapshot.getContextSnapshot()).isNotNull();
        assertThat(policySnapshot.getContextSnapshot().getInterval()).isEqualTo("4h");
        verify(snapshotService, times(1)).buildSnapshot(eq("BTCUSDT"), eq("4h"), anyList());
        verify(notificationService).send(any(AlertSignal.class));
    }

    private FeatureSnapshot snapshot(String interval, BigDecimal trendBias, BigDecimal breakoutConfirmation) {
        CompositeFactors compositeFactors = new CompositeFactors();
        compositeFactors.setTrendBiasScore(trendBias);
        compositeFactors.setBreakoutConfirmationScore(breakoutConfirmation);
        compositeFactors.setCrowdingScore(BigDecimal.ZERO);
        compositeFactors.setEventBiasScore(BigDecimal.ZERO);
        compositeFactors.setRegimeRiskScore(new BigDecimal("0.10"));

        FeatureQuality quality = new FeatureQuality();
        quality.setPriceReady(true);
        quality.setDerivativeReady(true);
        quality.setEventReady(true);
        quality.setRelevantEventCount(0);
        quality.setCompleteSnapshot(true);

        FeatureSnapshot snapshot = new FeatureSnapshot();
        snapshot.setSymbol("BTCUSDT");
        snapshot.setInterval(interval);
        snapshot.setAsOfTime(System.currentTimeMillis());
        snapshot.setGeneratedAt(System.currentTimeMillis());
        snapshot.setCompositeFactors(compositeFactors);
        snapshot.setQuality(quality);
        return snapshot;
    }

    private List<BinanceKlineDTO> executionKlines() {
        return List.of(
                kline("3m", 99.80, 100.20, 99.50, 99.90, 1L, 2L),
                kline("3m", 99.90, 100.50, 99.80, 100.20, 2L, 3L),
                kline("3m", 100.20, 100.80, 100.00, 100.60, 3L, 4L)
        );
    }

    private List<BinanceKlineDTO> contextKlines() {
        return List.of(
                kline("4h", 96.00, 98.00, 95.50, 97.80, 1L, 2L),
                kline("4h", 97.80, 100.00, 97.20, 99.50, 2L, 3L),
                kline("4h", 99.50, 101.50, 99.00, 101.00, 3L, 4L)
        );
    }

    private BinanceKlineDTO kline(String interval,
                                  double open,
                                  double high,
                                  double low,
                                  double close,
                                  long startTime,
                                  long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol("BTCUSDT");
        dto.setInterval(interval);
        dto.setOpen(String.format("%.2f", open));
        dto.setHigh(String.format("%.2f", high));
        dto.setLow(String.format("%.2f", low));
        dto.setClose(String.format("%.2f", close));
        dto.setVolume("100.0");
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        return dto;
    }
}
