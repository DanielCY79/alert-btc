package com.mobai.alert.strategy.pullback;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.shared.StrategySettings;
import com.mobai.alert.strategy.shared.StrategySupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 突破回踩策略。
 *
 * <p>在确认突破后，不立即追价，而是等待价格回踩关键位再判断是否继续顺势。</p>
 */
public class BreakoutPullbackStrategyEvaluator {

    private static final String PULLBACK_LONG_TYPE = "BREAKOUT_PULLBACK_LONG";
    private static final String PULLBACK_SHORT_TYPE = "BREAKOUT_PULLBACK_SHORT";

    /**
     * 评估突破后的回踩/回抽确认信号。
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

        // 回踩阶段如果成交量过大，往往说明抛压/承压过强，先不接。
        if (volumeRatio.compareTo(settings.pullbackMaxVolumeRatio()) > 0) {
            return Optional.empty();
        }

        if (bullishBreakout) {
            BigDecimal touchCeiling = breakoutLevel.multiply(StrategySupport.ONE.add(settings.pullbackTouchTolerance()));
            BigDecimal holdFloor = breakoutLevel.multiply(StrategySupport.ONE.subtract(settings.pullbackHoldBuffer()));
            if (latestLow.compareTo(touchCeiling) > 0) {
                return Optional.empty();
            }
            if (latestClose.compareTo(holdFloor) < 0 || latestClose.compareTo(latestOpen) <= 0) {
                return Optional.empty();
            }

            return Optional.of(new AlertSignal(
                    TradeDirection.LONG,
                    "BTC 突破回踩做多信号",
                    latest,
                    PULLBACK_LONG_TYPE,
                    "价格回踩突破位后守住支撑并重新收强，属于突破后的回踩确认做多。",
                    breakoutLevel.setScale(2, RoundingMode.HALF_UP),
                    holdFloor.setScale(2, RoundingMode.HALF_UP),
                    StrategySupport.scaleOrNull(targetPrice),
                    volumeRatio.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        BigDecimal touchFloor = breakoutLevel.multiply(StrategySupport.ONE.subtract(settings.pullbackTouchTolerance()));
        BigDecimal holdCeiling = breakoutLevel.multiply(StrategySupport.ONE.add(settings.pullbackHoldBuffer()));
        if (latestHigh.compareTo(touchFloor) < 0) {
            return Optional.empty();
        }
        if (latestClose.compareTo(holdCeiling) > 0 || latestClose.compareTo(latestOpen) >= 0) {
            return Optional.empty();
        }

        return Optional.of(new AlertSignal(
                TradeDirection.SHORT,
                "BTC 跌破回抽做空信号",
                latest,
                PULLBACK_SHORT_TYPE,
                "价格回抽跌破位后受阻转弱，属于跌破后的回抽确认做空。",
                breakoutLevel.setScale(2, RoundingMode.HALF_UP),
                holdCeiling.setScale(2, RoundingMode.HALF_UP),
                StrategySupport.scaleOrNull(targetPrice),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }
}
