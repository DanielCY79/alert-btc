package com.mobai.alert.strategy.delphi2;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.delphi2.shared.Delphi2Support;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(value = "monitoring.strategy.type", havingValue = "delphi2")
public class Delphi2SignalEvaluator {

    @Value("${monitoring.strategy.delphi.trend-fast-period:20}")
    private int trendFastPeriod;

    @Value("${monitoring.strategy.delphi.trend-slow-period:50}")
    private int trendSlowPeriod;

    @Value("${monitoring.strategy.delphi.entry-breakout-lookback:10}")
    private int entryBreakoutLookback;

    @Value("${monitoring.strategy.delphi.entry-atr-period:20}")
    private int entryAtrPeriod;

    @Value("${monitoring.strategy.delphi.entry-channel-factor:0.50}")
    private BigDecimal entryChannelFactor = new BigDecimal("0.50");

    @Value("${monitoring.strategy.delphi.stop-loss-atr-multiplier:2.0}")
    private BigDecimal stopLossAtrMultiplier = new BigDecimal("2.0");

    @Value("${monitoring.strategy.delphi.trailing.activation-atr-multiple:4.0}")
    private BigDecimal trailingActivationAtrMultiple = new BigDecimal("4.0");

    @Value("${monitoring.strategy.delphi.trailing.distance-atr-multiple:1.5}")
    private BigDecimal trailingDistanceAtrMultiple = new BigDecimal("1.5");

    @Value("${monitoring.strategy.delphi.volume-lookback:20}")
    private int volumeLookback;

    public Optional<AlertSignal> evaluateEntry(List<BinanceKlineDTO> closedEntryKlines,
                                               List<BinanceKlineDTO> closedTrendKlines) {
        if (!hasEnoughBars(closedEntryKlines, closedTrendKlines)) {
            return Optional.empty();
        }

        TradeDirection trendDirection = currentTrendDirection(closedTrendKlines);
        if (trendDirection == null) {
            return Optional.empty();
        }

        List<BinanceKlineDTO> breakoutWindow = Delphi2Support.trailingWindow(closedEntryKlines, entryBreakoutLookback, 1);
        if (CollectionUtils.isEmpty(breakoutWindow) || breakoutWindow.size() < entryBreakoutLookback) {
            return Optional.empty();
        }

        BinanceKlineDTO signalBar = Delphi2Support.last(closedEntryKlines);
        BigDecimal close = Delphi2Support.valueOf(signalBar.getClose());
        BigDecimal atr = currentAtr(closedEntryKlines);
        if (atr == null || atr.compareTo(Delphi2Support.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal channelRange = atr.multiply(entryChannelFactor);
        BigDecimal upperBand = Delphi2Support.highestHigh(breakoutWindow).add(channelRange);
        BigDecimal lowerBand = Delphi2Support.lowestLow(breakoutWindow).subtract(channelRange);
        BigDecimal stopPrice = trendDirection == TradeDirection.LONG
                ? close.subtract(atr.multiply(stopLossAtrMultiplier))
                : close.add(atr.multiply(stopLossAtrMultiplier));
        BigDecimal volumeRatio = Delphi2Support.scaleOrNull(Delphi2Support.volumeRatio(closedEntryKlines, volumeLookback));
        String contextComment = buildEntryContext(closedTrendKlines, atr, upperBand, lowerBand);

        if (trendDirection == TradeDirection.LONG && close.compareTo(upperBand) > 0) {
            return Optional.of(new AlertSignal(
                    TradeDirection.LONG,
                    "Delphi II Aggressive 多头突破",
                    signalBar,
                    "DELPHI2_AGGRESSIVE_LONG_ENTRY",
                    "日线趋势已经偏多，1 小时价格向上突破 ATR 扩展后的动态通道，按趋势启动信号处理。",
                    Delphi2Support.scaleOrNull(close),
                    Delphi2Support.scaleOrNull(stopPrice),
                    null,
                    volumeRatio,
                    null,
                    contextComment,
                    Delphi2Support.scaleOrNull(close),
                    Delphi2Support.scaleOrNull(stopPrice)
            ));
        }
        if (trendDirection == TradeDirection.SHORT && close.compareTo(lowerBand) < 0) {
            return Optional.of(new AlertSignal(
                    TradeDirection.SHORT,
                    "Delphi II Aggressive 空头突破",
                    signalBar,
                    "DELPHI2_AGGRESSIVE_SHORT_ENTRY",
                    "日线趋势已经偏空，1 小时价格向下跌破 ATR 扩展后的动态通道，按趋势启动信号处理。",
                    Delphi2Support.scaleOrNull(close),
                    Delphi2Support.scaleOrNull(stopPrice),
                    null,
                    volumeRatio,
                    null,
                    contextComment,
                    Delphi2Support.scaleOrNull(close),
                    Delphi2Support.scaleOrNull(stopPrice)
            ));
        }
        return Optional.empty();
    }

    public TradeDirection currentTrendDirection(List<BinanceKlineDTO> closedTrendKlines) {
        if (closedTrendKlines == null || closedTrendKlines.size() < trendSlowPeriod) {
            return null;
        }
        BigDecimal fastEma = Delphi2Support.ema(closedTrendKlines, trendFastPeriod, 0);
        BigDecimal slowEma = Delphi2Support.ema(closedTrendKlines, trendSlowPeriod, 0);
        BigDecimal close = Delphi2Support.valueOf(Delphi2Support.last(closedTrendKlines).getClose());
        if (fastEma == null || slowEma == null) {
            return null;
        }
        if (close.compareTo(slowEma) > 0 && fastEma.compareTo(slowEma) > 0) {
            return TradeDirection.LONG;
        }
        if (close.compareTo(slowEma) < 0 && fastEma.compareTo(slowEma) < 0) {
            return TradeDirection.SHORT;
        }
        return null;
    }

    public boolean hasTrendReversed(List<BinanceKlineDTO> closedTrendKlines, TradeDirection direction) {
        if (direction == null || closedTrendKlines == null || closedTrendKlines.size() < trendSlowPeriod + 1) {
            return false;
        }
        BigDecimal currentFast = Delphi2Support.ema(closedTrendKlines, trendFastPeriod, 0);
        BigDecimal currentSlow = Delphi2Support.ema(closedTrendKlines, trendSlowPeriod, 0);
        BigDecimal previousFast = Delphi2Support.ema(closedTrendKlines, trendFastPeriod, 1);
        BigDecimal previousSlow = Delphi2Support.ema(closedTrendKlines, trendSlowPeriod, 1);
        if (currentFast == null || currentSlow == null || previousFast == null || previousSlow == null) {
            return false;
        }
        if (direction == TradeDirection.LONG) {
            return previousFast.compareTo(previousSlow) > 0 && currentFast.compareTo(currentSlow) <= 0;
        }
        return previousFast.compareTo(previousSlow) < 0 && currentFast.compareTo(currentSlow) >= 0;
    }

    public AlertSignal buildTrendExitSignal(BinanceKlineDTO referenceBar,
                                            TradeDirection direction,
                                            BigDecimal referenceEntryPrice,
                                            BigDecimal referenceStopPrice) {
        return new AlertSignal(
                direction,
                direction == TradeDirection.LONG ? "Delphi II 日线反转离场" : "Delphi II 日线反转回补",
                referenceBar,
                direction == TradeDirection.LONG
                        ? "EXIT_DELPHI2_DAILY_REVERSAL_LONG"
                        : "EXIT_DELPHI2_DAILY_REVERSAL_SHORT",
                "日线 EMA20 与 EMA50 已经出现反向交叉，按系统规则应无条件结束当前趋势仓位。",
                Delphi2Support.scaleOrNull(Delphi2Support.valueOf(referenceBar.getClose())),
                Delphi2Support.scaleOrNull(referenceStopPrice),
                null,
                null,
                null,
                "dailyTrendExit=true | exitOn=EMA20/EMA50 reverse cross",
                Delphi2Support.scaleOrNull(referenceEntryPrice),
                Delphi2Support.scaleOrNull(referenceStopPrice)
        );
    }

    public BigDecimal currentAtr(List<BinanceKlineDTO> closedEntryKlines) {
        return Delphi2Support.atr(closedEntryKlines, entryAtrPeriod, 0);
    }

    public BigDecimal trailingActivationAtrMultiple() {
        return trailingActivationAtrMultiple;
    }

    public BigDecimal trailingDistanceAtrMultiple() {
        return trailingDistanceAtrMultiple;
    }

    private boolean hasEnoughBars(List<BinanceKlineDTO> closedEntryKlines, List<BinanceKlineDTO> closedTrendKlines) {
        return !CollectionUtils.isEmpty(closedEntryKlines)
                && !CollectionUtils.isEmpty(closedTrendKlines)
                && closedEntryKlines.size() >= Math.max(entryAtrPeriod, entryBreakoutLookback + 1)
                && closedTrendKlines.size() >= trendSlowPeriod;
    }

    private String buildEntryContext(List<BinanceKlineDTO> closedTrendKlines,
                                     BigDecimal atr,
                                     BigDecimal upperBand,
                                     BigDecimal lowerBand) {
        BigDecimal dailyFast = Delphi2Support.ema(closedTrendKlines, trendFastPeriod, 0);
        BigDecimal dailySlow = Delphi2Support.ema(closedTrendKlines, trendSlowPeriod, 0);
        BigDecimal dailyClose = Delphi2Support.valueOf(Delphi2Support.last(closedTrendKlines).getClose());
        return "dailyClose=" + scaled(dailyClose)
                + " | dailyEMA20=" + scaled(dailyFast)
                + " | dailyEMA50=" + scaled(dailySlow)
                + " | atr20=" + scaled(atr)
                + " | upperBand=" + scaled(upperBand)
                + " | lowerBand=" + scaled(lowerBand)
                + " | channelFactor=" + entryChannelFactor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String scaled(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
