# API 参考

[文档索引](README.md) | 简体中文 | [English](../reference.md)

本页是当前 Java 预览版的导航参考。精确签名以源码 Javadoc 为准；专题文档说明行为和运行约束。

## 公开包结构

| 包 | 职责 |
| --- | --- |
| `com.lark.channel` | Factory、门面、事件 handler 与 subscription |
| `com.lark.channel.config` | 构建与运行时配置模型 |
| `com.lark.channel.model` | 标准事件、发送、流式、身份与结果模型 |
| `com.lark.channel.exception` | 稳定 Channel 异常与 code 枚举 |

部分实现包当前也包含用于内部组合的 public Java 类，但不属于推荐业务入口。应用应优先使用上述包和 `LarkChannel` 门面。

## 构建

| API | 用途 |
| --- | --- |
| `LarkChannelOptions.newBuilder(appId, appSecret)` | 开始构建不可变顶层配置。 |
| `LarkChannelFactory.createLarkChannel(options)` | 创建门面与底层主 SDK Client。 |

`LarkChannelOptions` 下的配置类型：

- `Builder`
- `WebhookOptions`
- `PolicyConfig`
- `SafetyConfig`
- `BatchTextConfig`
- `OutboundConfig`
- `RetryConfig`

全部配置和默认值见[配置](configuration.md)。

## 生命周期与身份

| 方法 | 返回值 | 说明 |
| --- | --- | --- |
| `start()` | `void` | 连接独立 WebSocket Bot，并阻塞到 Channel 断开。 |
| `connect()` | `CompletableFuture<BotIdentity>` | 获取身份；配置 WebSocket 时启动并等待连接。 |
| `connectSync()` | `BotIdentity` | 同步等待连接就绪，随后返回。 |
| `disconnect()` | `CompletableFuture<Void>` | 关闭传输并销毁安全队列。 |
| `disconnectSync()` | `void` | 同步版本。 |
| `isConnected()` | `boolean` | 成功连接后、断开前为 true。 |
| `getBotIdentity()` | `BotIdentity` | 成功连接前为 null。 |

`BotIdentity` 提供 `getOpenId()` 和 `getName()`。

`start()` 面向独立运行的 WebSocket 进程。宿主应用和 Webhook 集成应通过自身生命周期调用 `connect()` / `disconnect()`。并发调用 `disconnect()` 会共享同一个清理 Future；Channel 一旦断开就不能重新连接。

## 事件订阅

| 方法 | 用途 |
| --- | --- |
| `<T> on(String, ChannelEventHandler<T>)` | 注册或替换一个事件 handler。 |
| `on(Map<String, ChannelEventHandler<?>>)` | 批量注册为一个 subscription。 |
| `ChannelSubscription.unsubscribe()` | 移除注册的 handler。 |
| `createWebhookDispatcher()` | 为 Webhook 宿主获取主 SDK `EventDispatcher`。 |

事件模型：

- `NormalizedMessage`
- `ReactionEvent`
- `BotAddedEvent`
- `CardActionEvent`
- `CommentEvent`
- `RejectEvent` 与 `RejectReason`
- `ChannelErrorEvent`

事件名与字段见[事件](events.md)。

## 发送

| 方法 | 返回值 |
| --- | --- |
| `send(to, input)` | `CompletableFuture<SendResult>` |
| `send(to, input, options)` | `CompletableFuture<SendResult>` |
| `sendSync(to, input)` | `SendResult` |
| `sendSync(to, input, options)` | `SendResult` |

`SendInput` 工厂：

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

`SendOptions.Builder` 支持 `replyTo`、`replyInThread`、`mentions(List<String>)` 和 `mentionInfos(List<MentionInfo>)`。`SendResult` 提供 `messageId` 与 `chunkIds`。

详见[发送消息](sending-messages.md)和[媒体](media.md)。

## 流式输出

| 方法 | 返回值 |
| --- | --- |
| `stream(to, input)` | `CompletableFuture<SendResult>` |
| `stream(to, input, options)` | `CompletableFuture<SendResult>` |
| `streamSync(to, input)` | `SendResult` |
| `streamSync(to, input, options)` | `SendResult` |

`StreamInput.markdown(MarkdownStreamProducer)` 提供带 `append`、`setContent`、`getMessageId` 的 `MarkdownStreamController`。`StreamInput.card(initial, CardStreamProducer)` 提供带 `update`、`getCurrent`、`getMessageId` 的 `CardStreamController`。

详见[流式输出](streaming.md)。

## 消息与资源辅助方法

以下方法均为异步：

| 方法 | 用途 |
| --- | --- |
| `editMessage(messageId, text)` | 更新文本/富文本内容。 |
| `updateCard(messageId, card)` | Patch 交互卡片。 |
| `recallMessage(messageId)` | 撤回机器人消息。 |
| `downloadResource(fileKey, type)` | 下载图片/文件字节。 |
| `addReaction(messageId, emojiType)` | 添加表情并返回 reaction ID。 |
| `removeReaction(messageId, reactionId)` | 删除指定的机器人表情回复。 |
| `removeReactionByEmoji(messageId, emojiType)` | 查找并删除机器人对应表情。 |
| `getChatInfo(chatId)` | 获取基础群信息。 |

`ChatInfo` 提供 chat ID、名称、描述、类型、群主 ID 和可选成员数。

## 策略

| 方法 | 用途 |
| --- | --- |
| `updatePolicy(PolicyConfig)` | 把所有策略字段复制到当前策略。 |
| `getPolicy()` | 返回当前可变策略对象。 |

更新应视为完整配置替换，并串行化变更。详见[策略与安全](policy-and-safety.md)。

## 逃生口

| 方法 | 返回值 |
| --- | --- |
| `getRawClient()` | `com.lark.oapi.Client` |
| `getRawWsClient()` | `com.lark.oapi.ws.Client`；Webhook 模式为 null |

原始 Client 遵循主 SDK 的 API、兼容性和安全规则；Channel 不会包装或标准化原始调用返回。

## 异常

`LarkChannelException` 是运行时异常，提供 `getCode()` 和可选 `getContext()`。预览版稳定 code 由 `LarkChannelErrorCode` 定义：`format_error`、`target_revoked`、`rate_limited`、`permission_denied`、`upload_failed`、`ssrf_blocked`、`send_timeout`、`not_connected` 和 `unknown`。

异步方法会异常完成；同步方法会抛出异常，使用 `.join()` 的路径可能包装为 `CompletionException`。检查 cause 链时不要向用户暴露敏感平台错误内容。

## Javadoc

通过 `mvn javadoc:javadoc` 或 `mvn verify` 生成本地 Javadoc，然后打开 `target/site/apidocs/index.html`，或构建生成的 `target/apidocs`。当前快照没有公开托管 Javadoc 地址，只有真实发布后才会加入链接。
