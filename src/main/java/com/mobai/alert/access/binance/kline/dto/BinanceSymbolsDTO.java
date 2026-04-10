package com.mobai.alert.access.binance.kline.dto;

import java.util.List;

/**
 * 浜ゆ槗瀵瑰垪琛?DTO銆? */
public class BinanceSymbolsDTO {

    private List<BinanceSymbolsDetailDTO> symbols;

    public List<BinanceSymbolsDetailDTO> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<BinanceSymbolsDetailDTO> symbols) {
        this.symbols = symbols;
    }
}

