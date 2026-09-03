# Lark Channel SDK for Java

[简体中文](README.zh.md) | English

> The first public beta is `1.0.0-beta.1`. APIs may change before the first stable release.

Lark Channel SDK is a high-level conversation layer for Lark and Feishu bots and AI agents. It sits on top of [`com.larksuite.oapi:oapi-sdk`](https://github.com/larksuite/oapi-sdk-java) and turns raw event and message APIs into a consistent channel abstraction.

Use it when an application needs to receive conversations, apply safety policy, send rich responses, or stream generated output. Use `getRawClient()` when the high-level facade does not cover an OpenAPI.

## What it provides

- WebSocket and Webhook inbound transports
- Normalized message, reaction, card-action, bot, and comment events
- Duplicate, stale-event, per-chat ordering, batching, and access-policy gates
- Text, Markdown, post, card, media, share, sticker, reply, and thread sends
- Markdown and interactive-card streaming
- Safe URL download and constrained local-file upload
- Raw OpenAPI and WebSocket client escape hatches

## Requirements

- JDK 8, 11, 17, or 21
- Maven 3.6.3 or later
- A Lark or Feishu application with bot capability and the permissions required by the APIs it calls

The project compiles Java 8 bytecode and verifies the build on JDK 8, 11, 17, and 21.

## Installation

Add the Maven dependency to the consuming project:

```xml
<dependency>
    <groupId>com.larksuite.oapi</groupId>
    <artifactId>channel-sdk</artifactId>
    <version>1.0.0-beta.1</version>
</dependency>
```

`channel-sdk` declares the main Java SDK as a transitive dependency. Applications should not copy Channel classes into the main SDK namespace.

## Minimal WebSocket bot

Keep credentials outside source control. This example reads them from environment variables and handles asynchronous send failures explicitly.

```java
import com.lark.channel.ChannelEventHandler;
import com.lark.channel.LarkChannel;
import com.lark.channel.LarkChannelFactory;
import com.lark.channel.config.LarkChannelOptions;
import com.lark.channel.model.NormalizedMessage;
import com.lark.channel.model.SendInput;
import com.lark.channel.model.SendOptions;

public final class EchoBot {
    public static void main(String[] args) throws Exception {
        final LarkChannel channel = LarkChannelFactory.createLarkChannel(
                LarkChannelOptions.newBuilder(
                                System.getenv("APP_ID"),
                                System.getenv("APP_SECRET"))
                        .transport("websocket")
                        .build());

        channel.on("message", new ChannelEventHandler<NormalizedMessage>() {
            @Override
            public void handle(NormalizedMessage message) {
                channel.send(
                                message.getChatId(),
                                SendInput.text("Received: " + message.getContent()),
                                SendOptions.newBuilder()
                                        .replyTo(message.getMessageId())
                                        .build())
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                // Send to your structured logger; do not log credentials or raw events.
                                error.printStackTrace();
                            }
                        });
            }
        });

        channel.start();
    }
}
```

`start()` connects a standalone WebSocket bot and blocks until the Channel is disconnected. Hosted applications can use `connect()` to wait only for readiness, then call `disconnect()` from the host lifecycle. A failed connection can be retried; a disconnected Channel cannot be restarted, so create a new instance instead.

## Documentation

- [Documentation index](docs/README.md)
- [Quick start](docs/quickstart.md)
- [Configuration](docs/configuration.md)
- [Events and normalized messages](docs/events.md)
- [Webhook integration](docs/webhook.md)
- [Sending messages](docs/sending-messages.md)
- [Streaming](docs/streaming.md)
- [Media and resource safety](docs/media.md)
- [Policy and safety pipeline](docs/policy-and-safety.md)
- [API reference](docs/reference.md)
- [Migration from `oapi-sdk-java`](docs/migration-from-oapi-sdk-java.md)
- [Compatibility](docs/compatibility.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Testing](docs/testing.md)

## Examples

The [`examples`](examples/README.md) reactor contains four external-consumer projects:

| Example | Purpose |
| --- | --- |
| `echo-bot` | Receive a WebSocket message and reply |
| `webhook-bot` | Attach a dispatcher to an existing HTTP host |
| `streaming-bot` | Stream Markdown into a card |
| `raw-client` | Call the main SDK through the escape hatch |

Build them after installing the SDK from this checkout:

```bash
mvn -DskipTests -Dmaven.javadoc.skip=true install
mvn -f examples/pom.xml clean verify
```

## Package and dependency boundary

Public Channel APIs use the new `com.lark.channel` namespace. The artifact depends on the main SDK for generated OpenAPI models, authentication, HTTP transport, event dispatching, and WebSocket transport. It does not duplicate `com.lark.oapi` implementation packages inside its JAR.

If your application already depends directly on `oapi-sdk`, Maven dependency convergence still applies. Prefer the version selected by `channel-sdk`, or manage one compatible version centrally.

## Migrating existing Channel code

The first migration target is code that previously used the legacy main-SDK Channel namespace or copied Channel sources from the main SDK. The important changes are:

1. Add `com.larksuite.oapi:channel-sdk`.
2. Replace Channel imports with `com.lark.channel.*`.
3. Keep raw OpenAPI imports under `com.lark.oapi.*`.
4. Recheck event names, connection lifecycle, raw-event opt-in, and package isolation.

See the [migration guide](docs/migration-from-oapi-sdk-java.md) for the complete checklist.

## Local development

Run the repository verification workflow:

```bash
./scripts/verify.sh
```

The workflow checks documentation and license headers, runs Maven verification, verifies JAR isolation, installs the artifact locally, and compiles all examples. Integration tests remain opt-in because they require real application credentials.

## Contributing and security

Read [CONTRIBUTING.md](CONTRIBUTING.md) before proposing a change. Please report suspected vulnerabilities privately according to [SECURITY.md](SECURITY.md); do not include secrets, access tokens, personal data, or raw event bodies in a public issue.

## License

This project is licensed under the [MIT License](LICENSE). See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the runtime and test
dependency inventory.

## Support

Use [GitHub Issues](https://github.com/larksuite/channel-sdk-java/issues) for reproducible bugs and feature requests. Report vulnerabilities privately according to [SECURITY.md](SECURITY.md).
