# Security Policy

## Supported Versions

Security updates are provided for the active `main` release line and the active `dev` pre-release line.

## Reporting a Vulnerability

Please do not open public issues for suspected vulnerabilities. Use GitHub private vulnerability reporting or a private EveryArrow maintainer channel.

Reports should include:

- affected package or module;
- vulnerable version, branch, or commit SHA;
- reproduction steps or proof-of-concept details;
- whether credentials, customer data, cloud resources, generated code, or deployment artifacts may be affected.

EveryArrow will triage reports before public disclosure and will coordinate fixes, rotation guidance, and downstream dependency notices when needed.

## Public Release Checks

Before a public release, maintainers must verify that:

- no private configuration, customer data, credentials, internal domains, account IDs, or resource IDs are present in the release tree;
- release workflows are pinned to full action commit SHAs and release only from protected tags on `main`;
- cloud, DB, filesystem, crypto, plugin, CI/CD, and LLM/template safety tests pass locally and in CI;
- historical secrets/data have been scanned and either removed from published history or the public release is created from a clean initial commit.
