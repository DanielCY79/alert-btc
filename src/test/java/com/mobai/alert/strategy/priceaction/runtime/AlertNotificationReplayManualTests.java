package com.mobai.alert.strategy.priceaction.runtime;

import com.mobai.alert.AlertApplication;
import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.capitalflow.dto.BinanceDerivativeFeaturesDTO;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.access.kline.rest.BinanceKlineRestClient;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.priceaction.shared.MultiTimeframeDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = AlertApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "network.proxy.enabled=false",
                "monitoring.initial-delay=600000",
                "monitoring.market-data.websocket.enabled=false",
                "monitoring.market-data.force-order.websocket.enabled=false",
                "monitoring.market-data.derivatives.enabled=false",
                "monitoring.market-data.derivatives.snapshot-initial-delay-ms=600000",
                "binance.cms.websocket.enabled=false",
                "gdelt.doc.enabled=false",
                "monitoring.strategy.breakout.record.ttl.ms=315360000000",
                "logging.level.root=WARN",
                "logging.level.com.mobai.alert=ERROR"
        }
)
@EnabledIfSystemProperty(named = "notification.replay.enabled", matches = "true")
class AlertNotificationReplayManualTests {

    private static final ZoneId REPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter COMPACT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
    private static final String DEFAULT_START = "2026-04-11T00:00:00+08:00";
    private static final String DEFAULT_END = "2026-04-12T11:00:00+08:00";
    private static final int PAGE_LIMIT = 1000;

    @Autowired
    private PriceActionExecutionProcessor alertSymbolProcessor;

    @Autowired
    private BinanceKlineRestClient binanceKlineRestClient;

    @Autowired
    private Environment environment;

    @MockBean
    private BinanceApi binanceApi;

    @MockBean
    private AlertNotificationService alertNotificationService;

    @Test
    void shouldReplayProcessorLevelNotificationsForConfiguredWindow() {
        reset(binanceApi, alertNotificationService);
        clearProcessorState();

        String symbol = environment.getProperty("monitoring.target-symbol", "BTCUSDT");
        String executionInterval = environment.getProperty("monitoring.kline.interval", "3m");
        String contextInterval = MultiTimeframeDefaults.CONTEXT_INTERVAL;
        int executionLimit = intProperty("monitoring.kline.limit", 360);
        int contextLimit = MultiTimeframeDefaults.CONTEXT_KLINE_LIMIT;
        Instant start = resolveInstant("notification.replay.start", DEFAULT_START);
        Instant end = resolveInstant("notification.replay.end", DEFAULT_END);
        Instant loadStart = resolveInstant("notification.replay.warmup-start", System.getProperty("notification.replay.start", DEFAULT_START));

        ReflectionTestUtils.setField(alertSymbolProcessor, "targetSymbol", symbol);

        List<BinanceKlineDTO> executionHistory = loadHistoricalKlines(symbol, executionInterval, loadStart.toEpochMilli(), end.toEpochMilli());
        List<BinanceKlineDTO> contextHistory = contextInterval.isBlank()
                ? List.of()
                : loadHistoricalKlines(symbol, contextInterval, loadStart.toEpochMilli(), end.toEpochMilli());
        assertThat(executionHistory).hasSizeGreaterThanOrEqualTo(3);
        int replayStartIndex = firstReplayIndex(executionHistory, start.toEpochMilli());
        assertThat(replayStartIndex).isGreaterThanOrEqualTo(2);

        AtomicInteger replayCursor = new AtomicInteger(2);
        List<ReplayNotification> notifications = new ArrayList<>();

        when(binanceApi.listKline(any(BinanceKlineDTO.class))).thenAnswer(invocation -> {
            BinanceKlineDTO request = invocation.getArgument(0);
            int currentCursor = replayCursor.get();
            long executionBarEndTime = executionHistory.get(currentCursor).getEndTime();
            if (executionInterval.equalsIgnoreCase(request.getInterval())) {
                return recentSlice(executionHistory, currentCursor, request.getLimit() == null ? executionLimit : request.getLimit());
            }
            if (!contextInterval.isBlank() && contextInterval.equalsIgnoreCase(request.getInterval())) {
                int requestedLimit = request.getLimit() == null ? contextLimit : request.getLimit();
                return contextSlice(contextHistory, executionBarEndTime, requestedLimit);
            }
            return List.of();
        });

        when(binanceApi.buildDerivativeFeatures(any(String.class))).thenAnswer(invocation -> {
            BinanceDerivativeFeaturesDTO dto = new BinanceDerivativeFeaturesDTO();
            dto.setSymbol(invocation.getArgument(0));
            dto.setAsOfTime(executionHistory.get(replayCursor.get()).getEndTime());
            return dto;
        });

        doAnswer(invocation -> {
            AlertSignal signal = invocation.getArgument(0);
            int currentCursor = replayCursor.get();
            notifications.add(new ReplayNotification(
                    currentCursor,
                    executionHistory.get(currentCursor).getEndTime(),
                    signal
            ));
            return null;
        }).when(alertNotificationService).send(any(AlertSignal.class));

        for (int cursor = replayStartIndex; cursor < executionHistory.size(); cursor++) {
            replayCursor.set(cursor);
            alertSymbolProcessor.process(symbol);
        }

        printSummary(symbol, executionInterval, loadStart, start, end, executionHistory.size(), replayStartIndex, notifications);
    }

    private List<BinanceKlineDTO> loadHistoricalKlines(String symbol, String interval, long startTime, long endTime) {
        List<BinanceKlineDTO> all = new ArrayList<>();
        long cursor = startTime;
        long intervalMs = intervalMs(interval);
        while (cursor < endTime) {
            BinanceKlineDTO request = new BinanceKlineDTO();
            request.setSymbol(symbol);
            request.setInterval(interval);
            request.setLimit(PAGE_LIMIT);
            request.setStartTime(cursor);
            request.setEndTime(endTime);
            List<BinanceKlineDTO> page = binanceKlineRestClient.listKline(request);
            if (page.isEmpty()) {
                break;
            }
            all.addAll(page);
            long nextCursor = page.get(page.size() - 1).getStartTime() + intervalMs;
            if (nextCursor <= cursor) {
                break;
            }
            cursor = nextCursor;
            if (page.size() < PAGE_LIMIT) {
                break;
            }
        }
        return all;
    }

    private List<BinanceKlineDTO> recentSlice(List<BinanceKlineDTO> history, int cursor, int limit) {
        int endExclusive = Math.min(history.size(), cursor + 1);
        int startInclusive = Math.max(0, endExclusive - Math.max(1, limit));
        return List.copyOf(history.subList(startInclusive, endExclusive));
    }

    private List<BinanceKlineDTO> contextSlice(List<BinanceKlineDTO> history, long executionBarEndTime, int limit) {
        int visibleCount = 0;
        while (visibleCount < history.size() && history.get(visibleCount).getEndTime() <= executionBarEndTime) {
            visibleCount++;
        }
        if (visibleCount == 0) {
            return List.of();
        }
        int startInclusive = Math.max(0, visibleCount - Math.max(1, limit));
        return List.copyOf(history.subList(startInclusive, visibleCount));
    }

    private int firstReplayIndex(List<BinanceKlineDTO> history, long replayStartTime) {
        for (int index = 0; index < history.size(); index++) {
            if (history.get(index).getEndTime() >= replayStartTime) {
                return Math.max(2, index);
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private void clearProcessorState() {
        ((Map<String, ?>) ReflectionTestUtils.getField(alertSymbolProcessor, "breakoutRecords")).clear();
        ((Map<String, ?>) ReflectionTestUtils.getField(alertSymbolProcessor, "marketStates")).clear();
        ((Map<String, ?>) ReflectionTestUtils.getField(alertSymbolProcessor, "activePositions")).clear();
    }

    private Instant resolveInstant(String propertyKey, String defaultValue) {
        String raw = System.getProperty(propertyKey, defaultValue);
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw, LOCAL_DATE_TIME_FORMATTER).atZone(REPLAY_ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw, COMPACT_DATE_TIME_FORMATTER).atZone(REPLAY_ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("Unsupported replay time format: " + raw);
    }

    private int intProperty(String key, int fallback) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value);
    }

    private long intervalMs(String interval) {
        return switch (interval) {
            case "3m" -> 3L * 60L * 1000L;
            case "15m" -> 15L * 60L * 1000L;
            case "1h" -> 60L * 60L * 1000L;
            case "4h" -> 4L * 60L * 60L * 1000L;
            case "1d" -> 24L * 60L * 60L * 1000L;
            default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
        };
    }

    private void printSummary(String symbol,
                              String interval,
                              Instant loadStart,
                              Instant start,
                              Instant end,
                              int barCount,
                              int replayStartIndex,
                              List<ReplayNotification> notifications) {
        Map<String, Integer> countsByType = new LinkedHashMap<>();
        for (ReplayNotification notification : notifications) {
            countsByType.merge(notification.signal().getType(), 1, Integer::sum);
        }

        System.out.println();
        System.out.println("=== Notification Replay Summary ===");
        System.out.println("symbol=" + symbol
                + " interval=" + interval
                + " loadStart=" + formatInstant(loadStart)
                + " start=" + formatInstant(start)
                + " end=" + formatInstant(end)
                + " bars=" + barCount
                + " replayStartIndex=" + replayStartIndex
                + " notifications=" + notifications.size());
        System.out.println("counts=" + countsByType);
        for (ReplayNotification notification : notifications) {
            AlertSignal signal = notification.signal();
            System.out.println(formatInstant(Instant.ofEpochMilli(signal.getKline().getEndTime()))
                    + " | type=" + signal.getType()
                    + " | direction=" + signal.getDirection()
                    + " | processedOn=" + formatInstant(Instant.ofEpochMilli(notification.processingBarEndTime()))
                    + " | trigger=" + signal.getTriggerPrice()
                    + " | stop=" + signal.getInvalidationPrice()
                    + " | target=" + signal.getTargetPrice());
        }
        System.out.println("=== End Replay Summary ===");
        System.out.println();
    }

    private String formatInstant(Instant instant) {
        return LOCAL_DATE_TIME_FORMATTER.format(instant.atZone(REPLAY_ZONE));
    }

    private record ReplayNotification(int cursor, long processingBarEndTime, AlertSignal signal) {
    }
}
