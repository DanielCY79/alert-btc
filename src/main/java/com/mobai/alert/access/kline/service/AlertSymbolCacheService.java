package com.mobai.alert.access.kline.service;

import com.alibaba.fastjson.JSON;
import com.mobai.alert.access.kline.dto.BinanceSymbolsDTO;
import com.mobai.alert.access.BinanceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 监控交易对缓存服务。
 * 将 Binance 交易对列表缓存到本地文件，避免每轮调度都访问交易所接口。
 * 当前策略主要监控单一目标交易对，因此缓存更新频率按“天”控制已经足够。
 */
@Service
public class AlertSymbolCacheService {

    private static final Logger log = LoggerFactory.getLogger(AlertSymbolCacheService.class);

    private final BinanceApi binanceApi;
    private final Path cacheFilePath = Paths.get(System.getProperty("user.dir"), "symbolsCache.json");

    public AlertSymbolCacheService(BinanceApi binanceApi) {
        this.binanceApi = binanceApi;
    }

    /**
     * 加载交易对列表。
     * 如果本地缓存文件存在且是当天生成，则优先读取本地缓存；否则回源 Binance 重新刷新。
     *
     * @return 交易对列表 DTO
     * @throws IOException 读取或清理缓存文件时抛出
     */
    public BinanceSymbolsDTO loadSymbols() throws IOException {
        if (Files.exists(cacheFilePath) && Files.size(cacheFilePath) > 0) {
            BasicFileAttributes attrs = Files.readAttributes(cacheFilePath, BasicFileAttributes.class);
            LocalDateTime lastModifiedTime = attrs.lastModifiedTime()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            if (isToday(lastModifiedTime)) {
                String content = Files.readString(cacheFilePath, StandardCharsets.UTF_8);
                if (!StringUtils.hasText(content) || "{}".equals(content)) {
                    log.info("交易对缓存文件存在但内容为空，准备重新刷新，path={}", cacheFilePath);
                    return refreshSymbols();
                }
                log.info("命中本地交易对缓存，直接读取缓存文件，path={}", cacheFilePath);
                return JSON.parseObject(content, BinanceSymbolsDTO.class);
            }

            log.info("交易对缓存文件已过期，先清空旧内容再刷新，path={}", cacheFilePath);
            clearFileContent();
        }

        return refreshSymbols();
    }

    /**
     * 判断缓存文件最后修改时间是否仍属于今天。
     */
    private boolean isToday(LocalDateTime dateTime) {
        return LocalDate.now().equals(dateTime.toLocalDate());
    }

    /**
     * 清空缓存文件内容，但保留文件本身。
     */
    private void clearFileContent() throws IOException {
        Files.writeString(cacheFilePath, "", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 回源 Binance 重新拉取交易对列表，并落盘到本地 UTF-8 缓存文件。
     */
    private BinanceSymbolsDTO refreshSymbols() {
        BinanceSymbolsDTO symbolsDTO = binanceApi.listSymbols();
        try {
            Files.writeString(
                    cacheFilePath,
                    Objects.requireNonNull(JSON.toJSONString(symbolsDTO)),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            int symbolCount = symbolsDTO == null || symbolsDTO.getSymbols() == null ? 0 : symbolsDTO.getSymbols().size();
            log.info("交易对缓存刷新完成，共写入 {} 个交易对，path={}", symbolCount, cacheFilePath);
        } catch (IOException e) {
            log.error("写入交易对缓存文件失败，path={}", cacheFilePath, e);
        }
        return symbolsDTO;
    }
}
