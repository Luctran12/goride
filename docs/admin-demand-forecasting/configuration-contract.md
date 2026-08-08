# Analytics processing configuration contract

> Version: 1.0
>
> Status: Frozen for Phase 0; implemented by the Phase 1 config loader

## 1. External storage root

Large data and model artifacts stay outside Git. The root is selected through:

```text
GORIDE_ANALYTICS_DATA_ROOT
```

Expected layout:

```text
raw/<dataset>/<version>/
interim/<dataset>/<version>/
processed/<dataset>/<version>/
features/<dataset>/<version>/
models/demand-forecasting/<model-version>/
runs/{training,evaluation,forecasting}/<run-id>/
manifests/{datasets,runs}/
```

The Phase 1 loader must resolve and normalize the absolute root, reject an
absent/non-directory root, and reject any configured output that escapes it.
Raw inputs are immutable by policy.

## 2. Environment variables

| Variable | Required | Meaning |
| --- | --- | --- |
| `GORIDE_ANALYTICS_DATA_ROOT` | Yes | Absolute external artifact root |
| `GORIDE_ANALYTICS_PROFILE` | Yes | `porto-thesis`, `goride-local` or a versioned test profile |
| `ANALYTICS_DATABASE_HOST` | For DB stages | PostgreSQL host |
| `ANALYTICS_DATABASE_PORT` | For DB stages | PostgreSQL port, normally `5432` |
| `ANALYTICS_DATABASE_NAME` | For DB stages | Experiment or GoRide database name |
| `ANALYTICS_DATABASE_USERNAME` | For DB stages | Least-privilege processing role |
| `ANALYTICS_DATABASE_PASSWORD` | For DB stages | Password supplied only at runtime |

No password, JDBC URL with embedded credentials or machine-specific absolute
path may be committed in YAML.

Spring's existing `DATABASE_URL`, `DATABASE_USERNAME` and
`DATABASE_PASSWORD` remain the application datasource contract. They are not
implicitly reused by Python so an experiment cannot accidentally target the
operational database.

## 3. Dataset manifest

The Porto manifest is resolved as:

```text
${GORIDE_ANALYTICS_DATA_ROOT}/manifests/datasets/porto-taxi-v1.json
```

Required fields:

```json
{
  "datasetName": "porto-taxi",
  "datasetVersion": "porto-2013-07_2014-06-v1",
  "sourceFile": "train.csv.zip",
  "sourceRelativePath": "raw/porto-taxi/v1/train.csv.zip",
  "sourceCrs": "EPSG:4326",
  "timezone": "Europe/Lisbon",
  "license": "CC BY 4.0",
  "sha256": "210dd0a20da66a8fc2de3440aecd84670921bc257591f8365a4475e31453c5ea",
  "immutable": true
}
```

Phase 1 must reject malformed JSON, an unsupported checksum algorithm, missing
source file, path traversal, checksum mismatch or a source file outside the
external root.

## 4. Profiles

### `porto-thesis`

- data timezone: `Europe/Lisbon`;
- source CRS: EPSG:4326;
- projected CRS: EPSG:3763 (`ETRS89 / Portugal TM06`, metre);
- primary cell size: 500 m;
- sensitivity sizes: 1,000 m and 2,000 m;
- supply features: disabled because the source has no GoRide supply snapshots;
- database/artifact namespace: Porto experiment only.

The Phase 0 environment verified EPSG:3763 through PostgreSQL 18/PostGIS 3.6.2
`spatial_ref_sys`. Phase 1 must retain a coordinate-transform smoke test. The
processing code must not reuse Ho Chi Minh City's EPSG:32648 default for Porto.

### `goride-local`

- data timezone: `Asia/Ho_Chi_Minh`;
- source CRS: EPSG:4326;
- projected CRS: EPSG:32648, matching historical Admin Analytics;
- supported cell sizes: 250, 500, 1,000 and 2,000 m;
- supply features: enabled only when snapshot coverage meets the quality gate;
- database namespace: GoRide `analytics` schema.

## 5. Run directory

Each invocation creates a new directory:

```text
runs/<run-type>/<UTC timestamp>-<commit>-<profile>-<config hash>/
```

The runner must refuse a non-empty target directory. At minimum it writes:

```text
run-manifest.json
quality.json
logs.jsonl
checksums.sha256
```

Training/evaluation add model configuration, raw predictions and metric
summaries. Large artifacts remain external; the repository may contain only a
small deterministic example.

## 6. Fail-fast validation order

1. Parse profile and reject unknown keys.
2. Resolve the external root and manifest below it.
3. Verify dataset identity, path and SHA-256.
4. Validate timezone, bucket, horizons, cell sizes and chronological splits.
5. Validate database configuration without printing the password.
6. Verify PostgreSQL/PostGIS compatibility for DB-dependent commands.
7. Allocate a new run ID/output directory.

Validation errors occur before reading the full dataset or writing database
state.
