package com.mobai.alert.access.market;

import com.alibaba.fastjson.JSON;
import com.mobai.alert.access.dto.BinanceSymbolsDTO;
import com.mobai.alert.access.exchange.BinanceApi;
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

@Service
public class AlertSymbolCacheService {

    private static final Logger log = LoggerFactory.getLogger(AlertSymbolCacheService.class);

    private final BinanceApi binanceApi;
    private final Path cacheFilePath = Paths.get(System.getProperty("user.dir"), "symbolsCache.json");

    public AlertSymbolCacheService(BinanceApi binanceApi) {
        this.binanceApi = binanceApi;
    }

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
                    log.info("交易对缓存文件存在但内容为空，准备重新刷新缓存：{}", cacheFilePath);
                    return refreshSymbols();
                }
                log.info("命中当日交易对缓存，直接复用本地文件：{}", cacheFilePath);
                return JSON.parseObject(content, BinanceSymbolsDTO.class);
            }

            log.info("交易对缓存已过期，准备清理并重新拉取：{}", cacheFilePath);
            clearFileContent();
        }

        return refreshSymbols();
    }

    private boolean isToday(LocalDateTime dateTime) {
        return LocalDate.now().equals(dateTime.toLocalDate());
    }

    private void clearFileContent() throws IOException {
        Files.writeString(cacheFilePath, "", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

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
            log.info("交易对缓存刷新完成，共写入 {} 个交易对，缓存文件：{}", symbolCount, cacheFilePath);
        } catch (IOException e) {
            log.error("写入交易对缓存文件失败，path={}", cacheFilePath, e);
        }
        return symbolsDTO;
    }
}
