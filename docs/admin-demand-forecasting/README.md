# Admin Demand Forecasting

This directory freezes the research and implementation contracts for the
spatio-temporal demand-forecasting extension of Admin Analytics.

## Phase 0 contracts

- [Processing-layer architecture ADR](adr/ADR-001-processing-layer-boundary.md)
- [Data contract](data-contract.md)
- [Evaluation protocol](evaluation-protocol.md)
- [Configuration contract](configuration-contract.md)

The historical Admin Analytics subsystem remains documented in
[`../admin-analytics`](../admin-analytics/README.md). The forecasting layer is
an extension and must not be described as an existing Phase 8 capability.

## Claim boundary

- Porto Taxi is used to evaluate the forecasting method on real trip data.
- GoRide synthetic/operational data is used to verify system integration.
- A model trained on Porto is not presented as a production model for Ho Chi
  Minh City.
- Synthetic smoke evidence validates plumbing, not real-world forecast
  accuracy.
