package com.mobai.alert.notification;

import com.mobai.alert.access.event.binance.cms.dto.BinanceAnnouncementDTO;
import com.mobai.alert.access.event.dto.MarketEventDTO;
import com.mobai.alert.access.kline.dto.BinanceKlineDTO;
import com.mobai.alert.notification.channel.AlertNotifier;
import com.mobai.alert.notification.model.NotificationMessage;
import com.mobai.alert.state.signal.AlertSignal;
import com.mobai.alert.state.signal.TradeDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationService.class);
    private static final long COOLDOWN_PERIOD = 2 * 60 * 60 * 1000L;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal SAFE_CHASE_DISTANCE_PCT = new BigDecimal("0.30");
    private static final BigDecimal SKIP_ENTRY_EXTENSION_PCT = new BigDecimal("0.80");
    private static final BigDecimal HIGH_CONFIDENCE_SCORE = new BigDecimal("0.75");
    private static final BigDecimal MEDIUM_CONFIDENCE_SCORE = new BigDecimal("0.65");
    private static final BigDecimal LOW_CONFIDENCE_SCORE = new BigDecimal("0.55");
    private static final BigDecimal WIDE_STOP_PCT = new BigDecimal("2.50");
    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final List<AlertNotifier> alertNotifiers;
    private final Map<String, AlertNotifier> notifierByChannel;
    private final Map<String, Long> sentRecords = new ConcurrentHashMap<>();

    @Value("${notification.channel:feishu}")
    private String notificationChannel;

    public AlertNotificationService(List<AlertNotifier> alertNotifiers) {
        this.alertNotifiers = alertNotifiers;
        this.notifierByChannel = new ConcurrentHashMap<>();
        for (AlertNotifier alertNotifier : alertNotifiers) {
            notifierByChannel.put(normalizeChannelName(alertNotifier.channelName()), alertNotifier);
        }
    }

    public void send(AlertSignal signal) {
        String recordKey = signal.getKline().getSymbol() + signal.getType();
        NotificationMessage message = buildSignalMessage(signal);
        send(recordKey, message, "策略信号", signal.getKline().getSymbol(), signal.getType());
    }

    public void sendAnnouncement(BinanceAnnouncementDTO announcement) {
        String title = StringUtils.hasText(announcement.getTitle()) ? announcement.getTitle() : "Binance 公告";
        long publishDate = announcement.getPublishDate() == null ? 0L : announcement.getPublishDate();
        String recordKey = "announcement:" + publishDate + ":" + title;
        NotificationMessage message = buildAnnouncementMessage(announcement);
        send(recordKey, message, "公告", announcement.getTopic(), title);
    }

    public void sendMarketEvent(MarketEventDTO event, String title, String url) {
        if (event == null) {
            return;
        }
        String normalizedTitle = StringUtils.hasText(title) ? title : event.getRawText();
        long eventTime = event.getEventTime() == null ? 0L : event.getEventTime().toEpochMilli();
        String recordKey = "market-event:" + event.getSource() + ":" + eventTime + ":" + normalizedTitle;
        NotificationMessage message = buildMarketEventMessage(event, normalizedTitle, url);
        send(recordKey, message, "市场事件", event.getSource(), normalizedTitle);
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanupExpiredRecords() {
        long currentTime = System.currentTimeMillis();
        int before = sentRecords.size();
        sentRecords.entrySet().removeIf(entry -> currentTime - entry.getValue() > COOLDOWN_PERIOD);
        int removed = before - sentRecords.size();
        if (removed > 0) {
            log.info("已清理 {} 条过期通知冷却记录", removed);
        }
    }

    private boolean allowSend(String recordKey) {
        long currentTime = System.currentTimeMillis();
        Long lastSentTime = sentRecords.get(recordKey);
        if (lastSentTime == null || currentTime - lastSentTime > COOLDOWN_PERIOD) {
            sentRecords.put(recordKey, currentTime);
            return true;
        }
        return false;
    }

    private NotificationMessage buildSignalMessage(AlertSignal signal) {
        BinanceKlineDTO kline = signal.getKline();
        BigDecimal closePrice = decimal(kline.getClose(), 2);
        BigDecimal amplitude = calculateAmplitude(kline).multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volume = convertToWan(decimal(kline.getVolume(), 0));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime klineTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.getEndTime()), ZoneId.systemDefault());
        String interval = StringUtils.hasText(kline.getInterval()) ? kline.getInterval() : "15m";
        String symbol = kline.getSymbol();
        String chartUrl = buildChartUrl(symbol, interval);
        String setup = setupLabel(signal);
        String direction = directionLabel(signal.getDirection());
        String action = executionInstruction(signal);
        String orderPlan = orderPlan(signal, closePrice);
        String abortIf = abortRule(signal, closePrice);
        String sizePlan = sizePlan(signal);
        String chaseRule = chaseWarning(signal, closePrice);
        String riskRule = riskInstruction(signal);
        String checklist = orderChecklist();
        String triggerDistance = formatNullablePercent(triggerDistancePct(signal, closePrice));
        String riskReward = formatNullableMultiple(estimatedRiskReward(signal));

        String markdownContent = new StringBuilder()
                .append(symbol).append(" 交易作战卡\n")
                .append("> 信号: ").append(defaultText(signal.getTitle(), signal.getType())).append("\n")
                .append("> 形态: ").append(setup).append("\n")
                .append("> 方向: ").append(direction).append("\n")
                .append("> 动作: ").append(action).append("\n")
                .append("> 挂单方案: ").append(orderPlan).append("\n")
                .append("> 放弃条件: ").append(abortIf).append("\n")
                .append("> 仓位方案: ").append(sizePlan).append("\n")
                .append("> 触发位: ").append(formatNullablePrice(signal.getTriggerPrice())).append("\n")
                .append("> 止损位: ").append(formatNullablePrice(signal.getInvalidationPrice())).append("\n")
                .append("> 目标位: ").append(formatNullablePrice(signal.getTargetPrice())).append("\n")
                .append("> 当前价: ").append(closePrice).append(" USDT\n")
                .append("> 距触发位: ").append(triggerDistance).append("\n")
                .append("> 预估盈亏比: ").append(riskReward).append("\n")
                .append("> 追单提示: ").append(chaseRule).append("\n")
                .append("> 风控提示: ").append(riskRule).append("\n")
                .append("> 下单清单: ").append(checklist).append("\n")
                .append("> 周期: ").append(interval).append("\n")
                .append("> 振幅: ").append(amplitude).append("%\n")
                .append("> 成交额: ").append(volume).append(" 万USDT\n")
                .append("> 放量倍数: ").append(formatNullableRatio(signal.getVolumeRatio())).append("\n")
                .append("> 环境分: ").append(formatNullableRatioLike(signal.getContextScore())).append("\n")
                .append("> 策略逻辑: ").append(defaultText(signal.getSummary(), "-")).append("\n")
                .append("> 环境说明: ").append(defaultText(signal.getContextComment(), "-")).append("\n")
                .append("> 发送时间: ").append(MESSAGE_TIME_FORMATTER.format(now)).append("\n")
                .append("> K线收盘: ").append(MESSAGE_TIME_FORMATTER.format(klineTime)).append("\n")
                .append("[打开图表](").append(chartUrl).append(")")
                .toString();

        String plainTextContent = new StringBuilder()
                .append(symbol).append(" 交易作战卡\n")
                .append("信号: ").append(defaultText(signal.getTitle(), signal.getType())).append("\n")
                .append("形态: ").append(setup).append("\n")
                .append("方向: ").append(direction).append("\n")
                .append("动作: ").append(action).append("\n")
                .append("挂单方案: ").append(orderPlan).append("\n")
                .append("放弃条件: ").append(abortIf).append("\n")
                .append("仓位方案: ").append(sizePlan).append("\n")
                .append("触发位: ").append(formatNullablePrice(signal.getTriggerPrice())).append("\n")
                .append("止损位: ").append(formatNullablePrice(signal.getInvalidationPrice())).append("\n")
                .append("目标位: ").append(formatNullablePrice(signal.getTargetPrice())).append("\n")
                .append("当前价: ").append(closePrice).append(" USDT\n")
                .append("距触发位: ").append(triggerDistance).append("\n")
                .append("预估盈亏比: ").append(riskReward).append("\n")
                .append("追单提示: ").append(chaseRule).append("\n")
                .append("风控提示: ").append(riskRule).append("\n")
                .append("下单清单: ").append(checklist).append("\n")
                .append("周期: ").append(interval).append("\n")
                .append("振幅: ").append(amplitude).append("%\n")
                .append("成交额: ").append(volume).append(" 万USDT\n")
                .append("放量倍数: ").append(formatNullableRatio(signal.getVolumeRatio())).append("\n")
                .append("环境分: ").append(formatNullableRatioLike(signal.getContextScore())).append("\n")
                .append("策略逻辑: ").append(defaultText(signal.getSummary(), "-")).append("\n")
                .append("环境说明: ").append(defaultText(signal.getContextComment(), "-")).append("\n")
                .append("发送时间: ").append(MESSAGE_TIME_FORMATTER.format(now)).append("\n")
                .append("K线收盘: ").append(MESSAGE_TIME_FORMATTER.format(klineTime)).append("\n")
                .append("图表: ").append(chartUrl)
                .toString();

        return new NotificationMessage(markdownContent, plainTextContent);
    }

    private NotificationMessage buildAnnouncementMessage(BinanceAnnouncementDTO announcement) {
        LocalDateTime publishTime = announcement.getPublishDate() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(announcement.getPublishDate()), ZoneId.systemDefault());
        String topic = StringUtils.hasText(announcement.getTopic()) ? announcement.getTopic() : "默认公告流";
        String catalogName = StringUtils.hasText(announcement.getCatalogName()) ? announcement.getCatalogName() : "-";
        String title = StringUtils.hasText(announcement.getTitle()) ? announcement.getTitle() : "Binance 公告";
        String body = abbreviate(cleanText(announcement.getBody()), 900);
        String disclaimer = abbreviate(cleanText(announcement.getDisclaimer()), 200);
        String url = "https://www.binance.com/en/support/announcement";

        StringBuilder markdownBuilder = new StringBuilder()
                .append("Binance 公告\n")
                .append("> 主题: ").append(topic).append("\n")
                .append("> 分类: ").append(catalogName).append("\n")
                .append("> 发布时间: ").append(MESSAGE_TIME_FORMATTER.format(publishTime)).append("\n")
                .append("> 标题: ").append(title).append("\n");
        if (StringUtils.hasText(body)) {
            markdownBuilder.append("> 正文: ").append(body).append("\n");
        }
        if (StringUtils.hasText(disclaimer)) {
            markdownBuilder.append("> 提示: ").append(disclaimer).append("\n");
        }
        markdownBuilder.append("[打开公告](").append(url).append(")");

        StringBuilder plainTextBuilder = new StringBuilder()
                .append("Binance 公告\n")
                .append("主题: ").append(topic).append("\n")
                .append("分类: ").append(catalogName).append("\n")
                .append("发布时间: ").append(MESSAGE_TIME_FORMATTER.format(publishTime)).append("\n")
                .append("标题: ").append(title).append("\n");
        if (StringUtils.hasText(body)) {
            plainTextBuilder.append("正文: ").append(body).append("\n");
        }
        if (StringUtils.hasText(disclaimer)) {
            plainTextBuilder.append("提示: ").append(disclaimer).append("\n");
        }
        plainTextBuilder.append("打开公告: ").append(url);

        return new NotificationMessage(markdownBuilder.toString(), plainTextBuilder.toString());
    }

    private NotificationMessage buildMarketEventMessage(MarketEventDTO event, String title, String url) {
        LocalDateTime publishTime = event.getEventTime() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(event.getEventTime(), ZoneId.systemDefault());
        String source = StringUtils.hasText(event.getSource()) ? event.getSource() : "market_event";
        String cleanTitle = StringUtils.hasText(title) ? title : "市场事件";
        String rawText = abbreviate(cleanText(event.getRawText()), 900);

        StringBuilder markdownBuilder = new StringBuilder()
                .append("市场事件\n")
                .append("> 来源: ").append(source).append("\n")
                .append("> 发布时间: ").append(MESSAGE_TIME_FORMATTER.format(publishTime)).append("\n")
                .append("> 标题: ").append(cleanTitle).append("\n")
                .append("> 实体: ").append(defaultText(event.getEntity(), "市场")).append("\n")
                .append("> 事件类型: ").append(defaultText(event.getEventType(), "新闻")).append("\n")
                .append("> 情绪: ").append(defaultText(event.getSentiment(), "中性")).append("\n")
                .append("> 置信度: ").append(formatNullableScore(event.getConfidence())).append("\n")
                .append("> 新颖度: ").append(formatNullableScore(event.getNovelty())).append("\n")
                .append("> 传播速度: ").append(formatNullableScore(event.getMentionVelocity())).append("\n");
        if (StringUtils.hasText(rawText)) {
            markdownBuilder.append("> 摘要: ").append(rawText).append("\n");
        }
        if (StringUtils.hasText(url)) {
            markdownBuilder.append("[打开原文](").append(url).append(")");
        }

        StringBuilder plainTextBuilder = new StringBuilder()
                .append("市场事件\n")
                .append("来源: ").append(source).append("\n")
                .append("发布时间: ").append(MESSAGE_TIME_FORMATTER.format(publishTime)).append("\n")
                .append("标题: ").append(cleanTitle).append("\n")
                .append("实体: ").append(defaultText(event.getEntity(), "市场")).append("\n")
                .append("事件类型: ").append(defaultText(event.getEventType(), "新闻")).append("\n")
                .append("情绪: ").append(defaultText(event.getSentiment(), "中性")).append("\n")
                .append("置信度: ").append(formatNullableScore(event.getConfidence())).append("\n")
                .append("新颖度: ").append(formatNullableScore(event.getNovelty())).append("\n")
                .append("传播速度: ").append(formatNullableScore(event.getMentionVelocity())).append("\n");
        if (StringUtils.hasText(rawText)) {
            plainTextBuilder.append("摘要: ").append(rawText).append("\n");
        }
        if (StringUtils.hasText(url)) {
            plainTextBuilder.append("打开原文: ").append(url);
        }

        return new NotificationMessage(markdownBuilder.toString(), plainTextBuilder.toString());
    }

    private String directionLabel(TradeDirection direction) {
        if (direction == null) {
            return "观望";
        }
        return direction == TradeDirection.LONG ? "做多" : "做空";
    }

    private String setupLabel(AlertSignal signal) {
        if (signal == null || !StringUtils.hasText(signal.getType())) {
            return "未分类";
        }
        if (isBreakout(signal)) {
            return "趋势确认突破";
        }
        if (isPullback(signal)) {
            return "突破后回踩延续";
        }
        if (isRangeFailure(signal)) {
            return "区间失败反转";
        }
        return signal.getType();
    }

    private String executionInstruction(AlertSignal signal) {
        if (signal == null) {
            return "等待下一次有效信号。";
        }
        String baseAction = signal.getDirection() == TradeDirection.LONG ? "计划做多" : "计划做空";
        if (isBreakout(signal)) {
            return baseAction + "，优先等价格贴近触发位后执行，不在明显拉开后追单。";
        }
        if (isPullback(signal)) {
            return baseAction + "，等价格回踩触发区并重新收强后再执行。";
        }
        if (isRangeFailure(signal)) {
            return baseAction + "，等价格重新站回失败侧并确认反转后再执行。";
        }
        return baseAction + "，只在价格贴近触发位时执行。";
    }

    private String orderPlan(AlertSignal signal, BigDecimal closePrice) {
        if (signal == null) {
            return "暂无挂单方案，等待下一次有效信号。";
        }

        BigDecimal distancePct = triggerDistancePct(signal, closePrice);
        boolean nearTrigger = distancePct != null && distancePct.compareTo(SAFE_CHASE_DISTANCE_PCT) <= 0;

        if (isBreakout(signal)) {
            return nearTrigger
                    ? "离触发位较近时，可用止损限价单或回踩挂单进场；先上 50% 计划仓位，确认突破位站稳后再补剩余 50%。"
                    : "不要在远离触发位时市价追单；把挂单放回触发位附近，只在回踩承接干净时成交。";
        }
        if (isPullback(signal)) {
            return "在触发区附近分批限价吸入；先上 60% 计划仓位，回踩 K 线重新收强后再补剩余 40%。";
        }
        if (isRangeFailure(signal)) {
            return "按假跌破收回区间处理；先用 50% 试探仓位进场，下一根 K 线继续留在区间内再补仓。";
        }
        return "围绕触发位挂单，避免情绪化追价。";
    }

    private String abortRule(AlertSignal signal, BigDecimal closePrice) {
        if (signal == null) {
            return "暂无放弃条件。";
        }

        BigDecimal distancePct = triggerDistancePct(signal, closePrice);
        String stopText = formatNullablePrice(signal.getInvalidationPrice());
        if (isBreakout(signal)) {
            if (distancePct != null && distancePct.compareTo(SKIP_ENTRY_EXTENSION_PCT) > 0) {
                return "若未成交前价格已经离触发位拉开超过 " + formatNullablePercent(SKIP_ENTRY_EXTENSION_PCT)
                        + "，直接放弃；若后续收盘重新跌回触发区或跌破 " + stopText + "，也放弃。";
            }
            return "若下一根收盘重新跌回触发区、收盘跌破 " + stopText + "，或突破后没有延续而是立刻反包，直接放弃。";
        }
        if (isPullback(signal)) {
            return "若回踩收盘跌破 " + stopText + "，或反弹 K 线没有重新收强，或回踩放量转成明显抛压，直接放弃。";
        }
        if (isRangeFailure(signal)) {
            return "若价格再次跌回失败侧、收盘跌破 " + stopText + "，或收回区间后下一根没有延续，直接放弃。";
        }
        return "若失效位被打穿，或价格不再尊重触发位，直接放弃。";
    }

    private String sizePlan(AlertSignal signal) {
        if (signal == null) {
            return "暂无仓位建议。";
        }

        BigDecimal score = signal.getContextScore();
        BigDecimal stopDistance = stopDistancePct(signal);
        String scoreText = formatNullableRatioLike(score);
        String stopText = formatNullablePercent(stopDistance);

        if (score != null && score.compareTo(HIGH_CONFIDENCE_SCORE) >= 0) {
            return baseSizeMessage("账户风险 0.80%-1.00%", scoreText, stopText, stopDistance);
        }
        if (score != null && score.compareTo(MEDIUM_CONFIDENCE_SCORE) >= 0) {
            return baseSizeMessage("账户风险 0.50%-0.70%", scoreText, stopText, stopDistance);
        }
        if (score != null && score.compareTo(LOW_CONFIDENCE_SCORE) >= 0) {
            return baseSizeMessage("账户风险 0.25%-0.40%", scoreText, stopText, stopDistance);
        }
        return "环境分偏弱或缺失，仅用试探仓，单笔风险控制在账户权益 0.25% 左右，等形态继续证明自己。";
    }

    private String chaseWarning(AlertSignal signal, BigDecimal closePrice) {
        if (signal == null || signal.getTriggerPrice() == null || closePrice == null) {
            return "缺少触发位，先确认图表后再下单。";
        }
        BigDecimal distancePct = triggerDistancePct(signal, closePrice);
        if (distancePct == null) {
            return "缺少触发位，先确认图表后再下单。";
        }
        boolean aboveTrigger = closePrice.compareTo(signal.getTriggerPrice()) > 0;
        if (distancePct.compareTo(SAFE_CHASE_DISTANCE_PCT) <= 0) {
            return "现价离触发位仍近，可在触发区附近工作挂单，不要被短线波动带着追价。";
        }
        if (signal.getDirection() == TradeDirection.LONG) {
            return aboveTrigger
                    ? "现价已经高于触发位 " + formatNullablePercent(distancePct) + "，不要追多，等待回踩。"
                    : "现价仍低于触发位 " + formatNullablePercent(distancePct) + "，先等真正触发确认。";
        }
        return aboveTrigger
                ? "现价仍高于触发位 " + formatNullablePercent(distancePct) + "，先等真正触发确认。"
                : "现价已经低于触发位 " + formatNullablePercent(distancePct) + "，不要追空，等待反抽。";
    }

    private String riskInstruction(AlertSignal signal) {
        BigDecimal stopDistance = stopDistancePct(signal);
        BigDecimal riskReward = estimatedRiskReward(signal);
        return "单笔风险建议控制在账户权益 0.5%-1.0%，先按止损距离 "
                + formatNullablePercent(stopDistance)
                + " 反推仓位，只在预估盈亏比接近 "
                + formatNullableMultiple(riskReward)
                + " 或更高时执行。";
    }

    private String orderChecklist() {
        return "确认触发位、挂单后立即带止损、第一目标前不随意加仓，失效位被打到时无条件退出。";
    }

    private String baseSizeMessage(String riskBudget, String scoreText, String stopText, BigDecimal stopDistance) {
        StringBuilder builder = new StringBuilder()
                .append("建议最大单笔风险控制在")
                .append(riskBudget)
                .append("；当前环境分=")
                .append(scoreText)
                .append("，止损距离=")
                .append(stopText)
                .append("。");
        if (stopDistance != null && stopDistance.compareTo(WIDE_STOP_PCT) > 0) {
            builder.append(" 由于止损较宽，最终仓位再额外减半。");
        }
        return builder.toString();
    }

    private String buildChartUrl(String symbol, String interval) {
        return "https://www.binance.com/en/futures/"
                + symbol
                + "?type=spot&layout=pro&interval="
                + interval;
    }

    private BigDecimal calculateAmplitude(BinanceKlineDTO kline) {
        BigDecimal high = new BigDecimal(kline.getHigh());
        BigDecimal low = new BigDecimal(kline.getLow());
        return high.subtract(low).abs().divide(low, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal convertToWan(BigDecimal amount) {
        return amount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(String value, int scale) {
        return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private String formatNullablePrice(BigDecimal price) {
        if (price == null) {
            return "-";
        }
        return price.setScale(2, RoundingMode.HALF_UP) + " USDT";
    }

    private String formatNullableRatio(BigDecimal ratio) {
        if (ratio == null) {
            return "-";
        }
        return ratio.setScale(2, RoundingMode.HALF_UP) + "x";
    }

    private String formatNullableRatioLike(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatNullablePercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String formatNullableMultiple(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "R";
    }

    private String formatNullableScore(Double score) {
        if (score == null) {
            return "-";
        }
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String normalizeChannelName(String channelName) {
        if (channelName == null) {
            return "";
        }
        return channelName.trim().toLowerCase(Locale.ROOT);
    }

    private void send(String recordKey, NotificationMessage message, String category, String subject, String detail) {
        if (!allowSend(recordKey)) {
            log.info("跳过发送{}，仍处于冷却期，recordKey={}, subject={}, detail={}",
                    category,
                    recordKey,
                    subject,
                    detail);
            return;
        }

        if (CollectionUtils.isEmpty(alertNotifiers)) {
            log.warn("未配置通知通道，跳过发送{}，subject={}, detail={}",
                    category,
                    subject,
                    detail);
            return;
        }

        String selectedChannel = normalizeChannelName(notificationChannel);
        AlertNotifier alertNotifier = notifierByChannel.get(selectedChannel);
        if (alertNotifier == null) {
            log.warn("未匹配到通知通道，channel={}, availableChannels={}",
                    selectedChannel,
                    notifierByChannel.keySet());
            return;
        }

        log.info("发送{}，channel={}, subject={}, detail={}",
                category,
                selectedChannel,
                subject,
                detail);

        try {
            alertNotifier.send(message);
        } catch (Exception e) {
            log.error("发送{}失败，channel={}, subject={}, detail={}",
                    category,
                    alertNotifier.channelName(),
                    subject,
                    detail,
                    e);
        }
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private BigDecimal triggerDistancePct(AlertSignal signal, BigDecimal closePrice) {
        if (signal == null || signal.getTriggerPrice() == null || closePrice == null
                || signal.getTriggerPrice().compareTo(ZERO) == 0) {
            return null;
        }
        return closePrice.subtract(signal.getTriggerPrice())
                .abs()
                .divide(signal.getTriggerPrice(), 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED);
    }

    private BigDecimal stopDistancePct(AlertSignal signal) {
        if (signal == null || signal.getTriggerPrice() == null || signal.getInvalidationPrice() == null
                || signal.getTriggerPrice().compareTo(ZERO) == 0) {
            return null;
        }
        return signal.getTriggerPrice().subtract(signal.getInvalidationPrice())
                .abs()
                .divide(signal.getTriggerPrice(), 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED);
    }

    private BigDecimal estimatedRiskReward(AlertSignal signal) {
        if (signal == null || signal.getTriggerPrice() == null || signal.getInvalidationPrice() == null || signal.getTargetPrice() == null) {
            return null;
        }
        BigDecimal risk = signal.getTriggerPrice().subtract(signal.getInvalidationPrice()).abs();
        if (risk.compareTo(ZERO) == 0) {
            return null;
        }
        BigDecimal reward = signal.getTargetPrice().subtract(signal.getTriggerPrice()).abs();
        return reward.divide(risk, 4, RoundingMode.HALF_UP);
    }

    private boolean isBreakout(AlertSignal signal) {
        return signal != null && StringUtils.hasText(signal.getType()) && signal.getType().startsWith("CONFIRMED_BREAKOUT");
    }

    private boolean isPullback(AlertSignal signal) {
        return signal != null && StringUtils.hasText(signal.getType()) && signal.getType().startsWith("BREAKOUT_PULLBACK");
    }

    private boolean isRangeFailure(AlertSignal signal) {
        return signal != null && StringUtils.hasText(signal.getType()) && signal.getType().startsWith("RANGE_FAILURE");
    }
}
