package com.mobai.alert.strategy.breakout;

import com.mobai.alert.access.dto.BinanceKlineDTO;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import com.mobai.alert.strategy.shared.RangeContext;
import com.mobai.alert.strategy.shared.StrategySettings;
import com.mobai.alert.strategy.shared.StrategySupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 确认突破策略。
 *
 * 这类策略专门处理“区间真的被突破并被市场接受”的场景。
 * 它不是看到价格越过边界就算突破，而是要求同时满足：
 * 1. 收盘明确站到边界外；
 * 2. K线实体质量足够好；
 * 3. 收盘位置靠近突破方向一侧；
 * 4. 相对量能明显放大；
 * 5. 价格没有离边界拉得过远。
 *
 * 因此它比“碰一下就追”的突破逻辑更保守，但也更适合 BTC 这种假突破很多的市场。
 */
public class ConfirmedBreakoutStrategyEvaluator {

    private static final String BREAKOUT_LONG_TYPE = "CONFIRMED_BREAKOUT_LONG";
    private static final String BREAKOUT_SHORT_TYPE = "CONFIRMED_BREAKOUT_SHORT";

    /**
     * 区间向上确认突破。
     *
     * 中文理解：
     * - 区间上沿被有效站上；
     * - 不是影线刺破，而是收盘站上；
     * - 并且放量、实体够强，说明上破被市场接受。
     */
    public Optional<AlertSignal> evaluateTrendBreakout(List<BinanceKlineDTO> klines, StrategySettings settings) {
        return evaluateConfirmedBreakout(klines, true, settings);
    }

    /**
     * 区间向下确认跌破。
     *
     * 中文理解：
     * - 区间下沿被有效跌破；
     * - 收盘明确落到下沿之外；
     * - 并且伴随较强的实体和放量，说明市场接受向下扩展。
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

        // 当前K线必须明确收在边界外，不接受“只刺穿一下”的假动作。
        if (bullishBreakout && close.compareTo(closeThreshold) <= 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && close.compareTo(closeThreshold) >= 0) {
            return Optional.empty();
        }
        // 前一根不能已经提前站稳边界外，否则这里更像延续而不是首次确认突破。
        if (bullishBreakout && previousClose.compareTo(closeThreshold) > 0) {
            return Optional.empty();
        }
        if (!bullishBreakout && previousClose.compareTo(closeThreshold) < 0) {
            return Optional.empty();
        }
        // 实体占比要足够大，减少长影线、犹豫型K线带来的误判。
        if (StrategySupport.bodyRatio(latest).compareTo(settings.breakoutBodyRatioThreshold()) < 0) {
            return Optional.empty();
        }
        // 收盘位置必须偏向突破方向，避免表面突破、实际收得很虚。
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
        // 放量是市场“接受突破”的重要证据之一。
        if (volumeRatio.compareTo(settings.breakoutVolumeMultiplier()) < 0) {
            return Optional.empty();
        }

        BigDecimal extensionCap = bullishBreakout
                ? boundary.multiply(StrategySupport.ONE.add(settings.breakoutMaxExtension()))
                : boundary.multiply(StrategySupport.ONE.subtract(settings.breakoutMaxExtension()));
        // 如果已经远离突破位太多，再追进去通常不划算。
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
                ? String.format("成熟区间被向上有效突破，当前K线实体强、收盘高、并伴随 %.2fx 放量，说明市场正在接受向上扩展。", volumeRatio.setScale(2, RoundingMode.HALF_UP))
                : String.format("成熟区间被向下有效跌破，当前K线实体强、收盘弱、并伴随 %.2fx 放量，说明市场正在接受向下扩展。", volumeRatio.setScale(2, RoundingMode.HALF_UP));

        return Optional.of(new AlertSignal(
                bullishBreakout ? TradeDirection.LONG : TradeDirection.SHORT,
                bullishBreakout ? "BTC 区间确认突破做多" : "BTC 区间确认跌破做空",
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
