# Event Manager contributor guidance

Scope: `event-manager/` in the consolidated EveryArrow platform repository.

## Runtime boundaries

- Keep transports as adapters around the single `ServiceCall` execution model.
- Put business behavior in handlers, not HTTP, queue, schedule, or webhook adapters.
- Keep cross-cutting behavior in hooks and public extension points.
- The public runtime must boot in `local` mode without opening a database connection.
- Database-backed handlers must fail clearly when database access is unavailable.

## Plugins and security

- Keep plugin discovery, registry loading, lifecycle, health, and readiness provider-neutral.
- Do not add concrete identity-provider implementations to this module.
- Explicitly public calls may run without a Security plugin; protected calls must fail closed.
- Optional plugin absence must not create hidden startup dependencies.
- Plugin manifests and registries are source resources; staged plugin directories are runtime artifacts.

## Verification

- Add focused tests for registry reconciliation, transport mapping, plugin lifecycle, and fail-closed behavior.
- Update module documentation when public configuration or operating modes change.
