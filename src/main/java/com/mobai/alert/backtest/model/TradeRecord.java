package com.mobai.alert.backtest.model;

import com.mobai.alert.strategy.model.TradeDirection;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单笔回测交易记录。
 * 从开仓信号、入场、止损止盈到退出原因都集中保存在这里。
 */
public record TradeRecord(String signalType,
                          TradeDirection direction,
                          long signalTime,
                          long entryTime,
                          int entryBarIndex,
                          BigDecimal entryPrice,
                          BigDecimal stopPrice,
                          BigDecimal targetPrice,
                          BigDecimal riskPerUnit,
                          int maxHoldingBars,
                          Long exitTime,
                          BigDecimal exitPrice,
                          String exitReason,
                          BigDecimal realizedROverride) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * 创建未平仓的交易记录。
     */
    public TradeRecord(String signalType,
                       TradeDirection direction,
                       long signalTime,
                       long entryTime,
                       int entryBarIndex,
                       BigDecimal entryPrice,
                       BigDecimal stopPrice,
                       BigDecimal targetPrice,
                       BigDecimal riskPerUnit,
                       int maxHoldingBars) {
        this(signalType, direction, signalTime, entryTime, entryBarIndex, entryPrice, stopPrice, targetPrice, riskPerUnit, maxHoldingBars, null, null, null, null);
    }

    public TradeRecord close(long exitTime, BigDecimal exitPrice, String exitReason) {
        return new TradeRecord(signalType, direction, signalTime, entryTime, entryBarIndex, entryPrice, stopPrice, targetPrice, riskPerUnit, maxHoldingBars, exitTime, exitPrice, exitReason, null);
    }

    public TradeRecord closeManaged(long exitTime, BigDecimal exitPrice, String exitReason, BigDecimal realizedR) {
        return new TradeRecord(signalType, direction, signalTime, entryTime, entryBarIndex, entryPrice, stopPrice, targetPrice, riskPerUnit, maxHoldingBars, exitTime, exitPrice, exitReason, realizedR);
    }

    /**
     * 在回测尾部强制平仓。
     */
    public TradeRecord forceClose(long exitTime, BigDecimal exitPrice, String exitReason) {
        return close(exitTime, exitPrice, exitReason);
    }

    /**
     * 按风险单位计算本笔交易的实现收益。
     */
    public BigDecimal realizedR() {
        if (realizedROverride != null) {
            return realizedROverride;
        }
        if (exitPrice == null) {
            return ZERO;
        }
        BigDecimal pnl = direction == TradeDirection.LONG
                ? exitPrice.subtract(entryPrice)
                : entryPrice.subtract(exitPrice);
        return pnl.divide(riskPerUnit, 8, RoundingMode.HALF_UP);
    }

    public BigDecimal realizedReturnRatio() {
        if (exitPrice == null || entryPrice == null || entryPrice.compareTo(ZERO) == 0) {
            return ZERO;
        }
        BigDecimal pnl = direction == TradeDirection.LONG
                ? exitPrice.subtract(entryPrice)
                : entryPrice.subtract(exitPrice);
        return pnl.divide(entryPrice, 8, RoundingMode.HALF_UP);
    }

    public BigDecimal realizedPnl(BigDecimal positionCapital) {
        if (positionCapital == null) {
            return ZERO;
        }
        return positionCapital.multiply(realizedReturnRatio()).setScale(8, RoundingMode.HALF_UP);
    }
}
