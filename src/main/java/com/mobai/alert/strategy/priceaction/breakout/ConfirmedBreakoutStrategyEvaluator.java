package com.mobai.alert.strategy.priceaction.breakout;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import com.mobai.alert.strategy.priceaction.shared.RangeContext;
import com.mobai.alert.strategy.priceaction.shared.StrategySettings;
import com.mobai.alert.strategy.priceaction.shared.StrategySupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 确认突破策略。
 *
 * <p>这类信号要求价格、K线形态和成交量同时确认，尽量过滤掉刚碰边界就回落的假突破。</p>
 */
public class ConfirmedBreakoutStrategyEvaluator {

    private static final String BREAKOUT_LONG_TYPE = "CONFIRMED_BREAKOUT_LONG";
    private static final String BREAKOUT_SHORT_TYPE = "CONFIRMED_BREAKOUT_SHORT";

    /**
     * 评估向上确认突破信号。
     */
    public Optional<AlertSignal> evaluateTrendBreakout(List<BinanceKlineDTO> klines, StrategySettings settings) {
        return evaluateConfirmedBreakout(klines, true, settings);
    }

    /**
     * 评估向下确认跌破信号。
     */
    public Optional<AlertSignal> evaluateTrendBreakdown(List<BinanceKlineDTO> klines, StrategySettings settings) {
        return evaluateConfirmedBreakout(klines, false, settings);
    }

    private Optional<AlertSignal> evaluateConfirmedBreakout(List<BinanceKlineDTO> klines,
                                                            boolean bullishBreakout,
                                                            StrategySettings settings) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        if (!StrategySupport.hasEnoughBars(closedKlines, StrategySupport.minimumBarsRequired(settings))) {
            return Optional.empty();
        }

        RangeContext range = StrategySupport.buildRangeContext(closedKlines, settings);
        if (range == null) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = StrategySupport.last(closedKlines);
        BinanceKlineDTO previous = closedKlines.get(closedKlines.size() - 2);
        BigDecimal close = StrategySupport.valueOf(latest.getClose());
        BigDecimal previousClose = StrategySupport.valueOf(previous.getClose());
        BigDecimal boundary = bullishBreakout ? range.resistance() : range.support();
        BigDecimal closeThreshold = bullishBreakout
                ? boundary.multiply(StrategySupport.ONE.add(settings.breakoutCloseBuffer()))
                : boundary.multiply(StrategySupport.ONE.subtract(settings.breakoutCloseBuffer()));

        // 最新收盘必须真正站上或跌破边界缓冲。
        if (bullishBreakout && close.compareTo(closeThreshold) <= 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && close.compareTo(closeThreshold) >= 0) {
            return Optional.empty();
        }

        // 前一根K线仍应在区间内，避免连续多根都在边界外的追价信号。
        if (bullishBreakout && previousClose.compareTo(closeThreshold) > 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && previousClose.compareTo(closeThreshold) < 0) {
            return Optional.empty();
        }

        // 实体占比不够，通常说明突破力度不够坚决。
        if (StrategySupport.bodyRatio(latest).compareTo(settings.breakoutBodyRatioThreshold()) < 0) {
            return Optional.empty();
        }

        // 收盘位置要尽量靠近突破方向的一侧。
        if (bullishBreakout && StrategySupport.closeLocation(latest).compareTo(new BigDecimal("0.65")) < 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && StrategySupport.closeLocation(latest).compareTo(new BigDecimal("0.35")) > 0) {
            return Optional.empty();
        }
        if (bullishBreakout && !StrategySupport.isBullish(latest)) {
            return Optional.empty();
        }
        if (!bullishBreakout && !StrategySupport.isBearish(latest)) {
            return Optional.empty();
        }

        BigDecimal averageVolume = StrategySupport.averageVolume(range.window());
        BigDecimal volumeRatio = StrategySupport.ratio(StrategySupport.volumeOf(latest), averageVolume);
        if (volumeRatio.compareTo(settings.breakoutVolumeMultiplier()) < 0) {
            return Optional.empty();
        }

        // 避免离边界过远的过度延伸追涨或追空。
        BigDecimal extensionCap = bullishBreakout
                ? boundary.multiply(StrategySupport.ONE.add(settings.breakoutMaxExtension()))
                : boundary.multiply(StrategySupport.ONE.subtract(settings.breakoutMaxExtension()));
        if (bullishBreakout && close.compareTo(extensionCap) > 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && close.compareTo(extensionCap) < 0) {
            return Optional.empty();
        }

        BigDecimal invalidationPrice = bullishBreakout
                ? boundary.multiply(StrategySupport.ONE.subtract(settings.breakoutFailureBuffer()))
                : boundary.multiply(StrategySupport.ONE.add(settings.breakoutFailureBuffer()));
        BigDecimal measuredMoveTarget = bullishBreakout
                ? range.resistance().add(range.resistance().subtract(range.support()))
                : range.support().subtract(range.resistance().subtract(range.support()));
        String type = bullishBreakout ? BREAKOUT_LONG_TYPE : BREAKOUT_SHORT_TYPE;
        String summary = bullishBreakout
                ? String.format("价格向上有效突破区间，成交量放大至 %.2fx，属于确认型突破。", volumeRatio.setScale(2, RoundingMode.HALF_UP))
                : String.format("价格向下有效跌破区间，成交量放大至 %.2fx，属于确认型跌破。", volumeRatio.setScale(2, RoundingMode.HALF_UP));

        return Optional.of(new AlertSignal(
                bullishBreakout ? TradeDirection.LONG : TradeDirection.SHORT,
                bullishBreakout ? "BTC 确认突破做多信号" : "BTC 确认跌破做空信号",
                latest,
                type,
                summary,
                boundary.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                measuredMoveTarget.setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }
}
