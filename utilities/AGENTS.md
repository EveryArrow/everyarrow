# Utilities contributor guidance

Scope: `utilities/` in the consolidated EveryArrow platform repository.

## Boundary

- Keep this module limited to reusable, product-independent infrastructure primitives.
- Do not add product workflows, private deployment logic, identity-provider integrations, or compatibility wrappers for internal applications.
- Treat shared APIs as high-risk because all other modules may consume them.
- Keep cloud integrations optional; consumers that select them must provide their required SDKs.
- Environment loading must support ordinary process/container values without requiring a cloud secret service.

## Engineering

- Prefer small explicit APIs, visible dependencies, and focused tests.
- Preserve backward compatibility when it does not retain private or obsolete product coupling.
- Keep database, messaging, environment, retry, JSON, templating, and plugin helpers generic.
- Never commit credentials, generated environment files, IDE metadata, or operating-system metadata.

## Verification

- Add focused tests for shared behavior and release hygiene.
- Update `README.md` when a public utility contract or optional dependency changes.
