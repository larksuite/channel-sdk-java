# Configuration

[Documentation index](README.md) | [简体中文](zh-CN/configuration.md) | English

All channel construction starts with `LarkChannelOptions.newBuilder(appId, appSecret)`. The top-level options are immutable after `build()`; nested configuration objects are mutable Java beans so they can be assembled before construction and, for policy only, applied later through `updatePolicy`.

## Top-level builder methods

| Method | Default | Purpose |
| --- | --- | --- |
| `transport(String)` | `websocket` | Select WebSocket or Webhook inbound mode. |
| `webhook(WebhookOptions)` | empty bean | Configure callback verification/decryption. |
| `safety(SafetyConfig)` | documented safety defaults | Configure stale, dedup, queue, lock, and batching behavior. |
| `policy(PolicyConfig)` | documented policy defaults | Configure group and direct-message admission. |
| `outbound(OutboundConfig)` | documented outbound defaults | Configure splitting, streaming, media, and retries. |
| `cache(ICache)` | `null` | Main-SDK cache plus cross-process seen-event lookup. |
| `domain(String)` | main-SDK Feishu default | Override OpenAPI and WebSocket domain. |
| `httpTransport(IHttpTransport)` | main-SDK default | Override HTTP transport. |
| `httpInstance(RequestOptions)` | `null` | Supply request options to main-SDK calls. |
| `source(String)` | `null` | Supply a main-SDK source identifier. |
| `clientAssertionProvider(...)` | `null` | Enable client-assertion authentication. |
| `oauthBaseUrl(String)` | main-SDK default | Override OAuth/assertion base URL. |
| `includeRawEvent(boolean)` | `false` | Attach original callback data to normalized models. |
| `includeRawInMessage(boolean)` | `false` | Deprecated alias of `includeRawEvent`. |

The nested default objects are created by the builder. Passing null into nested setters is not uniformly normalized, so application configuration should construct and validate complete objects.

## Credentials

```java
LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret).build();
```

Read `appId` and `appSecret` from a secret manager or runtime environment. Do not log either value. The SDK does not validate arbitrary application input for you; reject null or blank configuration at your process boundary.

For private-key authentication, provide the main SDK's `ClientAssertionProvider`:

```java
LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, "")
        .clientAssertionProvider(clientAssertionProvider)
        .build();
```

An empty app secret is appropriate only when a non-null provider supplies valid client assertions. Keep private keys outside this SDK and rotate them through your credential system. `oauthBaseUrl(...)` can override the assertion/OAuth endpoint base URL when the main SDK deployment requires it.

## Transport

The supported values are:

| Value | Behavior |
| --- | --- |
| `websocket` | Default. Creates the raw WebSocket client; `connect()` waits for identity and first handshake. |
| `webhook` | Does not create a WebSocket client. The host obtains `createWebhookDispatcher()` and attaches it to its HTTP endpoint. |

```java
LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .transport("websocket")
        .build();
```

The preview does not reject unknown transport strings; unknown values behave like a non-WebSocket transport but are unsupported. Validate configuration against exactly `websocket` or `webhook` before building the channel.

Webhook verification values are separate from application credentials:

```java
LarkChannelOptions.WebhookOptions webhook = new LarkChannelOptions.WebhookOptions();
webhook.setVerificationToken(verificationToken);
webhook.setEncryptKey(encryptKey);

LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .transport("webhook")
        .webhook(webhook)
        .build();
```

See [Webhook integration](webhook.md) for request handling and lifecycle requirements.

## Domain and main SDK transport

The Channel SDK forwards these settings to the underlying main SDK:

| Builder method | Purpose |
| --- | --- |
| `domain(String)` | OpenAPI base domain and WebSocket domain; default is the Feishu domain used by the main SDK. |
| `httpTransport(IHttpTransport)` | Custom main-SDK HTTP transport. |
| `httpInstance(RequestOptions)` | Per-client request options used by bot identity and OpenAPI calls. |
| `cache(ICache)` | Main-SDK cache and Channel cross-process seen-event lookup. |
| `source(String)` | Source identifier passed to the main SDK. |
| `clientAssertionProvider(...)` | Private-key client assertion provider. |
| `oauthBaseUrl(String)` | OAuth base URL override. |

Use one consistent environment/domain for HTTP and WebSocket traffic. Do not accept a domain directly from an untrusted request; configure it from trusted deployment settings.

## Raw event opt-in

Normalized events omit the original event body by default:

```java
LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .includeRawEvent(true)
        .build();
```

Enable this only when a handler needs fields that normalization intentionally drops. Raw events can contain identifiers, tenant metadata, message content, and vendor extensions. Avoid retaining or logging them. `includeRawInMessage(...)` is deprecated; use `includeRawEvent(...)`.

## Policy defaults

`PolicyConfig` controls which messages reach the `message` handler:

| Option | Default | Meaning |
| --- | --- | --- |
| `groupAllowlist` | empty | Empty permits all groups; otherwise only listed chat IDs pass. |
| `dmMode` | `open` | Supported values are `open`, `disabled`, and `allowlist`. |
| `dmAllowlist` | empty | Sender open IDs allowed when `dmMode=allowlist`. |
| `requireMention` | `true` | Group messages must directly mention the bot. |
| `respondToMentionAll` | `false` | Whether `@all` passes the group mention gate. |

```java
LarkChannelOptions.PolicyConfig policy = new LarkChannelOptions.PolicyConfig();
policy.setGroupAllowlist("oc_allowed_chat");
policy.setDmMode("allowlist");
policy.setDmAllowlist("ou_allowed_user");
policy.setRequireMention(true);
policy.setRespondToMentionAll(false);
```

Unknown `dmMode` values currently behave like `open`, so applications must validate the value themselves. See [Policy and safety](policy-and-safety.md) before changing defaults.

## Safety defaults

| Option | Default |
| --- | --- |
| Duplicate TTL | 12 hours |
| In-memory duplicate capacity | 5,000 entries |
| Duplicate sweep interval | 5 minutes |
| Stale-message window | 30 minutes |
| Per-chat queue | enabled |
| Process-local processing-lock TTL | 5 minutes |
| Dedup namespace | `channel:seen` |
| Short-text batch delay | 600 ms |
| Long-text threshold/delay | 1,000 characters / 2,000 ms |
| Batch limit | 8 messages or 4,000 characters |

```java
LarkChannelOptions.SafetyConfig safety = new LarkChannelOptions.SafetyConfig();
safety.setDedupTtlMs(6L * 60L * 60L * 1000L);
safety.setDedupMaxEntries(3000);
safety.setStaleMessageWindowMs(10L * 60L * 1000L);
safety.setChatQueueEnabled(true);

LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .safety(safety)
        .build();
```

Capacity and TTL values must remain positive and bounded in application configuration. A shared `ICache` helps later duplicate checks observe events marked by another process. The check-and-mark sequence and processing lock are not distributed or atomic, so application side effects must remain idempotent across instances.

## Outbound defaults

| Option | Default | Purpose |
| --- | --- | --- |
| `textChunkLimit` | 3,500 | Split long text and Markdown. |
| `streamThrottleMs` | 100 | Minimum timed flush interval. |
| `streamThrottleChars` | 50 | Character-based stream flush threshold. |
| `streamInitialText` | `Thinking...` | Initial Markdown streaming card text. |
| `ssrfGuardEnabled` | `true` | Reject private/reserved remote media destinations. |
| `ssrfAllowlist` | empty | Exact normalized hosts permitted through the address guard. |
| retry max attempts | 3 | Total attempts for retryable outbound failures. |
| retry base delay | 500 ms | First backoff delay. |
| `allowedFileDirs` | empty | No additional directory restriction; any readable non-blocked local path can pass. Configure this in production. |

```java
LarkChannelOptions.OutboundConfig outbound = new LarkChannelOptions.OutboundConfig();
outbound.setTextChunkLimit(3000);
outbound.setStreamInitialText("Generating...");
outbound.setAllowedFileDirs(java.util.Collections.singletonList("/srv/bot/uploads"));

LarkChannelOptions.RetryConfig retry = new LarkChannelOptions.RetryConfig();
retry.setMaxAttempts(3);
retry.setBaseDelayMs(500L);
outbound.setRetry(retry);

LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .outbound(outbound)
        .build();
```

Keep SSRF protection enabled. An allowlisted host bypasses private/reserved-address rejection, so add only infrastructure-controlled hosts. See [Media](media.md) and [Sending messages](sending-messages.md).

## Complete construction example

```java
LarkChannelOptions.PolicyConfig policy = new LarkChannelOptions.PolicyConfig();
policy.setGroupAllowlist("oc_support_group");
policy.setDmMode("allowlist");
policy.setDmAllowlist("ou_operator");

LarkChannelOptions.SafetyConfig safety = new LarkChannelOptions.SafetyConfig();
safety.setStaleMessageWindowMs(15L * 60L * 1000L);

LarkChannelOptions.OutboundConfig outbound = new LarkChannelOptions.OutboundConfig();
outbound.setAllowedFileDirs(java.util.Collections.singletonList("/srv/channel/files"));

LarkChannel channel = LarkChannelFactory.createLarkChannel(
        LarkChannelOptions.newBuilder(appId, appSecret)
                .transport("websocket")
                .policy(policy)
                .safety(safety)
                .outbound(outbound)
                .includeRawEvent(false)
                .build());
```

Treat configuration loading as an application boundary: validate type, range, enum values, paths, and domains before constructing these objects.

## Common misconfiguration

- Blank app credentials without a valid `ClientAssertionProvider` lead to authentication or connection failure.
- Any transport string other than exact `websocket` or `webhook` is unsupported and may silently behave as non-WebSocket.
- Unknown `dmMode` values widen behavior to open; validate the enum.
- Empty `allowedFileDirs` does not block local files; configure a narrow production directory list.
- Zero/negative capacities or excessively large TTL/batch/retry values can remove resource bounds or create long stalls.
- A custom `domain` does not automatically prove the chosen OAuth endpoint and network environment are consistent; validate both `domain` and `oauthBaseUrl`.
- Enabling raw events can expose extra user and tenant data to application memory and logs.
