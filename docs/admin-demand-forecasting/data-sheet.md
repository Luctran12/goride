# Data Sheet — Porto Taxi Demand-Forecasting Dataset

## Dataset identity

| Field | Value |
| --- | --- |
| Name | Porto Taxi |
| Version | `porto-2013-07_2014-06-v1` |
| Source artifact | `train.csv.zip` |
| Source bytes | 533,605,607 |
| SHA-256 | `210dd0a20da66a8fc2de3440aecd84670921bc257591f8365a4475e31453c5ea` |
| License recorded by source | CC BY 4.0 |
| Source CRS / timezone | EPSG:4326 / Europe/Lisbon |
| Observation period | July 2013 through June 2014 |

The source artifact and all generated data remain outside Git under
`GORIDE_ANALYTICS_DATA_ROOT`. The manifest is immutable and the CLI fails closed
when the source checksum, identity, CRS or timezone differs.

## Purpose and composition

This public dataset is used to evaluate the thesis forecasting method on real
historical trajectories. GoRide operational/synthetic data is a separate
integration profile and is never merged into Porto accuracy results.

The frozen full extraction produces 1,704,685 canonical demand events and a
758,208,824-byte deterministic JSONL snapshot. Each event represents a valid
trip-start pickup with UTC event time and WGS84 point. `TRIP_STARTED_PROXY`
therefore measures observed served taxi trips, not all requested demand.

## Processing

1. Validate immutable archive and SHA-256.
2. Reject missing trajectories, invalid/out-of-study coordinates and future events.
3. Normalize local timestamps to UTC with an explicit cutoff.
4. Deduplicate and deterministically order canonical events.
5. Project pickups to EPSG:3763 and assign zero-origin floor-grid cells.
6. Aggregate complete 15-minute buckets and construct leakage-safe lags/rolling features.
7. Select cells from training-period coverage only; do not use FINAL demand for selection.

The primary 500 m feature artifact has 14,084,740 rows. Sensitivity artifacts
contain 4,309,510 rows at 1,000 m and 1,366,430 rows at 2,000 m.

## Chronological split

| Split | UTC interval | Use |
| --- | --- | --- |
| Training | 2013-07-01 to 2014-03-01 | Fit history and candidate models |
| Validation | 2014-03-01 to 2014-05-01 | Rolling-origin selection only |
| FINAL | 2014-05-01 to 2014-07-01 | One-time frozen evaluation |

No random shuffle is used. Every feature row is evaluated against its immutable
inference cutoff, and target labels are only attached after the target bucket closes.

## Quality and known exclusions

Feature-build status is WARN because early buckets cannot contain the full lag
history and some trips fall outside the frozen study area. Leakage, uniqueness,
continuity, grid assignment and population gates pass. Quality FAIL stops a stage
and prevents model promotion or forecast publication.

## Privacy and access

- Raw trajectories and canonical trip keys are internal batch artifacts, never API fields.
- Serving responses expose aggregate grid polygons and demand values only.
- Positive actual counts below 3 are suppressed together with absolute error/evaluated time.
- User, passenger, driver, email, phone and artifact-location fields are forbidden by DTO tests.
- The external data root must use restricted filesystem access and must not be deployed inside the web artifact.

The count threshold is a disclosure-reduction control, not a formal differential
privacy or k-anonymity guarantee.

## Bias and generalization limits

- Taxi availability, regulation, road layout and travel behavior are Porto-specific.
- Historical data may underrepresent unmet demand and people who did not take taxis.
- One year cannot establish robustness to later policy, infrastructure or demand shifts.
- Retraining and local validation are mandatory before any TP.HCM operational claim.

## Traceability

Dataset manifest: `manifests/datasets/porto-taxi-v1.json` under the external root.
The Phase 11 frozen contract is
`analytics-processing/configs/porto-phase11-evidence.yml`; its SHA-256 is
`1c41721e33c2b78148c1f9d84cbf39b29b0620a3f1ea5dcb4f9c86dc222b4e94`.
