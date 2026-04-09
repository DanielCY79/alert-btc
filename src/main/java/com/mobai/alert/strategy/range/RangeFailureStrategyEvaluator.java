package com.mobai.alert.strategy.range;

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
 * 区间失败突破策略。
 *
 * 这类策略的核心思想不是“追突破”，而是专门寻找：
 * 1. 价格短暂刺穿区间边界；
 * 2. 但收盘又重新回到区间内部；
 * 3. 且K线形态表现出明显拒绝。
 *
 * 换句话说，这里抓的是“突破失败后的反向机会”：
 * - 下破失败，偏向做多；
 * - 上破失败，偏向做空。
 *
 * 这类信号最适合出现在成熟区间的边缘，而不是趋势启动阶段。
 */
public class RangeFailureStrategyEvaluator {

    private static final String RANGE_FAILURE_LONG_TYPE = "RANGE_FAILURE_LONG";
    private static final String RANGE_FAILURE_SHORT_TYPE = "RANGE_FAILURE_SHORT";

    /**
     * 区间下沿假跌破后的做多信号。
     *
     * 中文理解：
     * - 市场先向下刺穿支撑，看起来像要跌破；
     * - 但最终又被买盘拉回区间；
     * - 如果K线下影线够长、收盘够强，就说明这次下破更像“扫止损”而不是真跌破。
     *
     * 这个信号的目标一般不是一口气看新高，
     * 而是先看区间中轴，属于典型的区间内反向交易。
     */
    public Optional<AlertSignal> evaluateRangeFailedBreakdownLong(List<BinanceKlineDTO> klines, StrategySettings settings) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        if (!StrategySupport.hasEnoughBars(closedKlines, StrategySupport.minimumBarsRequired(settings))) {
            return Optional.empty();
        }

        RangeContext range = StrategySupport.buildRangeContext(closedKlines, settings);
        if (range == null) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = StrategySupport.last(closedKlines);
        BigDecimal low = StrategySupport.valueOf(latest.getLow());
        BigDecimal close = StrategySupport.valueOf(latest.getClose());
        BigDecimal support = range.support();

        // 价格必须真正探到支撑下方，而不是只在边界附近震荡。
        if (low.compareTo(support.multiply(StrategySupport.ONE.subtract(settings.failureProbeBuffer()))) > 0) {
            return Optional.empty();
        }
        // 收盘要重新站回支撑上方，证明市场不接受这次下破。
        if (close.compareTo(support.multiply(StrategySupport.ONE.add(settings.failureReentryBuffer()))) < 0) {
            return Optional.empty();
        }
        // 需要阳线并且收盘靠近K线高位，说明回收动作足够强。
        if (!StrategySupport.isBullish(latest) || StrategySupport.closeLocation(latest).compareTo(new BigDecimal("0.60")) < 0) {
            return Optional.empty();
        }
        // 下影线要明显长于实体，才能体现“向下试错后被拒绝”。
        if (StrategySupport.lowerWick(latest).compareTo(StrategySupport.bodySize(latest).multiply(settings.failureMinWickBodyRatio())) < 0) {
            return Optional.empty();
        }
        // 如果已经一口气收回到区间中轴上方，反向交易的盈亏比通常会下降。
        if (close.compareTo(range.midpoint()) > 0) {
            return Optional.empty();
        }

        BigDecimal volumeRatio = StrategySupport.ratio(StrategySupport.volumeOf(latest), StrategySupport.averageVolume(range.window()));
        BigDecimal invalidationPrice = low.multiply(StrategySupport.ONE.subtract(settings.failureReentryBuffer()));
        return Optional.of(new AlertSignal(
                TradeDirection.LONG,
                "BTC 区间下破失败做多",
                latest,
                RANGE_FAILURE_LONG_TYPE,
                "区间下沿被短暂刺穿后迅速收回，价格重新回到区间内部，并留下明显下影线，说明这次下破更像流动性扫损而不是有效跌破。",
                support.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                range.midpoint().setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    /**
     * 区间上沿假突破后的做空信号。
     *
     * 中文理解：
     * - 市场先向上刺穿阻力，看起来像要突破；
     * - 但最终收盘又掉回区间内部；
     * - 如果K线上影线长、收盘偏弱，就说明上方抛压很重。
     *
     * 这个信号本质上是“上破失败后的反向回落”，
     * 常见于成熟区间顶部或者趋势衰竭后的横盘上沿。
     */
    public Optional<AlertSignal> evaluateRangeFailedBreakoutShort(List<BinanceKlineDTO> klines, StrategySettings settings) {
        List<BinanceKlineDTO> closedKlines = StrategySupport.closedKlines(klines);
        if (!StrategySupport.hasEnoughBars(closedKlines, StrategySupport.minimumBarsRequired(settings))) {
            return Optional.empty();
        }

        RangeContext range = StrategySupport.buildRangeContext(closedKlines, settings);
        if (range == null) {
            return Optional.empty();
        }

        BinanceKlineDTO latest = StrategySupport.last(closedKlines);
        BigDecimal high = StrategySupport.valueOf(latest.getHigh());
        BigDecimal close = StrategySupport.valueOf(latest.getClose());
        BigDecimal resistance = range.resistance();

        // 价格必须先探到阻力上方，才算出现“假突破”的前提。
        if (high.compareTo(resistance.multiply(StrategySupport.ONE.add(settings.failureProbeBuffer()))) < 0) {
            return Optional.empty();
        }
        // 收盘重新掉回阻力下方，证明上破没有被市场接受。
        if (close.compareTo(resistance.multiply(StrategySupport.ONE.subtract(settings.failureReentryBuffer()))) > 0) {
            return Optional.empty();
        }
        // 需要阴线并且收盘靠近K线低位，说明卖盘在收盘时仍占优。
        if (!StrategySupport.isBearish(latest) || StrategySupport.closeLocation(latest).compareTo(new BigDecimal("0.40")) > 0) {
            return Optional.empty();
        }
        // 上影线要足够长，表示价格向上试探后被明显打回。
        if (StrategySupport.upperWick(latest).compareTo(StrategySupport.bodySize(latest).multiply(settings.failureMinWickBodyRatio())) < 0) {
            return Optional.empty();
        }
        // 如果已经跌得太深，中轴以下再追空，区间内反向的盈亏比会变差。
        if (close.compareTo(range.midpoint()) < 0) {
            return Optional.empty();
        }

        BigDecimal volumeRatio = StrategySupport.ratio(StrategySupport.volumeOf(latest), StrategySupport.averageVolume(range.window()));
        BigDecimal invalidationPrice = high.multiply(StrategySupport.ONE.add(settings.failureReentryBuffer()));
        return Optional.of(new AlertSignal(
                TradeDirection.SHORT,
                "BTC 区间上破失败做空",
                latest,
                RANGE_FAILURE_SHORT_TYPE,
                "区间上沿被短暂刺穿后快速回落，价格重新收回区间内部，并留下明显上影线，说明这次上破更像诱多而不是有效突破。",
                resistance.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                range.midpoint().setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }
}
