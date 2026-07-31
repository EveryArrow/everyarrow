# Security Policy

## Supported version

Security fixes are applied to the current `main` branch while EveryArrow is in
its initial release stage.

## Release and deployment boundary

The public repository release gate covers the non-container source and JAR
artifacts in this repository. Docker installers, private platform plugins,
identity services, and managed Swarm execution are separate release surfaces
with their own security reviews.

Standalone DITTOE execution is controlled by the local operator. Database
connectors support vendor TLS configuration but do not universally reject
plaintext connections, which keeps local and disposable integration
environments usable. Operators should require certificate-verified TLS for
remote databases. A future managed-platform transport policy must remain
disabled until the Swarm release surface completes its separate security
screening.

The built-in webhook replay cache protects a single Event Manager process.
Deployments with multiple processes or restart-resilient replay requirements
must provide a shared atomic nonce or idempotency store.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting or open a draft security
advisory for this repository. Do not disclose an unpatched vulnerability in a
public issue.

Include the affected module, reproduction steps, expected impact, and any known
workaround. Reports involving private Console, Security, Perch, identity-provider,
or deployment components should be filed against the corresponding private
EveryArrow repository instead.

## Local automated release evidence

Release maintainers run private EveryArrow security automation outside this
public source repository. It covers native tests, secret history, dependency
and supply-chain scans, SBOMs, safe dynamic probes, and GitHub control
inspection. A completed automated run is not an OWASP certification and does
not replace manual trust-boundary review or finding triage.
