# BTC Range Strategy

This project now implements a BTC range-first alert model derived from the Brooks-style validation we reviewed.

## Current layered structure

The codebase is now organized around four core trading layers plus an independent notification layer:

1. `access`
   - exchange integration, cache, and external-facing config
   - examples: Binance market data pull, symbol cache, HTTP config

2. `strategy`
   - indicator logic, signal generation, and reusable backtest strategy rules
   - examples: range recognition, breakout validation, pullback logic, historical replay

3. `control`
   - scheduling, orchestration, alert flow, and execution-oriented decision glue
   - examples: symbol processing and batch backtest runner

4. `state`
   - signal state, runtime memory, backtest trade state, and analysis outputs
   - examples: alert signal model, breakout memory, trade record, backtest report

5. `notification`
   - alert formatting, cooldown control, and multi-channel push delivery
   - examples: enterprise WeChat bot and Feishu bot

### Directory mapping

```text
src/main/java/com/mobai/alert
|- access
|  |- config
|  |- dto
|  |- exchange
|  `- market
|- control
|- notification
|  |- channel
|  `- model
|- state
|  |- backtest
|  |- runtime
|  `- signal
`- strategy
   `- backtest
```

## Core idea

The strategy assumes:

- most BTC breakouts from established ranges fail on the first attempt
- the best alerts happen at range edges, not in the middle
- a valid breakout needs acceptance, not just a wick outside the range
- the preferred continuation entry is the first pullback that holds the breakout level

## Signals

The alert engine evaluates the following setup families in order:

1. `RANGE_FAILURE_LONG`
   - support is swept
   - price closes back inside the range
   - candle closes strong enough to show rejection

2. `RANGE_FAILURE_SHORT`
   - resistance is swept
   - price closes back inside the range
   - candle closes weak enough to show rejection

3. `CONFIRMED_BREAKOUT_LONG`
   - an established range is identified
   - price closes above resistance with a strong body
   - relative volume confirms acceptance

4. `CONFIRMED_BREAKOUT_SHORT`
   - an established range is identified
   - price closes below support with a strong body
   - relative volume confirms acceptance

5. `BREAKOUT_PULLBACK_LONG`
   - price retests a confirmed bullish breakout level
   - the retest holds and closes back in favor of the breakout

6. `BREAKOUT_PULLBACK_SHORT`
   - price retests a confirmed bearish breakdown level
   - the retest fails and closes back in favor of the breakdown

## Notification channels

The alert pipeline now supports multi-channel delivery from the independent `notification` layer:

- enterprise WeChat bot
- Feishu bot

## Programmatic range definition

The evaluator treats a structure as a tradeable range only when all of these conditions are met:

- width stays within a configurable minimum and maximum range
- price has touched both the upper and lower edge multiple times
- a sufficient number of bars overlap with the previous bar
- fast moving average drift is flat enough to avoid trend-phase false positives

## Default operating mode

- symbol: `BTCUSDT`
- timeframe: `4h`
- history size: `180` bars

This keeps the system closer to BTC swing structure and reduces 15m noise.

## Backtest

The project now contains an internal BTC historical backtester with sensitivity analysis.

### Assumptions

- signal is generated from the latest fully closed bar
- entry happens at the next bar open
- stop uses the strategy invalidation level
- range-failure signals target the range midpoint
- confirmed breakouts target a measured move equal to range height
- breakout-pullback signals inherit the original breakout target
- if stop and target are both touched in the same bar, the simulator uses the conservative assumption and counts stop first
- if neither stop nor target is reached, the trade is closed after a setup-specific holding limit

### Run

```bash
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--backtest.enabled=true --spring.task.scheduling.enabled=false --spring.main.web-application-type=none"
```

### Current baseline result

Using `BTCUSDT` futures `4h` candles from `2020-01-01` to the current run time:

- trades: `229`
- win rate: `46.29%`
- average R: `0.29`
- total R: `66.04`
- profit factor: `1.57`
- max drawdown: `11.92R`

### Sensitivity highlights

- `rangeLookback=48` improved robustness versus the baseline
- `breakoutCloseBuffer=0.004` improved both total R and profit factor
- `rangeLookback=28` materially degraded the strategy by making the range definition too reactive
