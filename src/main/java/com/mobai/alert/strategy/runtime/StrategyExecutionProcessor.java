package com.mobai.alert.strategy.runtime;

public interface StrategyExecutionProcessor {

    void process(String symbol);

    default void prepareExecutionContext(String symbol) {
    }
}
