
# Event Engine – Architectural Blueprint

## 1. Purpose and Goals

The Event Engine is the core runtime for EveryArrow’s service platform. It provides:

- A **unified way** to describe services (via annotations → definitions).
- A **single execution pipeline** for all invocations, regardless of trigger:
    - REST
    - Webhooks
    - Queues
    - Files / batch
    - Schedules
    - Other reviewed protocols
- A **pluggable architecture** for adding new services and transports.
- Integrated **security**, **monitoring**, and **observability** without coupling business code to infrastructure.

At its heart, the engine answers one question:

> “Given an incoming event, which service definition should run, with which parameters, under which security + monitoring rules?”

Everything else is plumbing around that core idea.

## 2. High-Level Architecture

### 2.1 Component Overview

```
            ┌───────────────────────────────────────────┐
            │                Callers                    │
            │                                           │
            │  REST  |  Webhooks  |  Queues  |  ...    │
            └───────────────────────────────────────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │   Adapters & Servlets│
                    │  (HTTP, MQ, File...) │
                    └──────────────────────┘
                               │
                         ServiceCallFactory
                               │
                               ▼
                    ┌──────────────────────┐
                    │      Engine          │
                    │  - ServiceRegistry   │
                    │  - HookManager       │
                    │  - PluginManager     │
                    └──────────────────────┘
                        ▲            │
                        │            │ executes
                        │            ▼
                BinderBootstrap   Handlers
                        ▲
                        │ uses ServiceCallDefinition
                        │
           ┌───────────────────────────────┐
           │     ServiceRegistry           │
           │  (ServiceCallDefinition +     │
           │    ServiceCallHandler)        │
           └───────────────────────────────┘
                        ▲
                        │ derived from
                        │
              Annotations (@RestRoute, @Webhook,
                 @Secured, @Monitored, ...)
```

### 2.2 Observability and Security Context

```
ServiceCall ──┐
              │ runtime context
              ▼
       Hooks (Security, Monitoring, etc.)
              │
              ├──> Security store
              │
              └──> PERCH (instrument_unit, instrument_execution)
```

## 3. Core Concepts

### 3.1 Annotations

Annotations are the **source of truth** for service behavior.

### 3.2 ServiceCallDefinition

Canonical compiled model of a service derived from annotations.

### 3.3 ServiceCall

Runtime instance of a service definition.

### 3.4 ServiceRegistry

Holds service definitions and handlers.

### 3.5 RouteBinding & RouteRegistry

Maps external routes to service types.

## 4. Lifecycle

### 4.1 Engine Lifecycle

```
initialize()
start()
run()
shutdown()
```

### 4.2 Plugin Lifecycle

Plugins load from first-level directories under `plugins/`.

Each plugin may contribute:

- a manifest at `plugin.json` or `META-INF/everyarrow/plugin.json`
- one or more JSON DB registries named `service_registry*.json`
- annotated handler classes discovered from nested JARs

Within one plugin:

- different executable candidates at the same precedence are a load error
- duplicate REST, Webhook, or WebSocket route is a load error

Across plugins:

- resolution follows the registry precedence contract, not plugin load order
- a local annotation overrides a server DB function while preserving it as a fallback
- a contract-enabled generated function requires an explicit plugin override
  whose namespace, signature, capabilities, and version range validate
- equivalent declarations are idempotent
- non-equivalent candidates at the same precedence fail with provenance-rich evidence
- route collisions retain their existing warn-and-first-discovered behavior

See [Function Resolution](function-resolution.md) for candidate identity,
precedence, persistence, diagnostics, and uninstall fallback behavior.

## 5. Routing and Adapters

REST, Webhook, and Socket.IO adapters bind routes from annotations. Socket.IO
event handlers run through the same registry and hook pipeline.

DB-function-backed services can also be loaded from plugin JSON registries
and routed through the same execution pipeline via `GenericProcessHandler`.

## 6. Execution Pipeline

```
hookManager.before()
handler.execute()
hookManager.after()
hookManager.error()
```

## 7. Observability with PERCH

Mapped via MonitoringHook.

## 8. Security Model

Policies defined in annotations, enforced at runtime.

## 9. Extensibility

Add new services, adapters, hooks.

## 10. MVP Scope

REST + Webhooks + Socket.IO + annotation-driven definitions + plugin JSON registries + core handlers.

## 11. Summary

A fully pluggable, annotation-driven, observable, secure execution engine.
