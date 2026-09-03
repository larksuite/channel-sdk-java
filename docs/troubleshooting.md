# Troubleshooting

[Documentation index](README.md) | [简体中文](zh-CN/troubleshooting.md) | English

Start with the exception type and `LarkChannelException.getCode()`, then check lifecycle, configuration, permissions, and dependency convergence. Preserve causes in private diagnostics, but never log credentials, tokens, private keys, raw callback bodies, or media bytes.

## Maven cannot resolve `channel-sdk`

Confirm the consumer requests exactly `com.larksuite.oapi:channel-sdk:1.0.0-beta.1`, can reach Maven Central, and is not using offline mode. When testing an unpublished source change, install it locally with `mvn -DskipTests -Dmaven.javadoc.skip=true install` and use that exact local version.

## Channel construction fails

Validate the `LarkChannelOptions` object before calling the factory: non-null options, non-blank app ID, either a valid app secret or `ClientAssertionProvider`, exact supported transport, and non-null nested config objects. Construction creates main-SDK clients and dispatchers; incompatible dependency mediation can also appear here. Run the dependency checks below.

## Connection problems

### `connect()` fails with `not_connected`

Check:

1. `APP_ID` and credential source are non-empty and belong to the same application.
2. The bot capability is enabled and installed in the target tenant.
3. `domain`, `oauthBaseUrl`, proxy, DNS, and egress rules point to the intended environment.
4. The bot-info OpenAPI can return a bot `open_id`.
5. For WebSocket, the first handshake can complete within 15 seconds.

A failed `connect()` attempt clears its shared future, so a later call can retry after the root cause is fixed. Use bounded application backoff; do not run an infinite tight reconnect loop.

### Identity succeeds but WebSocket never becomes ready

Confirm `transport("websocket")`, long-connection event mode in the developer console, compatible `oapi-sdk`, network egress, and app installation. Subscribe to `reconnecting`/`reconnected` for state metrics. Avoid printing the WebSocket URL or authentication data.

### Processing reports `not_connected`

Inbound message normalization needs the bot identity. Register handlers and complete `connect()` before enabling Webhook routing or accepting WebSocket events.

## No `message` handler invocation

Check in order:

- the platform callback is a supported message-receive event;
- the application version and event subscription are active;
- a second `channel.on("message", ...)` did not replace the intended handler;
- the message is not older than the stale window;
- it is not a duplicate already marked seen;
- the group chat is allowed;
- the bot is directly mentioned when required;
- `@all` is allowed only if intended;
- direct-message mode and sender allowlist permit it;
- batching delay has elapsed.

Subscribe to `reject` and `error`. Stale and duplicate events are silently dropped by the current pipeline, so use controlled test fixtures and metrics when isolating those cases.

### The raw callback arrives but the normalized handler does not

If the HTTP/WebSocket layer sees a callback, confirm it is one of the supported event types, the dispatcher is the one created by this Channel, and `connect()` completed before dispatch. Then inspect `reject` and `error`. Enabling `includeRawEvent` only adds raw data to a normalized model; it does not make unsupported callbacks become `message` events.

### Group messages are rejected

Read `RejectEvent.getReason()`: check group allowlist first, then `@all`, then direct bot mention. An empty allowlist permits all groups, while a non-empty list must contain the exact chat ID. Do not disable mention policy globally just to fix one chat; correct the intended allowlist or message interaction.

## Unexpected merged messages

Per-chat batching is enabled by default. Nearby messages can become one `NormalizedMessage`, with content joined and metadata taken from the latest message. Disable `chatQueueEnabled` when exact per-event semantics are required, or tune `BatchTextConfig`. Read the sender-identity warning in [Policy and safety](policy-and-safety.md#message-batching).

## Webhook verification failure

Verify that:

- the runtime Verification Token and Encrypt Key exactly match the developer console;
- the HTTP adapter preserves raw body bytes and signature-related headers;
- the body was not decoded/re-encoded before `EventReq` construction;
- the endpoint path and method are correct;
- proxy middleware does not consume or alter the body.

Never solve this by calling `doWithoutValidation` in production. Use synthetic fixtures to debug the adapter.

## Send failures

| Error code | Action |
| --- | --- |
| `permission_denied` | Check app permission, tenant grant/install state, and credential environment. Do not retry. |
| `rate_limited` | Keep built-in attempts at three or fewer; reduce traffic and honor platform limits. |
| `format_error` | Validate post/card structure. Markdown may fall back to text. |
| `target_revoked` | The reply target is gone; decide whether create-message fallback is acceptable. |
| `send_timeout` | Inspect network and main-SDK timeout settings; avoid blind retry of an ambiguous send. |
| `unknown` | Inspect the private cause and platform response; avoid nested retry loops. |

For long text, `messageId` is the first ID and `chunkIds` lists all created chunks. Only the first chunk is a reply. A report that “the rest escaped the thread” can be this documented behavior rather than a platform failure.

### Receiver ID type is wrong

The target prefix selects routing: `oc_` chat, `ou_` open ID, `on_` union ID, values containing `@` email, and other values user ID. Validate the ID from its authoritative source. An arbitrary string can be routed as `user_id` and then fail at the platform.

### A reply became a new message

When the platform reports the reply target has disappeared, the SDK creates a new message in `to`. Verify the original message ID and retention state. If fallback is unsafe for the workflow, detect and constrain it at the business layer rather than assuming every returned message remains a reply.

### Markdown became plain text

Markdown is converted to a platform post. A classified post `format_error` triggers plain-text fallback. Simplify/validate the Markdown or use a schema-valid raw post/card when exact layout matters.

## Media failures

### `ssrf_blocked`

Confirm the URL uses HTTP(S), resolves only to public addresses, passes every redirect, and has a valid TLS hostname. Do not disable the guard. If an internal host is truly required, use a narrowly controlled exact host allowlist plus network egress policy.

### Local file is rejected

Confirm it is a readable regular file, resolves under an `allowedFileDirs` real path, and is not in a blocked system tree. Do not broaden the directory to `/`, a home directory, or another general-purpose root.

### `upload_failed` for audio/video duration

Supply a positive duration in milliseconds, or use parseable Opus/Ogg audio and MP4 video. Apply a size limit before passing local, byte-array, or stream content.

### Memory usage increases during upload/download

The preview materializes byte arrays and input streams and returns downloads as `byte[]`; duration parsing also reads files. Bound content before SDK calls and avoid concurrent large transfers. Remote URL download alone has the built-in 50 MiB cap.

## Streaming failures

If users see “Generation interrupted,” the streaming message was created but the producer or update failed. Inspect the operation's exceptional completion. Do not automatically replay the whole producer: it can create duplicate messages or model calls. Provide an explicit business retry with idempotency.

If updates appear infrequent, remember throttling is checked only when the producer calls `append`, `setContent`, or `update`; there is no background flush timer. The final producer return forces a flush.

## Dependency and classpath problems

Run:

```bash
mvn dependency:tree -Dincludes=com.larksuite.oapi
mvn dependency:analyze
jar tf target/channel-sdk-*.jar | sort
```

Look for multiple `oapi-sdk` versions, both Channel namespaces, or copied `com.lark.oapi` packages. The standalone artifact should own only `com.lark.channel` classes. If a method is missing at runtime but compilation passed, inspect the final packaged application's dependency resolution, not only the module POM.

## Custom domain or OAuth problems

`domain(...)` configures the main OpenAPI client and WebSocket domain; `oauthBaseUrl(...)` separately overrides OAuth/client-assertion endpoints. Ensure both belong to the same intended environment, use trusted deployment configuration, resolve through the proxy/DNS policy, and have valid TLS. Do not accept either value from an untrusted request. If defaults work and custom values fail, compare the two routing paths without logging credentials.

## JDK build problems

Use Maven 3.6.3+ and a real JDK, not a JRE. The release targets are JDK 8 and 11 with Java 8 bytecode. Run `mvn -version` to confirm the active Java home. Newer JDKs are not yet in the verified matrix.

## Collecting a safe diagnostic report

Include:

- Channel version, main SDK version, JDK, Maven, and OS;
- transport mode and whether connection reached ready;
- stable exception code and redacted stack trace;
- event name or send kind, without content;
- minimal reproducible code using synthetic IDs/data;
- dependency tree limited to relevant SDK artifacts.

Exclude:

- application credentials, access tokens, assertions, and private keys;
- verification/encryption values and tenant/user identifiers;
- raw event bodies, message text, media bytes, and unrestricted logs.
