package com.mobai.alert.dto;

public class BinanceSymbolsDetailDTO {
    private String symbol;
    /**
     * 交易状态 TRADING-交易中
     */
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