# 发送消息

[文档索引](README.md) | 简体中文 | [English](../sending-messages.md)

`LarkChannel.send(...)` 为常见出站消息提供统一 Java 入口。它会从目标判断平台 Receive ID 类型，在需要时上传媒体，应用回复与 @ 配置，拆分长文本，并对平台错误分类。

## 异步与同步 API

```java
CompletableFuture<SendResult> future = channel.send(
        "oc_chat_id",
        SendInput.text("你好"));

future.whenComplete((result, error) -> {
    if (error != null) {
        // 处理并分类失败。
    }
});
```

命令行或其他阻塞式代码可使用：

```java
SendResult result = channel.sendSync("oc_chat_id", SendInput.text("你好"));
```

异步方法返回 `CompletableFuture`，必须挂接错误处理或显式等待，不能直接丢弃。Future 本身不接收可取消 Context；有需要时，业务应为整个操作设置截止时间。

## 目标

`to` 字符串决定 Receive ID 类型：

| 格式 | Receive ID 类型 |
| --- | --- |
| 以 `oc_` 开头 | `chat_id` |
| 以 `ou_` 开头 | `open_id` |
| 以 `on_` 开头 | `union_id` |
| 包含 `@` | `email` |
| 其他非空值 | `user_id` |

业务入口必须拒绝空目标并校验标识格式。未经授权，不能使用不可信请求传入的任意目标发送消息。

## 内容工厂

每个 `SendInput` 工厂只创建一种 `Kind`，Java 调用方不会传入互相竞争的多组内容字段。

```java
SendInput.text("纯文本");
SendInput.markdown("**Markdown** 和 `code`");
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

图片/文件/音频/视频来源可以是 URL 字符串、本地路径字符串、`File`、`byte[]` 或 `InputStream`，这些形式都会重新上传平台资源。传给 `image(...)` 的字符串不会被当作已有 `image_key`；只有表情包工厂直接接受已有 file key。详见[媒体](media.md)。

## 文本与 Markdown 拆分

文本或 Markdown 超过 `outbound.textChunkLimit`（默认 3,500 字符）时会拆分。Markdown 拆分会保留代码围栏，然后把每块转换成平台富文本。如果某块因为 `format_error` 被平台拒绝，发送器会回退为纯文本。

多块发送时：

- `SendResult.getMessageId()` 是第一条消息 ID；
- `SendResult.getChunkIds()` 包含所有块返回的 ID；
- 回复选项只作用于第一块，后续块是新消息；
- @ 选项只写入第一块 Markdown。

`SendInput.post(...)` 的原始富文本对象会按原样发送，`SendOptions.mentions` 不会注入原始 post；如需 @，应自行在 post 对象中放入合法元素。

## 回复与话题

```java
SendOptions options = SendOptions.newBuilder()
        .replyTo(inbound.getMessageId())
        .replyInThread(Boolean.TRUE)
        .build();

channel.send(inbound.getChatId(), SendInput.text("回复"), options);
```

平台如果报告被回复消息已消失，SDK 会回退为向 `to` 创建新消息。敏感流程需要判断这种回退是否可以接受，因为结果可能脱离原上下文。

## @ 用户

按 open ID @ 用户：

```java
SendOptions options = SendOptions.newBuilder()
        .mentions(java.util.Arrays.asList("ou_user_1", "ou_user_2"))
        .build();
```

也可以通过 `mentionInfos(...)` 传完整 `MentionInfo`。纯文本会在开头加入 @，SDK 生成的 Markdown 富文本会组合 @ 元素。业务必须确认调用者有权通知这些用户，不能把任意输入转换成群体通知。

## 卡片、分享与表情包

`SendInput.card(...)` 接受由 `Map<String, Object>` 表示的交互卡片 JSON。卡片结构由平台定义，Channel SDK 不做 Schema 校验；构建 Map 前应校验模板和用户输入。

`shareChat(...)`、`shareUser(...)` 和 `sticker(...)` 会直接发送平台引用。平台是否接受仍取决于机器人身份和应用权限。

## 返回结果

`SendResult` 只包含：

- `messageId`：第一条或唯一一条消息 ID；
- `chunkIds`：一次操作创建多条消息时的全部 ID。

结果不包含目标元数据或内嵌错误。失败会让 Future 异常完成，或由同步方法抛出。

## 错误分类

出站错误会包装成 `LarkChannelException`，通过 `getCode()` 获取稳定字符串：

| Code | 常见含义 | 默认重试 |
| --- | --- | --- |
| `format_error` | 平台拒绝消息格式 | 否；Markdown 可能回退纯文本 |
| `target_revoked` | 回复目标已不存在 | 否；回复可能回退新建消息 |
| `rate_limited` | 平台限流 | 是 |
| `permission_denied` | 凭证或权限问题 | 否 |
| `upload_failed` | 媒体准备或上传失败 | 否 |
| `ssrf_blocked` | 远程媒体 URL 未通过 SSRF 策略 | 否 |
| `send_timeout` | 发送超时 | 不自动重试 |
| `not_connected` | 缺少所需身份或连接 | 否 |
| `unknown` | 未分类的平台/传输失败 | 是 |

内置发送器默认只对 `rate_limited` 和 `unknown` 最多尝试三次，基础等待为 500 毫秒，之后 1,500 毫秒，不包含 jitter。配置的总尝试次数应保持不超过三次，且不要在 `send` 外再包一层自动重试，否则会级联放大流量。带业务副作用的流程应使用返回消息 ID 和自身幂等记录。

当前分类会检查平台错误文本与错误码。诊断时应保留底层 cause，但不能直接向最终用户暴露。

## 低层辅助方法

门面还提供以下异步方法：

```java
channel.editMessage(messageId, "替换文本");
channel.updateCard(messageId, cardMap);
channel.recallMessage(messageId);
channel.addReaction(messageId, "THUMBSUP");
channel.removeReaction(messageId, reactionId);
channel.removeReactionByEmoji(messageId, "THUMBSUP");
channel.getChatInfo(chatId);
```

需要精确删除表情回复时，应保存 `addReaction` 返回的 `reactionId`。机器人只能删除自己添加的表情。`editMessage` 用于文本/富文本，交互卡片应使用 `updateCard`。

没有被门面覆盖的 API 可通过 `channel.getRawClient()` 调用，并遵循主 SDK 的模型与错误处理。建议把原始调用封装在业务适配层，方便未来改回 Channel 高层接口。
