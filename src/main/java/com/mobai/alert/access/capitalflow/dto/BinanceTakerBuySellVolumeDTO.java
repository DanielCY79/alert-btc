package com.mobai.alert.access.capitalflow.dto;

/**
 * Binance 主动买卖量统计对象。
 * 反映某个统计窗口内，主动买单与主动卖单成交量的对比关系，
 * 常用于衡量短线进攻性资金方向。
 */
public class BinanceTakerBuySellVolumeDTO {

    /**
     * 主动买卖量比值。
     */
    private String buySellRatio;

    /**
     * 主动买成交量。
     */
    private String buyVol;

    /**
     * 主动卖成交量。
     */
    private String sellVol;

    /**
     * 统计窗口对应时间戳。
     */
    private Long timestamp;

    public String getBuySellRatio() {
        return buySellRatio;
    }

    public void setBuySellRatio(String buySellRatio) {
        this.buySellRatio = buySellRatio;
    }

    public String getBuyVol() {
        return buyVol;
    }

    public void setBuyVol(String buyVol) {
        this.buyVol = buyVol;
    }

    public String getSellVol() {
        return sellVol;
    }

    public void setSellVol(String sellVol) {
        this.sellVol = sellVol;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
