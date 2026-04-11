package com.mobai.alert.control;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.service.MarketFeatureSnapshotService;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.state.runtime.RuntimePosition;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.AlertRuleEvaluator;
import com.mobai.alert.strategy.policy.CompositeFactorSignalPolicy;
import com.mobai.alert.strategy.policy.MarketStateMachine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertSymbolProcessorExitAlertTests {

    @Test
    void shouldSendExitAlertWhenFollowThroughFailsOnNextClosedBar() {
        Fixture fixture = fixture();
        when(fixture.binanceApi().listKline(any())).thenReturn(failedFollowThroughKlines());
        fixture.activePositions().put("BTCUSDT", managedRuntimePosition());

        fixture.processor().process("BTCUSDT");

        AlertSignal exitSignal = captureSingleSignal(fixture.notificationService());
        assertThat(exitSignal.getType()).isEqualTo("EXIT_FAILED_FOLLOW_THROUGH_LONG");
        assertThat(exitSignal.getReferenceEntryPrice()).isEqualByComparingTo("100.00");
        assertThat(exitSignal.getReferenceStopPrice()).isEqualByComparingTo("95.00");
        assertThat(fixture.activePositions()).isEmpty();
        verifyNoInteractions(fixture.marketFeatureSnapshotService(), fixture.evaluator(), fixture.compositeFactorSignalPolicy(), fixture.marketStateMachine());
    }

    @Test
    void shouldSendScaleOutAlertAndKeepManagedPosition() {
        Fixture fixture = fixture();
        when(fixture.binanceApi().listKline(any())).thenReturn(scaleOutKlines());
        fixture.activePositions().put("BTCUSDT", managedRuntimePosition());

        fixture.processor().process("BTCUSDT");

        AlertSignal scaleOutSignal = captureSingleSignal(fixture.notificationService());
        RuntimePosition runtimePosition = fixture.activePositions().get("BTCUSDT");
        assertThat(scaleOutSignal.getType()).isEqualTo("EXIT_SCALE_OUT_LONG");
        assertThat(runtimePosition).isNotNull();
        assertThat(runtimePosition.scaleOutTaken()).isTrue();
        assertThat(runtimePosition.pendingAddOn()).isFalse();
        assertThat(runtimePosition.addOnsUsed()).isEqualTo(1);
        assertThat(runtimePosition.stopPrice()).isEqualByComparingTo("104.00");
        assertThat(runtimePosition.remainingSize()).isEqualByComparingTo("0.85");
        verifyNoInteractions(fixture.marketFeatureSnapshotService(), fixture.evaluator(), fixture.compositeFactorSignalPolicy(), fixture.marketStateMachine());
    }

    @Test
    void shouldSendTargetExitAlertFromLiveBar() {
        Fixture fixture = fixture();
        when(fixture.binanceApi().listKline(any())).thenReturn(targetHitKlines());
        fixture.activePositions().put("BTCUSDT", managedRuntimePosition());

        fixture.processor().process("BTCUSDT");

        AlertSignal targetSignal = captureSingleSignal(fixture.notificationService());
        assertThat(targetSignal.getType()).isEqualTo("EXIT_TARGET_LONG");
        assertThat(targetSignal.getTriggerPrice()).isEqualByComparingTo("115.00");
        assertThat(fixture.activePositions()).isEmpty();
        verifyNoInteractions(fixture.marketFeatureSnapshotService(), fixture.evaluator(), fixture.compositeFactorSignalPolicy(), fixture.marketStateMachine());
    }

    @Test
    void shouldSendInitialStopExitAlertFromLiveBar() {
        Fixture fixture = fixture();
        when(fixture.binanceApi().listKline(any())).thenReturn(initialStopHitKlines());
        fixture.activePositions().put("BTCUSDT", managedRuntimePosition());

        fixture.processor().process("BTCUSDT");

        AlertSignal stopSignal = captureSingleSignal(fixture.notificationService());
        assertThat(stopSignal.getType()).isEqualTo("EXIT_STOP_LONG");
        assertThat(stopSignal.getTriggerPrice()).isEqualByComparingTo("95.00");
        assertThat(fixture.activePositions()).isEmpty();
        verifyNoInteractions(fixture.marketFeatureSnapshotService(), fixture.evaluator(), fixture.compositeFactorSignalPolicy(), fixture.marketStateMachine());
    }

    @Test
    void shouldExitAtTrailingStopAfterScaleOutAndClosedBarAdvance() {
        Fixture fixture = fixture();
        when(fixture.binanceApi().listKline(any())).thenReturn(scaleOutKlines(), trailingStopKlines());
        fixture.activePositions().put("BTCUSDT", managedRuntimePosition());

        fixture.processor().process("BTCUSDT");
        RuntimePosition afterScaleOut = fixture.activePositions().get("BTCUSDT");
        assertThat(afterScaleOut).isNotNull();
        assertThat(afterScaleOut.scaleOutTaken()).isTrue();

        fixture.processor().process("BTCUSDT");

        ArgumentCaptor<AlertSignal> captor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(fixture.notificationService(), times(2)).send(captor.capture());
        List<AlertSignal> sentSignals = captor.getAllValues();
        assertThat(sentSignals.get(0).getType()).isEqualTo("EXIT_SCALE_OUT_LONG");
        assertThat(sentSignals.get(1).getType()).isEqualTo("EXIT_TRAILING_STOP_LONG");
        assertThat(sentSignals.get(1).getTriggerPrice()).isEqualByComparingTo("106.00");
        assertThat(fixture.activePositions()).isEmpty();
        verifyNoInteractions(fixture.marketFeatureSnapshotService(), fixture.evaluator(), fixture.compositeFactorSignalPolicy(), fixture.marketStateMachine());
    }

    private AlertSignal captureSingleSignal(AlertNotificationService notificationService) {
        ArgumentCaptor<AlertSignal> captor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(notificationService).send(captor.capture());
        return captor.getValue();
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

    private List<BinanceKlineDTO> failedFollowThroughKlines() {
        return List.of(
                kline(99.50, 101.00, 99.00, 100.00, 8L, 9L),
                kline(100.00, 100.60, 99.50, 100.20, 9L, 10L),
                kline(100.50, 100.80, 97.80, 98.00, 10L, 11L),
                kline(98.10, 98.40, 97.90, 98.20, 11L, 12L)
        );
    }

    private List<BinanceKlineDTO> scaleOutKlines() {
        return List.of(
                kline(99.50, 101.00, 99.00, 100.00, 8L, 9L),
                kline(100.00, 100.60, 99.50, 100.20, 9L, 10L),
                kline(100.50, 109.00, 100.10, 108.50, 10L, 11L),
                kline(109.20, 109.40, 108.80, 109.00, 11L, 12L)
        );
    }

    private List<BinanceKlineDTO> trailingStopKlines() {
        return List.of(
                kline(99.50, 101.00, 99.00, 100.00, 8L, 9L),
                kline(100.00, 100.60, 99.50, 100.20, 9L, 10L),
                kline(100.50, 109.00, 100.10, 108.50, 10L, 11L),
                kline(109.20, 111.00, 106.50, 110.50, 11L, 12L),
                kline(106.20, 106.40, 105.50, 105.80, 12L, 13L)
        );
    }

    private List<BinanceKlineDTO> targetHitKlines() {
        return List.of(
                kline(99.50, 101.00, 99.00, 100.00, 8L, 9L),
                kline(100.00, 100.60, 99.50, 100.20, 9L, 10L),
                kline(100.20, 116.00, 99.80, 114.90, 10L, 11L)
        );
    }

    private List<BinanceKlineDTO> initialStopHitKlines() {
        return List.of(
                kline(99.50, 101.00, 99.00, 100.00, 8L, 9L),
                kline(100.00, 100.60, 99.50, 100.20, 9L, 10L),
                kline(99.80, 100.10, 94.80, 95.20, 10L, 11L)
        );
    }

    private BinanceKlineDTO kline(double open,
                                  double high,
                                  double low,
                                  double close,
                                  long startTime,
                                  long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol("BTCUSDT");
        dto.setInterval("4h");
        dto.setOpen(String.format("%.2f", open));
        dto.setHigh(String.format("%.2f", high));
        dto.setLow(String.format("%.2f", low));
        dto.setClose(String.format("%.2f", close));
        dto.setVolume("100.0");
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        BinanceApi binanceApi = mock(BinanceApi.class);
        AlertRuleEvaluator evaluator = mock(AlertRuleEvaluator.class);
        AlertNotificationService alertNotificationService = mock(AlertNotificationService.class);
        MarketFeatureSnapshotService marketFeatureSnapshotService = mock(MarketFeatureSnapshotService.class);
        CompositeFactorSignalPolicy compositeFactorSignalPolicy = mock(CompositeFactorSignalPolicy.class);
        MarketStateMachine marketStateMachine = mock(MarketStateMachine.class);

        AlertSymbolProcessor processor = new AlertSymbolProcessor(
                binanceApi,
                evaluator,
                alertNotificationService,
                marketFeatureSnapshotService,
                compositeFactorSignalPolicy,
                marketStateMachine
        );
        ReflectionTestUtils.setField(processor, "targetSymbol", "BTCUSDT");
        ReflectionTestUtils.setField(processor, "klineInterval", "4h");
        ReflectionTestUtils.setField(processor, "klineLimit", 180);
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

    private record Fixture(AlertSymbolProcessor processor,
                           BinanceApi binanceApi,
                           AlertNotificationService notificationService,
                           MarketFeatureSnapshotService marketFeatureSnapshotService,
                           AlertRuleEvaluator evaluator,
                           CompositeFactorSignalPolicy compositeFactorSignalPolicy,
                           MarketStateMachine marketStateMachine,
                           Map<String, RuntimePosition> activePositions) {
    }
}
