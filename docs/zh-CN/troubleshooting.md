# 故障排查

[文档索引](README.md) | 简体中文 | [English](../troubleshooting.md)

先从异常类型和 `LarkChannelException.getCode()` 入手，再检查生命周期、配置、权限与依赖收敛。可在私有诊断中保留 cause，但严禁记录凭证、Token、私钥、原始回调体或媒体字节。

## Maven 无法解析 `channel-sdk`

确认消费者请求的是精确 `com.larksuite.oapi:channel-sdk:1.0.0-beta.1`、能够访问 Maven Central，且未开启离线模式。测试尚未发布的源码改动时，执行 `mvn -DskipTests -Dmaven.javadoc.skip=true install` 安装到本地，并使用完全一致的本地版本。

## 创建 Channel 失败

调用 Factory 前校验 `LarkChannelOptions`：options 非 null、App ID 非空、具有有效 App Secret 或 `ClientAssertionProvider`、传输值精确受支持、嵌套配置非 null。构建时会创建主 SDK Client 和分发器，不兼容依赖仲裁也可能在这里暴露；执行本文后面的依赖检查。

## 连接问题

### `connect()` 返回 `not_connected`

检查：

1. `APP_ID` 和凭证来源非空且属于同一应用。
2. 已启用机器人能力并安装到目标租户。
3. `domain`、`oauthBaseUrl`、代理、DNS 和出站规则指向同一目标环境。
4. 机器人信息 OpenAPI 能返回 bot `open_id`。
5. WebSocket 第一次握手能在 15 秒内完成。

失败的 `connect()` 会清理共享 Future，修复根因后可以再次调用。业务应使用有上限的退避，不能运行无限紧密重连循环。

### 身份成功但 WebSocket 一直未就绪

确认 `transport("websocket")`、开发者控制台长连接事件模式、兼容 `oapi-sdk`、网络出站和应用安装。订阅 `reconnecting`/`reconnected` 作为状态指标。不要打印 WebSocket URL 或鉴权数据。

### 处理消息时报告 `not_connected`

入站标准化需要机器人身份。必须先注册 handler 并完成 `connect()`，再开放 Webhook 路由或接收 WebSocket 事件。

## `message` handler 没有被调用

按顺序检查：

- 平台回调是受支持的消息接收事件；
- 应用版本和事件订阅已生效；
- 后续 `channel.on("message", ...)` 没有覆盖目标 handler；
- 消息没有超过过期窗口；
- 不是已标记的重复消息；
- 群聊在允许范围；
- 策略要求时直接 @ 了机器人；
- `@所有人` 仅在明确允许时通过；
- 私聊模式和发送者白名单允许；
- 消息合并等待已经结束。

订阅 `reject` 和 `error`。当前流水线会静默丢弃过期和重复事件，因此排查时使用可控测试夹具和指标。

### 原始回调已到达，但标准 handler 不执行

HTTP/WebSocket 层看到回调时，确认它是受支持事件类型、使用的是该 Channel 创建的 dispatcher，并且分发前 `connect()` 已完成。之后检查 `reject` 和 `error`。开启 `includeRawEvent` 只会给标准模型附加原数据，不会把不受支持回调变成 `message`。

### 群消息被策略拒绝

读取 `RejectEvent.getReason()`：依次检查群白名单、`@所有人`、直接 @ 机器人。空白名单允许所有群；非空时必须包含精确 chat ID。不要为修复一个群而全局关闭 @ 策略，应修正预期白名单或交互方式。

## 消息意外合并

按会话合并默认开启。时间接近的消息可能成为一个 `NormalizedMessage`，文本会拼接，元数据取最后一条。要求精确逐事件语义时关闭 `chatQueueEnabled`，或调整 `BatchTextConfig`。请阅读[策略与安全](policy-and-safety.md#消息合并)中的发送者身份提醒。

## Webhook 校验失败

确认：

- 运行时 Verification Token/Encrypt Key 与控制台完全一致；
- HTTP 适配器保留原始请求体字节和签名相关头；
- 构建 `EventReq` 前没有解码后重新编码 body；
- 路径与方法正确；
- 代理中间件没有消费或改变 body。

生产环境禁止通过 `doWithoutValidation` 解决。应使用合成测试夹具排查适配层。

## 发送失败

| 错误码 | 处理方式 |
| --- | --- |
| `permission_denied` | 检查应用权限、租户授权/安装和凭证环境，不重试。 |
| `rate_limited` | 内置总尝试不超过三次，降低流量并遵守平台限制。 |
| `format_error` | 校验 post/card 结构；Markdown 可能回退文本。 |
| `target_revoked` | 回复目标已消失；判断新建消息回退是否可接受。 |
| `send_timeout` | 检查网络和主 SDK 超时，不要盲目重试结果不明确的发送。 |
| `unknown` | 私下检查 cause 与平台响应，避免嵌套重试。 |

长文本场景中，`messageId` 是第一条 ID，`chunkIds` 是所有块；只有第一块属于回复。“后续内容跑出话题”可能是文档约定行为，而不是平台故障。

### Receiver ID 类型错误

目标前缀决定路由：`oc_` 为 chat，`ou_` 为 open ID，`on_` 为 union ID，包含 `@` 为邮箱，其他为 user ID。应从权威来源校验 ID；任意字符串会按 `user_id` 路由，然后可能被平台拒绝。

### 回复变成新消息

平台报告回复目标消失时，SDK 会向 `to` 创建新消息。检查原消息 ID 和留存状态。业务如果不能接受回退，应在业务层检测并约束，不能假设每个返回消息都仍是回复。

### Markdown 退化为纯文本

Markdown 会先转换成平台 post。Post 发送被分类为 `format_error` 时回退纯文本。可简化/校验 Markdown；精确布局场景使用符合 Schema 的原始 post/card。

## 媒体失败

### `ssrf_blocked`

确认 URL 使用 HTTP(S)，只解析到公网地址，每次重定向都通过校验，TLS 主机名有效。不能关闭防护。确需内部主机时，使用严格受控的精确主机白名单，并配合网络出站策略。

### 本地文件被拒绝

确认它是可读普通文件，真实路径位于 `allowedFileDirs` 下，且不在系统禁止目录。不能把目录扩大到 `/`、用户主目录或其他通用根目录。

### 音视频时长导致 `upload_failed`

显式传入正毫秒时长，或使用可解析的 Opus/Ogg 音频与 MP4 视频。传入本地文件、字节或流前设置大小上限。

### 上传/下载时内存增长

预览版会物化字节和输入流，下载返回 `byte[]`，时长解析也会读取文件。SDK 调用前必须限制内容，并避免并发大文件传输。只有远程 URL 下载内置 50 MiB 上限。

## 流式失败

用户看到“Generation interrupted”表示流式消息已创建，但 Producer 或更新失败。检查操作的异常完成。不能自动重放整个 Producer，否则可能重复消息或模型调用。应提供带幂等保护的显式业务重试。

更新不够频繁时注意：只有 Producer 调用 `append`、`setContent` 或 `update` 时才检查节流，没有后台定时刷新；Producer 返回会强制最终刷新。

## 依赖与 classpath 问题

执行：

```bash
mvn dependency:tree -Dincludes=com.larksuite.oapi
mvn dependency:analyze
jar tf target/channel-sdk-*.jar | sort
```

检查多个 `oapi-sdk` 版本、两套 Channel 命名空间或复制的 `com.lark.oapi` 包。独立产物只应拥有 `com.lark.channel` 类。如果编译通过但运行时缺方法，应检查最终业务产物的依赖解析，而不只是模块 POM。

## 自定义域名或 OAuth 问题

`domain(...)` 配置主 OpenAPI Client 和 WebSocket 域名，`oauthBaseUrl(...)` 单独覆盖 OAuth/客户端断言端点。确认二者属于同一目标环境，来自可信部署配置，能通过代理/DNS 策略解析且 TLS 有效。不能从不可信请求接收这些值。如果默认配置正常而自定义失败，应对比两条路由，但不要记录凭证。

## JDK 构建问题

使用 Maven 3.6.3+ 和完整 JDK，不能使用 JRE。发布目标是 JDK 8、11，产物为 Java 8 字节码。用 `mvn -version` 确认实际 Java Home。更新 JDK 尚未进入验证矩阵。

## 安全诊断报告

可以包含：

- Channel、主 SDK、JDK、Maven 和 OS 版本；
- 传输模式，以及连接是否达到 ready；
- 稳定异常 code 和脱敏堆栈；
- 事件名或发送类型，不含内容；
- 使用合成 ID/数据的最小复现代码；
- 仅相关 SDK 的依赖树。

必须排除：

- 应用凭证、访问令牌、断言和私钥；
- 校验/加密值和租户/用户标识；
- 原始事件、消息文本、媒体字节和未筛选日志。
