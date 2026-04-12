package com.mobai.alert.access.kline.sync;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.kline.persistence.entity.AccessKlineBarEntity;
import com.mobai.alert.access.kline.persistence.entity.AccessKlineSyncCheckpointEntity;
import com.mobai.alert.access.kline.persistence.repository.AccessKlineBarJdbcRepository;
import com.mobai.alert.access.kline.persistence.repository.AccessKlineSyncCheckpointRepository;
import com.mobai.alert.access.kline.rest.BinanceKlineRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BinanceKlineHistorySyncService {

    private static final Logger log = LoggerFactory.getLogger(BinanceKlineHistorySyncService.class);

    private final BinanceKlineRestClient klineRestClient;
    private final AccessKlineBarJdbcRepository barRepository;
    private final AccessKlineSyncCheckpointRepository checkpointRepository;
    private final KlineSyncProperties properties;

    public BinanceKlineHistorySyncService(BinanceKlineRestClient klineRestClient,
                                          AccessKlineBarJdbcRepository barRepository,
                                          AccessKlineSyncCheckpointRepository checkpointRepository,
                                          KlineSyncProperties properties) {
        this.klineRestClient = klineRestClient;
        this.barRepository = barRepository;
        this.checkpointRepository = checkpointRepository;
        this.properties = properties;
    }

    public void syncRecentHistory() {
        for (String rawIntervalCode : properties.getIntervals()) {
            if (!StringUtils.hasText(rawIntervalCode)) {
                continue;
            }
            syncInterval(rawIntervalCode.trim());
        }
    }

    private void syncInterval(String intervalCode) {
        long intervalMs = KlineIntervalSupport.toMillis(intervalCode);
        long lookbackStartTime = System.currentTimeMillis() - Duration.ofDays(properties.getLookbackDays()).toMillis();
        AccessKlineSyncCheckpointEntity checkpoint = prepareCheckpoint(intervalCode);
        checkpointRepository.markRunning(checkpoint);

        long nextStartTime = resolveNextStartTime(intervalCode, checkpoint, intervalMs, lookbackStartTime);
        int persistedCount = 0;

        try {
            while (nextStartTime <= System.currentTimeMillis()) {
                long requestEndTime = System.currentTimeMillis();
                List<BinanceKlineDTO> page = fetchPage(intervalCode, nextStartTime, requestEndTime);
                if (CollectionUtils.isEmpty(page)) {
                    break;
                }

                List<BinanceKlineDTO> sortedPage = new ArrayList<>(page);
                sortedPage.sort(Comparator.comparingLong(BinanceKlineDTO::getStartTime));
                List<BinanceKlineDTO> closedPage = filterClosedBars(sortedPage, requestEndTime);
                if (CollectionUtils.isEmpty(closedPage)) {
                    break;
                }

                List<AccessKlineBarEntity> entities = toEntities(closedPage, intervalCode);
                persistBars(entities);
                persistedCount += entities.size();

                BinanceKlineDTO lastBar = closedPage.get(closedPage.size() - 1);
                checkpointRepository.markProgress(checkpoint, lastBar.getStartTime(), lastBar.getEndTime());
                log.info("Kline sync progress: symbol={}, interval={}, persisted={}, lastOpenTimeMs={}",
                        properties.getSymbol(),
                        intervalCode,
                        persistedCount,
                        lastBar.getStartTime());

                long candidateNextStartTime = lastBar.getStartTime() + intervalMs;
                if (page.size() < resolveFetchLimit() || candidateNextStartTime <= nextStartTime) {
                    break;
                }
                nextStartTime = candidateNextStartTime;
            }

            checkpointRepository.markSuccess(checkpoint);
            log.info("Kline sync finished: symbol={}, interval={}, persisted={}",
                    properties.getSymbol(),
                    intervalCode,
                    persistedCount);
        } catch (Exception ex) {
            checkpointRepository.markFailed(checkpoint, ex);
            throw ex;
        }
    }

    private AccessKlineSyncCheckpointEntity prepareCheckpoint(String intervalCode) {
        if (properties.isResetCheckpoint()) {
            int deleted = checkpointRepository.deleteCheckpoint(
                    properties.getExchange(),
                    properties.getMarketType(),
                    properties.getSymbol(),
                    intervalCode
            );
            log.info("Kline sync checkpoint reset: symbol={}, interval={}, deleted={}",
                    properties.getSymbol(),
                    intervalCode,
                    deleted);
        }
        return checkpointRepository.getOrCreate(
                properties.getExchange(),
                properties.getMarketType(),
                properties.getSymbol(),
                intervalCode
        );
    }

    private long resolveNextStartTime(String intervalCode,
                                      AccessKlineSyncCheckpointEntity checkpoint,
                                      long intervalMs,
                                      long lookbackStartTime) {
        if (properties.isForceFullSync()) {
            log.info("Kline force full sync enabled: symbol={}, interval={}, checkpointLastOpenTimeMs={}, lookbackStartTime={}",
                    properties.getSymbol(),
                    intervalCode,
                    checkpoint.getLastOpenTimeMs(),
                    lookbackStartTime);
            return lookbackStartTime;
        }
        return checkpoint.getLastOpenTimeMs() == null
                ? lookbackStartTime
                : Math.max(lookbackStartTime, checkpoint.getLastOpenTimeMs() + intervalMs);
    }

    private List<BinanceKlineDTO> fetchPage(String intervalCode, long startTimeMs, long endTimeMs) {
        BinanceKlineDTO request = new BinanceKlineDTO();
        request.setSymbol(properties.getSymbol());
        request.setInterval(intervalCode);
        request.setStartTime(startTimeMs);
        request.setEndTime(endTimeMs);
        request.setLimit(resolveFetchLimit());
        return klineRestClient.listKlineStrict(request);
    }

    private List<AccessKlineBarEntity> toEntities(List<BinanceKlineDTO> bars, String intervalCode) {
        long now = System.currentTimeMillis();
        List<AccessKlineBarEntity> entities = new ArrayList<>(bars.size());
        for (BinanceKlineDTO bar : bars) {
            AccessKlineBarEntity entity = new AccessKlineBarEntity();
            entity.setExchange(properties.getExchange());
            entity.setMarketType(properties.getMarketType());
            entity.setSymbol(properties.getSymbol());
            entity.setIntervalCode(intervalCode);
            entity.setOpenTimeMs(bar.getStartTime());
            entity.setCloseTimeMs(bar.getEndTime());
            entity.setOpenPrice(toBigDecimal(bar.getOpen()));
            entity.setHighPrice(toBigDecimal(bar.getHigh()));
            entity.setLowPrice(toBigDecimal(bar.getLow()));
            entity.setClosePrice(toBigDecimal(bar.getClose()));
            entity.setBaseVolume(toBigDecimal(bar.getAmount()));
            entity.setQuoteVolume(toBigDecimal(bar.getVolume()));
            entity.setTradeCount(null);
            entity.setTakerBuyBaseVolume(null);
            entity.setTakerBuyQuoteVolume(null);
            entity.setIsClosed(bar.getEndTime() <= now ? 1 : 0);
            entity.setDataSource("REST");
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entities.add(entity);
        }
        return entities;
    }

    private void persistBars(List<AccessKlineBarEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return;
        }
        int batchSize = Math.max(1, properties.getWriteBatchSize());
        for (int start = 0; start < entities.size(); start += batchSize) {
            int end = Math.min(start + batchSize, entities.size());
            barRepository.batchUpsert(entities.subList(start, end));
        }
    }

    private List<BinanceKlineDTO> filterClosedBars(List<BinanceKlineDTO> page, long requestEndTime) {
        return page.stream()
                .filter(bar -> bar.getEndTime() != null && bar.getEndTime() <= requestEndTime)
                .collect(Collectors.toList());
    }

    private int resolveFetchLimit() {
        return Math.max(1, Math.min(1000, properties.getFetchLimit()));
    }

    private BigDecimal toBigDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }
}
