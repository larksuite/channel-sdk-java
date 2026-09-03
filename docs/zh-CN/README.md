# 文档索引

简体中文 | [English](../README.md)

这里的文档对应 `1.0.0-beta.1` 公开 Beta 版本。建议先完成快速开始，再按接入范围阅读相应专题。

## 开始使用

- [快速开始](quickstart.md)：构建、配置、连接、接收与回复
- [配置](configuration.md)：凭证、传输方式、域名、客户端断言、安全与发送配置
- [示例](../../examples/README.zh.md)：按真实消费者形态组织的示例工程

## 接收事件

- [事件](events.md)：标准事件名、消息字段、订阅方式与原始事件开关
- [Webhook](webhook.md)：安全地创建并托管 SDK 分发器
- [策略与安全](policy-and-safety.md)：白名单、@ 规则、去重、串行与合并

## 发送响应

- [发送消息](sending-messages.md)：目标、内容类型、回复、@、重试与错误
- [流式输出](streaming.md)：Markdown 与卡片流控制器
- [媒体](media.md)：URL、本地文件、字节、输入流、时长与 SSRF 防护

## 运维与扩展

- [API 参考](reference.md)：公开门面、模型与低层方法列表
- [从 `oapi-sdk-java` 迁移](migration-from-oapi-sdk-java.md)：包名和依赖迁移清单
- [兼容性](compatibility.md)：Java、Maven、主 SDK 与包边界保证
- [故障排查](troubleshooting.md)：连接、策略、发送与媒体常见问题
- [测试](testing.md)：本地验证与可选集成测试

## 项目规范

- [贡献指南](../../CONTRIBUTING.zh.md)
- [安全策略](../../SECURITY.zh.md)
- [行为准则](../../CODE_OF_CONDUCT.zh.md)
- [变更记录（英文）](../../CHANGELOG.md)
- [发布手册](../../RELEASING.zh.md)

## 建议阅读路径

- 第一个机器人：快速开始 → 事件 → 发送消息 → 策略与安全
- 已有 HTTP 服务：快速开始 → Webhook → 策略与安全 → 测试
- AI/Agent 响应：快速开始 → 流式输出 → 媒体 → 发送消息
- 从主 SDK 迁移：迁移 → 兼容性 → 故障排查 → 测试

每个专题都提供简体中文与英文切换，发布产物同时包含源码与 Javadoc JAR。
