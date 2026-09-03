# Streaming

[Documentation index](README.md) | [简体中文](zh-CN/streaming.md) | English

Streaming turns incrementally generated output into a progressively updated message. The Java Channel SDK supports generated Markdown through a CardKit streaming card and arbitrary interactive cards through message patching.

## Markdown streaming

```java
SendResult result = channel.streamSync(
        "oc_chat_id",
        StreamInput.markdown(new MarkdownStreamProducer() {
            @Override
            public void produce(MarkdownStreamController controller) throws Exception {
                controller.append("## Answer\n\n");
                controller.append("First chunk. ");
                controller.append("Second chunk.");
            }
        }));
```

The SDK creates a streaming card with the configured initial text (`Thinking...` by default). `append(...)` merges incremental deltas. `setContent(...)` replaces the full accumulated text, which is useful when a model returns a corrected complete answer. `getMessageId()` is available after the streaming message has been created.

An empty chunk is ignored. On normal completion, pending updates are flushed and the card leaves streaming mode. If the producer fails, the SDK attempts to finish the card with a “Generation interrupted” marker, then completes the stream operation exceptionally.

## Interactive-card streaming

```java
Map<String, Object> initialCard = buildInitialCard();

CompletableFuture<SendResult> future = channel.stream(
        "oc_chat_id",
        StreamInput.card(initialCard, new CardStreamProducer() {
            @Override
            public void produce(CardStreamController controller) throws Exception {
                Map<String, Object> next = new LinkedHashMap<String, Object>(controller.getCurrent());
                next.put("body", buildUpdatedBody());
                controller.update(next);
            }
        }));

future.whenComplete((result, error) -> {
    if (error != null) {
        // Record a bounded failure and decide whether the business job should be retried.
    }
});
```

Card mode sends the initial map as an interactive message, then patches the whole current card state on `update(...)`. The controller copies the top-level map, but nested objects are still application-owned; avoid mutating shared maps concurrently. `getCurrent()` returns the latest state known by the controller.

On producer failure, the SDK attempts to append a visible interruption note to the card. The original failure is still propagated.

## Replies and target routing

Streaming accepts the same target formats and `SendOptions` as normal sends:

```java
SendOptions options = SendOptions.newBuilder()
        .replyTo(messageId)
        .replyInThread(Boolean.TRUE)
        .build();

channel.stream(chatId, StreamInput.markdown(producer), options);
```

The initial streaming message uses these reply options. See [Sending messages](sending-messages.md) for target detection and reply fallback.

## Throttling

Two outbound options control update coalescing:

| Option | Default | Trigger |
| --- | --- | --- |
| `streamThrottleMs` | 100 ms | Flush when enough time has elapsed since the previous update. |
| `streamThrottleChars` | 50 | Flush when accumulated changed characters reach the threshold. |

The current implementation checks these thresholds when `append`, `setContent`, or `update` is called; it does not run a background timer. A final flush occurs when the producer returns. Updates are queued and applied in order within that stream.

Choose values that respect platform rate limits while preserving perceived responsiveness. Very small thresholds create excessive API traffic. Very large thresholds delay visible output. Validate positive, bounded values in application configuration.

## Producer design

The producer executes synchronously inside the stream operation. Follow these rules:

- give model and downstream calls explicit timeouts;
- propagate failures instead of swallowing them;
- do not start unmanaged threads from the producer;
- do not update one controller concurrently from multiple threads;
- bound generated content and number of updates;
- stop generating if the application request is cancelled, even though the SDK controller has no cancellation token;
- avoid logging generated content when it can contain sensitive user data.

If an external model emits callbacks on another thread, serialize them through an application-owned bounded queue and keep the producer alive only until a defined deadline.

## Error and retry behavior

Streaming setup and update failures are classified as `LarkChannelException`. Some underlying raw sends may use the normal outbound retry policy, but a producer is not automatically replayed from the beginning. Replaying a producer can duplicate messages or model work; decide at the business layer using an idempotency record and the known `messageId`.

When a stream fails after the message is created, users may see partial content plus an interruption marker. Treat that as a valid terminal presentation state and provide a deliberate “retry generation” action if your product needs one.

## Testing

Test producers with a fake or mocked outbound layer for:

- no content;
- one chunk and many small chunks;
- `setContent` replacement;
- content that crosses throttle thresholds;
- producer exception before and after the first update;
- platform patch failure;
- reply and thread options;
- maximum expected generated output.

The [`streaming-bot`](../examples/streaming-bot/) example provides a compact consumer-shaped Markdown stream.
