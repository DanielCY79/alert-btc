package com.mobai.alert.strategy.pullback;

import com.mobai.alert.access.dto.BinanceKlineDTO;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.shared.StrategySettings;
import com.mobai.alert.strategy.shared.StrategySupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 突破后回踩确认策略。
 *
 * 这类策略不在“刚突破那一下”直接追，而是等市场先突破，
 * 再等价格第一次回到突破位附近测试，确认该位置由阻力变支撑、
 * 或由支撑变阻力之后，再考虑顺着突破方向入场。
 *
 * 它的优点是：
 * - 入场位置通常比直接追突破更好；
 * - 止损位置更清晰；
 * - 更适合 BTC 这种突破后经常先回踩再走趋势的结构。
 */
public class BreakoutPullbackStrategyEvaluator {

    private static final String PULLBACK_LONG_TYPE = "BREAKOUT_PULLBACK_LONG";
    private static final String PULLBACK_SHORT_TYPE = "BREAKOUT_PULLBACK_SHORT";

    /**
     * 突破后的第一次回踩确认。
     *
     * 参数说明：
     * - breakoutLevel：之前确认突破的关键价格位
     * - targetPrice：沿用突破信号时的目标位，可为空
     * - bullishBreakout：true 表示向上突破后的做多回踩，false 表示向下跌破后的做空反抽
     *
     * 中文理解：
     * - 真突破之后，市场经常会回头测试突破位；
     * - 如果这个位置守住了，就说明原来的边界发生了角色转换；
     * - 这类二次确认往往比“第一根突破K线直接追”更稳。
     */
    public Optional<AlertSignal> evaluateBreakoutPullback(List<BinanceKlineDTO> klines,
                                                          BigDecimal breakoutLevel,
                                                          BigDecimal targetPrice,
                                                          boolean bullishBreakout,
                                                          StrategySettings settings) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        if (!StrategySupport.hasEnoughBars(closedKlines, StrategySupport.minimumBarsRequired(settings))) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = StrategySupport.last(closedKlines);
        BigDecimal latestLow = StrategySupport.valueOf(latest.getLow());
        BigDecimal latestHigh = StrategySupport.valueOf(latest.getHigh());
        BigDecimal latestClose = StrategySupport.valueOf(latest.getClose());
        BigDecimal latestOpen = StrategySupport.valueOf(latest.getOpen());

        BigDecimal averageVolume = StrategySupport.averageVolume(
                StrategySupport.trailingWindow(closedKlines, Math.min(10, settings.rangeLookback()), 1)
        );
        BigDecimal volumeRatio = StrategySupport.ratio(StrategySupport.volumeOf(latest), averageVolume);
        // 回踩阶段更偏好缩量，说明这只是技术性回测，而不是反向力量重新主导。
        if (volumeRatio.compareTo(settings.pullbackMaxVolumeRatio()) > 0) {
            return Optional.empty();
        }

        if (bullishBreakout) {
            BigDecimal touchCeiling = breakoutLevel.multiply(StrategySupport.ONE.add(settings.pullbackTouchTolerance()));
            BigDecimal holdFloor = breakoutLevel.multiply(StrategySupport.ONE.subtract(settings.pullbackHoldBuffer()));
            // 必须真正回踩到突破位附近，否则不算“回踩确认”。
            if (latestLow.compareTo(touchCeiling) > 0) {
                return Optional.empty();
            }
            // 回踩后不能有效跌回突破位下方，同时最好重新收阳，说明买盘仍在掌控。
            if (latestClose.compareTo(holdFloor) < 0 || latestClose.compareTo(latestOpen) <= 0) {
                return Optional.empty();
            }

            return Optional.of(new AlertSignal(
                    TradeDirection.LONG,
                    "BTC 突破后回踩确认做多",
                    latest,
                    PULLBACK_LONG_TYPE,
                    "价格在确认上破后第一次回踩突破位，并成功守住该区域，说明原阻力位正在转化为新的支撑位，是更稳健的顺势做多结构。",
                    breakoutLevel.setScale(2, RoundingMode.HALF_UP),
                    holdFloor.setScale(2, RoundingMode.HALF_UP),
                    StrategySupport.scaleOrNull(targetPrice),
                    volumeRatio.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        BigDecimal touchFloor = breakoutLevel.multiply(StrategySupport.ONE.subtract(settings.pullbackTouchTolerance()));
        BigDecimal holdCeiling = breakoutLevel.multiply(StrategySupport.ONE.add(settings.pullbackHoldBuffer()));
        // 空头场景要求反抽到跌破位附近，确认原支撑转阻力。
        if (latestHigh.compareTo(touchFloor) < 0) {
            return Optional.empty();
        }
        // 反抽不能重新站回跌破位上方，同时最好重新收阴，说明卖盘仍掌控局面。
        if (latestClose.compareTo(holdCeiling) > 0 || latestClose.compareTo(latestOpen) >= 0) {
            return Optional.empty();
        }

        return Optional.of(new AlertSignal(
                TradeDirection.SHORT,
                "BTC 跌破后反抽确认做空",
                latest,
                PULLBACK_SHORT_TYPE,
                "价格在确认跌破后第一次反抽到跌破位附近，但未能重新站回，说明原支撑位正在转化为新的阻力位，是更稳健的顺势做空结构。",
                breakoutLevel.setScale(2, RoundingMode.HALF_UP),
                holdCeiling.setScale(2, RoundingMode.HALF_UP),
                StrategySupport.scaleOrNull(targetPrice),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }
}
