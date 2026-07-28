# Admin Analytics Metric Dictionary

> Version: 1.0-draft
>
> Phase: 00 - Contracts and Architecture
>
> Status: Proposed for review

## 1. Shared Conventions

### Time Range

- All API ranges use `[from, to)`.
- `from` is inclusive.
- `to` is exclusive.
- Both values are required ISO-8601 timestamps with an offset or `Z`.
- Database timestamps for new analytics objects use `TIMESTAMPTZ`.
- Storage and comparison use UTC.
- Calendar buckets use the requested reporting timezone.
- Default reporting timezone is `Asia/Ho_Chi_Minh`.

Example:

```text
from=2026-07-01T00:00:00+07:00
to=2026-08-01T00:00:00+07:00
```

This represents July 2026 in `Asia/Ho_Chi_Minh` and normalizes to:

```text
[2026-06-30T17:00:00Z, 2026-07-31T17:00:00Z)
```

### Optional Dimensions

- `vehicleType`
- `serviceAreaId`
- spatial cell, only for spatial responses

An omitted dimension means all valid values are included.

### Ratio Rules

- API ratios use decimal values from `0` to `1`, not percentages.
- A zero denominator returns `null`, not `0`.
- Counts are integer values.
- Monetary values use the existing payment currency unit and `BigDecimal`.
- Duration values use milliseconds.
- Distance values use meters.
- API calculations must not use binary floating point for monetary values.

### Terminal Matching States

Matching run terminal outcomes:

```text
MATCHED
NO_DRIVER
CANCELLED
FAILED
```

Matching offer terminal outcomes:

```text
ACCEPTED
REJECTED
TIMEOUT
CANCELLED
EXPIRED
```

`IN_PROGRESS` runs and `OFFERED` offers are excluded from terminal rates.

---

## 2. Trip Metrics

### `tripRequests`

- Definition: Number of non-deleted trips whose `requestedAt` is inside the normalized range.
- Formula: `COUNT(trips.id)`.
- Time field: `trips.requested_at`.
- Unit: trips.
- Includes all trip statuses.
- Excludes soft-deleted trips.

### `completedTrips`

- Definition: Number of non-deleted trips with status `COMPLETED` whose `completedAt` is inside the range.
- Formula: `COUNT(trips.id)`.
- Time field: `trips.completed_at`.
- Unit: trips.
- Excludes records with null `completedAt`.

### `completedTripsByRequestCohort`

- Definition: Number of non-deleted trips requested inside the range that eventually reached `COMPLETED`.
- Formula: `COUNT(trips.id)`.
- Cohort time field: `trips.requested_at`.
- Outcome condition: current terminal status is `COMPLETED`.
- Unit: trips.
- This is distinct from `completedTrips`, which measures completed-trip throughput by `completedAt`.

### `cancelledTrips`

- Definition: Number of non-deleted trips with status `CANCELLED` whose terminal timestamp is inside the range.
- Preferred time field: `trips.cancelled_at`.
- Fallback is not allowed; missing terminal timestamps are treated as data-quality errors.
- Unit: trips.

### `noDriverTrips`

- Definition: Number of non-deleted trips with terminal status `NO_DRIVER`.
- Time field: the timestamp of the `NO_DRIVER` transition in `trip_status_history`.
- Unit: trips.
- A trip currently in `SEARCHING` is not a no-driver trip.

### `completionRate`

- Definition: Share of requested trips that reached `COMPLETED`.
- Formula: `completedTripsByRequestCohort / tripRequests`.
- Cohort rule: Both numerator and denominator use trips requested inside the selected range.
- Unit: ratio.
- Zero denominator: `null`.

The cohort rule prevents mixing completed timestamps from older requests with new requests in the denominator.

---

## 3. Revenue Metrics

### `completedPayments`

- Definition: Number of payments with status `COMPLETED` and `paidAt` inside the range.
- Formula: `COUNT(payments.id)`.
- Time field: `payments.paid_at`.
- Unit: payments.
- Excludes `PENDING`, `FAILED` and other non-completed statuses.

### `completedRevenue`

- Definition: Sum of payment amounts recognized as completed inside the range.
- Formula: `SUM(payments.amount)` where `status = COMPLETED`.
- Time field: `payments.paid_at`.
- Unit: payment currency.
- Empty result: numeric zero.
- Must not use `trips.estimated_fare`.
- Must not use `trips.final_fare` as the source of recognized revenue.

---

## 4. Matching Run Metrics

### `matchingRuns`

- Definition: Number of matching runs started inside the range.
- Formula: `COUNT(matching_runs.id)`.
- Time field: `matching_runs.started_at`.
- Unit: runs.

### `terminalRuns`

- Definition: Number of matching runs with a terminal outcome and `finishedAt` inside the range.
- Formula: Count outcomes in `MATCHED`, `NO_DRIVER`, `CANCELLED`, `FAILED`.
- Time field: `matching_runs.finished_at`.
- Unit: runs.

### `matchedRuns`

- Definition: Terminal runs with outcome `MATCHED`.
- Time field: `matching_runs.finished_at`.
- Unit: runs.

### `noDriverRuns`

- Definition: Terminal runs with outcome `NO_DRIVER`.
- Time field: `matching_runs.finished_at`.
- Unit: runs.

### `cancelledMatchingRuns`

- Definition: Terminal runs with outcome `CANCELLED`.
- Time field: `matching_runs.finished_at`.
- Unit: runs.

### `failedMatchingRuns`

- Definition: Terminal runs with outcome `FAILED`.
- Time field: `matching_runs.finished_at`.
- Unit: runs.
- `failureReasonCode` must be present.

### `matchingSuccessRate`

- Definition: Share of terminal runs that matched a driver.
- Formula: `matchedRuns / terminalRuns`.
- Unit: ratio.
- Zero denominator: `null`.
- Open runs are excluded.

### `matchingDurationMs`

- Definition: Duration from the start of a run to its terminal state.
- Per-run formula: `finishedAt - startedAt`.
- Unit: milliseconds.
- Excludes open runs.
- Negative durations are invalid data.

### `averageMatchingDurationMs`

- Definition: Arithmetic mean of `matchingDurationMs` over terminal runs in the selected cohort.
- Cohort time field: `matching_runs.finished_at`.
- Unit: milliseconds.
- Empty cohort: `null`.

### `p50MatchingDurationMs`

- Definition: Continuous 50th percentile of `matchingDurationMs`.
- SQL basis: PostgreSQL `percentile_cont(0.50)`.
- Unit: milliseconds.
- Empty cohort: `null`.

### `p95MatchingDurationMs`

- Definition: Continuous 95th percentile of `matchingDurationMs`.
- SQL basis: PostgreSQL `percentile_cont(0.95)`.
- Unit: milliseconds.
- Empty cohort: `null`.

### `averageSearchesPerRun`

- Definition: Average `searchCount` across terminal runs.
- Unit: searches per run.
- Empty cohort: `null`.

### `averageCandidatesPerRun`

- Definition: Average cumulative `candidateCount` across terminal runs.
- Unit: candidates per run.
- Empty cohort: `null`.

### `averageOffersPerRun`

- Definition: Number of persisted offer events divided by terminal runs.
- Formula: `terminalOffersInRuns / terminalRuns`.
- Unit: offers per run.
- Zero denominator: `null`.

---

## 5. Matching Offer Metrics

### `offersSent`

- Definition: Number of offer records created in the selected cohort.
- Cohort time field: `matching_offer_events.offered_at`.
- Unit: offers.
- A candidate that never received a notification is not an offer.

### `terminalOffers`

- Definition: Offers whose outcome is terminal.
- Time field: `COALESCE(responded_at, expires_at)`.
- Unit: offers.
- `OFFERED` is excluded.
- `responded_at` stores the resolution timestamp for `ACCEPTED`, `REJECTED`, `CANCELLED` and `EXPIRED`.
- `TIMEOUT` keeps `responded_at = null`; its resolution timestamp is `expires_at`.

### `acceptedOffers`

- Definition: Terminal offers with outcome `ACCEPTED`.
- Unit: offers.

### `rejectedOffers`

- Definition: Terminal offers with outcome `REJECTED`.
- Unit: offers.

### `timedOutOffers`

- Definition: Terminal offers with outcome `TIMEOUT`.
- Unit: offers.

### `cancelledOffers`

- Definition: Terminal offers with outcome `CANCELLED`.
- Unit: offers.

### `expiredOffers`

- Definition: Offers that were already expired when a late response was received or reconciled.
- Unit: offers.
- Automatic scheduler expiry is classified as `TIMEOUT`, not `EXPIRED`.

### `offerAcceptanceRate`

- Formula: `acceptedOffers / terminalOffers`.
- Unit: ratio.
- Zero denominator: `null`.

### `offerRejectionRate`

- Formula: `rejectedOffers / terminalOffers`.
- Unit: ratio.
- Zero denominator: `null`.

### `offerTimeoutRate`

- Formula: `timedOutOffers / terminalOffers`.
- Unit: ratio.
- Zero denominator: `null`.

### `offerResponseDurationMs`

- Definition: Duration from offer creation to driver accept/reject.
- Formula: `respondedAt - offeredAt`.
- Includes outcomes: `ACCEPTED`, `REJECTED`.
- Excludes timeout, cancellation and expiry.
- Unit: milliseconds.

### `averageCandidateDistanceM`

- Definition: Mean candidate distance for offers with a non-null, non-negative distance.
- Time field: `offered_at`.
- Unit: meters.
- Empty cohort: `null`.

---

## 6. Demand Metrics

### `demandByTimeBucket`

- Definition: `tripRequests` grouped by calendar bucket in the reporting timezone.
- Time field: `trips.requested_at`.
- Supported buckets: `HOUR`, `DAY`, `WEEK`.
- Missing buckets must be returned with count `0` for a continuous time series.

### `demandBySpatialCell`

- Definition: Number of trip pickup points inside each configured square grid cell.
- Time field: `trips.requested_at`.
- Geometry field: `trips.pickup_location`.
- Unit: trips.
- Output geometry: EPSG:4326.
- Cell calculation uses a documented projected coordinate system before returning EPSG:4326.

### `spatialCompletionRate`

- Definition: Completed trip cohort divided by requested trip cohort for each pickup cell.
- Cohort rule: Both counts use trips requested in the selected range and cell.
- Zero denominator: `null`.

---

## 7. Driver Supply Metrics

### `snapshotCoverage`

- Definition: Share of expected five-minute snapshot buckets that contain a persisted sample.
- Formula: `observedBuckets / expectedBuckets`.
- Unit: ratio.
- Zero expected buckets: `null`.

### `averageOnlineDrivers`

- Definition: Arithmetic mean of `onlineDrivers` across observed snapshots.
- Missing buckets are excluded and reflected in `snapshotCoverage`.
- Unit: drivers.

### `averageAvailableDrivers`

- Definition: Arithmetic mean of `availableDrivers` across observed snapshots.
- Unit: drivers.

### `averageBusyDrivers`

- Definition: Arithmetic mean of `busyDrivers` across observed snapshots.
- Unit: drivers.

### `requestToAvailableDriverRatio`

- Definition: Trip requests in a bucket divided by the average available drivers sampled in the same bucket and dimensions.
- Unit: trip requests per available driver.
- Returns `null` when:
  - average available drivers is zero;
  - no supply sample exists;
  - snapshot coverage is below `0.80`.

This metric is descriptive and must not be presented as a causal measure.

---

## 8. Data Quality Rules

- A terminal run must have `finishedAt`.
- A matched run must have `matchedDriverId`.
- A failed run must have `failureReasonCode`.
- An accepted/rejected offer must have `respondedAt`.
- `finishedAt >= startedAt`.
- `respondedAt >= offeredAt`.
- `expiresAt > offeredAt`.
- Candidate distance cannot be negative.
- Supply counts cannot be negative.
- `availableDrivers + busyDrivers <= onlineDrivers`.
- Data-quality violations are counted and excluded from derived duration/rate metrics where necessary.

---

## 9. Versioning

Metric changes require:

1. A version update in this file.
2. An entry in `docs/changes-in-implementation.md` if API behavior changes.
3. Updated SQL/repository tests.
4. Updated OpenAPI examples.
5. Updated benchmark query version.
