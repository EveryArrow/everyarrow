# Security Policy

DITTOE Schema defines metadata and SDK helpers for integration pipelines. Please
report suspected security issues privately before opening a public issue.

## Reporting a Vulnerability

Send vulnerability reports to `security@everyarrow.io`, or to the current
EveryArrow security contact listed by the organization if that address changes.

Include:

- Affected repository and commit, tag, or package version.
- A short description of the issue and impact.
- Reproduction steps or a minimal proof of concept.
- Whether live credentials, customer data, or external services were involved.

Do not include real production secrets in the report. Use placeholders or
sanitized examples whenever possible.

## Scope

Security-sensitive areas include:

- Pipeline metadata and SDK-generated PMDs.
- Source/target connection metadata.
- Credential-shaped configuration fields.
- SDK execution helpers that launch the DITTOE engine.
- Generated artifacts that may contain local or demo credentials.

## Public Disclosure

Please allow EveryArrow time to validate and remediate confirmed issues before
public disclosure.
