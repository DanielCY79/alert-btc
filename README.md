# alert-btc

This project keeps the domain boundaries fixed around five business areas:

```text
src/main/java/com/mobai/alert
|- access/          # raw market/news/event access and adapters
|- feature/         # feature extraction from raw access data
|- strategy/        # strategy domain
|  |- config/       # strategy metadata and property loading
|  |- model/        # AlertSignal / TradeDirection / MarketState
|  |- runtime/      # generic runtime orchestration interfaces and entrypoints
|  `- priceaction/  # current concrete strategy implementation
|     |- breakout/
|     |- pullback/
|     |- range/
|     |- policy/
|     |- runtime/
|     `- shared/
|- backtest/        # backtest domain
|  |- model/
|  |- runner/       # generic batch runner / app entry
|  |- priceaction/  # strategy-specific backtest implementation
|  `- export/
`- notification/    # Feishu / WeCom notification delivery
```

`src/test/java` mirrors the same structure. Strategy-specific tests stay under
`strategy/<strategy-type>/...` and `backtest/<strategy-type>/...`.

## Dependency direction

- `access -> feature -> strategy -> notification`
- `backtest -> access + feature + strategy`
- `notification` consumes signals only and does not call back into strategy logic
- `strategy/runtime` stays generic; concrete strategy code lives under `strategy/<type>`

## Strategy extension rule

When a new strategy is introduced, add a new peer directory under `strategy/`,
for example `strategy/meanreversion/` or `strategy/momentum/`. The runtime and
backtest entrypoints should depend on interfaces, and the active strategy is
selected by `monitoring.strategy.type` in `application.properties`.

## Configuration layout

- `src/main/resources/application.properties`
  Holds shared runtime, access, feature, notification, and strategy selection
  settings. The current default runtime is `priceaction` on `3m` bars with a
  `4h` higher-timeframe context.
- `src/main/resources/strategy/priceaction.properties`
  Holds price action signal parameters, position management defaults, and
  price-action-specific backtest defaults.

## Guardrails

- Do not add new top-level business directories outside the five domains.
- Do not bring back generic buckets such as `control/` or `state/`.
- Shared strategy models stay in `strategy/model/`.
- Concrete strategy internals stay inside `strategy/<type>/`.
- Concrete backtest implementations stay inside `backtest/<type>/`.
- Do not put strategy tuning back into `application-*.properties`; strategy
  parameters belong in `src/main/resources/strategy/`.
