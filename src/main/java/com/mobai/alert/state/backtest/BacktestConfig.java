package com.mobai.alert.state.backtest;

import java.math.BigDecimal;

public record BacktestConfig(String symbol,
                             String interval,
                             long startTime,
                             long endTime,
                             int fastPeriod,
                             int slowPeriod,
                             int rangeLookback,
                             BigDecimal rangeMinWidth,
                             BigDecimal rangeMaxWidth,
                             BigDecimal rangeEdgeTolerance,
                             int requiredEdgeTouches,
                             BigDecimal overlapThreshold,
                             int minOverlapBars,
                             BigDecimal maFlatThreshold,
                             BigDecimal breakoutCloseBuffer,
                             BigDecimal breakoutVolumeMultiplier,
                             BigDecimal breakoutBodyRatioThreshold,
                             BigDecimal breakoutMaxExtension,
                             BigDecimal breakoutFailureBuffer,
                             BigDecimal failureProbeBuffer,
                             BigDecimal failureReentryBuffer,
                             BigDecimal failureMinWickBodyRatio,
                             BigDecimal pullbackTouchTolerance,
                             BigDecimal pullbackHoldBuffer,
                             BigDecimal pullbackMaxVolumeRatio,
                             long breakoutRecordTtlMs,
                             int rangeHoldingBars,
                             int breakoutHoldingBars,
                             int pullbackHoldingBars,
                             BigDecimal fallbackTargetMultiple) {

    public BacktestConfig withRangeLookback(int value) {
        return new BacktestConfig(symbol, interval, startTime, endTime, fastPeriod, slowPeriod, value, rangeMinWidth, rangeMaxWidth,
                rangeEdgeTolerance, requiredEdgeTouches, overlapThreshold, minOverlapBars, maFlatThreshold, breakoutCloseBuffer,
                breakoutVolumeMultiplier, breakoutBodyRatioThreshold, breakoutMaxExtension, breakoutFailureBuffer, failureProbeBuffer,
                failureReentryBuffer, failureMinWickBodyRatio, pullbackTouchTolerance, pullbackHoldBuffer, pullbackMaxVolumeRatio,
                breakoutRecordTtlMs, rangeHoldingBars, breakoutHoldingBars, pullbackHoldingBars, fallbackTargetMultiple);
    }

    public BacktestConfig withBreakoutVolumeMultiplier(BigDecimal value) {
        return new BacktestConfig(symbol, interval, startTime, endTime, fastPeriod, slowPeriod, rangeLookback, rangeMinWidth, rangeMaxWidth,
                rangeEdgeTolerance, requiredEdgeTouches, overlapThreshold, minOverlapBars, maFlatThreshold, breakoutCloseBuffer,
                value, breakoutBodyRatioThreshold, breakoutMaxExtension, breakoutFailureBuffer, failureProbeBuffer,
                failureReentryBuffer, failureMinWickBodyRatio, pullbackTouchTolerance, pullbackHoldBuffer, pullbackMaxVolumeRatio,
                breakoutRecordTtlMs, rangeHoldingBars, breakoutHoldingBars, pullbackHoldingBars, fallbackTargetMultiple);
    }

    public BacktestConfig withBreakoutCloseBuffer(BigDecimal value) {
        return new BacktestConfig(symbol, interval, startTime, endTime, fastPeriod, slowPeriod, rangeLookback, rangeMinWidth, rangeMaxWidth,
                rangeEdgeTolerance, requiredEdgeTouches, overlapThreshold, minOverlapBars, maFlatThreshold, value,
                breakoutVolumeMultiplier, breakoutBodyRatioThreshold, breakoutMaxExtension, breakoutFailureBuffer, failureProbeBuffer,
                failureReentryBuffer, failureMinWickBodyRatio, pullbackTouchTolerance, pullbackHoldBuffer, pullbackMaxVolumeRatio,
                breakoutRecordTtlMs, rangeHoldingBars, breakoutHoldingBars, pullbackHoldingBars, fallbackTargetMultiple);
    }
}
