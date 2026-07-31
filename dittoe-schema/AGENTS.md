# DITTOE Schema contributor guidance

Scope: `dittoe-schema/` in the consolidated EveryArrow platform repository.

## Boundary

- This module defines the canonical DITTOE grammar and Pipeline Metadata Document; it is not the runtime.
- Keep Source, Target, FADS, BAPPTE, fields, mappings, behaviors, policies, and connection properties runtime-independent.
- Do not embed Event Manager, product-database, connector-runtime, or cloud-deployment behavior in schema models.

## Compatibility and verification

- Make model changes backward-aware and preserve empty/default forms when valid.
- Keep SDK-facing serialization stable and explicit.
- Add focused round-trip and fallback tests for schema behavior changes.
