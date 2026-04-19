package com.mobai.alert.strategy.delphi2.runtime;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.delphi2.shared.Delphi2Support;
import com.mobai.alert.strategy.model.TradeDirection;

import java.math.BigDecimal;

final class Delphi2RuntimePosition {

    private final String signalType;
    private final TradeDirection direction;
    private final long signalTime;
    private final BigDecimal entryPrice;
    private final BigDecimal entryAtr;
    private final BigDecimal initialStopPrice;
    private BigDecimal stopPrice;
    private BigDecimal highestHigh;
    private BigDecimal lowestLow;
    private boolean trailingActive;

    Delphi2RuntimePosition(String signalType,
                           TradeDirection direction,
                           long signalTime,
                           BigDecimal entryPrice,
                           BigDecimal entryAtr,
                           BigDecimal initialStopPrice) {
        this.signalType = signalType;
        this.direction = direction;
        this.signalTime = signalTime;
        this.entryPrice = entryPrice;
        this.entryAtr = entryAtr;
        this.initialStopPrice = initialStopPrice;
        this.stopPrice = initialStopPrice;
        this.highestHigh = entryPrice;
        this.lowestLow = entryPrice;
        this.trailingActive = false;
    }

    void updateAfterClosedBar(BinanceKlineDTO closedBar,
                              BigDecimal currentAtr,
                              BigDecimal trailingActivationAtrMultiple,
                              BigDecimal trailingDistanceAtrMultiple) {
        BigDecimal high = Delphi2Support.valueOf(closedBar.getHigh());
        BigDecimal low = Delphi2Support.valueOf(closedBar.getLow());
        if (high.compareTo(highestHigh) > 0) {
            highestHigh = high;
        }
        if (low.compareTo(lowestLow) < 0) {
            lowestLow = low;
        }
        BigDecimal favorableMove = direction == TradeDirection.LONG
                ? highestHigh.subtract(entryPrice)
                : entryPrice.subtract(lowestLow);
        if (!trailingActive
                && entryAtr != null
                && favorableMove.compareTo(entryAtr.multiply(trailingActivationAtrMultiple)) >= 0) {
            trailingActive = true;
        }
        if (!trailingActive || currentAtr == null || currentAtr.compareTo(Delphi2Support.ZERO) <= 0) {
            return;
        }
        BigDecimal candidate = direction == TradeDirection.LONG
                ? highestHigh.subtract(currentAtr.multiply(trailingDistanceAtrMultiple))
                : lowestLow.add(currentAtr.multiply(trailingDistanceAtrMultiple));
        if (direction == TradeDirection.LONG && candidate.compareTo(stopPrice) > 0) {
            stopPrice = candidate;
        }
        if (direction == TradeDirection.SHORT && candidate.compareTo(stopPrice) < 0) {
            stopPrice = candidate;
        }
    }

    String signalType() {
        return signalType;
    }

    TradeDirection direction() {
        return direction;
    }

    long signalTime() {
        return signalTime;
    }

    BigDecimal entryPrice() {
        return entryPrice;
    }

    BigDecimal initialStopPrice() {
        return initialStopPrice;
    }

    BigDecimal stopPrice() {
        return stopPrice;
    }

    boolean trailingActive() {
        return trailingActive;
    }
}
