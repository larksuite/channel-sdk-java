# 兼容性

[文档索引](README.md) | 简体中文 | [English](../compatibility.md)

Channel SDK 与主 OpenAPI SDK 独立发版。兼容承诺只覆盖本仓库验证过的组合，不覆盖 Maven 能解析出的所有版本。

## 当前矩阵

| Channel SDK | 最低 `oapi-sdk` | 默认 `oapi-sdk` | 验证 JDK | 状态 |
| --- | --- | --- | --- | --- |
| `1.0.0-beta.1` | `2.8.5` | `2.8.5` | 8、11、17、21 | 公开 Beta |

字节码目标是 Java 8，最低 Maven 版本是 3.6.3。CI 在 JDK 8、11、17 和 21 上验证构建。在未列出的 JDK 上偶然运行成功，不构成兼容保证。

## 依赖模型

`com.larksuite.oapi:channel-sdk` 会传递依赖 `com.larksuite.oapi:oapi-sdk`。Maven 最近定义规则允许消费者选择其他版本，但未列出的组合必须通过完整 Channel 与消费者示例验证后才受支持。

直接调用原始 OpenAPI 的应用可以显式声明 `oapi-sdk`。通过依赖管理统一到一个版本并检查收敛：

```bash
mvn dependency:tree -Dincludes=com.larksuite.oapi
mvn dependency:analyze
```

不能把主 SDK 包 shade 或复制进 `channel-sdk`。预期包归属：

| 包 | 产物 |
| --- | --- |
| `com.lark.channel.**` | `channel-sdk` |
| `com.lark.oapi.**` | `oapi-sdk` |

独立 Channel JAR 不能包含 `com.lark.oapi.channel`、`com.lark.oapi.core`、`com.lark.oapi.event` 或 `com.lark.oapi.service` 实现类。

## 源码与二进制兼容

项目使用 `source=1.8` 和 `target=1.8` 编译，公开 API 避免 Java 8 之后的语言特性。

当前版本是 Beta。`1.0.0` 前，包名、签名、默认值、行为和产物元数据都可能变化。每个有意的用户可见改动都必须记录在 [CHANGELOG.md](../../CHANGELOG.md)，必要时补充迁移说明。

稳定版后会随发布策略明确语义化版本预期。不能从当前 Beta 版本推断稳定二进制兼容性。

## 平台兼容

SDK 可通过 `LarkChannelOptions.domain(...)` 使用主 SDK 接受的域名，HTTP 与 WebSocket 应保持同一环境。平台权限、事件可用性、卡片 Schema 和媒体限制由飞书/Lark 控制，可能独立演进。

高层 Channel 只支持文档中列出的事件和内容类型。其他主 SDK API 可通过 `getRawClient()` 调用，但遵循主 SDK 自身兼容约定。

## 验证新组合

引入不同主 SDK 或 JDK 版本时：

1. 在独立分支更新依赖；
2. 使用 JDK 8、11、17 和 21 分别运行 `./scripts/verify.sh`；
3. 运行完整单测和包隔离检查；
4. 编译全部外部消费者示例；
5. 在安全测试租户执行带凭证的 Webhook/WebSocket 集成测试；
6. 检查依赖收敛和公开 API/Javadoc 差异；
7. 只有证据完成后才更新本矩阵。

不能仅凭编译成功就标记为已验证。

后续每个 Channel SDK 版本都必须更新本矩阵，记录最低/默认主 SDK 版本和已完成的 JDK 证据。
