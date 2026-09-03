# Lark Channel SDK for Java

简体中文 | [English](README.md)

> 首个公开 Beta 版本为 `1.0.0-beta.1`。首个稳定版发布前，API 仍可能调整。

Lark Channel SDK 是面向飞书/Lark 机器人与 AI Agent 的高层会话 SDK。它构建在 [`com.larksuite.oapi:oapi-sdk`](https://github.com/larksuite/oapi-sdk-java) 之上，将原始事件和消息 API 统一成一致的 Channel 抽象。

当应用需要接收会话、执行安全策略、发送富文本响应或流式输出时，优先使用 Channel SDK；高层接口尚未覆盖的 OpenAPI，可通过 `getRawClient()` 调用主 SDK。

## 核心能力

- WebSocket 与 Webhook 两种入站方式
- 标准化的消息、表情回复、卡片回调、机器人和评论事件
- 去重、过期过滤、按会话串行、短消息合并和访问策略
- 文本、Markdown、富文本、卡片、媒体、分享、表情包、回复与话题内发送
- Markdown 和交互卡片流式更新
- 带 SSRF 防护的 URL 下载与受目录约束的本地文件上传
- 原始 OpenAPI Client 与 WebSocket Client 逃生口

## 环境要求

- JDK 8、11、17 或 21
- Maven 3.6.3 及以上
- 已启用机器人能力，并具备所调用 API 所需权限的飞书或 Lark 应用

项目产物为 Java 8 字节码，并在 JDK 8、11、17 和 21 上验证构建。

## 安装

在业务项目中添加 Maven 依赖：

```xml
<dependency>
    <groupId>com.larksuite.oapi</groupId>
    <artifactId>channel-sdk</artifactId>
    <version>1.0.0-beta.1</version>
</dependency>
```

`channel-sdk` 会传递依赖主 Java SDK。业务项目不应将 Channel 源码复制回主 SDK 的命名空间。

## 最小 WebSocket 机器人

凭证必须放在源码仓库之外。下面示例从环境变量读取凭证，并显式处理异步发送失败。

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
                                SendInput.text("收到：" + message.getContent()),
                                SendOptions.newBuilder()
                                        .replyTo(message.getMessageId())
                                        .build())
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                // 接入业务结构化日志；不要记录凭证或原始事件。
                                error.printStackTrace();
                            }
                        });
            }
        });

        channel.start();
    }
}
```

`start()` 会连接独立运行的 WebSocket Bot，并阻塞到 Channel 断开。由框架托管的应用可以使用 `connect()` 只等待连接就绪，再通过宿主生命周期调用 `disconnect()`。连接失败后可以重试；已经断开的 Channel 不能重新启动，需要创建新实例。

## 文档

- [文档索引](docs/zh-CN/README.md)
- [快速开始](docs/zh-CN/quickstart.md)
- [配置](docs/zh-CN/configuration.md)
- [事件与标准化消息](docs/zh-CN/events.md)
- [Webhook 集成](docs/zh-CN/webhook.md)
- [发送消息](docs/zh-CN/sending-messages.md)
- [流式输出](docs/zh-CN/streaming.md)
- [媒体与资源安全](docs/zh-CN/media.md)
- [策略与安全流水线](docs/zh-CN/policy-and-safety.md)
- [API 参考](docs/zh-CN/reference.md)
- [从 `oapi-sdk-java` 迁移](docs/zh-CN/migration-from-oapi-sdk-java.md)
- [兼容性](docs/zh-CN/compatibility.md)
- [故障排查](docs/zh-CN/troubleshooting.md)
- [测试](docs/zh-CN/testing.md)

## 示例

[`examples`](examples/README.zh.md) Maven 工程包含四个独立消费者示例：

| 示例 | 用途 |
| --- | --- |
| `echo-bot` | 通过 WebSocket 接收消息并回复 |
| `webhook-bot` | 将事件分发器接入已有 HTTP 服务 |
| `streaming-bot` | 以卡片形式流式输出 Markdown |
| `raw-client` | 通过逃生口调用主 SDK |

从当前仓库安装 SDK 后构建全部示例：

```bash
mvn -DskipTests -Dmaven.javadoc.skip=true install
mvn -f examples/pom.xml clean verify
```

## 包与依赖边界

Channel 对外 API 使用新的 `com.lark.channel` 命名空间。产物依赖主 SDK 提供生成的 OpenAPI 模型、鉴权、HTTP 传输、事件分发与 WebSocket 传输，不会在 JAR 中复制 `com.lark.oapi` 的实现包。

如果业务已直接依赖 `oapi-sdk`，仍需满足 Maven 依赖收敛。建议使用 `channel-sdk` 选择的版本，或在业务的依赖管理中统一指定一个兼容版本。

## 迁移已有 Channel 代码

首要迁移对象是原先使用主 SDK 旧 Channel 命名空间、或从主 SDK 复制出的 Channel 代码：

1. 添加 `com.larksuite.oapi:channel-sdk` 依赖。
2. 将 Channel 相关 import 改为 `com.lark.channel.*`。
3. 原始 OpenAPI 相关 import 继续保留在 `com.lark.oapi.*`。
4. 重新检查事件名、连接生命周期、原始事件开关与产物隔离。

完整步骤见[迁移指南](docs/zh-CN/migration-from-oapi-sdk-java.md)。

## 本地开发

执行仓库完整验证：

```bash
./scripts/verify.sh
```

该流程会校验文档与许可证头、执行 Maven 验证、检查 JAR 包隔离、安装本地产物并编译所有示例。集成测试默认关闭，因为它们需要真实应用凭证。

## 贡献与安全

提交改动前请阅读 [CONTRIBUTING.zh.md](CONTRIBUTING.zh.md)。安全问题请按 [SECURITY.zh.md](SECURITY.zh.md) 私下报告；不要在公开 Issue 中附带密钥、访问令牌、个人数据或原始事件内容。

## 许可证

本项目使用 [MIT License](LICENSE)。运行时及测试依赖清单见
[THIRD_PARTY_NOTICES.zh.md](THIRD_PARTY_NOTICES.zh.md)。

## 支持渠道

可通过 [GitHub Issues](https://github.com/larksuite/channel-sdk-java/issues) 提交可复现的问题和功能建议。安全问题请按 [SECURITY.zh.md](SECURITY.zh.md) 私下报告。
