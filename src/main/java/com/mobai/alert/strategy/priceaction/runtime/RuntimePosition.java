package com.mobai.alert.strategy.priceaction.runtime;

import com.mobai.alert.strategy.model.TradeDirection;

import java.math.BigDecimal;

public final class RuntimePosition {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final String signalType;
    private final TradeDirection direction;
    private final long signalTime;
    private final BigDecimal entryPrice;
    private final BigDecimal initialStopPrice;
    private final BigDecimal targetPrice;
    private final BigDecimal riskPerUnit;
    private final BigDecimal scaleOutFraction;
    private final BigDecimal scaleOutPrice;
    private final BigDecimal trailingActivationPrice;
    private final BigDecimal trailingDistancePrice;
    private final int pyramidMaxAdds;
    private final BigDecimal pyramidTriggerPrice;
    private final BigDecimal pyramidAddFraction;

    private BigDecimal stopPrice;
    private BigDecimal remainingSize;
    private boolean scaleOutTaken;
    private int addOnsUsed;
    private boolean pendingAddOn;
    private boolean conflictReduced;
    private boolean conflictStopTightened;
    private boolean addOnsLocked;
    private BigDecimal highestHigh;
    private BigDecimal lowestLow;
    private long lastManagedBarEndTime;
    private int closedBarsSinceEntry;

    public RuntimePosition(String signalType,
                           TradeDirection direction,
                           long signalTime,
                           BigDecimal entryPrice,
                           BigDecimal stopPrice,
                           BigDecimal targetPrice) {
        this(
                signalType,
                direction,
                signalTime,
                entryPrice,
                stopPrice,
                targetPrice,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                0,
                ZERO,
                ZERO
        );
    }

    public RuntimePosition(String signalType,
                           TradeDirection direction,
                           long signalTime,
                           BigDecimal entryPrice,
                           BigDecimal stopPrice,
                           BigDecimal targetPrice,
                           BigDecimal scaleOutTriggerR,
                           BigDecimal scaleOutFraction,
                           BigDecimal trailingActivationR,
                           BigDecimal trailingDistanceR,
                           int pyramidMaxAdds,
                           BigDecimal pyramidTriggerR,
                           BigDecimal pyramidAddFraction) {
        this.signalType = signalType;
        this.direction = direction;
        this.signalTime = signalTime;
        this.entryPrice = entryPrice;
        this.initialStopPrice = stopPrice;
        this.targetPrice = targetPrice;
        this.riskPerUnit = entryPrice == null || stopPrice == null ? ZERO : entryPrice.subtract(stopPrice).abs();
        this.scaleOutFraction = clampFraction(scaleOutFraction);
        this.scaleOutPrice = directionalPrice(entryPrice, this.riskPerUnit.multiply(nonNegative(scaleOutTriggerR)), direction);
        this.trailingActivationPrice = directionalPrice(entryPrice, this.riskPerUnit.multiply(nonNegative(trailingActivationR)), direction);
        this.trailingDistancePrice = this.riskPerUnit.multiply(nonNegative(trailingDistanceR));
        this.pyramidMaxAdds = Math.max(0, pyramidMaxAdds);
        this.pyramidTriggerPrice = directionalPrice(entryPrice, this.riskPerUnit.multiply(nonNegative(pyramidTriggerR)), direction);
        this.pyramidAddFraction = clampFraction(pyramidAddFraction);
        this.stopPrice = stopPrice;
        this.remainingSize = ONE;
        this.highestHigh = entryPrice;
        this.lowestLow = entryPrice;
        this.lastManagedBarEndTime = signalTime;
        this.closedBarsSinceEntry = 0;
    }

    public String signalType() {
        return signalType;
    }

    public TradeDirection direction() {
        return direction;
    }

    public long signalTime() {
        return signalTime;
    }

    public BigDecimal entryPrice() {
        return entryPrice;
    }

    public BigDecimal initialStopPrice() {
        return initialStopPrice;
    }

    public BigDecimal targetPrice() {
        return targetPrice;
    }

    public BigDecimal riskPerUnit() {
        return riskPerUnit;
    }

    public BigDecimal stopPrice() {
        return stopPrice;
    }

    public BigDecimal remainingSize() {
        return remainingSize;
    }

    public boolean scaleOutTaken() {
        return scaleOutTaken;
    }

    public int addOnsUsed() {
        return addOnsUsed;
    }

    public boolean pendingAddOn() {
        return pendingAddOn;
    }

    public boolean conflictReduced() {
        return conflictReduced;
    }

    public boolean conflictStopTightened() {
        return conflictStopTightened;
    }

    public boolean addOnsLocked() {
        return addOnsLocked;
    }

    public long lastManagedBarEndTime() {
        return lastManagedBarEndTime;
    }

    public int closedBarsSinceEntry() {
        return closedBarsSinceEntry;
    }

    public boolean shouldProcessClosedBar(long barEndTime) {
        return barEndTime > signalTime && barEndTime > lastManagedBarEndTime;
    }

    public boolean canScaleOut() {
        if (scaleOutTaken
                || direction == null
                || targetPrice == null
                || remainingSize.compareTo(ZERO) <= 0
                || scaleOutFraction.compareTo(ZERO) <= 0
                || riskPerUnit.compareTo(ZERO) <= 0) {
            return false;
        }
        return direction == TradeDirection.LONG
                ? scaleOutPrice.compareTo(entryPrice) > 0 && scaleOutPrice.compareTo(targetPrice) < 0
                : scaleOutPrice.compareTo(entryPrice) < 0 && scaleOutPrice.compareTo(targetPrice) > 0;
    }

    public BigDecimal scaleOutPrice() {
        return scaleOutPrice;
    }

    public BigDecimal scaleOutFraction() {
        return scaleOutFraction;
    }

    public BigDecimal applyScaleOut() {
        BigDecimal previousSize = remainingSize;
        BigDecimal sizeToClose = previousSize.multiply(scaleOutFraction);
        BigDecimal remaining = previousSize.subtract(sizeToClose);
        remainingSize = remaining.compareTo(ZERO) < 0 ? ZERO : remaining;
        scaleOutTaken = true;
        if (direction == TradeDirection.LONG && stopPrice.compareTo(entryPrice) < 0) {
            stopPrice = entryPrice;
        }
        if (direction == TradeDirection.SHORT && stopPrice.compareTo(entryPrice) > 0) {
            stopPrice = entryPrice;
        }
        return previousSize.subtract(remainingSize);
    }

    public boolean canConflictReduce() {
        return !conflictReduced
                && remainingSize.compareTo(ZERO) > 0
                && scaleOutFraction.compareTo(ZERO) > 0;
    }

    public BigDecimal applyConflictReduce() {
        if (!canConflictReduce()) {
            return ZERO;
        }
        lockAddOns();
        BigDecimal previousSize = remainingSize;
        BigDecimal sizeToClose = previousSize.multiply(scaleOutFraction);
        BigDecimal remaining = previousSize.subtract(sizeToClose);
        remainingSize = remaining.compareTo(ZERO) < 0 ? ZERO : remaining;
        conflictReduced = true;
        return previousSize.subtract(remainingSize);
    }

    public boolean canTightenConflictStop() {
        return !conflictStopTightened
                && remainingSize.compareTo(ZERO) > 0
                && direction != null
                && entryPrice != null
                && stopPrice != null
                && !hasProtectedStop();
    }

    public BigDecimal conflictTightenedStopPrice() {
        if (direction == null || entryPrice == null || stopPrice == null) {
            return stopPrice;
        }
        return direction == TradeDirection.LONG
                ? stopPrice.max(entryPrice)
                : stopPrice.min(entryPrice);
    }

    public boolean tightenStopForConflict() {
        if (!canTightenConflictStop()) {
            return false;
        }
        lockAddOns();
        stopPrice = conflictTightenedStopPrice();
        conflictStopTightened = true;
        return true;
    }

    public void prepareForConflictExit() {
        lockAddOns();
    }

    public void activatePendingAddOn(BigDecimal openPrice) {
        if (!pendingAddOn
                || addOnsLocked
                || addOnsUsed >= pyramidMaxAdds
                || pyramidAddFraction.compareTo(ZERO) <= 0
                || openPrice == null) {
            return;
        }
        remainingSize = remainingSize.add(pyramidAddFraction);
        addOnsUsed++;
        pendingAddOn = false;
    }

    public void updateAfterClosedBar(BigDecimal high, BigDecimal low, BigDecimal close, long barEndTime) {
        if (high != null && (highestHigh == null || high.compareTo(highestHigh) > 0)) {
            highestHigh = high;
        }
        if (low != null && (lowestLow == null || low.compareTo(lowestLow) < 0)) {
            lowestLow = low;
        }
        updateTrailingStop();
        scheduleAddOnIfNeeded(close);
        lastManagedBarEndTime = barEndTime;
        closedBarsSinceEntry++;
    }

    public boolean isTrailingStopActive() {
        if (direction == null || entryPrice == null || stopPrice == null) {
            return false;
        }
        return direction == TradeDirection.LONG
                ? stopPrice.compareTo(entryPrice) > 0
                : stopPrice.compareTo(entryPrice) < 0;
    }

    public boolean hasProtectedStop() {
        if (direction == null || entryPrice == null || stopPrice == null) {
            return false;
        }
        return direction == TradeDirection.LONG
                ? stopPrice.compareTo(entryPrice) >= 0
                : stopPrice.compareTo(entryPrice) <= 0;
    }

    public BigDecimal trailingActivationPrice() {
        return trailingActivationPrice;
    }

    private void updateTrailingStop() {
        if (direction == null || trailingDistancePrice.compareTo(ZERO) <= 0) {
            return;
        }
        if (direction == TradeDirection.LONG && highestHigh != null && highestHigh.compareTo(trailingActivationPrice) >= 0) {
            BigDecimal candidateStop = highestHigh.subtract(trailingDistancePrice);
            if (candidateStop.compareTo(stopPrice) > 0) {
                stopPrice = candidateStop;
            }
        }
        if (direction == TradeDirection.SHORT && lowestLow != null && lowestLow.compareTo(trailingActivationPrice) <= 0) {
            BigDecimal candidateStop = lowestLow.add(trailingDistancePrice);
            if (candidateStop.compareTo(stopPrice) < 0) {
                stopPrice = candidateStop;
            }
        }
    }

    private void scheduleAddOnIfNeeded(BigDecimal close) {
        if (pendingAddOn
                || addOnsLocked
                || close == null
                || addOnsUsed >= pyramidMaxAdds
                || pyramidAddFraction.compareTo(ZERO) <= 0
                || !scaleOutTaken
                || !hasProtectedStop()) {
            return;
        }
        if (direction == TradeDirection.LONG && close.compareTo(pyramidTriggerPrice) >= 0) {
            pendingAddOn = true;
        }
        if (direction == TradeDirection.SHORT && close.compareTo(pyramidTriggerPrice) <= 0) {
            pendingAddOn = true;
        }
    }

    private static BigDecimal directionalPrice(BigDecimal basePrice, BigDecimal distance, TradeDirection direction) {
        if (basePrice == null || distance == null || direction == null) {
            return basePrice;
        }
        return direction == TradeDirection.LONG
                ? basePrice.add(distance)
                : basePrice.subtract(distance);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) < 0) {
            return ZERO;
        }
        return value;
    }

    private static BigDecimal clampFraction(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        if (value.compareTo(ONE) > 0) {
            return ONE;
        }
        return value;
    }

    private void lockAddOns() {
        pendingAddOn = false;
        addOnsLocked = true;
    }
}
