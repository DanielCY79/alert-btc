# Agent Development Guide

This file turns the architecture notes in `README.md` into working rules for
Codex and contributors. Unless the user explicitly asks for a different
structure, follow these rules for every code change in this repository.

## Fixed Domain Boundaries

Treat the following five top-level domains under
`src/main/java/com/mobai/alert` as fixed:

- `access/`: raw market, news, and event access plus adapters
- `feature/`: feature extraction from raw access data
- `strategy/`: strategy domain
- `backtest/`: backtest domain
- `notification/`: outbound notification delivery

Do not add new top-level business directories outside these five domains.
Do not reintroduce generic buckets such as `control/` or `state/`.

## Directory Ownership

Place code according to the responsibility of the directory:

- `strategy/config/`: strategy metadata and property loading
- `strategy/model/`: shared strategy models such as `AlertSignal`,
  `TradeDirection`, and `MarketState`
- `strategy/runtime/`: generic runtime orchestration interfaces and entrypoints
- `strategy/<type>/`: concrete strategy implementation
- `backtest/model/`: shared backtest models
- `backtest/runner/`: generic batch runner and app entry logic
- `backtest/<type>/`: strategy-specific backtest implementation
- `backtest/export/`: backtest export logic

Concrete strategy internals must stay inside `strategy/<type>/`.
Concrete backtest implementations must stay inside `backtest/<type>/`.
Shared strategy models must stay in `strategy/model/`.

## Dependency Direction

Keep dependency flow one-way:

- `access -> feature -> strategy -> notification`
- `backtest -> access + feature + strategy`

Additional guardrails:

- `notification` may consume signals, but must not call back into strategy logic
- `strategy/runtime` must stay generic and must not absorb concrete
  `strategy/<type>` logic

## Strategy Extension Rules

When adding a new strategy:

- Create a new peer directory under `strategy/`, for example
  `strategy/meanreversion/`
- Create the matching strategy-specific backtest package under
  `backtest/<type>/`
- Keep runtime and backtest entrypoints depending on interfaces rather than a
  concrete strategy package
- Select the active strategy through `monitoring.strategy.type`

## Configuration Layout

Use configuration files by scope:

- `src/main/resources/application.properties`:
  shared runtime, access, feature, notification, and strategy selection
  settings
- `src/main/resources/strategy/<type>.properties`:
  strategy-specific tuning, position management defaults, and
  strategy-specific backtest defaults

Do not put strategy tuning back into `application-*.properties`.
Strategy parameters belong under `src/main/resources/strategy/`.

## Test Layout

`src/test/java` should mirror the production structure.

- Strategy-specific tests belong under `strategy/<strategy-type>/...`
- Backtest-specific tests belong under `backtest/<strategy-type>/...`

## Change Checklist

Before creating or moving code, verify:

- The file belongs to one of the five fixed business domains
- The package matches the real responsibility of the code
- Shared code stays generic and strategy-specific code stays inside
  `strategy/<type>/` or `backtest/<type>/`
- Dependency direction still points one way
- New strategy-specific config lives under `src/main/resources/strategy/`
- Tests mirror the final production package layout
