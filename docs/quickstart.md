# Quick start

[Documentation index](README.md) | [简体中文](zh-CN/quickstart.md) | English

This guide builds a small WebSocket echo bot with the public beta. For an HTTP callback deployment, finish the common setup and continue with [Webhook integration](webhook.md).

## 1. Prepare the application

In the Lark or Feishu developer console:

1. Create an application and enable its bot capability.
2. Grant the permissions required for receiving messages and for every outbound API your bot will use.
3. For WebSocket mode, enable long-connection event delivery and subscribe to the message-receive event.
4. Publish or install a usable application version in the target tenant.
5. Add the bot to a test chat or open a direct conversation with it.

After changing permissions or event subscriptions, publish/install the updated application version again when the platform requires it.

Permissions and console labels can evolve independently of this SDK. If an API returns `permission_denied`, confirm the current platform documentation and tenant installation state rather than adding broader credentials to source code.

## 2. Add the dependency

Add the Maven Central artifact to your application:

```xml
<dependency>
    <groupId>com.larksuite.oapi</groupId>
    <artifactId>channel-sdk</artifactId>
    <version>1.0.0-beta.1</version>
</dependency>
```

You do not need to add `oapi-sdk` separately unless application code uses its APIs directly. If you do declare it, keep the dependency versions converged.

## 3. Supply credentials

Set credentials through the process environment or your secret manager:

```bash
export APP_ID='cli_xxx'
export APP_SECRET='replace-with-a-secret-manager-value'
```

Never commit real values, `.env` files, access tokens, private keys, verification tokens, or encryption keys. In production, inject secrets at runtime and restrict who can read them.

## 4. Create and subscribe

```java
import com.lark.channel.ChannelEventHandler;
import com.lark.channel.LarkChannel;
import com.lark.channel.LarkChannelFactory;
import com.lark.channel.config.LarkChannelOptions;
import com.lark.channel.model.ChannelErrorEvent;
import com.lark.channel.model.NormalizedMessage;
import com.lark.channel.model.RejectEvent;
import com.lark.channel.model.SendInput;
import com.lark.channel.model.SendOptions;

public final class QuickStartBot {
    public static void main(String[] args) throws Exception {
        final String appId = requiredEnv("APP_ID");
        final String appSecret = requiredEnv("APP_SECRET");

        final LarkChannel channel = LarkChannelFactory.createLarkChannel(
                LarkChannelOptions.newBuilder(appId, appSecret)
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
                                logError("send reply", error);
                            }
                        });
            }
        });

        channel.on("reject", new ChannelEventHandler<RejectEvent>() {
            @Override
            public void handle(RejectEvent event) {
                // Record a bounded reason metric. Do not log the full raw event.
                System.err.println("message rejected: " + event.getReason());
            }
        });

        channel.on("error", new ChannelEventHandler<ChannelErrorEvent>() {
            @Override
            public void handle(ChannelErrorEvent event) {
                logError("event " + event.getEventName(), event.getError());
            }
        });

        channel.start();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing environment variable: " + name);
        }
        return value;
    }

    private static void logError(String operation, Throwable error) {
        System.err.println(operation + " failed: " + error.getClass().getSimpleName());
    }
}
```

The default group policy requires a direct bot mention. Direct messages are open by default. Review [Policy and safety](policy-and-safety.md) before exposing a bot to untrusted tenants or broad group scopes.

## 5. Understand the lifecycle

`start()` is the standalone WebSocket lifecycle entry point. It calls `connectSync()` and then blocks the current thread until `disconnect()` finishes.

`connect()` itself performs two readiness operations:

1. Fetches the current bot identity through OpenAPI. Normalization and mention policy need the bot `open_id`.
2. In WebSocket mode, starts the raw WebSocket client and waits up to 15 seconds for its first ready handshake.

The returned `CompletableFuture<BotIdentity>` is shared by concurrent `connect()` calls. If a connection attempt fails, a later call starts a fresh attempt. `connectSync()` is the synchronous readiness alternative; unlike `start()`, it returns after the first connection is ready.

Use one long-lived Channel instance for the process. In Spring, Servlet or another hosted application, call `connect()` during startup and `disconnect()` or `disconnectSync()` during final shutdown. Disconnect disposes internal safety queues and releases a blocked `start()` call. Create a new Channel instance instead of trying to restart a disconnected one.

## 6. Verify behavior

Run the process, then send the installed bot a direct message or mention it in an allowed group. Check all of the following:

- `start()` reaches a ready connection and keeps the process waiting for events.
- One inbound message produces one normalized `message` event.
- The reply is associated with the inbound message.
- A rejected group message emits `reject` instead of invoking the message handler.
- Errors are handled without printing credentials or the full raw payload.

## Send without starting inbound transport

An outbound-only job can call `send` without `connect`; OpenAPI authentication is handled by the underlying main SDK. Select Webhook transport so no raw WebSocket client is constructed, validate the destination, and still dispose the Channel when the job finishes:

```java
LarkChannel channel = LarkChannelFactory.createLarkChannel(
        LarkChannelOptions.newBuilder(appId, appSecret)
                .transport("webhook")
                .build());
try {
    channel.sendSync(authorizedChatId, SendInput.text("Scheduled update"));
} finally {
    channel.disconnectSync();
}
```

Do not use this pattern for receiving Webhook callbacks: inbound normalization still requires a successful `connect()` to resolve the bot identity.

## Next steps

- Choose [WebSocket or Webhook configuration](configuration.md#transport).
- Learn the [normalized event model](events.md).
- Configure [access policy, deduplication, and batching](policy-and-safety.md).
- Send [Markdown, cards, media, replies, and mentions](sending-messages.md).
- Add [streaming output](streaming.md).
