# Sending messages

[Documentation index](README.md) | [简体中文](zh-CN/sending-messages.md) | English

`LarkChannel.send(...)` provides one Java entry point for common outbound message types. It selects the platform receive-ID type from the target, uploads media when necessary, applies reply and mention options, splits long text, and classifies platform failures.

## Asynchronous and blocking APIs

```java
CompletableFuture<SendResult> future = channel.send(
        "oc_chat_id",
        SendInput.text("Hello"));

future.whenComplete((result, error) -> {
    if (error != null) {
        // Handle and classify the failure.
    }
});
```

For command-line or otherwise blocking code:

```java
SendResult result = channel.sendSync("oc_chat_id", SendInput.text("Hello"));
```

The asynchronous method uses `CompletableFuture`; attach an error continuation or explicitly await it. Do not fire and forget. Apply an application-level operation deadline where needed, because the future itself does not accept a cancellation context.

## Targets

The `to` string determines the receive-ID type:

| Shape | Receive ID type |
| --- | --- |
| starts with `oc_` | `chat_id` |
| starts with `ou_` | `open_id` |
| starts with `on_` | `union_id` |
| contains `@` | `email` |
| any other non-empty value | `user_id` |

Reject empty targets and validate identifiers at the application boundary. Do not accept arbitrary destinations from an untrusted request without authorization.

## Content factories

Each `SendInput` factory creates exactly one `Kind`; Java callers do not pass a bag of competing content fields.

```java
SendInput.text("plain text");
SendInput.markdown("**Markdown** with `code`");
SendInput.post(postMap);
SendInput.card(cardMap);
SendInput.image("https://cdn.example.com/image.png");
SendInput.file("/srv/channel/files/report.pdf", "report.pdf");
SendInput.audio(audioBytes, durationMs);
SendInput.video(videoFile, durationMs, coverImageKey);
SendInput.shareChat("oc_chat_to_share");
SendInput.shareUser("ou_user_to_share");
SendInput.sticker("file_key");
```

Image/file/audio/video sources may be a URL string, local-path string, `File`, `byte[]`, or `InputStream`. All of those forms upload a new platform resource. A string passed to `image(...)` is not interpreted as an existing `image_key`; stickers are the factory that accepts an existing file key. See [Media](media.md).

## Text and Markdown splitting

Text and Markdown are split when they exceed `outbound.textChunkLimit` (default 3,500 characters). Markdown splitting preserves code fences, then each chunk is converted to a platform post. If a post-format send is rejected as `format_error`, the sender falls back to plain text for that chunk.

For a multi-chunk send:

- `SendResult.getMessageId()` is the first created message ID;
- `SendResult.getChunkIds()` contains the IDs returned for all chunks;
- reply options apply only to the first chunk; later chunks are new messages;
- mention options are composed only into the first Markdown chunk.

Raw `SendInput.post(...)` content is sent as supplied. `SendOptions.mentions` is not injected into a raw post; include valid mention elements in the post object yourself.

## Replies and threads

```java
SendOptions options = SendOptions.newBuilder()
        .replyTo(inbound.getMessageId())
        .replyInThread(Boolean.TRUE)
        .build();

channel.send(inbound.getChatId(), SendInput.text("Reply"), options);
```

If the platform reports that the reply target disappeared, the SDK falls back to creating a new message in `to`. Decide whether that fallback is acceptable for sensitive workflows; the result may no longer be attached to the original context.

## Mentions

Mention users by open ID:

```java
SendOptions options = SendOptions.newBuilder()
        .mentions(java.util.Arrays.asList("ou_user_1", "ou_user_2"))
        .build();
```

Or provide full `MentionInfo` values with `mentionInfos(...)`. Mentions are prepended to plain text and composed into generated Markdown posts. Validate that callers are allowed to notify those users; do not turn arbitrary input into mass notifications.

## Cards, shares, and stickers

`SendInput.card(...)` accepts an interactive-card JSON object represented as `Map<String, Object>`. Card structure is platform-defined and is not schema-validated by the Channel SDK. Validate templates and user-supplied values before building the map.

`shareChat(...)`, `shareUser(...)`, and `sticker(...)` send platform references directly. The current bot and application permissions still determine whether the platform accepts them.

## Results

`SendResult` contains only:

- `messageId`: first or single message ID;
- `chunkIds`: all chunk IDs when the operation creates multiple messages.

It does not contain target metadata or an embedded error. Failures complete the future exceptionally or are thrown by the sync method.

## Error classification

Outbound failures are wrapped as `LarkChannelException` with a stable `getCode()` string:

| Code | Typical meaning | Default retry |
| --- | --- | --- |
| `format_error` | Platform rejected message format | No; Markdown may fall back to text |
| `target_revoked` | Reply target no longer exists | No; reply may fall back to create |
| `rate_limited` | Platform throttled the request | Yes |
| `permission_denied` | Credential or permission problem | No |
| `upload_failed` | Media materialization or upload failed | No |
| `ssrf_blocked` | Remote media URL failed SSRF policy | No |
| `send_timeout` | Send operation timed out | No automatic retry |
| `not_connected` | Required channel identity/connection absent | No |
| `unknown` | Unclassified platform/transport failure | Yes |

The built-in sender defaults to at most three total attempts for `rate_limited` and `unknown`, with delays based on 500 ms then 1,500 ms. It does not add jitter. Keep configured attempts at three or fewer and do not wrap `send` in another automatic retry layer; nested retries multiply traffic. For non-idempotent business behavior, use the returned message ID and your own idempotency record.

Because classification currently examines platform error text and codes, retain the underlying cause for diagnostics but do not expose it directly to end users.

## Low-level helpers

The facade also exposes asynchronous helpers:

```java
channel.editMessage(messageId, "replacement text");
channel.updateCard(messageId, cardMap);
channel.recallMessage(messageId);
channel.addReaction(messageId, "THUMBSUP");
channel.removeReaction(messageId, reactionId);
channel.removeReactionByEmoji(messageId, "THUMBSUP");
channel.getChatInfo(chatId);
```

Store the `reactionId` returned by `addReaction` when exact removal is required. A bot can remove only reactions it created. `editMessage` is for text/post messages; use `updateCard` for interactive cards.

For APIs not represented here, call `channel.getRawClient()` and follow main SDK models and error handling. Keep that raw integration behind an application-owned adapter so it can be replaced when the Channel facade grows.
