package com.mobai.alert.strategy.priceaction.runtime;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.config.StrategyMetadata;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.service.MarketFeatureSnapshotService;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.strategy.model.MarketState;
import com.mobai.alert.strategy.priceaction.runtime.RuntimePosition;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import com.mobai.alert.strategy.priceaction.PriceActionSignalEvaluator;
import com.mobai.alert.strategy.priceaction.policy.CompositeFactorSignalPolicy;
import com.mobai.alert.strategy.priceaction.policy.MarketStateDecision;
import com.mobai.alert.strategy.priceaction.policy.MarketStateMachine;
import com.mobai.alert.strategy.priceaction.policy.SignalPolicyDecision;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PriceActionExecutionProcessorConflictManagementTests {

    @Test
    void shouldConvertRepeatedHigherTimeframeConflictsIntoReduceTightenAndClose() {
        Fixture fixture = fixture();
        when(fixture.binanceApi().listKline(any())).thenAnswer(invocation -> {
            BinanceKlineDTO request = invocation.getArgument(0);
            return "4h".equalsIgnoreCase(request.getInterval()) ? higherTimeframeKlines() : executionKlines();
        });
        when(fixture.marketFeatureSnapshotService().buildSnapshot(any(), argThat(interval("3m")), any()))
                .thenReturn(executionSnapshot());
        when(fixture.marketFeatureSnapshotService().buildSnapshot(any(), argThat(interval("4h")), any()))
                .thenReturn(contextSnapshot());
        when(fixture.marketStateMachine().evaluate(any(FeatureSnapshot.class), any(MarketState.class)))
                .thenAnswer(invocation -> {
                    FeatureSnapshot snapshot = invocation.getArgument(0);
                    if ("4h".equalsIgnoreCase(snapshot.getInterval())) {
                        return new MarketStateDecision(MarketState.TREND, "higher timeframe uptrend");
                    }
                    return new MarketStateDecision(MarketState.PULLBACK, "execution pullback");
                });

        when(fixture.evaluator().evaluateRangeFailedBreakdownLong(any())).thenReturn(Optional.empty());
        when(fixture.evaluator().evaluateRangeFailedBreakoutShort(any())).thenReturn(Optional.empty());
        when(fixture.evaluator().evaluateTrendBreakout(any())).thenReturn(Optional.empty());
        when(fixture.evaluator().evaluateTrendBreakdown(any())).thenReturn(Optional.empty());
        when(fixture.evaluator().evaluateBreakoutPullback(any(), any(), any(), any(Boolean.class))).thenReturn(Optional.empty());
        when(fixture.evaluator().evaluateSecondEntryLong(any(), any(), any())).thenReturn(Optional.empty());
        when(fixture.evaluator().evaluateSecondEntryShort(any(), any(), any())).thenReturn(Optional.of(counterTrendShortSignal()));
        when(fixture.compositeFactorSignalPolicy().evaluate(any(AlertSignal.class), any(FeatureSnapshot.class)))
                .thenAnswer(invocation -> {
                    AlertSignal blockedSignal = invocation.getArgument(0);
                    return new SignalPolicyDecision(
                            false,
                            blockedSignal,
                            null,
                            List.of(
                                    "HigherTF(4h)=趋势",
                                    "HigherTF bias=LONG",
                                    "Block: 4h bias LONG rejects SHORT breakout pullback"
                            )
                    );
                });

        fixture.activePositions().put("BTCUSDT", managedRuntimePosition());

        fixture.processor().process("BTCUSDT");
        RuntimePosition afterReduce = fixture.activePositions().get("BTCUSDT");
        assertThat(afterReduce).isNotNull();
        assertThat(afterReduce.conflictReduced()).isTrue();
        assertThat(afterReduce.remainingSize()).isEqualByComparingTo("0.50");
        assertThat(afterReduce.stopPrice()).isEqualByComparingTo("95.00");

        fixture.processor().process("BTCUSDT");
        RuntimePosition afterTighten = fixture.activePositions().get("BTCUSDT");
        assertThat(afterTighten).isNotNull();
        assertThat(afterTighten.conflictStopTightened()).isTrue();
        assertThat(afterTighten.stopPrice()).isEqualByComparingTo("100.00");

        fixture.processor().process("BTCUSDT");
        assertThat(fixture.activePositions()).isEmpty();

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(fixture.notificationService(), times(3)).send(signalCaptor.capture());
        List<AlertSignal> signals = signalCaptor.getAllValues();
        assertThat(signals).extracting(AlertSignal::getType).containsExactly(
                "EXIT_CONFLICT_REDUCE_LONG",
                "EXIT_CONFLICT_TIGHTEN_STOP_LONG",
                "EXIT_CONFLICT_CLOSE_LONG"
        );
        assertThat(signals.get(0).getTitle()).isEqualTo("BTCUSDT 高周期冲突，先减仓观察");
        assertThat(signals.get(0).getSummary()).contains("先减仓 50%");
        assertThat(signals.get(1).getTitle()).isEqualTo("BTCUSDT 高周期冲突，收紧防守");
        assertThat(signals.get(1).getSummary()).contains("把防守位从 95.00 收紧到 100.00");
        assertThat(signals.get(2).getTitle()).isEqualTo("BTCUSDT 高周期冲突，先退出等待");
        assertThat(signals.get(2).getSummary()).contains("等待重新同向的入场机会");
        assertThat(signals.get(0).getContextComment()).contains("blockedSignal=SECOND_ENTRY_SHORT");
        assertThat(signals.get(1).getContextComment()).contains("conflictStopTightened=true");
        assertThat(signals.get(2).getContextComment()).contains("reason=CONFLICT_CLOSE");
        verifyNoMoreInteractions(fixture.notificationService());
    }

    private AlertSignal counterTrendShortSignal() {
        BinanceKlineDTO kline = executionKlines().get(executionKlines().size() - 1);
        return new AlertSignal(
                TradeDirection.SHORT,
                "3m counter-trend short",
                kline,
                "SECOND_ENTRY_SHORT",
                "counter-trend setup blocked by 4h bias",
                new BigDecimal("100.80"),
                new BigDecimal("101.60"),
                new BigDecimal("98.50"),
                new BigDecimal("0.90")
        );
    }

    private RuntimePosition managedRuntimePosition() {
        return new RuntimePosition(
                "CONFIRMED_BREAKOUT_LONG",
                TradeDirection.LONG,
                10L,
                new BigDecimal("100.00"),
                new BigDecimal("95.00"),
                new BigDecimal("115.00"),
                new BigDecimal("1.00"),
                new BigDecimal("0.50"),
                new BigDecimal("1.20"),
                new BigDecimal("1.00"),
                1,
                new BigDecimal("1.60"),
                new BigDecimal("0.35")
        );
    }

    private FeatureSnapshot executionSnapshot() {
        FeatureSnapshot snapshot = new FeatureSnapshot();
        snapshot.setSymbol("BTCUSDT");
        snapshot.setInterval("3m");
        return snapshot;
    }

    private FeatureSnapshot contextSnapshot() {
        FeatureSnapshot snapshot = new FeatureSnapshot();
        snapshot.setSymbol("BTCUSDT");
        snapshot.setInterval("4h");
        return snapshot;
    }

    private List<BinanceKlineDTO> executionKlines() {
        return List.of(
                kline("3m", 99.80, 100.60, 99.60, 100.00, 8L, 9L),
                kline("3m", 100.10, 100.70, 99.90, 100.20, 9L, 10L),
                kline("3m", 100.30, 101.10, 100.10, 100.90, 10L, 11L),
                kline("3m", 100.80, 101.20, 100.30, 101.00, 11L, 12L)
        );
    }

    private List<BinanceKlineDTO> higherTimeframeKlines() {
        return List.of(
                kline("4h", 96.00, 100.00, 95.50, 99.50, 0L, 4L),
                kline("4h", 99.60, 103.00, 99.20, 102.40, 4L, 8L),
                kline("4h", 102.50, 105.50, 102.10, 105.00, 8L, 12L)
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

    private ArgumentMatcher<String> interval(String expected) {
        return expected::equalsIgnoreCase;
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        BinanceApi binanceApi = mock(BinanceApi.class);
        PriceActionSignalEvaluator evaluator = mock(PriceActionSignalEvaluator.class);
        AlertNotificationService alertNotificationService = mock(AlertNotificationService.class);
        MarketFeatureSnapshotService marketFeatureSnapshotService = mock(MarketFeatureSnapshotService.class);
        CompositeFactorSignalPolicy compositeFactorSignalPolicy = mock(CompositeFactorSignalPolicy.class);
        MarketStateMachine marketStateMachine = mock(MarketStateMachine.class);

        PriceActionExecutionProcessor processor = new PriceActionExecutionProcessor(
                binanceApi,
                evaluator,
                alertNotificationService,
                marketFeatureSnapshotService,
                compositeFactorSignalPolicy,
                marketStateMachine,
                new StrategyMetadata("test-strategy", "Test Strategy", true)
        );
        ReflectionTestUtils.setField(processor, "targetSymbol", "BTCUSDT");
        ReflectionTestUtils.setField(processor, "klineInterval", "3m");
        ReflectionTestUtils.setField(processor, "klineLimit", 180);
        ReflectionTestUtils.setField(processor, "contextInterval", "4h");
        ReflectionTestUtils.setField(processor, "contextKlineLimit", 180);
        ReflectionTestUtils.setField(processor, "failedFollowThroughMaxBars", 2);
        ReflectionTestUtils.setField(processor, "failedFollowThroughAdverseR", new BigDecimal("0.35"));
        ReflectionTestUtils.setField(processor, "failedFollowThroughMinBodyRatio", new BigDecimal("0.40"));
        ReflectionTestUtils.setField(processor, "failedFollowThroughCloseLocation", new BigDecimal("0.35"));
        ReflectionTestUtils.setField(processor, "scaleOutTriggerR", new BigDecimal("1.00"));
        ReflectionTestUtils.setField(processor, "scaleOutFraction", new BigDecimal("0.50"));
        ReflectionTestUtils.setField(processor, "trailingActivationR", new BigDecimal("1.20"));
        ReflectionTestUtils.setField(processor, "trailingDistanceR", new BigDecimal("1.00"));
        ReflectionTestUtils.setField(processor, "pyramidMaxAdds", 1);
        ReflectionTestUtils.setField(processor, "pyramidTriggerR", new BigDecimal("1.60"));
        ReflectionTestUtils.setField(processor, "pyramidAddFraction", new BigDecimal("0.35"));

        Map<String, RuntimePosition> activePositions = (Map<String, RuntimePosition>) ReflectionTestUtils.getField(processor, "activePositions");
        return new Fixture(
                processor,
                binanceApi,
                alertNotificationService,
                marketFeatureSnapshotService,
                evaluator,
                compositeFactorSignalPolicy,
                marketStateMachine,
                activePositions
        );
    }

    private record Fixture(PriceActionExecutionProcessor processor,
                           BinanceApi binanceApi,
                           AlertNotificationService notificationService,
                           MarketFeatureSnapshotService marketFeatureSnapshotService,
                           PriceActionSignalEvaluator evaluator,
                           CompositeFactorSignalPolicy compositeFactorSignalPolicy,
                           MarketStateMachine marketStateMachine,
                           Map<String, RuntimePosition> activePositions) {
    }
}
