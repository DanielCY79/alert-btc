package com.mobai.alert.access.kline.sync;

public final class KlineIntervalSupport {

    private KlineIntervalSupport() {
    }

    public static long toMillis(String intervalCode) {
        return switch (intervalCode) {
            case "1m" -> 60_000L;
            case "3m" -> 3 * 60_000L;
            case "15m" -> 15 * 60_000L;
            case "1h" -> 60 * 60_000L;
            case "4h" -> 4 * 60 * 60_000L;
            case "1d" -> 24 * 60 * 60_000L;
            default -> throw new IllegalArgumentException("Unsupported interval: " + intervalCode);
        };
    }
}
