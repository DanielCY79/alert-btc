package com.mobai.alert.state.runtime;

import java.math.BigDecimal;

/**
 * 实时监控阶段缓存的突破记忆。
 * 用于区分“刚突破待确认”和“已被 follow-through 接受”的两个阶段。
 */
public record BreakoutRecord(BigDecimal breakoutLevel,
                             BigDecimal invalidationPrice,
                             BigDecimal targetPrice,
                             long breakoutTime,
                             boolean bullish,
                             boolean followThroughConfirmed,
                             Long followThroughTime) {

    public BreakoutRecord confirm(long confirmationTime) {
        return new BreakoutRecord(
                breakoutLevel,
                invalidationPrice,
                targetPrice,
                breakoutTime,
                bullish,
                true,
                confirmationTime
        );
    }
}
