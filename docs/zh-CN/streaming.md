# 流式输出

[文档索引](README.md) | 简体中文 | [English](../streaming.md)

流式输出会把逐步生成的内容转换成持续更新的消息。Java Channel SDK 支持通过 CardKit 流式卡片输出 Markdown，也支持通过消息 patch 更新任意交互卡片。

## Markdown 流式输出

```java
SendResult result = channel.streamSync(
        "oc_chat_id",
        StreamInput.markdown(new MarkdownStreamProducer() {
            @Override
            public void produce(MarkdownStreamController controller) throws Exception {
                controller.append("## 回答\n\n");
                controller.append("第一段。");
                controller.append("第二段。");
            }
        }));
```

SDK 会使用配置的初始文本创建流式卡片，默认是 `Thinking...`。`append(...)` 合并增量；模型返回修正后的完整答案时，可用 `setContent(...)` 替换全部累计文本。流式消息创建后可通过 `getMessageId()` 获取消息 ID。

空 chunk 会被忽略。正常结束时，SDK 会刷出待处理更新并退出卡片流式模式。Producer 失败时，SDK 会尝试以“Generation interrupted”标记结束卡片，然后让流操作异常完成。

## 交互卡片流式输出

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
        // 记录有限失败信息，并由业务决定任务是否重试。
    }
});
```

卡片模式先把初始 Map 作为交互消息发送，之后每次 `update(...)` 都 patch 当前完整卡片状态。控制器会复制顶层 Map，但嵌套对象仍由业务持有；不要并发修改共享 Map。`getCurrent()` 返回控制器当前记录的最新状态。

Producer 失败后，SDK 会尝试在卡片中附加可见的中断提示，同时继续向调用方传播原异常。

## 回复与目标路由

流式 API 支持与普通发送相同的目标格式和 `SendOptions`：

```java
SendOptions options = SendOptions.newBuilder()
        .replyTo(messageId)
        .replyInThread(Boolean.TRUE)
        .build();

channel.stream(chatId, StreamInput.markdown(producer), options);
```

回复配置应用于第一条流式消息。目标判断和回复回退见[发送消息](sending-messages.md)。

## 节流

两个出站配置控制更新合并：

| 配置 | 默认值 | 触发条件 |
| --- | --- | --- |
| `streamThrottleMs` | 100 毫秒 | 与上次更新间隔达到阈值时刷新。 |
| `streamThrottleChars` | 50 | 累计变化字符达到阈值时刷新。 |

当前实现只在调用 `append`、`setContent` 或 `update` 时检查阈值，不会启动后台定时器。Producer 返回时会执行最终刷新，同一 Stream 内的更新按队列顺序应用。

配置需要在平台限流与响应感知之间取舍。阈值过小会产生过多 API 流量，过大会让用户等待。业务应校验值为正且有合理上限。

## Producer 设计

Producer 会在流操作内部同步执行，应遵循：

- 模型和下游调用设置明确超时；
- 失败向上抛出，不能吞掉；
- 不从 Producer 启动无生命周期管理的线程；
- 不从多个线程并发更新同一个 Controller；
- 限制生成内容大小和更新次数；
- 业务请求取消时停止生成，即使 SDK Controller 没有取消令牌；
- 生成内容可能包含敏感用户数据时，不要写入日志。

如果外部模型从其他线程回调，应通过业务自有的有界队列串行化，并只让 Producer 存活到明确截止时间。

## 错误与重试

流式创建和更新错误会分类为 `LarkChannelException`。部分底层发送可能使用普通出站重试，但 SDK 不会从头重放 Producer。重放可能重复创建消息或重复模型开销，应由业务结合幂等记录和已知 `messageId` 决策。

消息创建后的失败可能让用户看到部分内容和中断标记。这应视为合法终态；如果产品需要，可提供明确的“重新生成”操作。

## 测试

使用假或 Mock 出站层测试：

- 无内容；
- 单个 chunk 与大量小 chunk；
- `setContent` 全量替换；
- 跨越节流阈值；
- 第一次更新前后 Producer 异常；
- 平台 patch 失败；
- 回复和话题选项；
- 业务允许的最大生成内容。

[`streaming-bot`](../../examples/streaming-bot/) 提供了精简的消费者形态 Markdown 示例。
