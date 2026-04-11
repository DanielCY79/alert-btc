package com.mobai.alert.feature.model;

import java.math.BigDecimal;

/**
 * 价格特征对象。
 * 保存收益率、均线、波动率、量比、区间位置和突破强度等指标。
 */
public class PriceFeatures {
    /**
     * 特征时间戳。
     */
    private Long asOfTime;
    /**
     * 当前收盘价。
     */
    private BigDecimal closePrice;
    /**
     * 近 1 根收益率。
     */
    private BigDecimal return1Bar;
    /**
     * 近 3 根收益率。
     */
    private BigDecimal return3Bar;
    /**
     * 近 12 根收益率。
     */
    private BigDecimal return12Bar;
    /**
     * 快速均线。
     */
    private BigDecimal fastMa;
    /**
     * 慢速均线。
     */
    private BigDecimal slowMa;
    /**
     * 快慢均线价差占比。
     */
    private BigDecimal maSpreadPct;
    /**
     * ATR 相对收盘价比例。
     */
    private BigDecimal atrPct;
    /**
     * 当前量比。
     */
    private BigDecimal volumeRatio;
    /**
     * 实体占比。
     */
    private BigDecimal bodyRatio;
    /**
     * 上影线占比。
     */
    private BigDecimal upperWickRatio;
    /**
     * 下影线占比。
     */
    private BigDecimal lowerWickRatio;
    /**
     * 收盘位置。
     */
    private BigDecimal closeLocation;
    /**
     * 区间宽度占比。
     */
    private BigDecimal rangeWidthPct;
    /**
     * 收盘在区间中的位置。
     */
    private BigDecimal rangePosition;
    /**
     * 当前是否位于区间内部。
     */
    private Boolean insideRange;
    /**
     * 突破强度分数。
     */
    private BigDecimal breakoutStrengthScore;

    public Long getAsOfTime() {
        return asOfTime;
    }

    public void setAsOfTime(Long asOfTime) {
        this.asOfTime = asOfTime;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public BigDecimal getReturn1Bar() {
        return return1Bar;
    }

    public void setReturn1Bar(BigDecimal return1Bar) {
        this.return1Bar = return1Bar;
    }

    public BigDecimal getReturn3Bar() {
        return return3Bar;
    }

    public void setReturn3Bar(BigDecimal return3Bar) {
        this.return3Bar = return3Bar;
    }

    public BigDecimal getReturn12Bar() {
        return return12Bar;
    }

    public void setReturn12Bar(BigDecimal return12Bar) {
        this.return12Bar = return12Bar;
    }

    public BigDecimal getFastMa() {
        return fastMa;
    }

    public void setFastMa(BigDecimal fastMa) {
        this.fastMa = fastMa;
    }

    public BigDecimal getSlowMa() {
        return slowMa;
    }

    public void setSlowMa(BigDecimal slowMa) {
        this.slowMa = slowMa;
    }

    public BigDecimal getMaSpreadPct() {
        return maSpreadPct;
    }

    public void setMaSpreadPct(BigDecimal maSpreadPct) {
        this.maSpreadPct = maSpreadPct;
    }

    public BigDecimal getAtrPct() {
        return atrPct;
    }

    public void setAtrPct(BigDecimal atrPct) {
        this.atrPct = atrPct;
    }

    public BigDecimal getVolumeRatio() {
        return volumeRatio;
    }

    public void setVolumeRatio(BigDecimal volumeRatio) {
        this.volumeRatio = volumeRatio;
    }

    public BigDecimal getBodyRatio() {
        return bodyRatio;
    }

    public void setBodyRatio(BigDecimal bodyRatio) {
        this.bodyRatio = bodyRatio;
    }

    public BigDecimal getUpperWickRatio() {
        return upperWickRatio;
    }

    public void setUpperWickRatio(BigDecimal upperWickRatio) {
        this.upperWickRatio = upperWickRatio;
    }

    public BigDecimal getLowerWickRatio() {
        return lowerWickRatio;
    }

    public void setLowerWickRatio(BigDecimal lowerWickRatio) {
        this.lowerWickRatio = lowerWickRatio;
    }

    public BigDecimal getCloseLocation() {
        return closeLocation;
    }

    public void setCloseLocation(BigDecimal closeLocation) {
        this.closeLocation = closeLocation;
    }

    public BigDecimal getRangeWidthPct() {
        return rangeWidthPct;
    }

    public void setRangeWidthPct(BigDecimal rangeWidthPct) {
        this.rangeWidthPct = rangeWidthPct;
    }

    public BigDecimal getRangePosition() {
        return rangePosition;
    }

    public void setRangePosition(BigDecimal rangePosition) {
        this.rangePosition = rangePosition;
    }

    public Boolean getInsideRange() {
        return insideRange;
    }

    public void setInsideRange(Boolean insideRange) {
        this.insideRange = insideRange;
    }

    public BigDecimal getBreakoutStrengthScore() {
        return breakoutStrengthScore;
    }

    public void setBreakoutStrengthScore(BigDecimal breakoutStrengthScore) {
        this.breakoutStrengthScore = breakoutStrengthScore;
    }
}
