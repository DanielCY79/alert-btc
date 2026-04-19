package com.mobai.alert.access.kline.sync;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.kline.persistence.entity.AccessKlineSyncCheckpointEntity;
import com.mobai.alert.access.kline.persistence.repository.AccessKlineBarJdbcRepository;
import com.mobai.alert.access.kline.persistence.repository.AccessKlineSyncCheckpointRepository;
import com.mobai.alert.access.kline.rest.BinanceKlineRestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinanceKlineHistorySyncServiceTests {

    @Test
    void shouldResumeFromCheckpointByDefault() {
        BinanceKlineRestClient restClient = mock(BinanceKlineRestClient.class);
        AccessKlineBarJdbcRepository barRepository = mock(AccessKlineBarJdbcRepository.class);
        AccessKlineSyncCheckpointRepository checkpointRepository = mock(AccessKlineSyncCheckpointRepository.class);
        KlineSyncProperties properties = properties("1m");
        BinanceKlineHistorySyncService service = new BinanceKlineHistorySyncService(
                restClient,
                barRepository,
                checkpointRepository,
                properties
        );

        long checkpointStart = System.currentTimeMillis() - Duration.ofMinutes(2).toMillis();
        AccessKlineSyncCheckpointEntity checkpoint = new AccessKlineSyncCheckpointEntity();
        checkpoint.setLastOpenTimeMs(checkpointStart);
        when(checkpointRepository.getOrCreate("BINANCE", "USDT_PERPETUAL", "BTCUSDT", "1m"))
                .thenReturn(checkpoint);
        when(restClient.listKlineStrict(any(BinanceKlineDTO.class))).thenReturn(List.of());

        service.syncRecentHistory();

        ArgumentCaptor<BinanceKlineDTO> requestCaptor = ArgumentCaptor.forClass(BinanceKlineDTO.class);
        verify(restClient).listKlineStrict(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStartTime()).isEqualTo(checkpointStart + Duration.ofMinutes(1).toMillis());
        verify(checkpointRepository, never()).deleteCheckpoint("BINANCE", "USDT_PERPETUAL", "BTCUSDT", "1m");
        verify(checkpointRepository).markRunning(checkpoint);
        verify(checkpointRepository).markSuccess(checkpoint);
    }

    @Test
    void shouldIgnoreCheckpointWhenForceFullSyncIsEnabled() {
        BinanceKlineRestClient restClient = mock(BinanceKlineRestClient.class);
        AccessKlineBarJdbcRepository barRepository = mock(AccessKlineBarJdbcRepository.class);
        AccessKlineSyncCheckpointRepository checkpointRepository = mock(AccessKlineSyncCheckpointRepository.class);
        KlineSyncProperties properties = properties("1m");
        properties.setForceFullSync(true);
        BinanceKlineHistorySyncService service = new BinanceKlineHistorySyncService(
                restClient,
                barRepository,
                checkpointRepository,
                properties
        );

        long checkpointStart = System.currentTimeMillis() - Duration.ofMinutes(2).toMillis();
        AccessKlineSyncCheckpointEntity checkpoint = new AccessKlineSyncCheckpointEntity();
        checkpoint.setLastOpenTimeMs(checkpointStart);
        when(checkpointRepository.getOrCreate("BINANCE", "USDT_PERPETUAL", "BTCUSDT", "1m"))
                .thenReturn(checkpoint);
        when(restClient.listKlineStrict(any(BinanceKlineDTO.class))).thenReturn(List.of());

        long before = System.currentTimeMillis();
        service.syncRecentHistory();
        long after = System.currentTimeMillis();

        ArgumentCaptor<BinanceKlineDTO> requestCaptor = ArgumentCaptor.forClass(BinanceKlineDTO.class);
        verify(restClient).listKlineStrict(requestCaptor.capture());
        long expectedLowerBound = before - Duration.ofDays(properties.getLookbackDays()).toMillis();
        long expectedUpperBound = after - Duration.ofDays(properties.getLookbackDays()).toMillis();
        assertThat(requestCaptor.getValue().getStartTime()).isBetween(expectedLowerBound, expectedUpperBound);
        assertThat(requestCaptor.getValue().getStartTime()).isLessThan(checkpointStart);
        verify(checkpointRepository, never()).deleteCheckpoint("BINANCE", "USDT_PERPETUAL", "BTCUSDT", "1m");
    }

    @Test
    void shouldResetCheckpointBeforeSyncWhenResetCheckpointIsEnabled() {
        BinanceKlineRestClient restClient = mock(BinanceKlineRestClient.class);
        AccessKlineBarJdbcRepository barRepository = mock(AccessKlineBarJdbcRepository.class);
        AccessKlineSyncCheckpointRepository checkpointRepository = mock(AccessKlineSyncCheckpointRepository.class);
        KlineSyncProperties properties = properties("1m");
        properties.setResetCheckpoint(true);
        BinanceKlineHistorySyncService service = new BinanceKlineHistorySyncService(
                restClient,
                barRepository,
                checkpointRepository,
                properties
        );

        AccessKlineSyncCheckpointEntity checkpoint = new AccessKlineSyncCheckpointEntity();
        when(checkpointRepository.getOrCreate("BINANCE", "USDT_PERPETUAL", "BTCUSDT", "1m"))
                .thenReturn(checkpoint);
        when(restClient.listKlineStrict(any(BinanceKlineDTO.class))).thenReturn(List.of());

        long before = System.currentTimeMillis();
        service.syncRecentHistory();
        long after = System.currentTimeMillis();

        InOrder inOrder = inOrder(checkpointRepository);
        inOrder.verify(checkpointRepository).deleteCheckpoint("BINANCE", "USDT_PERPETUAL", "BTCUSDT", "1m");
        inOrder.verify(checkpointRepository).getOrCreate("BINANCE", "USDT_PERPETUAL", "BTCUSDT", "1m");
        inOrder.verify(checkpointRepository).markRunning(checkpoint);
        inOrder.verify(checkpointRepository).markSuccess(checkpoint);

        ArgumentCaptor<BinanceKlineDTO> requestCaptor = ArgumentCaptor.forClass(BinanceKlineDTO.class);
        verify(restClient).listKlineStrict(requestCaptor.capture());
        long expectedLowerBound = before - Duration.ofDays(properties.getLookbackDays()).toMillis();
        long expectedUpperBound = after - Duration.ofDays(properties.getLookbackDays()).toMillis();
        assertThat(requestCaptor.getValue().getStartTime()).isBetween(expectedLowerBound, expectedUpperBound);
    }

    @Test
    void shouldSupportFiveMinuteIntervalsWhenResumingFromCheckpoint() {
        BinanceKlineRestClient restClient = mock(BinanceKlineRestClient.class);
        AccessKlineBarJdbcRepository barRepository = mock(AccessKlineBarJdbcRepository.class);
        AccessKlineSyncCheckpointRepository checkpointRepository = mock(AccessKlineSyncCheckpointRepository.class);
        KlineSyncProperties properties = properties("5m");
        BinanceKlineHistorySyncService service = new BinanceKlineHistorySyncService(
                restClient,
                barRepository,
                checkpointRepository,
                properties
        );

        long checkpointStart = System.currentTimeMillis() - Duration.ofMinutes(15).toMillis();
        AccessKlineSyncCheckpointEntity checkpoint = new AccessKlineSyncCheckpointEntity();
        checkpoint.setLastOpenTimeMs(checkpointStart);
        when(checkpointRepository.getOrCreate("BINANCE", "USDT_PERPETUAL", "BTCUSDT", "5m"))
                .thenReturn(checkpoint);
        when(restClient.listKlineStrict(any(BinanceKlineDTO.class))).thenReturn(List.of());

        service.syncRecentHistory();

        ArgumentCaptor<BinanceKlineDTO> requestCaptor = ArgumentCaptor.forClass(BinanceKlineDTO.class);
        verify(restClient).listKlineStrict(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getInterval()).isEqualTo("5m");
        assertThat(requestCaptor.getValue().getStartTime()).isEqualTo(checkpointStart + Duration.ofMinutes(5).toMillis());
        verify(checkpointRepository).markRunning(checkpoint);
        verify(checkpointRepository).markSuccess(checkpoint);
    }

    private KlineSyncProperties properties(String interval) {
        KlineSyncProperties properties = new KlineSyncProperties();
        properties.setExchange("BINANCE");
        properties.setMarketType("USDT_PERPETUAL");
        properties.setSymbol("BTCUSDT");
        properties.setIntervals(List.of(interval));
        properties.setLookbackDays(365);
        properties.setFetchLimit(10);
        properties.setWriteBatchSize(5);
        return properties;
    }
}
