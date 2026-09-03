# Contributing

[简体中文](CONTRIBUTING.zh.md) | English

Thank you for helping improve the Lark Channel SDK for Java. Every change must preserve the artifact boundary and bilingual user experience described here.

## Before starting

For a bug, first prepare a minimal reproduction with synthetic data and confirm whether the behavior belongs to Channel SDK or the underlying `oapi-sdk`. For a feature or public API change, discuss the use case and compatibility impact with maintainers before implementing it.

Do not put vulnerabilities, app credentials, tenant data, user content, raw events, or private logs in an issue or code review. Follow [SECURITY.md](SECURITY.md) for suspected security problems.

## Development environment

Required:

- Git
- JDK 8, 11, 17, and 21 for final compatibility verification
- Maven 3.6.3 or later
- a POSIX-compatible shell for repository scripts

Confirm the active tools:

```bash
java -version
mvn -version
git status --short
```

No real Lark/Feishu credentials are needed for the default build.

Clone the repository and establish a clean baseline:

```bash
git clone https://github.com/larksuite/channel-sdk-java.git
cd channel-sdk-java
./scripts/verify.sh
```

## Repository boundaries

- Public Channel code belongs under `com.lark.channel`.
- Generated OpenAPI and main-SDK infrastructure remain under the `oapi-sdk` dependency.
- Do not copy `com.lark.oapi.core`, `event`, `service`, or other main-SDK implementations into this repository.
- Do not reintroduce legacy main-SDK Channel classes into the standalone artifact.
- Avoid public API additions unless the use case cannot be served by an existing facade or `getRawClient()`.
- This repository targets Java 8 source and bytecode; do not use newer language/API features in production sources.

## Make a focused change

1. Start from the current `main` branch and create a focused branch.
2. Add or update tests before changing behavior where practical.
3. Make the smallest implementation needed for the approved scope.
4. Remove only imports or code made obsolete by your own change.
5. Update Javadocs and user documentation for public behavior.
6. Run focused tests while iterating, then the full verification workflow.

Add this header to new Java source files:

```java
// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT
```

Do not combine unrelated formatting, dependency upgrades, refactors, and behavior changes in one review.

## Documentation requirements

Every user-facing documentation change must update both languages in the same contribution:

- English topic: `docs/<topic>.md`
- Chinese topic: `docs/zh-CN/<topic>.md`
- root entry: `README.md` and `README.zh.md`
- example entry: `examples/README.md` and `examples/README.zh.md`

Run:

```bash
./scripts/verify-docs.sh
```

Examples must compile with Java 8, use `com.lark.channel`, read credentials from the environment/secret manager, and handle asynchronous failures. Do not invent unpublished Maven, Javadoc, CI, support, or security links.

## Code and test expectations

- Validate external input at the application boundary shown in examples.
- Never hard-code or log credentials, tokens, assertions, private keys, raw user content, or media bytes.
- Keep retry attempts bounded at three or fewer and avoid nested retry layers.
- Give external calls timeouts through the appropriate main-SDK or host configuration.
- Keep shared state thread-safe and executors/queues bounded.
- Close caller-owned files/streams and release Channel resources during shutdown.
- Preserve idempotency expectations for duplicate platform delivery.
- Add security tests for changes to URL, filesystem, Webhook, authentication, or raw-event handling.

Default tests must be deterministic and must not contact a live tenant. Credentialed integration tests require explicit maintainer authorization and a dedicated test app.

## Verification

Before requesting review, run the verification workflow locally. CI repeats it on JDK 8, 11, 17, and 21:

```bash
./scripts/verify.sh
```

This checks documentation, Maven tests/Javadocs/dependencies, package isolation, local installation, and all consumer examples. Include the JDK/Maven versions and command results in the review description. Do not skip a failing stage to obtain a green result.

## Commit and review description

Use a short imperative commit subject and keep commits reviewable. The review description should include:

- problem and intended user outcome;
- implementation and compatibility decisions;
- public API/default/behavior changes;
- security and stability considerations;
- tests run and environments;
- documentation updated;
- rollout and rollback considerations where relevant.

Maintainers may request API redesign, additional bilingual documentation, or compatibility evidence before accepting a change.

## Contributor license agreement

External contributors must complete the ByteDance Contributor License Agreement before a contribution can be merged. After a pull request is opened, the CLA check reports whether every contributor has signed and provides signing instructions when required. A pull request cannot be merged until the CLA check passes.

By intentionally submitting a contribution, you also agree that it is provided under the repository's [MIT License](LICENSE), subject to the applicable CLA.

## Conduct

All project interactions must follow the [Code of Conduct](CODE_OF_CONDUCT.md).
