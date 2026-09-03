# 配置

[文档索引](README.md) | 简体中文 | [English](../configuration.md)

所有 Channel 都通过 `LarkChannelOptions.newBuilder(appId, appSecret)` 开始构建。调用 `build()` 后顶层选项不可变；嵌套配置采用可变 Java Bean，便于构建前组装。只有策略配置支持之后通过 `updatePolicy` 更新。

## 顶层 Builder 方法

| 方法 | 默认值 | 用途 |
| --- | --- | --- |
| `transport(String)` | `websocket` | 选择 WebSocket 或 Webhook 入站方式。 |
| `webhook(WebhookOptions)` | 空 Bean | 配置回调校验/解密。 |
| `safety(SafetyConfig)` | 文档中的安全默认值 | 配置过期、去重、队列、锁与合并。 |
| `policy(PolicyConfig)` | 文档中的策略默认值 | 配置群聊与私聊准入。 |
| `outbound(OutboundConfig)` | 文档中的出站默认值 | 配置拆分、流式、媒体与重试。 |
| `cache(ICache)` | `null` | 主 SDK 缓存及跨进程已处理事件查询。 |
| `domain(String)` | 主 SDK 飞书默认值 | 覆盖 OpenAPI 和 WebSocket 域名。 |
| `httpTransport(IHttpTransport)` | 主 SDK 默认值 | 覆盖 HTTP 传输。 |
| `httpInstance(RequestOptions)` | `null` | 为主 SDK 调用提供请求选项。 |
| `source(String)` | `null` | 设置主 SDK 来源标识。 |
| `clientAssertionProvider(...)` | `null` | 启用客户端断言鉴权。 |
| `oauthBaseUrl(String)` | 主 SDK 默认值 | 覆盖 OAuth/断言基础地址。 |
| `includeRawEvent(boolean)` | `false` | 在标准模型上附加原始回调。 |
| `includeRawInMessage(boolean)` | `false` | `includeRawEvent` 的废弃别名。 |

Builder 会创建嵌套默认对象。向嵌套配置 setter 传 null 时，各字段并非都以相同方式标准化，因此业务应构建并校验完整对象。

## 凭证

```java
LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret).build();
```

从密钥管理系统或运行时环境读取 `appId` 和 `appSecret`，不要记录二者。SDK 不会替业务校验任意配置输入；业务必须在进程入口拒绝 null 或空字符串。

使用私钥鉴权时，传入主 SDK 的 `ClientAssertionProvider`：

```java
LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, "")
        .clientAssertionProvider(clientAssertionProvider)
        .build();
```

只有在非空 Provider 能生成有效客户端断言时，才可以使用空 App Secret。私钥应由 SDK 外部的凭证系统保存和轮换。主 SDK 部署需要覆盖断言/OAuth 地址时，可设置 `oauthBaseUrl(...)`。

## 传输方式

支持的值只有：

| 值 | 行为 |
| --- | --- |
| `websocket` | 默认值。创建底层 WebSocket Client；`connect()` 等待身份解析和第一次握手。 |
| `webhook` | 不创建 WebSocket Client。宿主通过 `createWebhookDispatcher()` 获取分发器并接入 HTTP 路由。 |

```java
LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .transport("websocket")
        .build();
```

当前预览版不会拒绝未知传输字符串；未知值会表现为“非 WebSocket”，但不受支持。业务应在构建前把配置严格校验为 `websocket` 或 `webhook`。

Webhook 校验值与应用凭证分开配置：

```java
LarkChannelOptions.WebhookOptions webhook = new LarkChannelOptions.WebhookOptions();
webhook.setVerificationToken(verificationToken);
webhook.setEncryptKey(encryptKey);

LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .transport("webhook")
        .webhook(webhook)
        .build();
```

请求处理和生命周期要求见 [Webhook 集成](webhook.md)。

## 域名与主 SDK 传输配置

Channel SDK 会把以下配置转交给主 SDK：

| Builder 方法 | 用途 |
| --- | --- |
| `domain(String)` | OpenAPI 和 WebSocket 域名；默认使用主 SDK 的飞书域名。 |
| `httpTransport(IHttpTransport)` | 自定义主 SDK HTTP 传输。 |
| `httpInstance(RequestOptions)` | 机器人身份和 OpenAPI 调用使用的请求选项。 |
| `cache(ICache)` | 主 SDK 缓存，以及 Channel 跨进程已处理事件查询。 |
| `source(String)` | 传递给主 SDK 的来源标识。 |
| `clientAssertionProvider(...)` | 私钥客户端断言 Provider。 |
| `oauthBaseUrl(String)` | OAuth 基础地址覆盖。 |

HTTP 与 WebSocket 必须使用一致环境。不要直接使用不可信请求传入的域名，应从可信部署配置加载。

## 原始事件开关

默认情况下，标准化事件不携带原始事件体：

```java
LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .includeRawEvent(true)
        .build();
```

仅当业务确实需要标准化过程有意丢弃的字段时才开启。原始事件可能包含身份标识、租户元数据、消息内容和平台扩展字段，不要持久保存或输出到日志。`includeRawInMessage(...)` 已废弃，请使用 `includeRawEvent(...)`。

## 策略默认值

`PolicyConfig` 决定哪些消息可以进入 `message` handler：

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| `groupAllowlist` | 空 | 空列表允许所有群；非空时只允许指定 chat ID。 |
| `dmMode` | `open` | 支持 `open`、`disabled`、`allowlist`。 |
| `dmAllowlist` | 空 | `dmMode=allowlist` 时允许的发送者 open ID。 |
| `requireMention` | `true` | 群消息必须直接 @ 机器人。 |
| `respondToMentionAll` | `false` | `@所有人` 是否通过群聊 @ 门禁。 |

```java
LarkChannelOptions.PolicyConfig policy = new LarkChannelOptions.PolicyConfig();
policy.setGroupAllowlist("oc_allowed_chat");
policy.setDmMode("allowlist");
policy.setDmAllowlist("ou_allowed_user");
policy.setRequireMention(true);
policy.setRespondToMentionAll(false);
```

未知 `dmMode` 当前会表现得像 `open`，因此业务必须主动校验。修改默认策略前请阅读[策略与安全](policy-and-safety.md)。

## 安全流水线默认值

| 配置 | 默认值 |
| --- | --- |
| 去重 TTL | 12 小时 |
| 进程内去重容量 | 5,000 条 |
| 去重清理间隔 | 5 分钟 |
| 消息过期窗口 | 30 分钟 |
| 按会话队列 | 开启 |
| 进程内处理锁 TTL | 5 分钟 |
| 去重命名空间 | `channel:seen` |
| 短文本合并等待 | 600 毫秒 |
| 长文本阈值/等待 | 1,000 字符 / 2,000 毫秒 |
| 单批上限 | 8 条消息或 4,000 字符 |

```java
LarkChannelOptions.SafetyConfig safety = new LarkChannelOptions.SafetyConfig();
safety.setDedupTtlMs(6L * 60L * 60L * 1000L);
safety.setDedupMaxEntries(3000);
safety.setStaleMessageWindowMs(10L * 60L * 1000L);
safety.setChatQueueEnabled(true);

LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .safety(safety)
        .build();
```

业务配置必须保证容量和 TTL 为正且有合理上限。共享 `ICache` 能让后到的重复检查发现其他进程已标记的事件，但检查—标记过程和处理锁都不是分布式原子操作，因此跨实例业务副作用仍必须幂等。

## 发送默认值

| 配置 | 默认值 | 用途 |
| --- | --- | --- |
| `textChunkLimit` | 3,500 | 拆分长文本和 Markdown。 |
| `streamThrottleMs` | 100 | 流式定时刷新下限。 |
| `streamThrottleChars` | 50 | 按字符数触发刷新的阈值。 |
| `streamInitialText` | `Thinking...` | Markdown 流式卡片初始文本。 |
| `ssrfGuardEnabled` | `true` | 拒绝私有/保留地址的远程媒体下载。 |
| `ssrfAllowlist` | 空 | 可绕过地址拦截的精确标准化主机名。 |
| 最大尝试次数 | 3 | 可重试发送错误的总尝试次数。 |
| 重试基础等待 | 500 毫秒 | 第一次退避等待。 |
| `allowedFileDirs` | 空 | 不增加目录限制；任意可读且不在系统禁区的路径都可能通过，生产必须配置。 |

```java
LarkChannelOptions.OutboundConfig outbound = new LarkChannelOptions.OutboundConfig();
outbound.setTextChunkLimit(3000);
outbound.setStreamInitialText("生成中...");
outbound.setAllowedFileDirs(java.util.Collections.singletonList("/srv/bot/uploads"));

LarkChannelOptions.RetryConfig retry = new LarkChannelOptions.RetryConfig();
retry.setMaxAttempts(3);
retry.setBaseDelayMs(500L);
outbound.setRetry(retry);

LarkChannelOptions options = LarkChannelOptions.newBuilder(appId, appSecret)
        .outbound(outbound)
        .build();
```

应始终开启 SSRF 防护。白名单主机会绕过私有/保留地址拦截，只能加入由自身基础设施控制的主机。详见[媒体](media.md)和[发送消息](sending-messages.md)。

## 完整构建示例

```java
LarkChannelOptions.PolicyConfig policy = new LarkChannelOptions.PolicyConfig();
policy.setGroupAllowlist("oc_support_group");
policy.setDmMode("allowlist");
policy.setDmAllowlist("ou_operator");

LarkChannelOptions.SafetyConfig safety = new LarkChannelOptions.SafetyConfig();
safety.setStaleMessageWindowMs(15L * 60L * 1000L);

LarkChannelOptions.OutboundConfig outbound = new LarkChannelOptions.OutboundConfig();
outbound.setAllowedFileDirs(java.util.Collections.singletonList("/srv/channel/files"));

LarkChannel channel = LarkChannelFactory.createLarkChannel(
        LarkChannelOptions.newBuilder(appId, appSecret)
                .transport("websocket")
                .policy(policy)
                .safety(safety)
                .outbound(outbound)
                .includeRawEvent(false)
                .build());
```

配置加载是业务的输入边界：构建这些对象前，需要校验类型、范围、枚举、路径和域名。

## 常见错误配置

- 没有有效 `ClientAssertionProvider` 时使用空应用凭证，会导致鉴权或连接失败。
- 传输值不是精确 `websocket` 或 `webhook` 时不受支持，并可能静默表现为非 WebSocket。
- 未知 `dmMode` 会扩大为开放行为，必须校验枚举。
- 空 `allowedFileDirs` 不会阻止本地文件，生产必须配置严格目录列表。
- 零/负容量或过大的 TTL、批处理、重试值可能移除资源边界或造成长时间阻塞。
- 自定义 `domain` 不代表 OAuth 地址和网络环境自动一致，还需校验 `domain` 与 `oauthBaseUrl`。
- 开启原始事件会让更多用户/租户数据进入业务内存与日志路径。
