package com.mobai.alert.backtest.priceaction;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.feature.model.FeatureSnapshot;
import com.mobai.alert.feature.service.BacktestFeatureSnapshotService;
import com.mobai.alert.strategy.model.MarketState;
import com.mobai.alert.strategy.priceaction.policy.MarketStateDecision;
import com.mobai.alert.strategy.priceaction.policy.MarketStateMachine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceActionBacktestServiceMultiTimeframeTests {

    @Test
    void shouldCacheHigherTimeframeSnapshotUntilNewContextBarCloses() throws Exception {
        BacktestFeatureSnapshotService snapshotService = mock(BacktestFeatureSnapshotService.class);
        MarketStateMachine marketStateMachine = mock(MarketStateMachine.class);

        when(snapshotService.buildSnapshot(anyString(), anyString(), anyList()))
                .thenAnswer(invocation -> {
                    FeatureSnapshot snapshot = new FeatureSnapshot();
                    snapshot.setSymbol(invocation.getArgument(0));
                    snapshot.setInterval(invocation.getArgument(1));
                    return snapshot;
                });
        when(marketStateMachine.evaluate(any(FeatureSnapshot.class), any(MarketState.class)))
                .thenReturn(new MarketStateDecision(MarketState.TREND, "context up"));

        Object context = newHigherTimeframeContext(List.of(
                kline("4h", 0L, 4L),
                kline("4h", 4L, 8L),
                kline("4h", 8L, 12L),
                kline("4h", 12L, 16L)
        ));

        Method snapshotFor = context.getClass().getDeclaredMethod(
                "snapshotFor",
                long.class,
                BacktestFeatureSnapshotService.class,
                MarketStateMachine.class
        );
        snapshotFor.setAccessible(true);

        Object insufficient = snapshotFor.invoke(context, 8L, snapshotService, marketStateMachine);
        FeatureSnapshot firstSnapshot = (FeatureSnapshot) snapshotFor.invoke(context, 12L, snapshotService, marketStateMachine);
        FeatureSnapshot cachedSnapshot = (FeatureSnapshot) snapshotFor.invoke(context, 13L, snapshotService, marketStateMachine);
        FeatureSnapshot secondSnapshot = (FeatureSnapshot) snapshotFor.invoke(context, 16L, snapshotService, marketStateMachine);

        assertThat(insufficient).isNull();
        assertThat(firstSnapshot).isNotNull();
        assertThat(firstSnapshot.getInterval()).isEqualTo("4h");
        assertThat(firstSnapshot.getMarketState()).isEqualTo(MarketState.TREND);
        assertThat(cachedSnapshot).isSameAs(firstSnapshot);
        assertThat(secondSnapshot).isNotNull().isNotSameAs(firstSnapshot);
        assertThat(secondSnapshot.getMarketState()).isEqualTo(MarketState.TREND);

        ArgumentCaptor<List<BinanceKlineDTO>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(snapshotService, times(2)).buildSnapshot(anyString(), anyString(), historyCaptor.capture());
        assertThat(historyCaptor.getAllValues().get(0)).hasSize(3);
        assertThat(historyCaptor.getAllValues().get(1)).hasSize(4);
        verify(marketStateMachine, times(2)).evaluate(any(FeatureSnapshot.class), any(MarketState.class));
    }

    private Object newHigherTimeframeContext(List<BinanceKlineDTO> history) throws Exception {
        Class<?> contextClass = Class.forName("com.mobai.alert.backtest.priceaction.PriceActionBacktestService$HigherTimeframeBacktestContext");
        Constructor<?> constructor = contextClass.getDeclaredConstructor(String.class, String.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance("BTCUSDT", "4h", history);
    }

    private BinanceKlineDTO kline(String interval, long startTime, long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol("BTCUSDT");
        dto.setInterval(interval);
        dto.setOpen("100.00");
        dto.setHigh("101.00");
        dto.setLow("99.50");
        dto.setClose("100.50");
        dto.setVolume("100.0");
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        return dto;
    }
}
