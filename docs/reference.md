# API reference

[Documentation index](README.md) | [简体中文](zh-CN/reference.md) | English

This page is a navigation reference for the current Java preview. The source Javadocs remain authoritative for exact signatures; topic guides explain behavior and operational constraints.

## Public package map

| Package | Role |
| --- | --- |
| `com.lark.channel` | Factory, facade, event handler, and subscription |
| `com.lark.channel.config` | Construction and runtime configuration models |
| `com.lark.channel.model` | Normalized event, send, stream, identity, and result models |
| `com.lark.channel.exception` | Stable Channel exception and code enum |

Some implementation packages currently contain public Java classes for internal composition, but they are not the recommended application surface. Prefer the packages above and the `LarkChannel` facade.

## Construction

| API | Purpose |
| --- | --- |
| `LarkChannelOptions.newBuilder(appId, appSecret)` | Start immutable top-level configuration. |
| `LarkChannelFactory.createLarkChannel(options)` | Construct the facade and underlying main-SDK clients. |

Configuration types under `LarkChannelOptions`:

- `Builder`
- `WebhookOptions`
- `PolicyConfig`
- `SafetyConfig`
- `BatchTextConfig`
- `OutboundConfig`
- `RetryConfig`

See [Configuration](configuration.md) for every option and default.

## Lifecycle and identity

| Method | Return | Notes |
| --- | --- | --- |
| `start()` | `void` | Connect a standalone WebSocket bot and block until disconnected. |
| `connect()` | `CompletableFuture<BotIdentity>` | Resolve identity and start/wait for WebSocket when configured. |
| `connectSync()` | `BotIdentity` | Synchronously wait for connection readiness, then return. |
| `disconnect()` | `CompletableFuture<Void>` | Close transport and dispose safety queues. |
| `disconnectSync()` | `void` | Blocking variant. |
| `isConnected()` | `boolean` | True after successful connect and before disconnect. |
| `getBotIdentity()` | `BotIdentity` | Null before successful connect. |

`BotIdentity` exposes `getOpenId()` and `getName()`.

`start()` is intended for a standalone WebSocket process. Hosted applications and Webhook integrations should use `connect()` / `disconnect()` from their own lifecycle. Concurrent `disconnect()` calls share one cleanup future. Once disconnected, a Channel instance cannot reconnect.

## Event subscription

| Method | Purpose |
| --- | --- |
| `<T> on(String, ChannelEventHandler<T>)` | Register or replace one event handler. |
| `on(Map<String, ChannelEventHandler<?>>)` | Register a batch as one subscription. |
| `ChannelSubscription.unsubscribe()` | Remove the registered handler(s). |
| `createWebhookDispatcher()` | Get the main-SDK `EventDispatcher` for a Webhook host. |

Event models:

- `NormalizedMessage`
- `ReactionEvent`
- `BotAddedEvent`
- `CardActionEvent`
- `CommentEvent`
- `RejectEvent` and `RejectReason`
- `ChannelErrorEvent`

See [Events](events.md) for event names and fields.

## Sending

| Method | Return |
| --- | --- |
| `send(to, input)` | `CompletableFuture<SendResult>` |
| `send(to, input, options)` | `CompletableFuture<SendResult>` |
| `sendSync(to, input)` | `SendResult` |
| `sendSync(to, input, options)` | `SendResult` |

`SendInput` factories:

- `text(String)`
- `markdown(String)`
- `post(Map<String, Object>)`
- `image(String|byte[]|Object)`
- `file(String|byte[]|Object, fileName)`
- `audio(String|byte[]|Object, durationMs)`
- `video(String|byte[]|Object, durationMs, coverImageKey)`
- `card(Map<String, Object>)`
- `shareChat(chatId)`
- `shareUser(userId)`
- `sticker(fileKey)`

`SendOptions.Builder` supports `replyTo`, `replyInThread`, `mentions(List<String>)`, and `mentionInfos(List<MentionInfo>)`. `SendResult` exposes `messageId` and `chunkIds`.

See [Sending messages](sending-messages.md) and [Media](media.md).

## Streaming

| Method | Return |
| --- | --- |
| `stream(to, input)` | `CompletableFuture<SendResult>` |
| `stream(to, input, options)` | `CompletableFuture<SendResult>` |
| `streamSync(to, input)` | `SendResult` |
| `streamSync(to, input, options)` | `SendResult` |

`StreamInput.markdown(MarkdownStreamProducer)` provides a `MarkdownStreamController` with `append`, `setContent`, and `getMessageId`. `StreamInput.card(initial, CardStreamProducer)` provides a `CardStreamController` with `update`, `getCurrent`, and `getMessageId`.

See [Streaming](streaming.md).

## Message and resource helpers

All methods below are asynchronous:

| Method | Purpose |
| --- | --- |
| `editMessage(messageId, text)` | Update text/post content. |
| `updateCard(messageId, card)` | Patch an interactive card. |
| `recallMessage(messageId)` | Recall a bot-sent message. |
| `downloadResource(fileKey, type)` | Download image/file bytes. |
| `addReaction(messageId, emojiType)` | Add reaction and return reaction ID. |
| `removeReaction(messageId, reactionId)` | Remove an exact bot-created reaction. |
| `removeReactionByEmoji(messageId, emojiType)` | Find and remove the bot's matching reaction. |
| `getChatInfo(chatId)` | Fetch basic chat metadata. |

`ChatInfo` exposes chat ID, name, description, type, owner ID, and optional member count.

## Policy

| Method | Purpose |
| --- | --- |
| `updatePolicy(PolicyConfig)` | Copy all policy fields into the active policy. |
| `getPolicy()` | Return the mutable active policy object. |

Treat updates as complete configuration replacement and serialize mutations. See [Policy and safety](policy-and-safety.md).

## Escape hatches

| Method | Return |
| --- | --- |
| `getRawClient()` | `com.lark.oapi.Client` |
| `getRawWsClient()` | `com.lark.oapi.ws.Client`, or null in Webhook mode |

Raw clients follow the main SDK's API, compatibility, and security rules. Channel does not wrap or normalize raw call responses.

## Exceptions

`LarkChannelException` is a runtime exception with `getCode()` and optional `getContext()`. Stable preview codes are defined by `LarkChannelErrorCode`: `format_error`, `target_revoked`, `rate_limited`, `permission_denied`, `upload_failed`, `ssrf_blocked`, `send_timeout`, `not_connected`, and `unknown`.

Asynchronous methods complete exceptionally; blocking variants throw, often wrapped by `CompletionException` when `.join()` is involved. Inspect the cause chain without exposing sensitive platform messages.

## Javadoc

Generate local Javadocs with `mvn javadoc:javadoc` or as part of `mvn verify`, then open `target/site/apidocs/index.html` or the generated `target/apidocs` output used by the build. Each Maven Central release also includes a Javadoc JAR.
