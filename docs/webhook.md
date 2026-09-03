# Webhook integration

[Documentation index](README.md) | [简体中文](zh-CN/webhook.md) | English

Webhook mode lets an existing HTTP service receive Lark/Feishu callbacks while reusing the same normalized events and safety pipeline as WebSocket mode. The Channel SDK creates an `EventDispatcher`; the host still owns routing, TLS, request-size limits, timeouts, concurrency, observability, and deployment.

## Choose a transport

| Consideration | WebSocket | Webhook |
| --- | --- | --- |
| Inbound host | SDK long-connection client | Application HTTP service |
| Public callback URL | Not required | Required and configured in the developer console |
| Verification/encryption values | Not used by inbound connection | Configured in console and host secrets |
| Reconnect handling | Main-SDK WebSocket client | HTTP platform/load balancer behavior |
| Best fit | Worker/bot process without an HTTP callback stack | Existing web service with established ingress controls |

Choose one inbound transport per Channel instance. Both still use OpenAPI credentials for identity and outbound calls.

## Configure webhook mode

```java
LarkChannelOptions.WebhookOptions webhook = new LarkChannelOptions.WebhookOptions();
webhook.setVerificationToken(requiredSecret("VERIFICATION_TOKEN"));
webhook.setEncryptKey(requiredSecret("ENCRYPT_KEY"));

LarkChannel channel = LarkChannelFactory.createLarkChannel(
        LarkChannelOptions.newBuilder(requiredSecret("APP_ID"), requiredSecret("APP_SECRET"))
                .transport("webhook")
                .webhook(webhook)
                .build());
```

Read verification and encryption values from a secret manager. Do not include them in URLs, logs, exception messages, example configuration, or health endpoints.

## Register handlers before serving

Register all handlers, then connect, then expose the route:

```java
channel.on("message", new ChannelEventHandler<NormalizedMessage>() {
    @Override
    public void handle(NormalizedMessage message) {
        // Process an already verified and normalized callback.
    }
});

channel.connectSync();
EventDispatcher dispatcher = channel.createWebhookDispatcher();
```

`connectSync()` is required even though Webhook mode creates no WebSocket client. It resolves the bot identity needed to detect and strip bot mentions and to enforce mention policy. Do not send callback traffic to the dispatcher before connection succeeds.

At final application shutdown, call `channel.disconnectSync()`. Create a new Channel instance if the HTTP component is restarted later.

## Attach the dispatcher

`EventDispatcher` is the main Java SDK's event handler. Adapt the host framework's request into `com.lark.oapi.core.request.EventReq`, call `dispatcher.handle(eventReq)`, and map the returned `EventResp` back to the HTTP response. Preserve the request body bytes and the platform signature/timestamp/nonce headers exactly as the main SDK expects.

The precise adapter depends on the host framework; keep it at the HTTP boundary instead of putting servlet or Spring types into Channel handlers. The [`webhook-bot`](../examples/webhook-bot/) example demonstrates Channel construction and dispatcher creation but intentionally does not select a web framework.

The endpoint must:

- accept only the configured callback method and path;
- enforce TLS at the edge;
- set a bounded request body size;
- pass signature-related headers unchanged;
- use the SDK dispatcher for verification and decryption;
- return the dispatcher response without leaking stack traces;
- apply a request timeout and concurrency limit;
- avoid logging full request bodies.

Do not call `doWithoutValidation(...)` for production traffic. It bypasses dispatcher validation and is not an HTTP integration shortcut.

## URL verification and encrypted callbacks

The main SDK dispatcher handles platform URL verification and encrypted callback bodies using the configured verification token and encrypt key. Configure the same values in the developer console and the running service. A mismatch should fail closed; do not retry with validation disabled.

If the application intentionally receives unencrypted callbacks, follow the current platform security guidance and provide the appropriate verification configuration. The SDK preview permits empty values, but production hosts must validate that deployment configuration matches their chosen platform mode before startup.

## Response timing and asynchronous work

Callback endpoints should acknowledge within the platform deadline. Channel handler invocation is part of dispatcher processing, so long-running work can delay the HTTP response. Keep handler work bounded and hand off durable jobs to a queue or executor with explicit limits. The handoff itself must be idempotent because callbacks can be delivered more than once.

Do not return success before work has been durably accepted if losing the event would violate your application requirements. Conversely, do not perform unbounded downstream retries inside the request.

## Multi-instance deployment

For more than one Webhook instance:

1. Configure a shared main-SDK `ICache` on `LarkChannelOptions`.
2. Use stable dedup and idempotency keys for application writes.
3. Keep clock synchronization healthy because stale-event checks use timestamps.
4. Make shutdown drain or stop accepting HTTP requests before disposing the channel.
5. Monitor request rate, handler latency, dispatcher errors, rejection reasons, and downstream failure rate.

A shared cache improves later duplicate visibility, but it is not a distributed processing lock. Concurrent delivery to two instances can still race; application idempotency is the final protection.

## Testing a webhook adapter

Test the adapter with fixture requests that cover:

- valid URL verification;
- valid encrypted event;
- invalid signature/token/key;
- malformed and oversized body;
- missing required headers;
- duplicate callback;
- handler failure;
- timeout and shutdown behavior.

Fixtures must use synthetic secrets and data. Real callback bodies often contain user content and must not be committed.

## Common failures

| Symptom | Check |
| --- | --- |
| Identity or `not_connected` error | Call `connect()` successfully before serving requests. |
| Verification failure | Console and runtime verification token/encrypt key must match. |
| No normalized event | Confirm the callback type is in the [supported event table](events.md). |
| Repeated business action | Add application idempotency; configure a shared cache for multiple instances. |
| Callback timeout | Bound handler work and downstream calls; use a durable handoff. |
| Works only with validation bypassed | Fix body/header adaptation; never ship the bypass. |
