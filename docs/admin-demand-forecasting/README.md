# Admin Demand Forecasting

This directory freezes the research and implementation contracts for the
spatio-temporal demand-forecasting extension of Admin Analytics.

## Phase 0 contracts

- [Processing-layer architecture ADR](adr/ADR-001-processing-layer-boundary.md)
- [Data contract](data-contract.md)
- [Evaluation protocol](evaluation-protocol.md)
- [Configuration contract](configuration-contract.md)

## Experiment evidence

- [Phase 6 candidate-model evidence](phase-06-candidate-evidence.md)
- [Phase 7 model-registry and inference evidence](phase-07-operational-evidence.md)
- [Phase 8 Spring serving API evidence](phase-08-serving-api-evidence.md)
- [Phase 9 processing/model frontend evidence](phase-09-frontend-evidence.md)
- [Phase 10 forecast-map frontend evidence](phase-10-forecast-ui-evidence.md)

The historical Admin Analytics subsystem remains documented in
[`../admin-analytics`](../admin-analytics/README.md). The forecasting layer is
an extension. Phase 8 provides the read-only Spring Admin contract, Phase 9
provides the processing/data-quality/model-evaluation UI, and Phase 10 provides
forecast-map and hotspot exploration.

## Claim boundary

- Porto Taxi is used to evaluate the forecasting method on real trip data.
- GoRide synthetic/operational data is used to verify system integration.
- A model trained on Porto is not presented as a production model for Ho Chi
  Minh City.
- Synthetic smoke evidence validates plumbing, not real-world forecast
  accuracy.
