package com.mobai.alert.service;

import com.alibaba.fastjson.JSON;
import com.mobai.alert.api.BinanceApi;
import com.mobai.alert.api.EnterpriseWechatApi;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDTO;
import com.mobai.alert.dto.BinanceSymbolsDetailDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class AlertService {
    @Value("${monitoring.kline.interval}")
    private String interval;

    @Value("${monitoring.exclude.symbol}")
    private String excludeSymbol;

    @Value("${monitoring.rate.low}")
    private String rateLow;
    @Value("${monitoring.rate.back.low}")
    private String rateBackLow;

    @Value("${monitoring.rate.high}")
    private String rateHigh;

    @Value("${monitoring.alert.volume.low}")
    private String volumeLow;
    @Value("${monitoring.alert.volume.back.low}")
    private String volumeBackLow;

    @Value("${monitoring.alert.volume.high}")
    private String volumeHigh;

    @Value("${monitoring.rate.two.low}")
    private String twoRateLow;

    @Value("${monitoring.volume.two.low}")
    private String twoVolumeLow;

    // C:\Users\Administrator\Codes\alert\src\main\resources\application.properties
    private String filePath = "C:\\Users\\Administrator\\Codes\\alert\\src\\main\\resources\\symbolsCache.json";

    @Autowired
    private BinanceApi binanceApi;

    @Autowired
    private EnterpriseWechatApi enterpriseWechatApi;

    private static final ConcurrentHashMap<String, Long> smsRecords = new ConcurrentHashMap<>();
    private static final long COOLDOWN_PERIOD = 2*60 * 60 * 1000; // 120分钟冷却时间（毫秒）
    /** 回踩相关参数逻辑 **/
    private static final ConcurrentHashMap<String, Long> backRecords = new ConcurrentHashMap<>();
    private static final long BACK_COOLDOWN_PERIOD = 2*60 * 60 * 1000; // 120分钟冷却时间（毫秒）
    static {
        // 定时清理过期记录（每5分钟执行一次）
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            smsRecords.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > COOLDOWN_PERIOD
            );
            backRecords.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > BACK_COOLDOWN_PERIOD
            );
        }, 0, 5, TimeUnit.MINUTES);
    }

    public static synchronized boolean allowSend(String phoneNumber) {
        long currentTime = System.currentTimeMillis();
        Long lastSentTime = smsRecords.get(phoneNumber);

        if (lastSentTime == null || currentTime - lastSentTime > COOLDOWN_PERIOD) {
            smsRecords.put(phoneNumber, currentTime);
            return true;
        }
        return false;
    }


    @Scheduled(fixedRate = 60000)
    public void monitoring() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println("定时任务开始 " + formatter.format(LocalDateTime.now()));
        BinanceSymbolsDTO symbolsDTO = null;
        try {
            symbolsDTO = this.checkAndUpdateFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(symbolsDTO == null){
            return;
        }
        List<BinanceSymbolsDetailDTO> symbols = symbolsDTO.getSymbols();

        processUsersWithCompletableFuture(symbols, 4);
//        for (BinanceSymbolsDetailDTO symbolDTO : symbols) {
//            process(symbolDTO);
//
//        }
        System.out.println("定时任务结束 " + formatter.format(LocalDateTime.now()));
    }

    private void processUsersWithCompletableFuture(List<BinanceSymbolsDetailDTO> userList, int threadCount) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            List<CompletableFuture<Void>> futures = userList.stream()
                    .map(user -> CompletableFuture.runAsync(() -> process(user), executor))
                    .collect(Collectors.toList());

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } finally {
            executor.shutdown();
        }
    }

    private void process(BinanceSymbolsDetailDTO symbolDTO) {
        String symbol = symbolDTO.getSymbol();
        if(!symbol.contains("USDT")){
            return;
        }
        String[] excludeSymbolArrs = excludeSymbol.split(",");
        List<String> excludeList = Arrays.asList(excludeSymbolArrs);
        if (excludeList.contains(symbol)) {
            return;         }
        String status = symbolDTO.getStatus();
        if (!Objects.equals(status, "TRADING")) {

            return;
        }

        // 老逻辑
        BinanceKlineDTO reqDTO = new BinanceKlineDTO();
        reqDTO.setSymbol(symbol);
        reqDTO.setInterval("1m");
        reqDTO.setLimit(5);
        reqDTO.setTimeZone("8");
        Instant now = Instant.now();
        Instant previousMinute = now.minus(1, ChronoUnit.MINUTES);
        long previousMinuteMillis = previousMinute.toEpochMilli();
        reqDTO.setEndTime(System.currentTimeMillis());
        reqDTO.setStartTime(previousMinuteMillis);
        long currentTimeMillis = System.currentTimeMillis();
        List<BinanceKlineDTO> binanceKlineDTOSTmp = binanceApi.listKline(reqDTO);
        if(CollectionUtils.isEmpty(binanceKlineDTOSTmp)){
            return;
        }

        // 去除未完成的K线
        BinanceKlineDTO lastKline = binanceKlineDTOSTmp.get(3);
        List<BinanceKlineDTO> binanceKlineThree;
        List<BinanceKlineDTO> binanceKlineBackThree;
        List<BinanceKlineDTO> binanceKlineTwo;

        // 未完成的情况
        binanceKlineThree = new ArrayList<>();
        for (int i = binanceKlineDTOSTmp.size() - 2; i >= 1; i--) {
            binanceKlineThree.add(binanceKlineDTOSTmp.get(i));
        }

        binanceKlineBackThree = new ArrayList<>();
        for (int i = binanceKlineDTOSTmp.size() - 2; i >=0; i--) {
            binanceKlineBackThree.add(binanceKlineDTOSTmp.get(i));
        }

        binanceKlineTwo = new ArrayList<>();
        for (int i = binanceKlineDTOSTmp.size() - 2; i >=2; i--) {
            binanceKlineTwo.add(binanceKlineDTOSTmp.get(i));
        }


        boolean continuousThree = true;
        for (BinanceKlineDTO binanceKlineDTO : binanceKlineThree) {
            // 判断是否满足 一般交易对通知 情况
            // 连续3根涨幅
            boolean isDoSend = this.continuousThreeDoSend(binanceKlineDTO);
            if(!isDoSend){
                continuousThree = false;
                break;
            }
        }
        if(continuousThree){
            // 满足3根K线连续涨幅
            BinanceKlineDTO binanceKlineDTO = binanceKlineDTOSTmp.get(2);
            String volume = binanceKlineDTO.getVolume();
            String high = binanceKlineDTO.getHigh();
            String low = binanceKlineDTO.getLow();
            BigDecimal highD = new BigDecimal(high);
            BigDecimal lowD = new BigDecimal(low);
            BigDecimal openD = new BigDecimal(binanceKlineDTO.getOpen());
            BigDecimal closeD = new BigDecimal(binanceKlineDTO.getClose());
            BigDecimal divide = highD.subtract(lowD).abs().divide(lowD, RoundingMode.HALF_UP);
            String rate = divide.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%";
            this.doSendNormalMsg("连续 3 根K线涨", binanceKlineDTO, symbol, closeD, rate, volume, "1");

            // 加入回踩判断中
            backRecords.put(symbol, System.currentTimeMillis());
        }


        if(backRecords.get(symbol) != null){
            BinanceKlineDTO backBinanceKlineDTO = binanceKlineBackThree.get(binanceKlineDTOSTmp.size() - 2);
            boolean backDoSend = this.isBackDoSend(backBinanceKlineDTO);

            if(backDoSend){
                BinanceKlineDTO binanceKlineDTO = binanceKlineDTOSTmp.get(2);
                String volume = binanceKlineDTO.getVolume();
                String high = binanceKlineDTO.getHigh();
                String low = binanceKlineDTO.getLow();
                BigDecimal highD = new BigDecimal(high);
                BigDecimal lowD = new BigDecimal(low);
                BigDecimal closeD = new BigDecimal(binanceKlineDTO.getClose());
                BigDecimal divide = highD.subtract(lowD).abs().divide(lowD, RoundingMode.HALF_UP);
                String rate = divide.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%";
                this.doSendNormalMsg("回踩交易对", binanceKlineDTO, symbol, closeD, rate, volume, "2");
            }
        }

        boolean continuousTwo = true;
        for (BinanceKlineDTO binanceKlineDTO : binanceKlineTwo) {
            // 判断是否满足 一般交易对通知 情况
            // 连续3根涨幅
            boolean isDoSend = this.continuousTwoDoSend(binanceKlineDTO);
            if(!isDoSend){
                continuousTwo = false;
                break;
            }
        }
        if(continuousTwo){
            // 满足3根K线连续涨幅
            BinanceKlineDTO binanceKlineDTO = binanceKlineDTOSTmp.get(2);
            String volume = binanceKlineDTO.getVolume();
            String high = binanceKlineDTO.getHigh();
            String low = binanceKlineDTO.getLow();
            BigDecimal highD = new BigDecimal(high);
            BigDecimal lowD = new BigDecimal(low);
            BigDecimal openD = new BigDecimal(binanceKlineDTO.getOpen());
            BigDecimal closeD = new BigDecimal(binanceKlineDTO.getClose());
            BigDecimal divide = highD.subtract(lowD).abs().divide(lowD, RoundingMode.HALF_UP);
            String rate = divide.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%";
            this.doSendNormalMsg("连续 2 根K线涨", binanceKlineDTO, symbol, closeD, rate, volume, "3");
        }

    }

    private boolean continuousTwoDoSend(BinanceKlineDTO binanceKlineDTO) {
        String volume = binanceKlineDTO.getVolume();
        String high = binanceKlineDTO.getHigh();
        String low = binanceKlineDTO.getLow();
        BigDecimal highD = new BigDecimal(high);
        BigDecimal lowD = new BigDecimal(low);
        BigDecimal openD = new BigDecimal(binanceKlineDTO.getOpen());
        BigDecimal closeD = new BigDecimal(binanceKlineDTO.getClose());
        if (closeD.compareTo(openD) <= 0) {
            return false;
        }
        BigDecimal divide = highD.subtract(lowD).divide(lowD, RoundingMode.HALF_UP);
        if (divide.compareTo(new BigDecimal(twoRateLow)) < 0) {
            return false;
        }

        if (new BigDecimal(volume).compareTo(new BigDecimal(twoVolumeLow)) < 0){
            return false;
        }

        return true;
    }


    private boolean isBackDoSend(BinanceKlineDTO binanceKlineDTO) {
        String volume = binanceKlineDTO.getVolume();
        String high = binanceKlineDTO.getHigh();
        String low = binanceKlineDTO.getLow();
        BigDecimal highD = new BigDecimal(high);
        BigDecimal lowD = new BigDecimal(low);
        BigDecimal openD = new BigDecimal(binanceKlineDTO.getOpen());
        BigDecimal closeD = new BigDecimal(binanceKlineDTO.getClose());
        if (closeD.compareTo(openD) <= 0) {
            return false;
        }
        if(openD.compareTo(closeD) < 0){
            return false;
        }
        BigDecimal divide = openD.subtract(closeD).abs().divide(closeD, RoundingMode.HALF_UP);
        if (divide.compareTo(new BigDecimal(rateBackLow)) < 0) {
            return false;
        }

        if (new BigDecimal(volume).compareTo(new BigDecimal(volumeBackLow)) < 0){
            return false;
        }

        return true;
    }

    private boolean continuousThreeDoSend(BinanceKlineDTO binanceKlineDTO) {
        String volume = binanceKlineDTO.getVolume();
        String high = binanceKlineDTO.getHigh();
        String low = binanceKlineDTO.getLow();
        BigDecimal highD = new BigDecimal(high);
        BigDecimal lowD = new BigDecimal(low);
        BigDecimal openD = new BigDecimal(binanceKlineDTO.getOpen());
        BigDecimal closeD = new BigDecimal(binanceKlineDTO.getClose());
        if (closeD.compareTo(openD) <= 0) {
            return false;
        }
        BigDecimal divide = highD.subtract(lowD).divide(lowD, RoundingMode.HALF_UP);
        if (divide.compareTo(new BigDecimal(rateLow)) < 0 || divide.compareTo(new BigDecimal(rateHigh)) > 0) {
            return false;
        }

        if (new BigDecimal(volume).compareTo(new BigDecimal(volumeLow)) < 0 || new BigDecimal(volume).compareTo(new BigDecimal(volumeHigh)) > 0){
            return false;
        }

        return true;
    }

    private void doSendNormalMsg(String title, BinanceKlineDTO binanceKlineDTO, String symbol, BigDecimal closeD, String rate, String volume, String type) {
        // type 1-普通 2-回踩
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss");
        String msg = title + "：**" + symbol +
                "**\n 收盘价：" + closeD.setScale(4, RoundingMode.HALF_DOWN) + " USDT" +
                "\n 振幅：" + rate + "\n 成交额："
                + convertToWan(new BigDecimal(volume).setScale(0, RoundingMode.HALF_DOWN)) + "万 USDT"
                + "\n 当前时间：" + sdf.format(date)
                + "\n K线时间：" + sdf.format(new Date(binanceKlineDTO.getEndTime()))
                + "\n [点击查看实时K线图](https://www.binance.com/en/futures/" + symbol + "?type=spot&layout=pro&interval=1m)";
        if (!allowSend(binanceKlineDTO.getSymbol() + type)) {
            System.out.println("[拒绝] " + binanceKlineDTO.getSymbol() + " 15分钟内已发送过通知了");
            return;
        }
        enterpriseWechatApi.sendGroupMessage(msg);
    }

    public BinanceSymbolsDTO checkAndUpdateFile() throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path) && Files.size(path) > 0) {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            LocalDateTime lastModifiedTime = attrs.lastModifiedTime()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            if (isToday(lastModifiedTime)) {
                // 如果是今天修改的，则直接读取文件内容
                String content = new String(Files.readAllBytes(path));
                if(!StringUtils.hasText(content) || "{}".equals(content)){
                    return updateFileFromApi();
                }
                return JSON.parseObject(content, BinanceSymbolsDTO.class);
            } else {
                // 如果不是今天修改的，清空文件内容，并重新获取
                clearFileContent(path);
                return updateFileFromApi();
            }
        } else {
            // 如果文件不存在或为空，直接从API获取内容
            return updateFileFromApi();
        }
    }

    private boolean isToday(LocalDateTime dateTime) {
        LocalDate today = LocalDate.now();
        return today.equals(dateTime.toLocalDate());
    }

    private void clearFileContent(Path path) throws IOException {
        Files.write(path, "".getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    private BinanceSymbolsDTO updateFileFromApi() {
        BinanceSymbolsDTO symbolsDTO = binanceApi.listSymbols();
        try {
            Files.write(Paths.get(filePath), Objects.requireNonNull(JSON.toJSONString(symbolsDTO)).getBytes());
            System.out.println("Updated file with new data.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return symbolsDTO;
    }

    private BigDecimal convertToWan(BigDecimal amount) {
        // 1万元 = 10000元
        BigDecimal wanUnit = new BigDecimal("10000");

        // 使用divide方法进行除法运算，并指定保留两位小数，四舍五入模式
        return amount.divide(wanUnit, 2, RoundingMode.HALF_UP);
    }
}
