package com.mobai.alert.strategy.backtest;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.state.backtest.BacktestConfig;
import com.mobai.alert.state.backtest.TradeRecord;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.policy.CompositeFactorPolicyProfile;
import com.mobai.alert.strategy.policy.MarketStateMachine;
import com.mobai.alert.strategy.policy.PolicyWeights;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyBacktestServiceExecutionOrderTests {

    @Test
    void shouldScaleOutThenAdvanceTrailingStopAndOnlyScheduleAddOnForNextBar() throws Exception {
        StrategyBacktestService service = new StrategyBacktestService(null, null, null, new MarketStateMachine());
        Object position = openLongPosition(service);
        List<TradeRecord> completedTrades = new ArrayList<>();

        Object nextPosition = evaluatePosition(
                service,
                position,
                kline(100.50, 109.00, 100.10, 108.50, 2L),
                2,
                completedTrades,
                config()
        );

        assertThat(nextPosition).isNotNull();
        assertThat(completedTrades).isEmpty();
        assertThat(readBoolean(nextPosition, "scaleOutTaken")).isTrue();
        assertThat(readBoolean(nextPosition, "pendingAddOn")).isTrue();
        assertThat(readInt(nextPosition, "addOnsUsed")).isZero();
        assertThat(readBigDecimal(nextPosition, "realizedR")).isEqualByComparingTo("0.50000000");
        assertThat(invokeBigDecimal(nextPosition, "remainingSize")).isEqualByComparingTo("0.50");
        assertThat(readBigDecimal(nextPosition, "stopPrice")).isEqualByComparingTo("104.0000");
        assertThat(readOpenLots(nextPosition)).hasSize(1);
    }

    @Test
    void shouldActivatePendingAddOnOnNextBarOpenBeforeTrailingUpdate() throws Exception {
        StrategyBacktestService service = new StrategyBacktestService(null, null, null, new MarketStateMachine());
        List<TradeRecord> completedTrades = new ArrayList<>();
        Object position = openLongPosition(service);

        position = evaluatePosition(
                service,
                position,
                kline(100.50, 109.00, 100.10, 108.50, 2L),
                2,
                completedTrades,
                config()
        );
        Object nextPosition = evaluatePosition(
                service,
                position,
                kline(109.20, 111.00, 106.50, 110.50, 3L),
                3,
                completedTrades,
                config()
        );

        assertThat(nextPosition).isNotNull();
        assertThat(completedTrades).isEmpty();
        assertThat(readBoolean(nextPosition, "pendingAddOn")).isFalse();
        assertThat(readInt(nextPosition, "addOnsUsed")).isEqualTo(1);
        assertThat(invokeBigDecimal(nextPosition, "remainingSize")).isEqualByComparingTo("0.85");
        assertThat(readBigDecimal(nextPosition, "stopPrice")).isEqualByComparingTo("106.0000");
        assertThat(readOpenLots(nextPosition)).hasSize(2);
    }

    @Test
    void shouldExitAtTrailingStopAfterScaleOutAndAddOnSequence() throws Exception {
        StrategyBacktestService service = new StrategyBacktestService(null, null, null, new MarketStateMachine());
        List<TradeRecord> completedTrades = new ArrayList<>();
        Object position = openLongPosition(service);

        position = evaluatePosition(
                service,
                position,
                kline(100.50, 109.00, 100.10, 108.50, 2L),
                2,
                completedTrades,
                config()
        );
        position = evaluatePosition(
                service,
                position,
                kline(109.20, 111.00, 106.50, 110.50, 3L),
                3,
                completedTrades,
                config()
        );
        Object finalPosition = evaluatePosition(
                service,
                position,
                kline(106.20, 106.40, 105.50, 105.80, 4L),
                4,
                completedTrades,
                config()
        );

        assertThat(finalPosition).isNull();
        assertThat(completedTrades).hasSize(1);
        TradeRecord trade = completedTrades.get(0);
        assertThat(trade.exitReason()).contains("STOP");
        assertThat(trade.exitReason()).contains("scaleOut=true");
        assertThat(trade.exitReason()).contains("addOns=1");
        assertThat(trade.exitReason()).contains("finalStop=106");
        assertThat(trade.realizedR()).isEqualByComparingTo("0.87600000");
    }

    @Test
    void shouldExitEarlyWhenFollowThroughFailsImmediately() throws Exception {
        StrategyBacktestService service = new StrategyBacktestService(null, null, null, new MarketStateMachine());
        List<TradeRecord> completedTrades = new ArrayList<>();
        Object position = openLongPosition(service);

        Object finalPosition = evaluatePosition(
                service,
                position,
                kline(100.20, 100.80, 97.80, 98.00, 2L),
                2,
                completedTrades,
                config()
        );

        assertThat(finalPosition).isNull();
        assertThat(completedTrades).hasSize(1);
        TradeRecord trade = completedTrades.get(0);
        assertThat(trade.exitReason()).contains("FAILED_FOLLOW_THROUGH");
        assertThat(trade.realizedR()).isEqualByComparingTo("-0.40000000");
    }

    private Object openLongPosition(StrategyBacktestService service) throws Exception {
        Method openPosition = StrategyBacktestService.class.getDeclaredMethod(
                "openPosition",
                AlertSignal.class,
                BinanceKlineDTO.class,
                int.class,
                BacktestConfig.class
        );
        openPosition.setAccessible(true);
        return openPosition.invoke(service, breakoutLongSignal(), entryBar(), 1, config());
    }

    private Object evaluatePosition(StrategyBacktestService service,
                                    Object position,
                                    BinanceKlineDTO bar,
                                    int barIndex,
                                    List<TradeRecord> completedTrades,
                                    BacktestConfig config) throws Exception {
        Class<?> positionClass = Class.forName("com.mobai.alert.strategy.backtest.StrategyBacktestService$ManagedPosition");
        Method evaluatePositionOnBar = StrategyBacktestService.class.getDeclaredMethod(
                "evaluatePositionOnBar",
                positionClass,
                BinanceKlineDTO.class,
                int.class,
                List.class,
                BacktestConfig.class
        );
        evaluatePositionOnBar.setAccessible(true);
        return evaluatePositionOnBar.invoke(service, position, bar, barIndex, completedTrades, config);
    }

    private AlertSignal breakoutLongSignal() {
        return new AlertSignal(
                TradeDirection.LONG,
                "Breakout Long",
                entryBar(),
                "CONFIRMED_BREAKOUT_LONG",
                "test",
                new BigDecimal("100.00"),
                new BigDecimal("95.00"),
                new BigDecimal("115.00"),
                new BigDecimal("1.50")
        );
    }

    private BinanceKlineDTO entryBar() {
        return kline(100.00, 101.00, 99.50, 100.80, 1L);
    }

    private BinanceKlineDTO kline(double open,
                                  double high,
                                  double low,
                                  double close,
                                  long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol("BTCUSDT");
        dto.setInterval("4h");
        dto.setOpen(String.format("%.2f", open));
        dto.setHigh(String.format("%.2f", high));
        dto.setLow(String.format("%.2f", low));
        dto.setClose(String.format("%.2f", close));
        dto.setVolume("100.0");
        dto.setStartTime(endTime - 1);
        dto.setEndTime(endTime);
        return dto;
    }

    private BacktestConfig config() {
        return new BacktestConfig(
                "BTCUSDT",
                "4h",
                0L,
                1L,
                20,
                60,
                36,
                new BigDecimal("0.03"),
                new BigDecimal("0.18"),
                new BigDecimal("0.015"),
                2,
                new BigDecimal("0.45"),
                12,
                new BigDecimal("0.012"),
                new BigDecimal("0.003"),
                new BigDecimal("1.50"),
                new BigDecimal("0.45"),
                new BigDecimal("0.05"),
                new BigDecimal("0.008"),
                new BigDecimal("0.003"),
                new BigDecimal("0.001"),
                new BigDecimal("1.20"),
                new BigDecimal("0.008"),
                new BigDecimal("0.006"),
                new BigDecimal("1.10"),
                new BigDecimal("0.001"),
                new BigDecimal("0.25"),
                new BigDecimal("0.55"),
                new BigDecimal("0.80"),
                43200000L,
                12,
                18,
                18,
                new BigDecimal("1.50"),
                new BigDecimal("1.00"),
                new BigDecimal("0.50"),
                new BigDecimal("1.20"),
                new BigDecimal("1.00"),
                1,
                new BigDecimal("1.60"),
                new BigDecimal("0.35"),
                profile()
        );
    }

    private CompositeFactorPolicyProfile profile() {
        PolicyWeights rangeWeights = new PolicyWeights(
                new BigDecimal("0.18"),
                new BigDecimal("0.10"),
                new BigDecimal("0.12"),
                new BigDecimal("0.12"),
                new BigDecimal("0.18")
        );
        PolicyWeights breakoutWeights = new PolicyWeights(
                new BigDecimal("0.18"),
                new BigDecimal("0.26"),
                new BigDecimal("0.10"),
                new BigDecimal("0.08"),
                new BigDecimal("0.22")
        );
        PolicyWeights pullbackWeights = new PolicyWeights(
                new BigDecimal("0.24"),
                new BigDecimal("0.18"),
                new BigDecimal("0.10"),
                new BigDecimal("0.06"),
                new BigDecimal("0.18")
        );
        return new CompositeFactorPolicyProfile(
                true,
                new BigDecimal("0.50"),
                new BigDecimal("0.50"),
                new BigDecimal("0.55"),
                new BigDecimal("0.53"),
                new BigDecimal("0.88"),
                BigDecimal.ZERO,
                new BigDecimal("0.80"),
                new BigDecimal("-0.20"),
                new BigDecimal("-0.35"),
                new BigDecimal("-0.60"),
                new BigDecimal("0.45"),
                new BigDecimal("0.70"),
                rangeWeights,
                breakoutWeights,
                pullbackWeights
        );
    }

    private BigDecimal readBigDecimal(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (BigDecimal) field.get(target);
    }

    private boolean readBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (boolean) field.get(target);
    }

    private int readInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (int) field.get(target);
    }

    private BigDecimal invokeBigDecimal(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (BigDecimal) method.invoke(target);
    }

    @SuppressWarnings("unchecked")
    private List<Object> readOpenLots(Object target) throws Exception {
        Field field = target.getClass().getDeclaredField("openLots");
        field.setAccessible(true);
        return (List<Object>) field.get(target);
    }
}
