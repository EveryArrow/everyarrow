# Security Policy

`ea-event-manager` is an active EveryArrow runtime repository with external REST, webhook, Socket.IO, queue, scheduler, plugin, and DB-dispatch surfaces.

## Reporting

Report suspected vulnerabilities privately to the EveryArrow maintainers before public disclosure. Include the affected branch or release, the route or component involved, reproduction steps, and whether credentials, plugins, queues, or DB functions are involved.

## Release Expectations

Before a public release, maintainers must complete:

- focused security regression tests for auth, webhooks, Socket.IO, queues, schedulers, plugin loading, and DB-backed dispatch
- dependency, license, and secret-history scans
- rotation of any credential that ever appeared in tracked runtime configuration
- review of staged plugins and DB service registries as trusted runtime inputs

Do not publish deployment secrets, generated runtime env files, staged private plugin registries, or historical secret material in this repository.
