# Admin Analytics Documentation

> Backend contract version: 1.0
>
> Benchmark protocol version: 1.0
>
> Core scope status: Phase 8 implementation complete; awaiting user review

Use this directory as the entry point for implementation, frontend handoff and
thesis evidence.

## Design and Contracts

- [Metric dictionary](metric-dictionary.md)
- [API contract](api-contract.md)
- [Architecture and data flow](architecture-and-data-flow.md)
- [Matching telemetry consistency ADR](adr/ADR-001-matching-telemetry-consistency.md)
- [Frontend integration guide](frontend-integration-guide.md)
- [Executable Postman collection](admin-analytics-smoke.postman_collection.json)

## Evaluation and Operations

- [Benchmark protocol](benchmark-protocol.md)
- [Committed smoke evidence](benchmark-example/smoke-seed-5537/README.md)
- [Thesis traceability matrix](thesis-traceability-matrix.md)
- [Evaluation, limitations and future work](evaluation-limitations-and-future-work.md)
- [Security, rate-limit and observability review](operations-hardening-review.md)

The committed smoke evidence verifies the methodology and artifact pipeline. It
is not a thesis-scale performance result. A reported experiment must use the
`thesis` profile or be explicitly labeled otherwise.
