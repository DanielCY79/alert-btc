package com.mobai.alert.access.binance.derivative.dto;

/**
 * 涓诲姩涔板崠閲?DTO銆? */
public class BinanceTakerBuySellVolumeDTO {

    private String buySellRatio;
    private String buyVol;
    private String sellVol;
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

