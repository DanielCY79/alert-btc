package com.mobai.alert.strategy.shared;

import com.mobai.alert.access.dto.BinanceKlineDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 区间上下文对象。
 *
 * 这个类表示：
 * “在最近一段历史K线里，系统已经确认出了一个可交易区间”。
 *
 * 它把后续策略判断最关心的几个价格区域统一打包：
 * - window：用于定义这个区间的历史样本窗口
 * - support：区间下沿
 * - resistance：区间上沿
 * - midpoint：区间中轴
 * - width：区间宽度
 *
 * 只要某个策略需要基于“成熟区间”做判断，
 * 它通常都会先依赖这个对象。
 */
public record RangeContext(List<BinanceKlineDTO> window,
                           BigDecimal support,
                           BigDecimal resistance,
                           BigDecimal midpoint,
                           BigDecimal width) {
    /*
     * 从交易理解上：
     * - support / resistance 决定边缘交易与突破位置；
     * - midpoint 决定区间内反向交易的第一目标；
     * - width 既能用来过滤区间，也能衍生 measured move 目标。
     */
}
