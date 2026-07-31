# DITTOE Engine contributor guidance

Scope: `dittoe-engine/` in the consolidated EveryArrow platform repository.

## Runtime boundary

- DITTOE executes pipelines; it is not a source, target, registry, or product database.
- Source and target connectivity comes from the Pipeline Metadata Document.
- Keep direct source-to-target execution independent of Event Manager and EveryArrow databases.
- Start connector work from the appropriate connector-family base class and make unsupported capabilities explicit.
- Keep local logging and in-memory instrumentation usable without cloud infrastructure.

## Engineering and verification

- Separate orchestration, connector IO, behaviors, and instrumentation.
- Add connector capability tests and pipeline fixtures for changed behavior.
- Preserve PMD compatibility and coordinate grammar changes with `dittoe-schema/`.
- Run focused tests plus the SQLite pipeline matrix for execution changes.
