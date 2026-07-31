# Function Resolution

Event Manager retains every registered function candidate and resolves one
implementation for each exact, case-sensitive `serviceType`. Resolution is
deterministic and independent of plugin directory order, registry row order,
classpath scan order, or restart timing.

## Function identity

The logical invocation identity is the fully qualified `serviceType`, split
for diagnostics into a namespace and logical name. Candidate identity also
records enough evidence to explain or reject a duplicate:

- origin and implementation kind
- source and owning plugin
- plugin version, function contract, and explicit override declaration
- Java handler class and target method
- database, schema, and function
- JSON type, metadata signature, and external route

Declarations with the same complete candidate identity are idempotent.
Different candidates at the same precedence are ambiguous and registration
fails. The exception lists the competing implementations, origins, sources,
plugins, signatures, and routes so the owner can rename the function or define
an explicit override relationship.

## Precedence

From highest to lowest:

1. Reserved Event Manager core implementation.
2. Validated explicit plugin override.
3. Live locally annotated Java handler.
4. Local registry Java handler.
5. Server-registry Java handler.
6. Local-snapshot Java handler.
7. Metadata-only candidates, ordered by local, server, then snapshot origin.
8. DB-function candidates.

The primary compatibility rule is therefore explicit:

> A local annotation may override a server-based function with the same
> `serviceType`.

Both candidates remain registered. Removing or quarantining the local plugin
removes only that plugin's candidates and immediately exposes the retained
server DB function through the existing generic process handler. Reinstalling
or restarting produces the same winner.

An equal-precedence ambiguity is validated even when a higher-precedence
candidate currently wins. This prevents a hidden, nondeterministic fallback
from appearing later when the winner is removed.

## Explicit plugin overrides

Contract-enabled generated functions declare durable compatibility metadata:

```json
{
  "annotations": {
    "functionContract": {
      "namespace": "security.access_control",
      "version": "1.2.0",
      "signature": "api-key-create-v1",
      "capabilities": ["service_call", "jsonb"]
    }
  }
}
```

Event Manager fills `pluginId` and `pluginVersion` from the owning
`plugin.json` and persists them with the registry definition. A Java handler
may explicitly replace that generated implementation with `@FunctionContract`
and `@FunctionOverride`, or with the equivalent JSON metadata:

```json
{
  "annotations": {
    "functionOverride": {
      "targetServiceType": "security.access_control.api_key_create",
      "targetPluginId": "generated-security",
      "versionRange": "[1.0.0,2.0.0)"
    }
  }
}
```

Before the override can win, Event Manager verifies:

- the target is a retained DB-function candidate from the declared plugin;
- both contracts use the namespace implied by the exact `serviceType`;
- signatures match exactly;
- the override provides every target capability;
- the target function version is within the declared semantic-version range;
- the overriding candidate is an executable Java handler.

The target may be discovered before or after the overriding plugin. Resolution
records a pending declaration during discovery and applies one strict validation
gate after all startup sources load. Live plugin installation uses the same
strict gate. This makes install order, refresh, snapshot restore, and restart
produce the same result.

Two valid explicit overrides for one function remain ambiguous and fail rather
than using plugin discovery order. Removing or quarantining the winning plugin
removes only its candidate, restoring the retained generated function.

A plugin handler that collides with a contract-bearing generated function
without declaring `functionOverride` is rejected. Generated functions without
the new contract metadata retain the legacy local-annotation behavior for
backward compatibility.

## Server persistence

The PostgreSQL refresh contract preserves an existing `db_function` registry
row when an annotation refresh presents `java_handler` or
`annotation_metadata` for the same `serviceType`. The local Java implementation
is a runtime override; it does not erase the server fallback or its provenance.

Database and local-snapshot loads add candidates to the registry instead of
deleting live local discoveries. Route indexes are rebuilt from resolved
winners only.

## Diagnostics

Authenticated operators with a `system` or `admin` role and `core.read` scope
can inspect the registry:

```text
GET /core/discovery/registry
GET /core/discovery/registry?serviceType=<exact-serviceType>
```

The response keeps the existing `serviceTypes` and `count` properties and adds
a `resolutions` array. Each resolution contains:

- `serviceType`
- `selected`
- `reason`
- `overrideEvidence`, when the selected implementation declares an override
- `candidates`, in deterministic order

Each candidate reports whether it was selected plus its identity and
provenance fields, including its `contract` and `override` declarations.
Override evidence names the target and reports namespace, signature,
capability, and version compatibility. The authenticated response is the
source of truth for read-only inspection; clients should not reconstruct
precedence from plugin filenames, database row order, or startup logs.

## Plugin and generator contract

Plugins should use stable, namespaced `serviceType` values and accurate source
provenance. DB functions must continue to use a `db:` source. Generated
registries must not depend on file or row order to express precedence.

Plugin packages may intentionally contain a legacy generic DB declaration and
a concrete local Java implementation for the same `serviceType`; the annotated
implementation wins while installed. Contract-enabled generated functions
require the explicit override relationship described above.

The inspected runtime state is authoritative. No duplicate-resolution state is
stored in a parallel registry.
