# Channel SDK examples

[简体中文](README.zh.md) | English

These Maven modules are external-consumer contract examples. Each declares only `com.larksuite.oapi:channel-sdk`; required main-SDK types resolve transitively. The default build compiles them but never contacts a live Lark/Feishu tenant.

## Prerequisites

- JDK 8, 11, 17, or 21
- Maven 3.6.3+
- the current Channel SDK artifact installed locally

From the repository root:

```bash
mvn -DskipTests -Dmaven.javadoc.skip=true install
mvn -f examples/pom.xml clean verify
```

The examples do not include a Maven execution plugin or select a production web framework. Run a main class from your IDE, or use your application's normal packaging and launch process after copying the relevant pattern.

Never commit real credentials or `.env` files. Environment variables shown below are for local demonstration; production deployments should use managed secret injection.

## `echo-bot`

Main class: `com.lark.channel.examples.echo.EchoBot`

Demonstrates:

- WebSocket construction and connection;
- a normalized `message` handler;
- replying to the inbound message;
- observing asynchronous send failure.

Required environment:

```text
APP_ID
APP_SECRET
```

`channel.start()` connects and blocks the process while it waits for events. Stop it through the host process lifecycle and ensure production code calls `disconnect()` during shutdown. The compact example focuses on event/send wiring; add structured logging, bounded downstream work, and a shutdown hook in a real service.

## `webhook-bot`

Main class: `com.lark.channel.examples.webhook.WebhookBot`

Demonstrates:

- selecting `transport("webhook")`;
- setting Verification Token and Encrypt Key;
- registering a normalized handler;
- obtaining the main-SDK `EventDispatcher`.

Required environment:

```text
APP_ID
APP_SECRET
VERIFICATION_TOKEN
ENCRYPT_KEY
```

This example intentionally does not open an HTTP port or choose Spring, Servlet, Vert.x, or another host. When integrating it, call `channel.connect()` successfully before accepting callback traffic, adapt the framework request to the main-SDK `EventReq`, call the verified dispatcher, and disconnect on final shutdown. See [Webhook integration](../docs/webhook.md).

## `streaming-bot`

Main class: `com.lark.channel.examples.streaming.StreamingBot`

Demonstrates:

- connecting synchronously;
- streaming several Markdown chunks;
- reading a destination chat from configuration;
- disconnecting in `finally`.

Required environment:

```text
APP_ID
APP_SECRET
CHANNEL_CHAT_ID
```

Running this main class sends a real message. Use only an authorized test app and chat. The destination must be authorized by the host application; do not expose it as an unrestricted request parameter.

## `raw-client`

Main class: `com.lark.channel.examples.raw.RawClient`

Demonstrates:

- connecting the Channel;
- calling `channel.getRawClient()` for an OpenAPI not wrapped by the facade;
- retaining main-SDK types under `com.lark.oapi.*`;
- disconnecting in `finally`.

Required environment:

```text
APP_ID
APP_SECRET
```

The sample prints a bot-info response for brevity. Production code must not print full raw responses because they may contain identifiers or other sensitive data. Parse and return only required fields.

## Using examples safely

- Use a dedicated test tenant and minimum-permission app.
- Treat every destination, path, URL, card value, and policy value as untrusted input.
- Keep SSRF protection enabled and configure narrow local-file directories.
- Handle every `CompletableFuture` failure.
- Add timeouts, rate limits, bounded executors, metrics, and graceful shutdown in the host.
- Never copy example logging or output directly into production without a data review.

Examples are teaching material and compilation contracts, not complete production applications.
