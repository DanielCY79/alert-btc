package com.mobai.alert.strategy.priceaction.breakout;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import com.mobai.alert.strategy.priceaction.shared.StrategySettings;
import com.mobai.alert.strategy.priceaction.shared.StrategySupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 突破后的 follow-through 确认器。
 * 只有市场继续接受突破，才把初始突破升级为可交易的成熟突破背景。
 */
public class BreakoutFollowThroughStrategyEvaluator {

    private static final String BREAKOUT_LONG_TYPE = "CONFIRMED_BREAKOUT_LONG";
    private static final String BREAKOUT_SHORT_TYPE = "CONFIRMED_BREAKOUT_SHORT";

    public Optional<AlertSignal> evaluateFollowThrough(List<BinanceKlineDTO> klines,
                                                       BigDecimal breakoutLevel,
                                                       BigDecimal invalidationPrice,
                                                       BigDecimal targetPrice,
                                                       boolean bullishBreakout,
                                                       StrategySettings settings) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        if (!StrategySupport.hasEnoughBars(closedKlines, StrategySupport.minimumBarsRequired(settings))) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = StrategySupport.last(closedKlines);
        BigDecimal close = StrategySupport.valueOf(latest.getClose());
        BigDecimal low = StrategySupport.valueOf(latest.getLow());
        BigDecimal high = StrategySupport.valueOf(latest.getHigh());
        BigDecimal holdThreshold = bullishBreakout
                ? breakoutLevel.multiply(StrategySupport.ONE.add(settings.breakoutFollowThroughCloseBuffer()))
                : breakoutLevel.multiply(StrategySupport.ONE.subtract(settings.breakoutFollowThroughCloseBuffer()));

        if (bullishBreakout) {
            if (close.compareTo(holdThreshold) <= 0) {
                return Optional.empty();
            }
            if (low.compareTo(invalidationPrice) <= 0) {
                return Optional.empty();
            }
            if (!isDirectionalFollowThrough(latest, true, settings)) {
                return Optional.empty();
            }
        } else {
            if (close.compareTo(holdThreshold) >= 0) {
                return Optional.empty();
            }
            if (high.compareTo(invalidationPrice) >= 0) {
                return Optional.empty();
            }
            if (!isDirectionalFollowThrough(latest, false, settings)) {
                return Optional.empty();
            }
        }

        BigDecimal averageVolume = StrategySupport.averageVolume(
                StrategySupport.trailingWindow(closedKlines, Math.min(10, settings.rangeLookback()), 1)
        );
        BigDecimal volumeRatio = StrategySupport.ratio(StrategySupport.volumeOf(latest), averageVolume);
        if (volumeRatio.compareTo(settings.breakoutFollowThroughMinVolumeRatio()) < 0) {
            return Optional.empty();
        }

        String summary = bullishBreakout
                ? String.format("突破后出现 follow-through，价格继续站稳突破位，量能维持在 %.2fx。", volumeRatio.setScale(2, RoundingMode.HALF_UP))
                : String.format("跌破后出现 follow-through，价格继续压在跌破位下方，量能维持在 %.2fx。", volumeRatio.setScale(2, RoundingMode.HALF_UP));

        return Optional.of(new AlertSignal(
                bullishBreakout ? TradeDirection.LONG : TradeDirection.SHORT,
                bullishBreakout ? "BTC follow-through 确认突破做多信号" : "BTC follow-through 确认跌破做空信号",
                latest,
                bullishBreakout ? BREAKOUT_LONG_TYPE : BREAKOUT_SHORT_TYPE,
                summary,
                breakoutLevel.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                StrategySupport.scaleOrNull(targetPrice),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    private boolean isDirectionalFollowThrough(BinanceKlineDTO latest,
                                               boolean bullishBreakout,
                                               StrategySettings settings) {
        BigDecimal bodyRatio = StrategySupport.bodyRatio(latest);
        BigDecimal closeLocation = StrategySupport.closeLocation(latest);
        if (bullishBreakout) {
            return StrategySupport.isBullish(latest)
                    && bodyRatio.compareTo(settings.breakoutFollowThroughMinBodyRatio()) >= 0
                    && closeLocation.compareTo(settings.breakoutFollowThroughMinCloseLocation()) >= 0;
        }
        BigDecimal shortCloseLocationFloor = StrategySupport.ONE.subtract(settings.breakoutFollowThroughMinCloseLocation());
        return StrategySupport.isBearish(latest)
                && bodyRatio.compareTo(settings.breakoutFollowThroughMinBodyRatio()) >= 0
                && closeLocation.compareTo(shortCloseLocationFloor) <= 0;
    }
}
