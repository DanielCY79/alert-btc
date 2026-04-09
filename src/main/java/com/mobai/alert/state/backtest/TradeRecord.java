package com.mobai.alert.state.backtest;

import com.mobai.alert.state.signal.TradeDirection;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
                          String exitReason) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

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
        this(signalType, direction, signalTime, entryTime, entryBarIndex, entryPrice, stopPrice, targetPrice, riskPerUnit, maxHoldingBars, null, null, null);
    }

    public TradeRecord close(long exitTime, BigDecimal exitPrice, String exitReason) {
        return new TradeRecord(signalType, direction, signalTime, entryTime, entryBarIndex, entryPrice, stopPrice, targetPrice, riskPerUnit, maxHoldingBars, exitTime, exitPrice, exitReason);
    }

    public TradeRecord forceClose(long exitTime, BigDecimal exitPrice, String exitReason) {
        return close(exitTime, exitPrice, exitReason);
    }

    public BigDecimal realizedR() {
        if (exitPrice == null) {
            return ZERO;
        }
        BigDecimal pnl = direction == TradeDirection.LONG
                ? exitPrice.subtract(entryPrice)
                : entryPrice.subtract(exitPrice);
        return pnl.divide(riskPerUnit, 8, RoundingMode.HALF_UP);
    }
}
