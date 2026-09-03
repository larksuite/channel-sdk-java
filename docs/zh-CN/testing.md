# 测试

[文档索引](README.md) | 简体中文 | [English](../testing.md)

仓库将确定性的本地验证与带凭证的平台集成测试分开。本地验证不能要求 App Secret，也不能依赖真实租户网络。

## 完整本地验证

```bash
./scripts/verify.sh
```

流程依次执行：

1. 文档结构、链接和过期引用检查；
2. `mvn clean verify`，覆盖编译、单测、Javadoc、依赖分析和构建期包检查；
3. JAR 包隔离检查；
4. 安装本地快照；
5. 编译验证全部消费者示例模块。

交付改动前必须执行。命令会修改 `target/` 和本地 Maven 缓存，但不能部署或发布产物。

## 分项命令

```bash
# 单测/构建验证
mvn clean verify

# 仅文档
./scripts/verify-docs.sh

# package/verify 后检查包隔离
./scripts/verify-package.sh

# 消费者示例
mvn -DskipTests -Dmaven.javadoc.skip=true install
mvn -f examples/pom.xml clean verify
```

稳定文档不写死单测数量，因为测试集会持续增长。

## JDK 矩阵

当前发布目标是 JDK 8 和 JDK 11。分别选择每个 JDK 并执行完整流程：

```bash
java -version
mvn -version
./scripts/verify.sh
```

Maven 输出必须显示目标 Java Home。一个 JDK 通过不能代表另一个。JDK 17/21 结果可以作为信息，但只有发布流程正式纳入后才能更新兼容矩阵。

GA 前，项目必须明确决定 JDK 17 和 21 是否纳入发布目标，并在每个采纳目标上执行同样的完整流程。在决策和证据完成前，它们属于未验证，而不是默认支持。

## 集成测试

真实平台集成测试默认跳过，只有显式开启后运行。应使用最小权限的专用测试应用和租户。

```bash
export LARK_CHANNEL_IT_ENABLED=true
export LARK_CHANNEL_IT_APP_ID='test-app-id-from-secret-manager'
export LARK_CHANNEL_IT_APP_SECRET='test-app-secret-from-secret-manager'

mvn -Dtest=TestLarkChannelIntegration test
```

包含 WebSocket 集成用例：

```bash
export LARK_CHANNEL_IT_ENABLE_WS=true
mvn -Dtest=TestLarkChannelIntegration test
```

禁止提交 `.env` 文件或在 CI 日志打印环境值。使用后清理 shell 变量并撤销临时凭证。集成测试会产生真实平台流量，只能在获授权测试环境运行。

## Channel E2E

`TestChannelE2E` 是拆分后 Channel SDK 的带凭证端到端测试，覆盖公开 Facade、部分 Raw SDK 调用、媒体上传/下载、流式消息和事件监听边界。只有在进程环境显式开启时才会运行；`.env` 中的值不能自行开启真实流量。

将 `.env.example` 复制为已忽略的本地 `.env`，填写获授权测试应用的值。运行器将其按数据文件解析（绝不作为 shell 脚本 `source`），再以进程环境变量覆盖；它会拒绝缺失、空文件、符号链接或逃出仓库根目录的媒体路径。Secret 只能保存在本地文件或密钥管理系统中。

```bash
# 校验凭证/标识、素材路径和用例计划；不发送任何平台请求。
LARK_CHANNEL_E2E_ENABLED=true LARK_CHANNEL_E2E_DRY_RUN=true \
  mvn -Dtest=TestChannelE2E test

# 执行自动真实用例；会在获授权租户中产生消息、媒体上传、表情、编辑和流式消息。
LARK_CHANNEL_E2E_ENABLED=true \
  mvn -Dtest=TestChannelE2E test
```

自动集覆盖连接和机器人身份、会话/消息查询、文本/Markdown/Post/Card 发送、群 @、已支持媒体格式、分享、回复、编辑/Card 更新、添加/删除表情、图片/文件直传与下载、Markdown/Card 流式消息。只有提供 `CHANNEL_E2E_STICKER_FILE_KEY` 时才覆盖贴纸，否则会明确跳过。

Raw 群消息列表用例要求机器人已在配置的群中，且测试租户部署的应用版本包含 `im:message.group_msg` 权限；平台返回 `230027` 时会按权限失败记录，不会重试掩盖问题。

事件与策略用例必须在运行器完成连接后由真实用户触发。运行器会发送带 trace 的目标消息/Card，并将操作说明写入结果报告；仅能在获授权测试租户中启动：

```bash
LARK_CHANNEL_E2E_ENABLED=true \
LARK_CHANNEL_E2E_MANUAL=true \
LARK_CHANNEL_E2E_ENABLE_POLICY=true \
CHANNEL_E2E_WAIT_SECONDS=180 \
  mvn -Dtest=TestChannelE2E test
```

已开启的手工事件共用同一个 `CHANNEL_E2E_WAIT_SECONDS` 等待窗口；缺失动作会统一报告，不会按每个事件分别等待一次。

要隔离文档评论投递问题，可使用下面的专用模式。它只建立 Java WebSocket 监听，并区分三种结果：收到目标文档的评论、收到其他文档的评论、或本监听器未观察到事件。使用该模式时不要同时运行其他应用长连接。

```bash
LARK_CHANNEL_E2E_ENABLED=true \
LARK_CHANNEL_E2E_FOCUSED_COMMENT=true \
CHANNEL_E2E_WAIT_SECONDS=45 \
  mvn -Dtest=TestChannelE2E test
```

`event.bot_added` 会变更群成员，默认不执行；它还必须设置 `LARK_CHANNEL_E2E_ENABLE_BOT_ADDED=true` 和 `CHANNEL_E2E_BOT_ADDED_CHAT_ID`。后者必须是机器人尚未加入的专用测试群，禁止在生产群执行。

每次已开启的运行都会在 `target/e2e/` 生成脱敏 Markdown 报告，记录通过/失败/跳过状态，并在错误详情中隐藏 App Secret、文档 token、目标 ID 和贴纸 key。对外测试报告只能引用该脱敏结果，不能粘贴 `.env`、原始事件、消息 ID 或任何凭证。

## 每类改动的测试要求

| 改动区域 | 最低证据 |
| --- | --- |
| 配置 | 默认值、自定义值、业务边界的 null/非法输入行为 |
| 事件 | 标准字段、raw 开关、handler 覆盖、error/reject 路径 |
| 策略 | 群/私聊矩阵、`@all`、运行时完整替换 |
| 安全 | 过期、去重 TTL/容量、队列、合并边界、销毁 |
| 发送 | 受影响类型、目标路由、回复回退、分块结果 |
| 媒体 | 来源类型、路径穿越/符号链接、SSRF/DNS/重定向/TLS、大小限制 |
| 流式 | 初始/更新/结束/失败、节流阈值、顺序 |
| 打包 | 源码/二进制/Javadoc 产物，不复制主 SDK 类 |
| 文档 | 中英文配对、链接、命令、版本/默认值一致 |

安全测试必须使用合成数据和本地测试服务器，夹具中不能放真实访问令牌、个人数据或回调内容。

## 示例工程作为契约测试

示例 Reactor 特意只声明 `channel-sdk`。构建成功可以证明公开产物能按外部消费者方式编译，且示例需要的主 SDK 类型能够传递解析。

示例应保持精简且可编译，不替代单测，也不能在 `verify` 阶段访问真实 API。

## 失败排查

验证失败时：

1. 找到第一个失败阶段，不要跳过检查重跑；
2. 使用分项命令复现；
3. 检查相关依赖/JDK 版本；
4. 修复后重跑分项；
5. 最终在两个目标 JDK 上完成全流程。

默认验证严禁依赖真实凭证、定时 sleep 或不受限公网。
