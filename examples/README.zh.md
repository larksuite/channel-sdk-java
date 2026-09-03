# Channel SDK 示例

简体中文 | [English](README.md)

这些 Maven 模块是外部消费者契约示例。每个模块只声明 `com.larksuite.oapi:channel-sdk`，需要的主 SDK 类型通过传递依赖解析。默认构建只编译，不会访问真实飞书/Lark 租户。

## 前置条件

- JDK 8、11、17 或 21
- Maven 3.6.3+
- 已在本地安装当前 Channel SDK 产物

在仓库根目录执行：

```bash
mvn -DskipTests -Dmaven.javadoc.skip=true install
mvn -f examples/pom.xml clean verify
```

示例没有配置 Maven 执行插件，也没有选择生产 Web 框架。可在 IDE 中运行 main class，或把相关模式复制到业务后，使用业务自己的打包和启动流程。

严禁提交真实凭证或 `.env` 文件。以下环境变量只用于本地演示；生产部署应使用托管密钥注入。

## `echo-bot`

Main class：`com.lark.channel.examples.echo.EchoBot`

演示：

- 构建并连接 WebSocket；
- 处理标准化 `message`；
- 回复入站消息；
- 观察异步发送失败。

必需环境变量：

```text
APP_ID
APP_SECRET
```

`channel.start()` 会完成连接并阻塞进程，使其持续等待事件。应通过宿主生命周期停止，并确保生产代码在退出时调用 `disconnect()`。精简示例聚焦事件/发送接线；真实服务还需增加结构化日志、有界下游任务和 Shutdown Hook。

## `webhook-bot`

Main class：`com.lark.channel.examples.webhook.WebhookBot`

演示：

- 选择 `transport("webhook")`；
- 设置 Verification Token 和 Encrypt Key；
- 注册标准化 handler；
- 获取主 SDK `EventDispatcher`。

必需环境变量：

```text
APP_ID
APP_SECRET
VERIFICATION_TOKEN
ENCRYPT_KEY
```

该示例不会开放 HTTP 端口，也不会替业务选择 Spring、Servlet、Vert.x 等框架。集成时，应先成功调用 `channel.connect()` 再接收回调，把框架请求适配为主 SDK `EventReq`，调用带校验的 dispatcher，并在最终退出时断开。详见 [Webhook 集成](../docs/zh-CN/webhook.md)。

## `streaming-bot`

Main class：`com.lark.channel.examples.streaming.StreamingBot`

演示：

- 同步连接；
- 流式发送多段 Markdown；
- 从配置读取目标 chat；
- 在 `finally` 中断开。

必需环境变量：

```text
APP_ID
APP_SECRET
CHANNEL_CHAT_ID
```

运行该 main class 会发送真实消息，只能使用获授权测试应用和群。目标必须经过宿主鉴权，不能作为无限制请求参数暴露。

## `raw-client`

Main class：`com.lark.channel.examples.raw.RawClient`

演示：

- 连接 Channel；
- 通过 `channel.getRawClient()` 调用门面未覆盖的 OpenAPI；
- 主 SDK 类型继续位于 `com.lark.oapi.*`；
- 在 `finally` 中断开。

必需环境变量：

```text
APP_ID
APP_SECRET
```

为保持简洁，示例会打印机器人信息响应。生产代码不能输出完整原始响应，因为其中可能包含标识等敏感数据；应解析并只返回必要字段。

## 安全使用示例

- 使用最小权限的专用测试租户和应用。
- 将每个目标、路径、URL、卡片值和策略值视为不可信输入。
- 保持 SSRF 防护开启，配置严格本地文件目录。
- 处理每个 `CompletableFuture` 失败。
- 宿主增加超时、限流、有界线程池、指标和优雅退出。
- 未经过数据审查，不要把示例日志或输出直接复制到生产。

示例是教学材料和编译契约，不是完整生产应用。
