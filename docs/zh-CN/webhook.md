# Webhook 集成

[文档索引](README.md) | 简体中文 | [English](../webhook.md)

Webhook 模式允许已有 HTTP 服务接收飞书/Lark 回调，同时复用与 WebSocket 相同的标准化事件和安全流水线。Channel SDK 负责创建 `EventDispatcher`；路由、TLS、请求大小限制、超时、并发、可观测性和部署仍由宿主负责。

## 选择传输方式

| 维度 | WebSocket | Webhook |
| --- | --- | --- |
| 入站宿主 | SDK 长连接 Client | 业务 HTTP 服务 |
| 公开回调地址 | 不需要 | 需要，并在开发者后台配置 |
| 校验/加密值 | 入站连接不使用 | 在控制台与宿主密钥中配置 |
| 重连处理 | 主 SDK WebSocket Client | HTTP 平台/负载均衡行为 |
| 适用场景 | 没有 HTTP 回调栈的 Worker/机器人进程 | 已有成熟入口控制的 Web 服务 |

每个 Channel 实例选择一种入站方式；两者仍都通过 OpenAPI 凭证获取身份和发送消息。

## 配置 Webhook 模式

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

Verification Token 和 Encrypt Key 必须从密钥管理系统读取。不要把它们放入 URL、日志、异常信息、示例配置或健康检查接口。

## 对外提供服务前注册 handler

先注册全部 handler，再连接，最后开放路由：

```java
channel.on("message", new ChannelEventHandler<NormalizedMessage>() {
    @Override
    public void handle(NormalizedMessage message) {
        // 处理已完成校验和标准化的回调。
    }
});

channel.connectSync();
EventDispatcher dispatcher = channel.createWebhookDispatcher();
```

即使 Webhook 模式不创建 WebSocket Client，也必须调用 `connectSync()`。该操作会获取机器人身份，供 @ 检测、移除机器人自身 @ 和策略判断使用。在连接成功前，不要把回调流量交给分发器。

应用最终退出时调用 `channel.disconnectSync()`。HTTP 组件之后重新启动时，应创建新的 Channel 实例。

## 接入分发器

`EventDispatcher` 是主 Java SDK 的事件处理器。宿主需要把框架请求适配为 `com.lark.oapi.core.request.EventReq`，调用 `dispatcher.handle(eventReq)`，再把返回的 `EventResp` 映射为 HTTP 响应。请求体字节及平台签名、时间戳、nonce 头必须按主 SDK 要求原样保留。

具体适配方式取决于宿主框架。应把 Servlet、Spring 等类型限制在 HTTP 边界，不要带入 Channel handler。[`webhook-bot`](../../examples/webhook-bot/) 示例展示 Channel 构建和分发器创建，但不会替业务选择 Web 框架。

端点必须：

- 只接受已配置的回调方法和路径；
- 在入口启用 TLS；
- 设置有限请求体大小；
- 原样传递签名相关请求头；
- 使用 SDK 分发器完成校验与解密；
- 返回分发器响应，不泄漏堆栈；
- 设置请求超时和并发上限；
- 不记录完整请求体。

生产流量禁止调用 `doWithoutValidation(...)`。它会跳过分发器校验，不是 HTTP 接入捷径。

## 地址校验与加密回调

主 SDK 分发器会使用 Verification Token 和 Encrypt Key 处理平台地址校验及加密回调。开发者控制台与运行服务必须使用相同配置。配置不匹配时应默认失败，不能通过关闭校验重试。

如果应用明确使用非加密回调，请遵循当前平台安全说明并配置对应校验。预览版允许空值，但生产宿主必须在启动前校验部署配置与所选平台模式一致。

## 响应时限与异步任务

回调端点应在平台时限内确认。Channel handler 调用属于分发过程，耗时任务会延迟 HTTP 响应。Handler 工作必须有边界；持久任务应交给具有明确容量和并发限制的队列或执行器。由于回调可能重复，任务投递本身也必须幂等。

如果业务不能接受丢事件，只有在任务被可靠接收后才能返回成功；同时也不能在请求内执行无界下游重试。

## 多实例部署

部署多个 Webhook 实例时：

1. 在 `LarkChannelOptions` 配置共享的主 SDK `ICache`。
2. 业务写操作使用稳定去重键和幂等约束。
3. 保证时钟同步，因为过期检查依赖时间戳。
4. 退出时先摘除或停止接收 HTTP 流量，再销毁 Channel。
5. 监控请求量、handler 延迟、分发错误、拒绝原因和下游失败率。

共享缓存能提高后到重复事件的可见性，但它不是分布式处理锁。回调并发投递到两个实例时仍可能竞争，业务幂等是最终保护。

## 测试 Webhook 适配器

测试至少覆盖：

- 合法地址校验；
- 合法加密事件；
- 错误签名、Token 或 Key；
- 非法及超大请求体；
- 缺少必要请求头；
- 重复回调；
- handler 异常；
- 超时与关闭行为。

测试夹具必须使用合成密钥和数据。真实回调通常包含用户内容，不应提交到仓库。

## 常见问题

| 现象 | 检查项 |
| --- | --- |
| 身份或 `not_connected` 错误 | 对外服务前先成功调用 `connect()`。 |
| 校验失败 | 控制台和运行环境的 Token/Key 必须一致。 |
| 没有标准化事件 | 确认回调类型位于[支持事件表](events.md)。 |
| 业务动作重复 | 增加业务幂等；多实例配置共享缓存。 |
| 回调超时 | 限制 handler 和下游耗时，使用可靠异步投递。 |
| 只有跳过校验才工作 | 修复请求体/请求头适配，禁止上线绕过。 |
