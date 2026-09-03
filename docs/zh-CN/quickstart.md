# 快速开始

[文档索引](README.md) | 简体中文 | [English](../quickstart.md)

本文基于公开 Beta 版本构建一个简单的 WebSocket 回声机器人。如果应用通过 HTTP 回调接收事件，请完成公共准备后继续阅读 [Webhook 集成](webhook.md)。

## 1. 准备应用

在飞书或 Lark 开放平台控制台中：

1. 创建应用并启用机器人能力。
2. 为接收消息及机器人调用的每个发送 API 开通所需权限。
3. WebSocket 模式下，启用长连接事件接收，并订阅消息接收事件。
4. 在目标租户发布或安装一个可用的应用版本。
5. 将机器人加入测试群，或与机器人建立私聊。

权限或事件订阅变化后，如果平台要求，应重新发布/安装更新后的应用版本。

权限名和控制台入口可能独立于 SDK 演进。如果 API 返回 `permission_denied`，应核对最新平台文档和租户安装状态，不要把更高权限凭证直接放入源码。

## 2. 添加依赖

在业务应用中添加 Maven Central 产物：

```xml
<dependency>
    <groupId>com.larksuite.oapi</groupId>
    <artifactId>channel-sdk</artifactId>
    <version>1.0.0-beta.1</version>
</dependency>
```

如果业务代码不直接调用主 SDK，无需再次声明 `oapi-sdk`。若需要直接依赖，请确保版本收敛。

## 3. 提供凭证

通过进程环境或密钥管理系统注入凭证：

```bash
export APP_ID='cli_xxx'
export APP_SECRET='replace-with-a-secret-manager-value'
```

严禁提交真实值、`.env` 文件、访问令牌、私钥、Verification Token 或 Encrypt Key。生产环境应在运行时注入，并限制凭证读取权限。

## 4. 创建 Channel 并订阅事件

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
                                SendInput.text("收到：" + message.getContent()),
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
                // 只记录有限的原因指标，不要打印完整原始事件。
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

默认群聊策略要求直接 @ 机器人，默认私聊策略为开放。将机器人暴露给不可信租户或大量群聊前，请先阅读[策略与安全](policy-and-safety.md)。

## 5. 理解生命周期

`start()` 是独立 WebSocket Bot 的生命周期入口：它先调用 `connectSync()`，然后阻塞当前线程，直到 `disconnect()` 完成。

`connect()` 本身只负责两个连接就绪步骤：

1. 通过 OpenAPI 获取当前机器人身份。消息标准化和 @ 策略需要机器人的 `open_id`。
2. WebSocket 模式下启动底层 WebSocket Client，并最多等待 15 秒，直到第一次连接就绪。

并发调用 `connect()` 会共享同一个 `CompletableFuture<BotIdentity>`。某次连接失败后，再次调用会启动新的连接尝试。同步等待连接就绪可以使用 `connectSync()`；它与 `start()` 不同，会在第一次连接就绪后返回。

每个进程应使用一个长生命周期 Channel 实例。在 Spring、Servlet 等宿主应用中，应在启动阶段调用 `connect()`，并在最终退出时调用 `disconnect()` 或 `disconnectSync()`。断开连接会销毁内部安全队列，并释放阻塞中的 `start()`；不要重启已经断开的实例，应重新创建 Channel。

## 6. 验证行为

启动进程后，向已安装的机器人发送私聊消息，或在允许的群中 @ 机器人。检查：

- `start()` 已建立就绪连接，并让进程持续等待事件。
- 一条入站消息只产生一次标准化 `message` 事件。
- 回复关联到原始消息。
- 不满足群策略的消息产生 `reject`，而不是进入业务 handler。
- 错误得到处理，日志中没有凭证和完整原始事件。

## 不启动入站传输，仅发送消息

仅出站的任务可以不调用 `connect`，直接使用 `send`；OpenAPI 鉴权由底层主 SDK 处理。选择 Webhook 传输可避免构建底层 WebSocket Client；业务需要校验目标，并在任务结束后销毁 Channel：

```java
LarkChannel channel = LarkChannelFactory.createLarkChannel(
        LarkChannelOptions.newBuilder(appId, appSecret)
                .transport("webhook")
                .build());
try {
    channel.sendSync(authorizedChatId, SendInput.text("定时更新"));
} finally {
    channel.disconnectSync();
}
```

接收 Webhook 回调时不能使用这种省略连接的方式：入站标准化仍需要先通过 `connect()` 获取机器人身份。

## 后续阅读

- 选择 [WebSocket 或 Webhook](configuration.md#传输方式)。
- 了解[标准化事件模型](events.md)。
- 配置[访问策略、去重和消息合并](policy-and-safety.md)。
- 发送 [Markdown、卡片、媒体、回复与 @](sending-messages.md)。
- 增加[流式输出](streaming.md)。
