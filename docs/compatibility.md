# Compatibility

[Documentation index](README.md) | [简体中文](zh-CN/compatibility.md) | English

The Channel SDK and main OpenAPI SDK have independent versions. Compatibility is promised only for combinations verified by this repository, not for every version Maven can resolve.

## Current matrix

| Channel SDK | Minimum `oapi-sdk` | Default `oapi-sdk` | Verified JDK | Status |
| --- | --- | --- | --- | --- |
| `1.0.0-beta.1` | `2.8.5` | `2.8.5` | 8, 11, 17, 21 | Public beta |

The bytecode target is Java 8 and the minimum Maven version is 3.6.3. CI verifies the build on JDK 8, 11, 17, and 21. Running successfully on an unlisted JDK is not a compatibility guarantee.

## Dependency model

`com.larksuite.oapi:channel-sdk` declares `com.larksuite.oapi:oapi-sdk` transitively. Maven's nearest-definition rules allow a consumer to select another version, but an unlisted combination is unsupported until it passes the full Channel and consumer example suite.

Applications that directly call raw OpenAPI can declare `oapi-sdk` explicitly. Use dependency management to select one version and check convergence:

```bash
mvn dependency:tree -Dincludes=com.larksuite.oapi
mvn dependency:analyze
```

Do not shade or copy main-SDK packages into `channel-sdk`. The expected package ownership is:

| Package | Artifact |
| --- | --- |
| `com.lark.channel.**` | `channel-sdk` |
| `com.lark.oapi.**` | `oapi-sdk` |

The standalone Channel JAR must not contain `com.lark.oapi.channel`, `com.lark.oapi.core`, `com.lark.oapi.event`, or `com.lark.oapi.service` implementation classes.

## Source and binary compatibility

The project compiles with `source=1.8` and `target=1.8`. Public APIs avoid post-Java-8 language features.

The current version is a beta. Before `1.0.0`, package names, signatures, defaults, behavior, and artifact metadata may change. Every intentional user-visible change must be recorded in [CHANGELOG.md](../CHANGELOG.md) and migration guidance when applicable.

After a stable release, semantic-versioning expectations will be documented as part of release policy. Do not infer stable binary compatibility from the current beta version.

## Platform compatibility

The SDK can target the domain accepted by the main SDK through `LarkChannelOptions.domain(...)`. HTTP and WebSocket should use the same environment. Platform permissions, event availability, card schema, and media limits are controlled by Lark/Feishu and can evolve independently of this library.

The high-level Channel supports only the events and content types listed in this documentation. `getRawClient()` can access other main-SDK APIs, but those calls follow the main SDK's own compatibility contract.

## Verifying a new combination

To propose a different main-SDK or JDK version:

1. update the dependency in a dedicated branch;
2. run `./scripts/verify.sh` on JDK 8, 11, 17, and 21;
3. run the full unit suite and package-isolation check;
4. compile all external-consumer examples;
5. run credentialed Webhook/WebSocket integration tests in a secure test tenant;
6. inspect dependency convergence and public API/Javadoc changes;
7. update this matrix only after evidence is recorded.

Do not mark a combination verified based only on successful compilation.

Every future Channel SDK release must update this matrix with its minimum/default main-SDK version and completed JDK evidence.
