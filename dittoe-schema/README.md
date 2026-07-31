# DITTOE Schema - Data Integration the Theory of Everything

> Part of the combined [EveryArrow repository](../README.md). This module
> defines the portable language contract and does not execute pipelines.

## A Canonical Grammar for Data Integration

For decades, data integration has been implemented but rarely formalized.

We built engines, but after 15+ years there is still no industry standard or
shared grammar.

Informatica, DataStage, SSIS, Talend, ADF, Glue, Matillion, Fivetran.
Each defines its own structure, its own mapping semantics, and its own
execution model. Pipelines are portable only within the boundary of the
engine that created them.

Rather than interoperability, the industry has many isolated systems where
pipeline definitions are locked to a single vendor and portability is limited.

DITTOE starts from a stricter premise: data integration is a structural
discipline. If it can be described formally, it can be standardized
independently of runtime.

This module defines that formal structure.

------------------------------------------------------------------------

## The Model

Every routine is directional:

```text
Source FADS -- BAPPTE lifecycle --> Target FADS
```

Both Source and Target belong to a finite set of storage primitives:

    FADS = { File, API, Database, Stream }

This set is closed. Everything else is vendor detail layered on top.

Every routine unfolds across a lifecycle:

    BAPPTE = {
      Begin,
      Acquire,
      Process,
      Package,
      Transmit,
      End
    }

Together, these define the canonical integration model used throughout
DITTOE.

------------------------------------------------------------------------

## What the DITTOE Schema Defines

The DITTOE Schema formalizes the Pipeline Metadata Document (PMD).

A PMD describes, in a portable and engine-agnostic format:

-   The Source resource
-   The Target resource
-   Structural fields and data types
-   Source-to-target mappings
-   Execution behaviors
-   Lifecycle staging
-   Policies and global properties

The schema does not execute pipelines.
It defines them.

Execution engines consume this specification.

### Global connection defaults

`globalProperties` may provide connection-detail defaults shared by the PMD's
source and target servers. A value declared in a server's own
`connectionDetails` takes precedence over the same global key. This keeps
common values such as a database type, host, or credential reference in one
place while still allowing either endpoint to override what differs.

The defaults are part of the portable PMD model; they do not imply an
EveryArrow database, registry, or cloud service. An empty server
`connectionDetails` object is valid when its required values are supplied by
`globalProperties`.

------------------------------------------------------------------------

## Architectural Position

DITTOE does not replace ETL systems.

It sits above them.

The schema can:

-   Generate pipelines for existing ETL engines
-   Be translated into engine-specific DAGs or job definitions
-   Standardize routines across heterogeneous platforms
-   Serve as a control-plane definition layer

Existing ETL systems become execution backends.
The grammar remains stable.

When specification and execution are decoupled, portability becomes
achievable.

------------------------------------------------------------------------

## Why a Grammar Is Necessary

Without a canonical grammar:

-   Pipelines cannot be compared structurally.
-   Validation is engine-specific.
-   Migration requires reimplementation.
-   Governance remains reactive.

With a canonical grammar:

-   Pipelines are structurally comparable.
-   Validation is possible before execution.
-   Translation between engines becomes systematic.
-   Engine lock-in is reduced.

Standardization must occur at the metadata layer. Runtimes will evolve.
The grammar should not.

------------------------------------------------------------------------

## Scope

This module defines the DITTOE Schema.

It is not:

-   An ETL runtime
-   A scheduler
-   A managed integration service

It is the formal specification layer for data integration.
For runtime execution, see the [DITTOE Engine module](../dittoe-engine/README.md).
DITTOE Swarm is the server-side orchestration layer for scheduling and
coordination.
If integration is to become interoperable, it must first become formal.
The DITTOE Schema is that formalization.
