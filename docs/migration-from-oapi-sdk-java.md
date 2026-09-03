# Migrating from `oapi-sdk-java`

[Documentation index](README.md) | [简体中文](zh-CN/migration-from-oapi-sdk-java.md) | English

The standalone Channel SDK separates the high-level conversation layer from the generated OpenAPI SDK. It introduces a new Java namespace while retaining the main SDK as a Maven dependency. This guide targets applications that used Channel classes from, or copied alongside, `oapi-sdk-java`.

## Migration outcome

After migration:

- Channel APIs come from `com.larksuite.oapi:channel-sdk` and `com.lark.channel.*`.
- Generated OpenAPI, authentication, HTTP, event dispatcher, and WebSocket APIs still come from `com.larksuite.oapi:oapi-sdk` and `com.lark.oapi.*`.
- The Channel JAR contains no legacy `com/lark/oapi/channel` classes and no copied main-SDK implementation packages.
- Application behavior is revalidated against the standalone preview rather than assumed from package renaming alone.

## 1. Establish a baseline

Before changing dependencies, record:

- current `oapi-sdk` version and dependency tree;
- JDK and Maven versions;
- inbound transport and subscribed events;
- Channel option values and defaults the application relies on;
- raw OpenAPI calls;
- send types, streaming, media sources, and retry wrappers;
- integration and shutdown tests.

Build and run the existing application once. Keep the last known-good artifact and dependency lock information for rollback.

## 2. Add the standalone artifact

Replace the main-SDK-only dependency with the standalone Channel artifact:

```diff
 <dependencies>
   <dependency>
     <groupId>com.larksuite.oapi</groupId>
-    <artifactId>oapi-sdk</artifactId>
-    <version>your-verified-main-sdk-version</version>
+    <artifactId>channel-sdk</artifactId>
+    <version>1.0.0-beta.1</version>
   </dependency>
 </dependencies>
```

Equivalent final XML:

```xml
<dependency>
    <groupId>com.larksuite.oapi</groupId>
    <artifactId>channel-sdk</artifactId>
    <version>1.0.0-beta.1</version>
</dependency>
```

`channel-sdk` transitively depends on `oapi-sdk:2.8.5` in this baseline. If application source imports raw main-SDK types, it may retain an explicit `oapi-sdk` declaration for clarity, but the selected version must converge with a [verified combination](compatibility.md).

Check the result:

```bash
mvn dependency:tree -Dincludes=com.larksuite.oapi
mvn dependency:analyze
```

Resolve version mediation explicitly; do not exclude the main SDK and copy its classes into the application.

## 3. Change Channel imports

The package mapping is mechanical:

| Before | After |
| --- | --- |
| `com.lark.oapi.channel` | `com.lark.channel` |
| `com.lark.oapi.channel.config` | `com.lark.channel.config` |
| `com.lark.oapi.channel.model` | `com.lark.channel.model` |
| `com.lark.oapi.channel.exception` | `com.lark.channel.exception` |

For example:

```diff
-import com.lark.oapi.channel.LarkChannel;
-import com.lark.oapi.channel.config.LarkChannelOptions;
+import com.lark.channel.LarkChannel;
+import com.lark.channel.config.LarkChannelOptions;
```

Main-SDK imports do not change:

```java
import com.lark.oapi.Client;
import com.lark.oapi.core.cache.ICache;
import com.lark.oapi.event.EventDispatcher;
```

Search for both source imports and fully qualified strings in reflection, DI configuration, serialization metadata, tests, and documentation:

```bash
rg 'com\.lark\.oapi\.channel|com/lark/oapi/channel' .
```

Generated code, shaded JAR rules, native-image configuration, and ProGuard/relocation rules need the same review.

Keeping old and new packages on one test classpath can help migration comparison only when the exact main-SDK release actually provides the legacy package. Do not run two Channel instances against the same application/event stream as a long-term architecture: duplicate delivery, competing connections, and duplicate sends become much harder to reason about.

The extraction baseline intends to preserve high-level behavior while changing ownership, namespace, and dependency packaging. That is not a blanket binary-compatibility claim; the checks below are required because application assumptions, main-SDK mediation, and preview fixes can change observed behavior.

## 4. Recheck construction and lifecycle

Create the standalone facade through:

```java
LarkChannel channel = LarkChannelFactory.createLarkChannel(
        LarkChannelOptions.newBuilder(appId, appSecret)
                .transport("websocket")
                .build());
```

Then verify:

- handlers are registered before inbound traffic;
- `connect()` succeeds before message processing;
- Webhook hosts also call `connect()` to resolve bot identity;
- async send/stream futures have error handling;
- final shutdown calls `disconnect()` once;
- a restarted component creates a new Channel instance.

The standalone preview uses `CompletableFuture` for asynchronous methods and supplies blocking `*Sync` variants for common operations. Do not ignore failed futures.

## 5. Recheck event behavior

Canonical event names are `message`, `reaction`, `botAdded`, `cardAction`, `comment`, `reject`, `error`, `reconnecting`, and `reconnected`. The card action name is camel-cased `cardAction`.

Only one handler is stored per event name; a later registration replaces the previous handler. If old code assumed fan-out to multiple listeners, add an application-owned composite handler.

Raw event bodies are disabled by default. Replace legacy raw-field assumptions with normalized getters, or explicitly configure:

```java
.includeRawEvent(true)
```

Do so only for a documented field gap and update logging/retention controls.

## 6. Recheck policy and batching

Defaults that materially affect behavior include:

- all groups allowed when the group allowlist is empty;
- a direct bot mention required in groups;
- `@all` blocked;
- direct messages open;
- stale messages older than 30 minutes dropped;
- per-chat queue and batching enabled;
- seen-event TTL 12 hours with a 5,000-entry local capacity.

Read [Policy and safety](policy-and-safety.md), especially the fact that batching can merge different senders' messages in one chat. `updatePolicy` copies every field and should be treated as a complete replacement despite its current parameter name.

## 7. Recheck outbound behavior

Test each used kind rather than only compilation:

- text and Markdown around the 3,500-character split boundary;
- reply and `replyInThread` behavior;
- mentions and raw post content;
- card send/update;
- URL, local-file, byte-array, and stream media sources;
- audio/video duration and cover key;
- streaming completion and interruption;
- reaction and recall helpers;
- raw client escape-hatch calls.

Applications can continue using generated OpenAPI through the transitively resolved main SDK or an explicit converged dependency; the standalone facade does not remove that capability.

Remote media now has SSRF checks and a 50 MiB download cap. Local paths should use `allowedFileDirs`; byte arrays, streams, and local files require application size limits. Do not loosen these controls merely to preserve unsafe legacy behavior.

## 8. Verify package isolation

Build the consumer and inspect all resolved artifacts:

```bash
mvn clean verify
mvn dependency:tree
jar tf target/your-app.jar | rg 'com/lark/(channel|oapi/channel)/'
```

Expected ownership:

- `com/lark/channel/**` belongs to `channel-sdk`;
- `com/lark/oapi/**` belongs to `oapi-sdk`;
- `com/lark/oapi/channel/**` must not be packaged by the standalone Channel artifact.

If the application creates an uber-JAR, confirm shading has not duplicated main-SDK classes or silently retained both Channel namespaces.

## 9. Test and roll out

At minimum, run:

```bash
./scripts/verify.sh
```

For the consumer application, run unit tests, one WebSocket or Webhook smoke test, send/reply tests, policy rejection tests, media security tests, and graceful shutdown. Compare event counts, send latency, error codes, rejection reasons, and duplicate business actions during rollout.

Because the artifact is a beta, start with a non-production tenant and a limited bot/group scope. Keep the prior artifact deployable until observations are complete.

## Rollback

Rollback means restoring the complete known-good dependency and import set, not mixing old and new Channel implementations:

1. stop or drain the migrated instance;
2. redeploy the previous application artifact;
3. restore its pinned main-SDK version and legacy Channel source/dependency;
4. verify only one Channel namespace is active;
5. confirm event and send health before reopening traffic.

Whether a particular main-SDK release still contains legacy Channel classes must be verified for that exact release. Do not rely on an indefinite compatibility window.

## Migration completion checklist

- [ ] One `channel-sdk` version selected
- [ ] One compatible `oapi-sdk` version selected
- [ ] No `com.lark.oapi.channel` references remain in application-owned code/config
- [ ] WebSocket or Webhook lifecycle tested
- [ ] All used event names and model fields tested
- [ ] Policy and batching behavior accepted
- [ ] Every async failure observed
- [ ] Media trust boundaries configured
- [ ] JAR/classpath contains no duplicate SDK implementation packages
- [ ] Business writes remain idempotent across duplicate delivery
- [ ] Rollback artifact and procedure verified
