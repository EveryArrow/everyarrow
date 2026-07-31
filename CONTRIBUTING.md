# Contributing to EveryArrow

EveryArrow is organized as one Maven reactor with four public modules. Keep
changes within the module that owns the behavior and preserve these boundaries:

- `dittoe-schema` defines the runtime-independent DITTOE grammar and PMD.
- `dittoe-engine` executes pipelines without requiring an EveryArrow database.
- `event-manager` routes transports through the registry and service-call model.
- `utilities` contains reusable infrastructure, not product-specific behavior.

Concrete identity-provider integrations, the Console, Security and Perch
implementations, internal deployment tooling, and operator UX are not part of
this repository.

## Before opening a pull request

Use Java 21 and run the complete reactor from the repository root:

```bash
./mvnw test
```

On Windows, use `./mvnw.cmd test`. Add focused tests when behavior changes and
keep examples free of credentials, private endpoints, and internal repository
references.

By contributing to Apache-licensed material, you agree that your changes are
provided under that module's Apache License 2.0 terms.

Contributions to Business Source License covered material are accepted only
after the contributor has completed the separate contributor agreement required
by EveryArrow LLC. That agreement must provide the rights EveryArrow LLC needs
to distribute the contribution under the Business Source License, the Change
License, and separate commercial licenses. Do not submit a contribution to
BSL-covered material unless EveryArrow LLC has confirmed the applicable
contributor agreement.

See [LICENSES.md](LICENSES.md) for the governing path and artifact boundaries.
