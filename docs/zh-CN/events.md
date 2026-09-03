# 事件

[文档索引](README.md) | 简体中文 | [English](../events.md)

Channel SDK 会将选定的飞书/Lark 回调转换为稳定 Java 模型，并使用简短事件名分发。WebSocket 和 Webhook 共用同一套分发与标准化流水线，因此业务 handler 可以与传输方式解耦。

## 支持的事件

| 事件名 | 模型 | 来源回调 | 安全处理 |
| --- | --- | --- | --- |
| `message` | `NormalizedMessage` | 收到消息 | 策略、过期检查、去重、按会话串行、可选合并 |
| `reaction` | `ReactionEvent` | 添加/删除表情回复 | 轻量去重分发 |
| `botAdded` | `BotAddedEvent` | 机器人被加入群 | 直接标准化分发 |
| `cardAction` | `CardActionEvent` | 卡片交互回调 | 去重并按会话串行 |
| `comment` | `CommentEvent` | `drive.notice.comment_add_v1` | 去重并按文件串行 |
| `reject` | `RejectEvent` | 本地策略拒绝 | 说明消息没有进入 `message` 的原因 |
| `error` | `ChannelErrorEvent` | 本地处理或 handler 失败 | 结构化错误通知 |
| `reconnecting` | `Void`/`null` | WebSocket 状态变化 | 仅 WebSocket |
| `reconnected` | `Void`/`null` | WebSocket 状态变化 | 仅 WebSocket |

当前预览版只转换表中回调。其他平台事件应通过主 SDK 原始能力订阅，不要假设任意事件都会进入 `channel.on(...)`。

WebSocket 模式由主 SDK 长连接 Client 把受支持回调送入该分发器；Webhook 模式由宿主使用 `EventDispatcher` 处理 HTTP 请求。标准化 Payload 相同，但重连事件仅存在于 WebSocket，HTTP 响应时限仅存在于 Webhook。

## 注册 handler

```java
ChannelSubscription subscription = channel.on(
        "message",
        new ChannelEventHandler<NormalizedMessage>() {
            @Override
            public void handle(NormalizedMessage message) {
                // 控制执行耗时；长任务交给有边界的业务线程池。
            }
        });

// 不再需要时：
subscription.unsubscribe();
```

每个事件名只有一个 handler 槽位。同一事件再次注册会覆盖原 handler 并输出警告。返回的 subscription 只会在当前注册项仍是它自己时移除 handler。

也可以批量注册：

```java
Map<String, ChannelEventHandler<?>> handlers = new HashMap<String, ChannelEventHandler<?>>();
handlers.put("message", new ChannelEventHandler<NormalizedMessage>() {
    @Override
    public void handle(NormalizedMessage event) {
        // 处理消息。
    }
});
handlers.put("reject", new ChannelEventHandler<RejectEvent>() {
    @Override
    public void handle(RejectEvent event) {
        // 按原因计数。
    }
});
ChannelSubscription all = channel.on(handlers);
```

## 标准化消息

`NormalizedMessage` 提供常用字段，业务无需自行解析 JSON：

| 字段 | 含义 |
| --- | --- |
| `messageId` | 当前平台消息 ID |
| `chatId` | 可直接传给 `channel.send(...)` 的目标 |
| `chatType` | 通常为 `p2p`、`group` 或 `topic_group` |
| `senderId` / `senderName` | 可用时的发送者身份 |
| `content` | 转换后的文本，机器人自身 @ 已被移除 |
| `rawContentType` | 原始类型，如 `text`、`post`、`image`、`file` |
| `resources` | 从消息提取的图片/文件/音频/视频描述 |
| `mentions` | 按出现顺序解析的用户 @ 信息 |
| `mentionAll` / `mentionedBot` | 策略判断使用的 @ 状态 |
| `rootId` / `threadId` / `replyToMessageId` | 平台提供时的回复和话题上下文 |
| `createTime` | 可用时的平台毫秒时间戳 |
| `raw` | 仅开启原始事件后包含原始回调 |

转换器覆盖常见的文本、富文本、交互卡片、图片、文件、音频、视频、表情包、位置、分享、文件夹、日历、系统、待办、投票、红包、视频会议和合并转发。未知类型会由兜底转换器尽量保留可读内容。标准化是便利层，不保证未来新增的平台字段全部保留。

入站媒体通过 `ResourceDescriptor` 提供平台 `fileKey`；业务需要二进制内容时调用 `downloadResource(fileKey, type)`。不要把 file key 当作出站本地路径或远程 URL。

## 其他标准化事件字段

| 模型 | 字段 |
| --- | --- |
| `ReactionEvent` | 消息 ID、操作者 ID/类型、表情类型、动作（`added`/`removed`）、动作时间、可选 raw |
| `BotAddedEvent` | chat ID、操作者 ID、回调提供的机器人/名称字段、可选 raw |
| `CardActionEvent` | 消息/chat/操作者 ID、组件 tag/name/option、动作 value Map、可选 raw |
| `CommentEvent` | 文件 token/type、评论/回复 ID、操作者 ID、是否 @ 机器人、时间戳、可选 raw |

平台回调未提供时，字段可能为 null 或空。执行业务动作前必须校验必要标识。卡片动作值和评论元数据仍是用户/平台输入，不能直接拼接到命令、路径或查询。

## 拒绝事件

`reject` 与 `error` 的运营语义不同：拒绝是预期策略结果。`RejectReason` 可能为：

- `group_not_allowed`
- `sender_not_allowed`
- `no_mention`
- `dm_disabled`
- `mention_all_blocked`

建议按有限的 reason 集合统计次数。不要因为消息被拒绝就记录完整消息或原始事件。

## 错误事件

标准化、安全处理或业务 handler 抛出异常时，SDK 会发送 `ChannelErrorEvent`，其中包含事件名、异常和关联事件对象。如果没有注册 `error` handler，SDK 会输出未处理警告；`error` handler 自身抛出的异常会被捕获并记录，避免递归。

```java
channel.on("error", new ChannelEventHandler<ChannelErrorEvent>() {
    @Override
    public void handle(ChannelErrorEvent event) {
        metrics.increment("channel_event_error", event.getEventName());
        logger.error("Channel event failed: {}", event.getEventName(), event.getError());
    }
});
```

示例中的 `metrics` 和 `logger` 由宿主应用提供。不要使用无限取值的事件内容作为指标标签，也不要默认把 `event.getEvent()` 序列化到日志。

## 原始事件开关

```java
LarkChannelOptions.newBuilder(appId, appSecret)
        .includeRawEvent(true)
        .build();
```

原始事件默认关闭。只有明确存在标准字段缺口时才开启，并限制留存时间、在日志前脱敏。该开关只影响标准化模型的 `getRaw()`，不改变公开标准字段。

## 交付语义

平台回调可能重复、延迟，或在重连后再次到达。Channel 会在配置范围内抑制重复，但业务副作用仍必须幂等。写操作应使用稳定业务键或数据库唯一约束。多实例部署时，共享 `ICache` 能帮助识别后来到达的重复事件，但处理锁与“检查后标记”仍是进程内且非原子的。

Handler 运行在 SDK 的事件处理线程。不要无限阻塞、启动失控线程或做无界重试。下游调用必须设置超时；重任务应转交给具有明确队列和并发上限的执行器。

`message` 的完整顺序是：过期检查 → 已处理查询 → 策略 → 进程内尽力锁 → 可选按会话队列/合并 → handler → 标记已处理/释放。卡片动作和评论执行已处理查询、本地锁、作用域排序后分发；表情回复在轻量分发前标记已处理。影响见[策略与安全](policy-and-safety.md)。
