# EveryArrow licenses

This repository contains material under more than one license. The table below
defines the current source-release boundary. A directory-level `LICENSE` file
controls that directory when present; otherwise the repository-root `LICENSE`
controls first-party material.

| Path or material | License |
| --- | --- |
| First-party repository-root files, `.github/`, and `docs/` | Business Source License 1.1 (`BUSL-1.1`) |
| `event-manager/` | Business Source License 1.1 (`BUSL-1.1`), using the parameters in `event-manager/LICENSE` |
| `dittoe-engine/` | Business Source License 1.1 (`BUSL-1.1`), using the parameters in `dittoe-engine/LICENSE` |
| `utilities/` | Apache License 2.0 |
| `dittoe-schema/` | Apache License 2.0 |
| Maven Wrapper files and other material carrying an upstream license notice | The license stated in that file or its accompanying notice |
| Third-party dependencies and separately packaged drivers | Their respective upstream licenses |

`BSL 1.1` is the common abbreviation for the Business Source License 1.1.
`BUSL-1.1` is its SPDX identifier.

## Business Source License boundary

The Additional Use Grant is `None`. Before the Change Date, the Business Source
License permits non-production use, and all production use requires a separate
written commercial license from EveryArrow LLC.

Use the complete applicable `LICENSE` text when determining rights. This summary
does not replace or modify the license.

## Future commercial distributions

The planned EveryArrow Docker platform and executable distribution are not part
of this source release. If those artifacts or additional commercially licensed
components are added later, their directories and distribution packages must
carry separate commercial terms and must be added to this license map. Nothing
in the current Business Source License automatically grants rights to a future
separately licensed commercial distribution.

## Embedded realtime libraries

The Event Manager distribution embeds `io.socket:engine.io-server` 6.3.2 and
`io.socket:socket.io-server` 4.1.2. Both are licensed under the Apache License
2.0. Event Manager overrides their transitive `org.json:json` dependency with
version 20250517, which is published as Public Domain.

## Separately packaged runtime driver

The DITTOE Engine MySQL connector uses MariaDB Connector/J, licensed under
LGPL-2.1-or-later. Maven copies the unmodified driver JAR to
`dittoe-engine/target/drivers/`; it is not embedded in the Business Source
License engine JAR. The engine manifest references that adjacent driver, which
may be replaced with a compatible version under its own license terms. The
distribution also places the corresponding source archive and license/source
notice beside the driver.
