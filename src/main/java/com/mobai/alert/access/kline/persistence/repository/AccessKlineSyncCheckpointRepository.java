package com.mobai.alert.access.kline.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mobai.alert.access.kline.persistence.entity.AccessKlineSyncCheckpointEntity;
import com.mobai.alert.access.kline.persistence.mapper.AccessKlineSyncCheckpointMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class AccessKlineSyncCheckpointRepository {

    private final AccessKlineSyncCheckpointMapper mapper;

    public AccessKlineSyncCheckpointRepository(AccessKlineSyncCheckpointMapper mapper) {
        this.mapper = mapper;
    }

    public AccessKlineSyncCheckpointEntity getOrCreate(String exchange,
                                                       String marketType,
                                                       String symbol,
                                                       String intervalCode) {
        AccessKlineSyncCheckpointEntity existing = find(exchange, marketType, symbol, intervalCode);
        if (existing != null) {
            return existing;
        }

        long now = System.currentTimeMillis();
        AccessKlineSyncCheckpointEntity created = new AccessKlineSyncCheckpointEntity();
        created.setExchange(exchange);
        created.setMarketType(marketType);
        created.setSymbol(symbol);
        created.setIntervalCode(intervalCode);
        created.setSyncStatus("IDLE");
        created.setCreateTime(now);
        created.setUpdateTime(now);
        try {
            mapper.insert(created);
            return created;
        } catch (DuplicateKeyException ex) {
            return find(exchange, marketType, symbol, intervalCode);
        }
    }

    public int deleteCheckpoint(String exchange,
                                String marketType,
                                String symbol,
                                String intervalCode) {
        return mapper.delete(new LambdaQueryWrapper<AccessKlineSyncCheckpointEntity>()
                .eq(AccessKlineSyncCheckpointEntity::getExchange, exchange)
                .eq(AccessKlineSyncCheckpointEntity::getMarketType, marketType)
                .eq(AccessKlineSyncCheckpointEntity::getSymbol, symbol)
                .eq(AccessKlineSyncCheckpointEntity::getIntervalCode, intervalCode));
    }

    public void markRunning(AccessKlineSyncCheckpointEntity checkpoint) {
        checkpoint.setSyncStatus("RUNNING");
        checkpoint.setLastErrorMessage(null);
        checkpoint.setUpdateTime(System.currentTimeMillis());
        mapper.updateById(checkpoint);
    }

    public void markProgress(AccessKlineSyncCheckpointEntity checkpoint, long lastOpenTimeMs, long lastCloseTimeMs) {
        checkpoint.setLastOpenTimeMs(lastOpenTimeMs);
        checkpoint.setLastCloseTimeMs(lastCloseTimeMs);
        checkpoint.setSyncStatus("RUNNING");
        checkpoint.setLastErrorMessage(null);
        checkpoint.setUpdateTime(System.currentTimeMillis());
        mapper.updateById(checkpoint);
    }

    public void markSuccess(AccessKlineSyncCheckpointEntity checkpoint) {
        checkpoint.setSyncStatus("IDLE");
        checkpoint.setLastErrorMessage(null);
        checkpoint.setUpdateTime(System.currentTimeMillis());
        mapper.updateById(checkpoint);
    }

    public void markFailed(AccessKlineSyncCheckpointEntity checkpoint, Exception exception) {
        checkpoint.setSyncStatus("FAILED");
        checkpoint.setLastErrorMessage(truncate(exception == null ? null : exception.getMessage(), 500));
        checkpoint.setUpdateTime(System.currentTimeMillis());
        mapper.updateById(checkpoint);
    }

    private AccessKlineSyncCheckpointEntity find(String exchange,
                                                 String marketType,
                                                 String symbol,
                                                 String intervalCode) {
        return mapper.selectOne(new LambdaQueryWrapper<AccessKlineSyncCheckpointEntity>()
                .eq(AccessKlineSyncCheckpointEntity::getExchange, exchange)
                .eq(AccessKlineSyncCheckpointEntity::getMarketType, marketType)
                .eq(AccessKlineSyncCheckpointEntity::getSymbol, symbol)
                .eq(AccessKlineSyncCheckpointEntity::getIntervalCode, intervalCode)
                .last("LIMIT 1"));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
