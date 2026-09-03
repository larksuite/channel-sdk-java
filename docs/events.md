# Events

[Documentation index](README.md) | [简体中文](zh-CN/events.md) | English

The Channel SDK converts selected Lark/Feishu callbacks into stable Java models and dispatches them by a short event name. WebSocket and Webhook transports use the same dispatcher and normalization pipeline, so application handlers can remain transport-independent.

## Supported events

| Event name | Model | Source callback | Safety behavior |
| --- | --- | --- | --- |
| `message` | `NormalizedMessage` | Message received | Policy, stale check, deduplication, per-chat ordering, optional batching |
| `reaction` | `ReactionEvent` | Reaction added/removed | Deduplicated lightweight dispatch |
| `botAdded` | `BotAddedEvent` | Bot added to chat | Direct normalized dispatch |
| `cardAction` | `CardActionEvent` | Card action trigger | Deduplication and per-chat ordering |
| `comment` | `CommentEvent` | `drive.notice.comment_add_v1` | Deduplication and per-file ordering |
| `reject` | `RejectEvent` | Local policy rejection | Reports why a message did not reach `message` |
| `error` | `ChannelErrorEvent` | Local processing or handler failure | Structured error notification |
| `reconnecting` | `Void`/`null` | WebSocket state change | WebSocket only |
| `reconnected` | `Void`/`null` | WebSocket state change | WebSocket only |

Only the callbacks above are converted by the preview. Subscribe to additional platform events through the raw main SDK rather than assuming arbitrary events reach `channel.on(...)`.

WebSocket mode feeds supported callbacks from the main SDK long-connection client into this dispatcher. Webhook mode feeds requests handled by the host's `EventDispatcher`. Normalized payloads are the same, but reconnecting events exist only for WebSocket and HTTP response timing exists only for Webhook.

## Register handlers

```java
ChannelSubscription subscription = channel.on(
        "message",
        new ChannelEventHandler<NormalizedMessage>() {
            @Override
            public void handle(NormalizedMessage message) {
                // Keep work bounded; hand long-running work to a controlled executor.
            }
        });

// Later:
subscription.unsubscribe();
```

There is one handler slot per event name. Registering a second handler for the same name replaces the first and logs a warning. The returned subscription removes the handler only if it is still the registered instance.

Several handlers can be registered together:

```java
Map<String, ChannelEventHandler<?>> handlers = new HashMap<String, ChannelEventHandler<?>>();
handlers.put("message", new ChannelEventHandler<NormalizedMessage>() {
    @Override
    public void handle(NormalizedMessage event) {
        // Handle message.
    }
});
handlers.put("reject", new ChannelEventHandler<RejectEvent>() {
    @Override
    public void handle(RejectEvent event) {
        // Count rejection reason.
    }
});
ChannelSubscription all = channel.on(handlers);
```

## Normalized messages

`NormalizedMessage` exposes common fields without requiring application code to parse JSON:

| Field | Meaning |
| --- | --- |
| `messageId` | Current platform message ID |
| `chatId` | Target accepted by `channel.send(...)` |
| `chatType` | Usually `p2p`, `group`, or `topic_group` |
| `senderId` / `senderName` | Sender identity when available |
| `content` | Converted text with the bot mention removed |
| `rawContentType` | Original type such as `text`, `post`, `image`, or `file` |
| `resources` | Image/file/audio/video descriptors extracted from the message |
| `mentions` | Parsed user mention metadata in message order |
| `mentionAll` / `mentionedBot` | Mention state used by policy |
| `rootId` / `threadId` / `replyToMessageId` | Reply and thread context when supplied by the platform |
| `createTime` | Platform timestamp in milliseconds when available |
| `raw` | Original event only when raw-event inclusion is enabled |

Converters handle common text, post, interactive, image, file, audio, video, sticker, location, share, folder, calendar, system, todo, vote, red-packet, video-chat, and merged-forward shapes. A fallback converter preserves a readable representation for unrecognized content. Treat conversion as convenience, not as a guarantee that every future platform field is preserved.

For inbound media, `ResourceDescriptor` gives a platform `fileKey`; call `downloadResource(fileKey, type)` when the application needs the bytes. Do not treat the key as an outbound local path or remote URL.

## Other normalized event fields

| Model | Fields |
| --- | --- |
| `ReactionEvent` | message ID, operator ID/type, emoji type, action (`added`/`removed`), action time, optional raw body |
| `BotAddedEvent` | chat ID, operator ID, bot/name field supplied by the callback, optional raw body |
| `CardActionEvent` | message/chat/operator IDs, component tag/name/option, action value map, optional raw body |
| `CommentEvent` | file token/type, comment/reply IDs, operator ID, whether the bot was mentioned, timestamp, optional raw body |

Fields can be null or empty when the platform callback does not supply them. Validate required identifiers before performing a business action. Card action values and comment metadata remain user/platform input and must not be concatenated into commands, paths, or queries.

## Rejections

The `reject` event is operationally different from `error`: rejection is an expected policy outcome. `RejectReason` can be:

- `group_not_allowed`
- `sender_not_allowed`
- `no_mention`
- `dm_disabled`
- `mention_all_blocked`

Track bounded counts by reason. Avoid logging full messages or raw events merely because they were rejected.

## Errors

When normalization, safety processing, or a user handler throws, the SDK emits `ChannelErrorEvent` with the event name, exception, and associated event object. If no `error` handler exists, the SDK logs an unhandled warning. An exception thrown by the `error` handler is caught and logged to avoid recursion.

```java
channel.on("error", new ChannelEventHandler<ChannelErrorEvent>() {
    @Override
    public void handle(ChannelErrorEvent event) {
        metrics.increment("channel_event_error", event.getEventName());
        logger.error("Channel event failed: {}", event.getEventName(), event.getError());
    }
});
```

The `metrics` and `logger` objects above belong to the host application. Never use unbounded event values as metric labels and never serialize `event.getEvent()` into logs by default.

## Raw event opt-in

```java
LarkChannelOptions.newBuilder(appId, appSecret)
        .includeRawEvent(true)
        .build();
```

Raw payloads are disabled by default. Enable them only for a documented field gap, limit retention, and redact before logging. This flag affects normalized model `getRaw()` values; it does not change the public normalized fields.

## Delivery expectations

Platform callbacks can be duplicated, delayed, or delivered again after a reconnect. The Channel pipeline suppresses duplicates within its configured scope, but business side effects must still be idempotent. Use a stable business key or database uniqueness constraint for writes. In multi-instance deployments, a shared `ICache` helps later duplicate checks, but the processing lock and check-then-mark sequence remain process-local and non-atomic.

Handlers execute on SDK event-processing threads. Do not block indefinitely, start unmanaged threads, or perform unbounded retries. Apply timeouts to downstream calls and move expensive work to an executor with explicit queue and concurrency limits.

For `message`, the complete order is stale check → seen lookup → policy → best-effort process-local lock → optional per-chat queue/batch → handler → mark seen/release. Card actions and comments use seen lookup, local lock, scoped ordering, then dispatch; reactions are marked seen before lightweight dispatch. See [Policy and safety](policy-and-safety.md) for consequences.
