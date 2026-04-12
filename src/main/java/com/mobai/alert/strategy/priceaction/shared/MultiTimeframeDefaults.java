package com.mobai.alert.strategy.priceaction.shared;

public final class MultiTimeframeDefaults {

    public static final String CONFLICT_POLICY = "context-first";
    public static final boolean ALLOW_COUNTERTREND_ENTRY = false;
    public static final boolean REQUIRE_EXECUTION_CONTEXT = true;
    public static final boolean EXECUTION_CONTEXT_WARMUP_ENABLED = true;
    public static final long EXECUTION_CONTEXT_GRACE_PERIOD_MS = 3_600_000L;
    public static final String CONTEXT_INTERVAL = "4h";
    public static final int CONTEXT_KLINE_LIMIT = 180;

    private MultiTimeframeDefaults() {
    }
}
