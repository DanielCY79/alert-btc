package com.mobai.alert.notification;

import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.strategy.config.StrategyMetadata;
import com.mobai.alert.notification.channel.AlertNotifier;
import com.mobai.alert.notification.model.NotificationMessage;
import com.mobai.alert.strategy.model.AlertSignal;
import com.mobai.alert.strategy.model.TradeDirection;
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

        NotificationMessage message = captureMessage(notifier);
        String plainText = message.plainTextContent();
        assertThat(plainText).contains("BTCUSDT");
        assertThat(plainText).contains("confirmed breakout long");
        assertThat(message.cardTitle()).contains("BTCUSDT");
        assertThat(message.headerTemplate()).isEqualTo(NotificationMessage.HeaderTemplate.GREEN);
        assertThat(plainText).contains("挂单方案");
        assertThat(plainText).contains("风控提示");
    }

    @Test
    void shouldUseRedCardHeaderForShortSignal() {
        AlertNotifier notifier = notifier();
        AlertNotificationService service = service(notifier);

        AlertSignal signal = new AlertSignal(
                TradeDirection.SHORT,
                "BTC confirmed breakout short",
                kline("110.00", "111.00", "101.00", "102.00", "120000"),
                "CONFIRMED_BREAKOUT_SHORT",
                "price accepted below the range",
                new BigDecimal("101.50"),
                new BigDecimal("105.00"),
                new BigDecimal("94.00"),
                new BigDecimal("2.10"),
                new BigDecimal("0.78"),
                "trendBias=-0.75"
        );

        service.send(signal);

        NotificationMessage message = captureMessage(notifier);
        assertThat(message.plainTextContent()).contains("confirmed breakout short");
        assertThat(message.cardTitle()).contains("BTCUSDT");
        assertThat(message.headerTemplate()).isEqualTo(NotificationMessage.HeaderTemplate.RED);
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
    void shouldRenderConflictReduceExitTemplate() {
        AlertNotifier notifier = notifier();
        AlertNotificationService service = service(notifier);

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTCUSDT 高周期冲突，先减仓观察",
                kline("100.80", "101.20", "100.30", "101.00", "88000"),
                "EXIT_CONFLICT_REDUCE_LONG",
                "前序 CONFIRMED_BREAKOUT_LONG 持仓期间，出现与大级别方向相悖的 SECOND_ENTRY_SHORT，当前更适合先减仓 50%，保留主趋势仓位观察，而不是直接反手。",
                new BigDecimal("101.00"),
                new BigDecimal("95.00"),
                new BigDecimal("115.00"),
                new BigDecimal("0.92"),
                null,
                "entryType=CONFIRMED_BREAKOUT_LONG | remainingSize=0.50 | conflictReduced=true | reason=CONFLICT_REDUCE",
                new BigDecimal("100.00"),
                new BigDecimal("95.00")
        );

        service.send(signal);

        String plainText = captureMessage(notifier).plainTextContent();
        assertThat(plainText).contains("高周期冲突，先减仓观察");
        assertThat(plainText).contains("先减仓降暴露");
        assertThat(plainText).contains("与高一级别背景相冲突的反向信号");
        assertThat(plainText).contains("保留主趋势仓位观察");
        assertThat(plainText).contains("当前浮动R: 0.20R");
    }

    @Test
    void shouldRenderConflictTightenStopExitTemplate() {
        AlertNotifier notifier = notifier();
        AlertNotificationService service = service(notifier);

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTCUSDT 高周期冲突，收紧防守",
                kline("100.90", "101.40", "100.50", "101.20", "86000"),
                "EXIT_CONFLICT_TIGHTEN_STOP_LONG",
                "前序 CONFIRMED_BREAKOUT_LONG 持仓期间，出现与大级别方向相悖的 SECOND_ENTRY_SHORT，当前更适合把防守位从 95.00 收紧到 100.00，先保护仓位，再等市场给出更清晰的答案。",
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("115.00"),
                new BigDecimal("0.88"),
                null,
                "entryType=CONFIRMED_BREAKOUT_LONG | activeStop=100.00 | conflictStopTightened=true | reason=CONFLICT_TIGHTEN_STOP",
                new BigDecimal("100.00"),
                new BigDecimal("95.00")
        );

        service.send(signal);

        String plainText = captureMessage(notifier).plainTextContent();
        assertThat(plainText).contains("高周期冲突，收紧防守");
        assertThat(plainText).contains("把止损收紧到新的防守位");
        assertThat(plainText).contains("顺势接受度可能减弱");
        assertThat(plainText).contains("先保护仓位");
        assertThat(plainText).contains("当前防守位: 100.00 USDT");
    }

    @Test
    void shouldRenderConflictCloseExitTemplate() {
        AlertNotifier notifier = notifier();
        AlertNotificationService service = service(notifier);

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTCUSDT 高周期冲突，先退出等待",
                kline("99.60", "100.20", "99.10", "99.40", "93000"),
                "EXIT_CONFLICT_CLOSE_LONG",
                "前序 CONFIRMED_BREAKOUT_LONG 持仓期间，高低周期冲突继续扩大，并出现 SECOND_ENTRY_SHORT，当前更适合先结束剩余仓位，等待重新同向的入场机会。",
                new BigDecimal("99.40"),
                new BigDecimal("100.00"),
                new BigDecimal("115.00"),
                new BigDecimal("1.05"),
                null,
                "entryType=CONFIRMED_BREAKOUT_LONG | remainingSize=0.50 | addOnsLocked=true | reason=CONFLICT_CLOSE",
                new BigDecimal("100.00"),
                new BigDecimal("95.00")
        );

        service.send(signal);

        String plainText = captureMessage(notifier).plainTextContent();
        assertThat(plainText).contains("高周期冲突，先退出等待");
        assertThat(plainText).contains("直接结束剩余仓位");
        assertThat(plainText).contains("高低周期冲突已经升级");
        assertThat(plainText).contains("等待重新同向的入场机会");
        assertThat(plainText).contains("当前浮动R: -0.12R");
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
        AlertNotificationService service = new AlertNotificationService(List.of(notifier), new StrategyMetadata("test-strategy", "", true));
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
