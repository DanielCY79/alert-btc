package com.mobai.alert.access.binance.kline.service;

import com.alibaba.fastjson.JSON;
import com.mobai.alert.access.facade.BinanceApi;
import com.mobai.alert.access.binance.kline.dto.BinanceSymbolsDTO;
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
 * 浜ゆ槗瀵圭紦瀛樻湇鍔°€? * 鎶婂綋鏃ヤ氦鏄撳鍒楄〃钀藉埌鏈湴鏂囦欢锛屽噺灏戦噸澶嶈姹傚甫鏉ョ殑杩愮淮鍣０銆? */
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
                    log.info("浜ゆ槗瀵圭紦瀛樻枃浠跺瓨鍦ㄤ絾鍐呭涓虹┖锛屽噯澶囬噸鏂板埛鏂扮紦瀛橈細{}", cacheFilePath);
                    return refreshSymbols();
                }
                log.info("鍛戒腑褰撴棩浜ゆ槗瀵圭紦瀛橈紝鐩存帴澶嶇敤鏈湴鏂囦欢锛歿}", cacheFilePath);
                return JSON.parseObject(content, BinanceSymbolsDTO.class);
            }

            log.info("浜ゆ槗瀵圭紦瀛樺凡杩囨湡锛屽噯澶囨竻鐞嗗苟閲嶆柊鎷夊彇锛歿}", cacheFilePath);
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
            log.info("浜ゆ槗瀵圭紦瀛樺埛鏂板畬鎴愶紝鍏卞啓鍏?{} 涓氦鏄撳锛岀紦瀛樻枃浠讹細{}", symbolCount, cacheFilePath);
        } catch (IOException e) {
            log.error("鍐欏叆浜ゆ槗瀵圭紦瀛樻枃浠跺け璐ワ紝path={}", cacheFilePath, e);
        }
        return symbolsDTO;
    }
}

