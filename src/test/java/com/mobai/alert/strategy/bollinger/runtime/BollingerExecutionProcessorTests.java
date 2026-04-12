package com.mobai.alert.strategy.bollinger.runtime;

import com.mobai.alert.access.BinanceApi;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.notification.AlertNotificationService;
import com.mobai.alert.strategy.bollinger.BollingerSignalEvaluator;
import com.mobai.alert.strategy.model.AlertSignal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BollingerExecutionProcessorTests {

    @Test
    void shouldSendEntryAndThenStopExit() {
        BinanceApi binanceApi = mock(BinanceApi.class);
        AlertNotificationService notificationService = mock(AlertNotificationService.class);
        BollingerSignalEvaluator evaluator = new BollingerSignalEvaluator();
        ReflectionTestUtils.setField(evaluator, "bollPeriod", 20);
        ReflectionTestUtils.setField(evaluator, "stddevMultiplier", new BigDecimal("2.0"));
        ReflectionTestUtils.setField(evaluator, "stopLossPct", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(evaluator, "entryVolumeLookback", 20);

        BollingerExecutionProcessor processor = new BollingerExecutionProcessor(binanceApi, evaluator, notificationService);
        ReflectionTestUtils.setField(processor, "targetSymbol", "BTCUSDT");
        ReflectionTestUtils.setField(processor, "entryInterval", "1m");
        ReflectionTestUtils.setField(processor, "contextInterval", "4h");
        ReflectionTestUtils.setField(processor, "entryKlineLimit", 240);
        ReflectionTestUtils.setField(processor, "contextKlineLimit", 80);

        List<BinanceKlineDTO> entryKlines = rawKlines(entryClosedBars(), kline("1m", 102.80, 103.00, 102.70, 102.90, 30, 31));
        List<BinanceKlineDTO> contextKlines = rawKlines(contextClosedBars(), kline("4h", 126.90, 127.20, 126.70, 127.00, 30, 31));
        when(binanceApi.listKline(any())).thenAnswer(invocation -> {
            BinanceKlineDTO request = invocation.getArgument(0);
            return "4h".equals(request.getInterval()) ? contextKlines : entryKlines;
        });

        processor.process("BTCUSDT");

        entryKlines.set(entryKlines.size() - 1, kline("1m", 92.40, 92.60, 91.80, 92.10, 31, 32));
        processor.process("BTCUSDT");

        ArgumentCaptor<AlertSignal> signalCaptor = ArgumentCaptor.forClass(AlertSignal.class);
        verify(notificationService, times(2)).send(signalCaptor.capture());
        assertThat(signalCaptor.getAllValues()).extracting(AlertSignal::getType)
                .containsExactly("BOLLINGER_LONG_ENTRY", "EXIT_BOLLINGER_STOP_LONG");
    }

    private List<BinanceKlineDTO> rawKlines(List<BinanceKlineDTO> closedBars, BinanceKlineDTO currentBar) {
        List<BinanceKlineDTO> all = new ArrayList<>(closedBars);
        all.add(currentBar);
        return all;
    }

    private List<BinanceKlineDTO> entryClosedBars() {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double close = 100.0 + 0.10 * i;
            klines.add(kline("1m", close - 0.08, close + 0.12, close - 0.18, close, i, i + 1));
        }
        return klines;
    }

    private List<BinanceKlineDTO> contextClosedBars() {
        List<BinanceKlineDTO> klines = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double close = 100.0 + 0.90 * i;
            klines.add(kline("4h", close - 0.30, close + 0.40, close - 0.50, close, i, i + 1));
        }
        return klines;
    }

    private BinanceKlineDTO kline(String interval,
                                  double open,
                                  double high,
                                  double low,
                                  double close,
                                  long startTime,
                                  long endTime) {
        BinanceKlineDTO dto = new BinanceKlineDTO();
        dto.setSymbol("BTCUSDT");
        dto.setInterval(interval);
        dto.setOpen(String.format("%.2f", open));
        dto.setHigh(String.format("%.2f", high));
        dto.setLow(String.format("%.2f", low));
        dto.setClose(String.format("%.2f", close));
        dto.setVolume("1000.00");
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        return dto;
    }
}
