package com.mobai.alert.notification;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.notification.channel.AlertNotifier;
import com.mobai.alert.notification.model.NotificationMessage;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertNotificationSignalContextTests {

    @Test
    void shouldIncludePlaybookForBreakoutSignal() {
        AlertNotifier notifier = notifier();
        AlertNotificationService service = service(notifier);

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTC confirmed breakout long",
                kline("100.00", "110.00", "99.00", "108.00", "100000"),
                "CONFIRMED_BREAKOUT_LONG",
                "price accepted above the range",
                new BigDecimal("109.00"),
                new BigDecimal("104.00"),
                new BigDecimal("118.00"),
                new BigDecimal("1.80"),
                new BigDecimal("0.73"),
                "trendBias=0.70"
        );

        service.send(signal);

        String plainText = captureMessage(notifier).plainTextContent();
        assertThat(plainText).contains("BTCUSDT");
        assertThat(plainText).contains("confirmed breakout long");
        assertThat(plainText).contains("挂单方案");
        assertThat(plainText).contains("风控提示");
    }

    @Test
    void shouldIncludePullbackSpecificPlaybook() {
        AlertNotifier notifier = notifier();
        AlertNotificationService service = service(notifier);

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTC breakout pullback long",
                kline("108.00", "110.00", "107.20", "109.10", "85000"),
                "BREAKOUT_PULLBACK_LONG",
                "pullback held the breakout area",
                new BigDecimal("108.50"),
                new BigDecimal("107.20"),
                new BigDecimal("112.40"),
                new BigDecimal("0.90"),
                new BigDecimal("0.68"),
                "breakoutConfirmation=0.62"
        );

        service.send(signal);

        String plainText = captureMessage(notifier).plainTextContent();
        assertThat(plainText).contains("突破后回踩延续");
        assertThat(plainText).contains("分批限价吸入");
        assertThat(plainText).contains("0.50%-0.70%");
    }

    @Test
    void shouldRenderFailedFollowThroughExitTemplate() {
        AlertNotifier notifier = notifier();
        AlertNotificationService service = service(notifier);

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTC failed follow-through exit",
                kline("100.50", "100.80", "97.80", "98.00", "90000"),
                "EXIT_FAILED_FOLLOW_THROUGH_LONG",
                "follow-through failed on the first bar after entry",
                new BigDecimal("98.00"),
                new BigDecimal("95.00"),
                new BigDecimal("115.00"),
                new BigDecimal("1.10"),
                null,
                "entryType=CONFIRMED_BREAKOUT_LONG | activeStop=95.00 | reason=FAILED_FOLLOW_THROUGH",
                new BigDecimal("100.00"),
                new BigDecimal("95.00")
        );

        service.send(signal);

        String plainText = captureMessage(notifier).plainTextContent();
        assertThat(plainText).contains("退出提醒");
        assertThat(plainText).contains("failed follow-through");
        assertThat(plainText).contains("入场基准: 100.00 USDT");
        assertThat(plainText).contains("当前浮动R: -0.40R");
    }

    @Test
    void shouldRenderScaleOutExitTemplate() {
        AlertNotifier notifier = notifier();
        AlertNotificationService service = service(notifier);

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTC scale-out alert",
                kline("100.50", "109.00", "100.10", "108.50", "95000"),
                "EXIT_SCALE_OUT_LONG",
                "first 1R scale-out level reached",
                new BigDecimal("105.00"),
                new BigDecimal("104.00"),
                new BigDecimal("115.00"),
                new BigDecimal("1.05"),
                null,
                "entryType=CONFIRMED_BREAKOUT_LONG | activeStop=104.00 | remainingSize=0.50 | reason=SCALE_OUT",
                new BigDecimal("100.00"),
                new BigDecimal("95.00")
        );

        service.send(signal);

        String plainText = captureMessage(notifier).plainTextContent();
        assertThat(plainText).contains("先兑现部分利润");
        assertThat(plainText).contains("首个 1R 减仓位");
        assertThat(plainText).contains("当前防守位: 104.00 USDT");
        assertThat(plainText).contains("当前浮动R: 1.70R");
    }

    @Test
    void shouldRenderTrailingStopExitTemplate() {
        AlertNotifier notifier = notifier();
        AlertNotificationService service = service(notifier);

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTC trailing stop exit",
                kline("106.20", "106.40", "105.50", "105.80", "91000"),
                "EXIT_TRAILING_STOP_LONG",
                "price pulled back into the trailing stop",
                new BigDecimal("106.00"),
                new BigDecimal("106.00"),
                new BigDecimal("115.00"),
                new BigDecimal("0.95"),
                null,
                "entryType=CONFIRMED_BREAKOUT_LONG | activeStop=106.00 | addOns=1 | reason=STOP",
                new BigDecimal("100.00"),
                new BigDecimal("95.00")
        );

        service.send(signal);

        String plainText = captureMessage(notifier).plainTextContent();
        assertThat(plainText).contains("trailing stop 已触发");
        assertThat(plainText).contains("当前防守位: 106.00 USDT");
        assertThat(plainText).contains("当前浮动R: 1.16R");
    }

    private AlertNotificationService service(AlertNotifier notifier) {
        AlertNotificationService service = new AlertNotificationService(List.of(notifier));
        ReflectionTestUtils.setField(service, "notificationChannel", "feishu");
        return service;
    }

    private AlertNotifier notifier() {
        AlertNotifier notifier = mock(AlertNotifier.class);
        when(notifier.channelName()).thenReturn("feishu");
        return notifier;
    }

    private NotificationMessage captureMessage(AlertNotifier notifier) {
        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifier).send(captor.capture());
        return captor.getValue();
    }

    private BinanceKlineDTO kline(String open, String high, String low, String close, String volume) {
        BinanceKlineDTO kline = new BinanceKlineDTO();
        kline.setSymbol("BTCUSDT");
        kline.setInterval("4h");
        kline.setOpen(open);
        kline.setHigh(high);
        kline.setLow(low);
        kline.setClose(close);
        kline.setVolume(volume);
        kline.setEndTime(System.currentTimeMillis());
        return kline;
    }
}
