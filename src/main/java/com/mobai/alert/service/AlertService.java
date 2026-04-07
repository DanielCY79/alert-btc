package com.mobai.alert.service;

import com.mobai.alert.dto.BinanceSymbolsDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private static final String TARGET_SYMBOL = "BTCUSDT";
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertSymbolCacheService alertSymbolCacheService;
    private final AlertSymbolProcessor alertSymbolProcessor;

    public AlertService(AlertSymbolCacheService alertSymbolCacheService, AlertSymbolProcessor alertSymbolProcessor) {
        this.alertSymbolCacheService = alertSymbolCacheService;
        this.alertSymbolProcessor = alertSymbolProcessor;
    }

    /**
     * 定时调度入口，只负责拉取交易对列表并分发给处理器。
     */
    @Scheduled(fixedRate = 60000)
    public void monitoring() {
        System.out.println("Monitoring started " + LOG_TIME_FORMATTER.format(LocalDateTime.now()));

        BinanceSymbolsDTO symbolsDTO;
        try {
            symbolsDTO = alertSymbolCacheService.loadSymbols();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (symbolsDTO == null || CollectionUtils.isEmpty(symbolsDTO.getSymbols())) {
            return;
        }

        List<BinanceSymbolsDetailDTO> targetSymbols = symbolsDTO.getSymbols().stream()
                .filter(symbol -> TARGET_SYMBOL.equals(symbol.getSymbol()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(targetSymbols)) {
            return;
        }

        processSymbols(targetSymbols, 1);
        System.out.println("Monitoring finished " + LOG_TIME_FORMATTER.format(LocalDateTime.now()));
    }

    private void processSymbols(List<BinanceSymbolsDetailDTO> symbols, int threadCount) {
        // 线程数不超过交易对数量，避免空转线程。
        int poolSize = Math.max(1, Math.min(threadCount, symbols.size()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            List<CompletableFuture<Void>> futures = symbols.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> alertSymbolProcessor.process(symbol), executor)
                            .exceptionally(ex -> {
                                ex.printStackTrace();
                                return null;
                            }))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }
}
