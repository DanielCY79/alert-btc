package com.mobai.alert.strategy.range;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
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
 * 区间失败策略。
 *
 * <p>核心思路是识别价格短暂跌破或上破区间边界后，又快速收回区间内部的反转机会。</p>
 */
public class RangeFailureStrategyEvaluator {

    private static final String RANGE_FAILURE_LONG_TYPE = "RANGE_FAILURE_LONG";
    private static final String RANGE_FAILURE_SHORT_TYPE = "RANGE_FAILURE_SHORT";

    /**
     * 评估区间假跌破后的反转做多信号。
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

        // 先真正跌破下沿，再要求收回区间内部。
        if (low.compareTo(support.multiply(StrategySupport.ONE.subtract(settings.failureProbeBuffer()))) > 0) {
            return Optional.empty();
        }
        if (close.compareTo(support.multiply(StrategySupport.ONE.add(settings.failureReentryBuffer()))) < 0) {
            return Optional.empty();
        }

        // 需要是明显的反转阳线，而不是弱反抽。
        if (!StrategySupport.isBullish(latest) || StrategySupport.closeLocation(latest).compareTo(new BigDecimal("0.60")) < 0) {
            return Optional.empty();
        }
        if (StrategySupport.lowerWick(latest).compareTo(StrategySupport.bodySize(latest).multiply(settings.failureMinWickBodyRatio())) < 0) {
            return Optional.empty();
        }
        if (close.compareTo(range.midpoint()) > 0) {
            return Optional.empty();
        }

        BigDecimal volumeRatio = StrategySupport.ratio(StrategySupport.volumeOf(latest), StrategySupport.averageVolume(range.window()));
        BigDecimal invalidationPrice = low.multiply(StrategySupport.ONE.subtract(settings.failureReentryBuffer()));
        return Optional.of(new AlertSignal(
                TradeDirection.LONG,
                "BTC 区间假跌破做多信号",
                latest,
                RANGE_FAILURE_LONG_TYPE,
                "价格短暂跌破区间下沿后迅速收回，形成区间假跌破反转做多。",
                support.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                range.midpoint().setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }

    /**
     * 评估区间假突破后的反转做空信号。
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

        if (high.compareTo(resistance.multiply(StrategySupport.ONE.add(settings.failureProbeBuffer()))) < 0) {
            return Optional.empty();
        }
        if (close.compareTo(resistance.multiply(StrategySupport.ONE.subtract(settings.failureReentryBuffer()))) > 0) {
            return Optional.empty();
        }
        if (!StrategySupport.isBearish(latest) || StrategySupport.closeLocation(latest).compareTo(new BigDecimal("0.40")) > 0) {
            return Optional.empty();
        }
        if (StrategySupport.upperWick(latest).compareTo(StrategySupport.bodySize(latest).multiply(settings.failureMinWickBodyRatio())) < 0) {
            return Optional.empty();
        }
        if (close.compareTo(range.midpoint()) < 0) {
            return Optional.empty();
        }

        BigDecimal volumeRatio = StrategySupport.ratio(StrategySupport.volumeOf(latest), StrategySupport.averageVolume(range.window()));
        BigDecimal invalidationPrice = high.multiply(StrategySupport.ONE.add(settings.failureReentryBuffer()));
        return Optional.of(new AlertSignal(
                TradeDirection.SHORT,
                "BTC 区间假突破做空信号",
                latest,
                RANGE_FAILURE_SHORT_TYPE,
                "价格短暂突破区间上沿后重新回落，形成区间假突破反转做空。",
                resistance.setScale(2, RoundingMode.HALF_UP),
                invalidationPrice.setScale(2, RoundingMode.HALF_UP),
                range.midpoint().setScale(2, RoundingMode.HALF_UP),
                volumeRatio.setScale(2, RoundingMode.HALF_UP)
        ));
    }
}
