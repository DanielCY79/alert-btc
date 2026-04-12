package com.mobai.alert.backtest.model;

import java.math.BigDecimal;

/**
 * 回测阶段缓存的突破记忆。
 * 把初始突破、follow-through 确认和后续回踩串成一条状态链。
 */
public record BreakoutMemory(BigDecimal breakoutLevel,
                             BigDecimal invalidationPrice,
                             BigDecimal targetPrice,
                             long signalTime,
                             boolean bullish,
                             boolean followThroughConfirmed,
                             Long followThroughTime) {

    public BreakoutMemory confirm(long confirmationTime) {
        return new BreakoutMemory(
                breakoutLevel,
                invalidationPrice,
                targetPrice,
                signalTime,
                bullish,
                true,
                confirmationTime
        );
    }
}
