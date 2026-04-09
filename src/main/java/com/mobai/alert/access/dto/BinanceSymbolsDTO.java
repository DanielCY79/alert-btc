package com.mobai.alert.access.dto;

import java.util.List;

public class BinanceSymbolsDTO {
    private List<BinanceSymbolsDetailDTO> symbols;

    public List<BinanceSymbolsDetailDTO> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<BinanceSymbolsDetailDTO> symbols) {
        this.symbols = symbols;
    }
}
