# EveryArrow Utilities

> Part of the combined [EveryArrow repository](../README.md). This module
> contains reusable infrastructure and does not own product behavior.

Shared Java utility classes for EveryArrow modules and applications.

This module is open source under the Apache License, Version 2.0. See
`LICENSE` for the full license text and `NOTICE` for attribution information.

## Public Release Safety

- Do not commit private environment files, generated `.bak` files, service
  keys, API keys, passwords, tokens, private keys, AWS account IDs, resource
  ARNs, or live endpoint configuration.
- Use tracked `.example` files as templates and keep local/private copies
  ignored.
- Plugin JARs are trusted code. Plugin directories must be writable only by
  trusted administrators or deployment automation.
- Optional cloud, database, OpenAI, and other external integrations
  must be exercised only through explicit local or CI jobs that are documented
  for that purpose.

## Release Hygiene

Release artifacts should be produced from protected release tags or branches,
with minimized CI permissions and documented artifact contents. AWS SDK
dependencies are optional and are not inherited by Event Manager or other
Utilities consumers; applications that deliberately use an AWS helper must add
the corresponding SDK modules themselves.

## License

EveryArrow Utilities is licensed under the Apache License, Version 2.0. You may
use, modify, and distribute this module under the terms of that license.
