# EveryArrow Event Manager

> Part of the combined [EveryArrow repository](../README.md). This module owns
> the registry, transport adapters, hooks, and canonical `ServiceCall`
> execution path.

A lightweight Java runtime that lets you expose the **same service
logic** through multiple trigger types --- without rewriting business
code for each protocol.

## License

EveryArrow Event Engine is source-available under the Business Source License
1.1. There is no Additional Use Grant. Before the Change Date, non-production
use is permitted, and all production use requires a separate written commercial
license from EveryArrow LLC.

See [LICENSE](LICENSE) and [LICENSE-NOTICE.md](LICENSE-NOTICE.md).

At the center is one rule:

> Everything becomes a `ServiceCall`.\
> Transport is just an adapter.

------------------------------------------------------------------------

## 🎯 What This Library Does

### 1️⃣ Discovers Services (Handlers)

You implement a `ServiceCallHandler` and write **one** method:

    execute(params, type, source)

At startup, the engine:

-   Scans the classpath for annotated services\
-   Loads plugin directories from `./plugins`\
-   Builds a `ServiceCallDefinition` from annotations

Your business logic is written once.

------------------------------------------------------------------------

### 2️⃣ Builds a Canonical Registry

The `ServiceRegistry` maintains:

-   `serviceType → handler`
-   REST route mappings (including templated routes like `/orders/{id}`)
-   Webhook route mappings
-   Queue subscription mappings
-   Scheduled job definitions

This registry is the core abstraction.

It is what makes **one service, many triggers** possible.

Function resolution is independent of discovery order. A live locally annotated
Java handler overrides a server-registry DB function with the same
`serviceType`, while the DB function remains registered as a fallback. Core
implementations remain reserved. Contract-enabled generated functions require
an explicit, compatibility-validated plugin override; accidental collisions are
rejected. Non-identical candidates at the same precedence are rejected with
provenance-rich conflict evidence instead of silently selecting whichever
candidate was loaded first. See
[Function Resolution](docs/function-resolution.md) for the complete contract
and diagnostics.

### Database and local operating modes

Set `EA_EVENT_MANAGER_MODE` to choose the registry/runtime boundary:

- `auto` uses the server registry when MetaDB is configured and otherwise uses the local JSON snapshot.
- `database` reconciles the current server surface on restart, soft-deactivates missing entries through the database refresh contract, loads active entries alongside locally discovered candidates, and saves a local snapshot. If the database is unavailable, startup falls back locally but readiness remains down until database reconciliation succeeds.
- `local` never opens a database connection. It loads the snapshot when present, otherwise it keeps definitions discovered from annotations and plugin JSON.

The snapshot path can be overridden with `EA_REGISTRY_SNAPSHOT_PATH`. A snapshot
does not replace live local registrations; both are retained and resolved by
the same policy. Database-backed service calls are unavailable in local mode,
but Event Manager core, Java handlers, routes, queues, schedules, and
non-database adapters continue to operate.

Database-backed handlers accept either unprefixed `DB_*` values or arbitrary
named profiles such as `ANALYTICS.DB_HOST` / `ANALYTICS_DB_HOST`. Profiles are
discovered from configured key names; `DB_DEFAULT_PROFILE` selects one
explicitly, and `EA_DB_PROFILES` may declare a preferred comma-separated order.
Aliases use `<PROFILE>.DB_PROFILE=<PHYSICAL_PROFILE>`. Event Manager does not
contain a built-in list of EveryArrow product databases.

Database mode requires the idempotent PostgreSQL contract in `src/main/resources/database/event_manager_registry.sql`. It creates the registry table plus the load/refresh functions used for bidirectional restart reconciliation.

`/health` is a database-independent liveness check. It reports the registry mode and the source selected at startup without opening a health-check database connection. `/ready` additionally reflects registry reconciliation and required-plugin readiness; authenticated `/core/ready` contains the detailed registry and plugin status.

------------------------------------------------------------------------

### 3️⃣ Normalizes All Triggers into One Execution Path 🔄

The engine supports multiple invocation types:

-   🌐 REST calls\
-   🔗 Webhooks\
-   🔌 Socket.IO events\
-   📬 Queue messages\
-   🗓 Scheduled jobs (cron-style)\
-   🛠 Manual or internal calls

Each transport adapter resolves a `serviceType` and delegates to the
same handler.

The business logic never changes based on transport.

------------------------------------------------------------------------

### 4️⃣ Schedules Jobs with Annotations 🗓

Using `@CalendarJob(cron=..., timezone=...)`, services can be scheduled.

Quartz executes the job, which routes back through the engine using the
same `serviceType` execution path.

Scheduled logic is not separate logic.

It is just another trigger.

------------------------------------------------------------------------

### 5️⃣ Consumes Queue Messages 📬

The queue adapter:

-   Discovers queue subscriptions from the registry\
-   Starts consumers automatically\
-   Dispatches to the service registered for the subscription\
-   Requires the Event Manager service-worker API key for queue execution

Queue payloads may name the registered `serviceType`, but they cannot switch to
another service unless that target is explicitly listed in
`EA_QUEUE_SERVICE_TYPE_OVERRIDE_ALLOWLIST`.

Queue messages are not "special code."

They are just another `ServiceCall`.

------------------------------------------------------------------------

### 6️⃣ Runs Hooks Around Execution 🪝

Hooks provide cross-cutting behaviors:

-   Security\
-   Observability\
-   Logging\
-   Metrics\
-   Exception handling

They wrap execution with:

-   `before`\
-   `after`\
-   `onException`

This keeps infrastructure concerns separate from business logic.

------------------------------------------------------------------------

## 🧠 Minimal Mental Model

  -----------------------------------------------------------------------
  Concept                     Responsibility
  --------------------------- -------------------------------------------
  `ServiceCallHandler`        Your business logic entry point

  `ServiceCallDefinition`     Canonical metadata extracted from
                              annotations

  `ServiceRegistry`           Index of services, routes, queues,
                              schedules

  Protocol Adapters           REST, Queue, Scheduler, future transports

  Hooks                       Cross-cutting wrappers around execution
  -----------------------------------------------------------------------

Everything funnels through the same execution pipeline.

------------------------------------------------------------------------

## 🤖 Why This Matters (for Humans and AI Agents)

Most systems repeatedly regenerate the same infrastructure metaphors:

-   REST endpoint\
-   Webhook listener\
-   Queue consumer\
-   Cron job

Each is implemented separately.\
Each drifts over time.\
Each duplicates boilerplate.

This engine standardizes those metaphors.

Instead of generating new scaffolding, AI agents and developers:

-   Define a service once\
-   Expose it via annotations\
-   Reuse canonical adapters

Infrastructure patterns are written once.\
Business logic is written once.\
Execution remains consistent.

------------------------------------------------------------------------

## 🚀 Bootstrapping

The module includes a boot entry that:

-   Initializes the engine\
-   Loads plugins\
-   Starts protocol adapters\
-   Installs a shutdown hook\
-   Blocks the main thread

From there, all triggers route through the same registry and handler
model.

Runtime environment files are intentionally not checked in. Use the sanitized
`src/main/resources/*.env.example` files as key-name references only, then
provide real values from process/container environment. An untracked local env
file selected with `EA_ENV_RESOURCE` is optional; Event Manager starts without
one.

`EA_LOG_ROOT` selects one shared platform logging root. Event Manager writes to
`<root>/event-manager/`, while DITTOE writes to `<root>/dittoe/`. The default
root is `logs`. Containers may mount the shared root to any filesystem chosen
by the operator; neither runtime requires AWS or EFS.

Before any public release, rotate credentials that ever appeared in tracked
runtime config and complete a historical secret scan.

Release-sensitive defaults:

-   `/ready` is the only public readiness route and returns minimal state only\
-   detailed readiness is exposed at `/core/ready` and must pass normal security checks\
-   non-loopback DB hosts must explicitly set `<PROFILE>.DB_SSL=true` unless plaintext is deliberately enabled with `EA_ALLOW_PLAINTEXT_DB=true`\
-   wildcard CORS is ignored unless `EA_ALLOW_ANY_CORS_ORIGIN=true`; prefer explicit origins for browser-facing deployments\
-   HTTP/Socket.IO binds to `127.0.0.1` by default; set `EVENT_ENGINE_HTTP_HOST` deliberately for non-local exposure\
-   HTTP request bodies are limited to 2 MiB by default; set `EA_HTTP_MAX_REQUEST_BODY_BYTES` to a positive byte count when a route requires a different ceiling\
-   all HTTP responses receive a restrictive security-header policy; HSTS is emitted only when Jetty receives a secure request\
-   HTTP requests are limited by socket peer address to 600 per minute by default; tune `EA_HTTP_MAX_REQUESTS_PER_MINUTE` and bound tracked peers with `EA_HTTP_RATE_LIMIT_MAX_CLIENTS`\
-   Socket.IO accepts WebSocket transport only and requires a bearer token in the v4 `auth` payload\
-   Socket.IO limits are controlled by `EA_SOCKET_IO_MAX_TEXT_MESSAGE_BYTES`, `EA_SOCKET_IO_IDLE_TIMEOUT_SECONDS`, `EA_SOCKET_IO_MAX_CONNECTIONS`, `EA_SOCKET_IO_MAX_EVENTS_PER_MINUTE`, `EA_SOCKET_IO_MAX_ROOMS_PER_CONNECTION`, and `EA_SOCKET_IO_TOKEN_REVALIDATION_SECONDS`\
-   plugin directory live-reload is disabled unless `EA_PLUGIN_WATCH_ENABLED=true`\
-   plugin JARs require SHA-256 entries in `EA_PLUGIN_INTEGRITY_MANIFEST` by default; `EA_PLUGIN_INTEGRITY_MODE=warn` is a migration aid and `off` is test-only

------------------------------------------------------------------------

## 🧩 Plugin Contract

Plugins are loaded from first-level directories under `./plugins`.

Security and Perch implementations are optional internal plugins, not public
runtime dependencies. Set `EA_PLUGIN_ROOT` to their staging root and set
`EA_REQUIRED_PLUGIN_IDS=security,perch` only in deployments that require both.
Without the Security provider plugin, protected external calls fail closed;
without the Perch plugin, calls run without Perch lifecycle tracking.

Plugins are trusted runtime code. Event Manager verifies each JAR before
creating its classloader. The integrity manifest uses paths relative to
`EA_PLUGIN_ROOT` and SHA-256 digests:

```json
{
  "schemaVersion": 1,
  "algorithm": "SHA-256",
  "jars": {
    "console/everyarrow-console-plugin-1.0.0.jar": "64-character-sha256-hex"
  }
}
```

Keep the manifest on a read-only deployment mount. Digest verification proves
that a JAR matches the reviewed build; publisher signing remains a separate
higher-assurance control for third-party distribution.

Each plugin directory may contain:

-   Nested JARs anywhere under the directory for handler class scanning\
-   A plugin manifest at `plugin.json` or the legacy `META-INF/everyarrow/plugin.json`\
-   Zero or more JSON DB registries named `service_registry*.json`

Supported plugin registry sources are:

-   Unpacked root files such as `plugins/my-plugin/service_registry.json`\
-   Unpacked legacy files under `plugins/my-plugin/META-INF/everyarrow/{pluginId}/service_registry*.json`\
-   Packaged classpath resources declared in `plugin.registryResources` inside `plugin.json`\
-   Packaged legacy resources under `META-INF/everyarrow/{pluginId}/service_registry*.json`

When `plugin.registryResources` is non-empty, that list is authoritative:
undeclared JSON files beside the plugin or inside its JAR are ignored. Plugins
that omit the list retain automatic discovery for legacy compatibility.

Discovery order is reproducible:

-   Plugin directories are processed alphabetically\
-   Within one plugin, manifest-declared services and discovered registry files are processed in sorted order

Resolution does not depend on that order:

-   Reserved core implementations always win\
-   A compatibility-validated explicit plugin override wins over its declared generated function target\
-   A live locally annotated Java handler wins over local registry, server registry, snapshot, metadata-only, and DB-function candidates\
-   A local registry Java handler wins over server and snapshot Java handlers\
-   Metadata-only declarations rank below executable Java handlers but above DB-function candidates\
-   Equivalent duplicate declarations are idempotent\
-   Non-equivalent function candidates at the same precedence fail registration with both candidates' source and implementation identities

The existing route-conflict contract is separate: duplicate REST, Webhook, or
Socket.IO event routes inside one plugin fail its load, while cross-plugin route
collisions still warn and retain the first discovered route.

The protected `GET /core/discovery/registry` endpoint returns the selected
candidate, all retained candidates, and the reason for each `serviceType`.
Pass `serviceType=<exact-name>` to inspect one resolution.

This lets one plugin directory mix:

-   Annotated Java handlers from JARs\
-   Manifest-declared handlers\
-   DB-function-backed services from one or many JSON registries

DB-function-backed services must declare provenance through their `db:` source.
Contract-enabled functions may also declare namespace, semantic version,
signature, and capabilities under `annotations.functionContract`. A plugin Java
handler must use `@FunctionOverride` (or equivalent JSON metadata) to replace
such a generated function, naming the target plugin and accepted function
version range.
Externally routed DB services must also declare an application key in
`annotations.call.applicationKey`; destructive DB routes cannot be public.

Webhook ingress requires an HMAC-SHA256 signature using a configured
`EA_WEBHOOK_SHARED_SECRET` or service-specific
`EA_WEBHOOK_SHARED_SECRET_<SERVICE_TYPE>` value. The signed payload is
`<timestamp>.<rawBody>` and the timestamp must be inside the configured replay
window.

------------------------------------------------------------------------

## Testing

The ordinary reactor builds its sample plugin JAR from checked-in source and
uses an in-memory implementation of the finance function contract. It does not
require a staged plugin directory or a database, and it must complete with zero
skipped tests.

Live PostgreSQL ledger validation is separate and explicit:

```powershell
mvn -pl event-manager -am -Ppostgres-ledger-it -Dcredit.ledger.jdbcUrl=jdbc:postgresql://HOST:5432/DATABASE -Dcredit.ledger.dbUser=USER -Dcredit.ledger.dbPassword=PASSWORD verify
```

Selecting `postgres-ledger-it` without its database configuration fails instead
of silently skipping coverage.

The full platform registry endpoint validation requires an initialized
EveryArrow database and its environment resource, so it has a distinct profile:

```powershell
mvn -pl event-manager -am -Pplatform-db-it -DEA_ENV_RESOURCE=PATH_TO_ENV verify
```

------------------------------------------------------------------------

## Container runtime

The production Dockerfile uses the reviewed, digest-pinned Eclipse Temurin Java
21 UBI 10 Minimal image. The application and its writable plugin and log
directories are owned by the fixed unprivileged identity `10001:10001`, and the
container switches to that identity before startup.

When changing the base image, update `event-manager/Dockerfile`, rebuild the
Event Manager image, run the complete reactor, and record an image scan with
zero Critical or High findings before advancing a public release gate.

------------------------------------------------------------------------

## 📌 In One Sentence

EveryArrow Event Manager turns REST calls, webhooks, WebSockets,
queues, and schedulers into a single reusable execution model --- so
services are defined once and invoked everywhere.
