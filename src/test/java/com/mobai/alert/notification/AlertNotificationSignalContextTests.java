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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 交易信号通知测试，验证通知文本包含完整的中文执行信息。
 */
class AlertNotificationSignalContextTests {

    @Test
    void shouldIncludeChinesePlaybookTemplate() {
        AlertNotifier notifier = mock(AlertNotifier.class);
        when(notifier.channelName()).thenReturn("feishu");
        AlertNotificationService service = new AlertNotificationService(List.of(notifier));
        ReflectionTestUtils.setField(service, "notificationChannel", "feishu");

        BinanceKlineDTO kline = new BinanceKlineDTO();
        kline.setSymbol("BTCUSDT");
        kline.setInterval("4h");
        kline.setOpen("100.00");
        kline.setHigh("110.00");
        kline.setLow("99.00");
        kline.setClose("108.00");
        kline.setVolume("100000");
        kline.setEndTime(System.currentTimeMillis());

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTC 确认突破做多信号",
                kline,
                "CONFIRMED_BREAKOUT_LONG",
                "价格向上有效突破区间，成交量放大至 1.80x，属于确认型突破。",
                new BigDecimal("109.00"),
                new BigDecimal("104.00"),
                new BigDecimal("118.00"),
                new BigDecimal("1.80"),
                new BigDecimal("0.73"),
                "综合环境分 0.73 | 趋势偏置=0.70"
        );

        service.send(signal);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifier).send(captor.capture());

        String plainText = captor.getValue().plainTextContent();
        assertTrue(plainText.contains("BTCUSDT 交易作战卡"));
        assertTrue(plainText.contains("挂单方案:"));
        assertTrue(plainText.contains("放弃条件:"));
        assertTrue(plainText.contains("仓位方案:"));
        assertTrue(plainText.contains("追单提示:"));
        assertTrue(plainText.contains("风控提示:"));
        assertTrue(plainText.contains("环境分: 0.73"));
        assertTrue(plainText.contains("环境说明: 综合环境分 0.73 | 趋势偏置=0.70"));
        assertTrue(plainText.contains("BTC 确认突破做多信号"));
    }

    @Test
    void shouldRenderPullbackSpecificChinesePlaybook() {
        AlertNotifier notifier = mock(AlertNotifier.class);
        when(notifier.channelName()).thenReturn("feishu");
        AlertNotificationService service = new AlertNotificationService(List.of(notifier));
        ReflectionTestUtils.setField(service, "notificationChannel", "feishu");

        BinanceKlineDTO kline = new BinanceKlineDTO();
        kline.setSymbol("BTCUSDT");
        kline.setInterval("4h");
        kline.setOpen("108.00");
        kline.setHigh("110.00");
        kline.setLow("107.20");
        kline.setClose("109.10");
        kline.setVolume("85000");
        kline.setEndTime(System.currentTimeMillis());

        AlertSignal signal = new AlertSignal(
                TradeDirection.LONG,
                "BTC 突破回踩做多信号",
                kline,
                "BREAKOUT_PULLBACK_LONG",
                "价格回踩突破位后守住支撑并重新收强，属于突破后的回踩确认做多。",
                new BigDecimal("108.50"),
                new BigDecimal("107.20"),
                new BigDecimal("112.40"),
                new BigDecimal("0.90"),
                new BigDecimal("0.68"),
                "综合环境分 0.68 | 突破确认=0.62"
        );

        service.send(signal);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifier).send(captor.capture());

        String plainText = captor.getValue().plainTextContent();
        assertTrue(plainText.contains("突破后回踩延续"));
        assertTrue(plainText.contains("在触发区附近分批限价吸入"));
        assertTrue(plainText.contains("账户风险 0.50%-0.70%"));
        assertTrue(plainText.contains("环境说明: 综合环境分 0.68 | 突破确认=0.62"));
    }
}
