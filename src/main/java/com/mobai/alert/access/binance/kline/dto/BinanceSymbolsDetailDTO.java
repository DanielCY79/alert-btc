package com.mobai.alert.access.binance.kline.dto;

/**
 * 鍗曚釜浜ゆ槗瀵规槑缁?DTO銆? */
public class BinanceSymbolsDetailDTO {

    private String symbol;

    /**
     * 浜ゆ槗鐘舵€侊紝渚嬪 TRADING銆?     */
    private String status;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

