# Dittoe Engine Matrix Test Architecture

This suite treats Dittoe Engine as a data I/O runtime. Metadata discovery, authorization validation, and PMD generation stay outside this repository and are owned by swarm. Engine tests provide complete PMDs, seed known data into local endpoints, run the CLI, and assert that rows move correctly.

## Boundary

- Engine input is a complete `pmd.json` under `BASE_DIR/{pipeline-name}/pmd.json`.
- Engine tests must not call source or target discovery to build PMDs.
- Discovery methods such as `getResources`, `getResourcesFields`, dependency extraction, and merge property extraction are tested separately as connector capability tests only when the adapter implements them.
- CLI matrix runs use `java ... com.everyarrow.dittoe.pipelines.execution.Driver --pipeline-name <case>` or the shaded jar main class after packaging.

## Emulator Strategy

Most cloud-style file I/O can be covered locally:

| Connector family | Local test endpoint | Notes |
| --- | --- | --- |
| S3 | MinIO | Uses `s3Endpoint` and `s3PathStyleAccess=true` connection details. |
| IBM COS | MinIO | Existing adapter already accepts `ibmEndpointUrl`. |
| DigitalOcean Spaces | MinIO | Existing adapter already accepts `doSpacesEndpoint`. |
| GCS | fake-gcs-server | Uses `gcsEndpoint`; credentials are optional when an endpoint is supplied. |
| Azure Blob | Azurite | Uses `azureConnectionString`. |
| FTP | Docker FTP server | Seeds source files and permits target writes. |
| SFTP | Docker SFTP server | Seeds source files and permits target writes. |
| PostgreSQL/MySQL/MSSQL/SQLite | Docker databases or local SQLite files | Seed SQL owns source data and empty target schema. |

Remote-only connectors remain in a separate profile until they have reliable local substitutes:

- BigQuery can be unit-tested heavily and optionally run as credential-gated remote I/O.
- Snowflake is credential-gated remote I/O.
- Dropbox, Google Drive, Oracle Object Storage, and Salesforce need either adapter-level injectable clients or dedicated fake services before they can join default CI.
- Salesforce currently has server operations that throw not-implemented exceptions, so it is excluded from e2e I/O.

## Test DNA

Each matrix case is generated from four inputs:

1. `connector-manifest.json` defines adapter classes, strategy classes, required PMD keys, role-specific source/target details, local emulator details, and readiness.
2. A canonical dataset, starting with `fixtures/canonical.csv`, exercises common data types and escaping.
3. A scenario manifest defines smoke, edge, negative, idempotency, and strategy-specific cases.
4. `matrix.py generate` writes `target/dittoe-matrix/pipelines/{source}-to-{target}/{scenario}/pmd.json`.

The runner then executes:

```bash
BASE_DIR=target/dittoe-matrix/pipelines \
java -cp target/dittoe-engine.jar \
  com.everyarrow.dittoe.pipelines.execution.Driver \
  --pipeline-name local-file-to-postgresql/canonical-smoke
```

For Maven-driven runs, package first and run a Failsafe/JUnit wrapper that shells out to the CLI so the real runtime path is covered.

## CLI Harness

Generate smoke PMDs and local SQLite/file fixtures:

```bash
python3 tests/matrix/matrix.py generate --profile smoke

python3 tests/matrix/matrix.py generate --profile smoke \
  --fixture tests/matrix/fixtures/complex.csv
```

List the cases that would run for a profile:

```bash
python3 tests/matrix/matrix.py list --profile docker-full
```

Run a single generated case through the shaded Driver jar:

```bash
python3 tests/matrix/matrix.py run --generate --package \
  --case local-file-to-sqlite/canonical-smoke

python3 tests/matrix/matrix.py run --generate \
  --fixture tests/matrix/fixtures/simple.csv \
  --case local-file-to-sqlite/canonical-smoke
```

Render a markdown status report and checklist from the latest matrix results:

```bash
python3 tests/matrix/matrix.py report --profile docker-full \
  --output tests/matrix/STATUS.md

python3 tests/matrix/matrix.py report --profile negative \
  --output tests/matrix/STATUS-negative.md

python3 tests/matrix/matrix.py permutation-report \
  --output tests/matrix/PERMUTATION-REPORT.md
```

`tests/matrix/STATUS.md` is the checked-in snapshot of the latest docker-backed validation pass.
`tests/matrix/STATUS-negative.md` is the checked-in snapshot of the negative profile.
`tests/matrix/PERMUTATION-REPORT.md` is the manifest-driven audit of every implemented source-target permutation, including actual canonical-smoke test evidence for runnable pairs, a separate credential-gated remote bucket for connectors that still need real cloud credentials, and explicit blocked counts for incomplete connector contracts.
`tests/matrix/REMOTE-CREDENTIAL-AUDIT.md` is the checked-in audit of credential-bearing remote connector rows from the metadata database and the planned credential-gated test rollout.
`tests/matrix/RUN-2026-05-16.md` is the checked-in snapshot of the completed simple/complex full matrix runs and the verified remote BigQuery runs.

Docker-backed profiles expect the matrix compose overlay to be running first.

## Required Assertions

Every successful I/O case should assert:

- CLI exit code is zero.
- `summary.json` exists and has `errorRowCount == 0`.
- Source read and target write counts match expected rows, using stage counts when populated and aggregate `rowsRead`/`rowsWritten` where an adapter does not fill stage rows consistently.
- Target row count matches expected rows.
- Canonical row equality matches after normalizing field order.
- File-like targets also assert a canonical CSV structure hash after re-rendering rows in manifest field order, so harmless connector quoting differences do not fail the suite while real rendered-output drift does.
- A second run behaves according to the target strategy: insert failure, replace overwrite, upsert stable count, copy append, or merge stable count.

Negative cases should assert:

- Missing object/table/file fails with a useful message.
- Expected-failure scenarios are recorded as passing matrix cases only when the CLI exits non-zero and the error output matches one of the scenario's required substrings.
- Bad credentials or endpoint failures bubble as `ExecutionException` and do not create false success summaries.
- Schema mismatch reports error rows or fails consistently.
- Network interruption exercises retry behavior for endpoints where Docker can simulate it.

## Profiles

| Profile | Contents | Default use |
| --- | --- | --- |
| `smoke` | LocalFile, SQLite, Postgres, MySQL, S3/MinIO, GCS/fake-gcs, Azure/Azurite | PR checks after the harness exists. |
| `docker-full` | Smoke plus MSSQL, FTP, SFTP, IBM/MinIO, DigitalOcean/MinIO | Nightly or pre-release local CI. |
| `remote-full` | BigQuery, Snowflake, Dropbox, Google Drive, Oracle, real cloud S3/GCS/Azure | Manual or scheduled credential-gated runs. |
| `negative` | Missing file/object/table scenarios and other expectation-aware failure cases | PR or nightly depending on runtime. |
| `performance` | Large files, large batches, concurrent pipeline runs | Scheduled only. |

## Implementation Sequence

1. Done: add emulator endpoint support to adapters that need it.
2. Done: add Docker overlay services and canonical seed data for MinIO, fake-gcs-server, Azurite, FTP, SFTP, PostgreSQL, MySQL, and MSSQL.
3. Done: build the PMD generator from this manifest and one canonical source fixture.
4. Done: implement a CLI runner that discovers generated cases by profile and checks exit code plus summary row counts.
5. Done: add connector-specific target hash assertions and reset hooks for repeated full-matrix runs.
6. Done: add negative scenarios and expected-failure verification.
7. Done: prove one real credential-gated BigQuery round-trip using metadata DB credentials and temp-resource cleanup.
8. Done: run the full `docker-full` matrix with both `simple.csv` and `complex.csv` fixtures.
9. Done: rerun the remote BigQuery round-trip with both `simple.csv` and `complex.csv` fixtures.
10. Done: promote BigQuery into a credential-gated pair-matrix profile and verify all BigQuery source/target combinations against both `simple.csv` and `complex.csv`.
11. Next: promote the remaining DB-backed remote connectors with complete runtime contracts into the same credential-gated profile, starting with PostgreSQL, MSSQL, S3, SFTP, and FTP.
12. Next: define the missing matrix contract for Snowflake, Dropbox, Google Drive, and Oracle Object Storage before treating their credentials as testable coverage.
