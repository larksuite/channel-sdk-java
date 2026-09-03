# 贡献指南

简体中文 | [English](CONTRIBUTING.md)

感谢参与改进 Lark Channel SDK for Java。所有改动都必须保持本文定义的产物边界和双语用户体验。

## 开始之前

报告 Bug 时，请先用合成数据准备最小复现，并确认问题属于 Channel SDK 还是底层 `oapi-sdk`。新增能力或公开 API 变更，应在实现前与维护者讨论使用场景和兼容影响。

Issue 和代码评审中禁止包含漏洞细节、应用凭证、租户数据、用户内容、原始事件或私有日志。疑似安全问题请遵循 [SECURITY.zh.md](SECURITY.zh.md)。

## 开发环境

要求：

- Git
- JDK 8、11、17 和 21（最终兼容验证）
- Maven 3.6.3 及以上
- 可执行仓库脚本的 POSIX Shell

确认实际工具：

```bash
java -version
mvn -version
git status --short
```

默认构建不需要真实飞书/Lark 凭证。

Clone 仓库并建立干净基线：

```bash
git clone https://github.com/larksuite/channel-sdk-java.git
cd channel-sdk-java
./scripts/verify.sh
```

## 仓库边界

- Channel 公开代码位于 `com.lark.channel`。
- 生成的 OpenAPI 和主 SDK 基础设施继续由 `oapi-sdk` 依赖提供。
- 不能把 `com.lark.oapi.core`、`event`、`service` 或其他主 SDK 实现复制进本仓库。
- 不能在独立产物中重新加入主 SDK 的旧 Channel 类。
- 除非现有门面和 `getRawClient()` 都无法满足，不要新增公开 API。
- 生产源码目标是 Java 8 源码与字节码，不能使用更新语言/API 特性。

## 保持改动聚焦

1. 从当前 `main` 分支创建聚焦的工作分支。
2. 修改行为时尽量先增加或更新测试。
3. 只实现已确认范围所需的最小改动。
4. 只清理由本次改动造成的无用 import 或代码。
5. 公开行为变化同步更新 Javadoc 和用户文档。
6. 迭代时运行分项测试，最后执行完整验证。

新增 Java 源文件时应包含以下文件头：

```java
// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT
```

不要在一个评审中混合无关格式化、依赖升级、重构和行为变更。

## 文档要求

用户文档必须在同一次贡献中同步修改中英文：

- 英文专题：`docs/<topic>.md`
- 中文专题：`docs/zh-CN/<topic>.md`
- 根入口：`README.md` 与 `README.zh.md`
- 示例入口：`examples/README.md` 与 `examples/README.zh.md`

执行：

```bash
./scripts/verify-docs.sh
```

示例必须兼容 Java 8，使用 `com.lark.channel`，从环境/密钥系统读取凭证，并处理异步失败。不能虚构尚未发布的 Maven、Javadoc、CI、支持或安全链接。

## 代码与测试要求

- 示例中展示的外部输入必须在业务边界校验。
- 严禁硬编码或记录凭证、Token、断言、私钥、原始用户内容或媒体字节。
- 重试总次数保持不超过三次，避免多层重试。
- 通过主 SDK 或宿主配置为外部调用设置超时。
- 共享状态线程安全，执行器和队列有界。
- 关闭调用方持有的文件/流，并在退出时释放 Channel 资源。
- 重复平台投递下保持业务幂等。
- URL、文件系统、Webhook、鉴权或原始事件处理变更必须增加安全测试。

默认测试必须确定且不访问真实租户。带凭证集成测试需要维护者明确授权和专用测试应用。

## 验证

请求评审前在本地执行验证流程，CI 会在 JDK 8、11、17 和 21 上重复验证：

```bash
./scripts/verify.sh
```

流程校验文档、Maven 测试/Javadoc/依赖、包隔离、本地安装与全部消费者示例。评审说明中应列出 JDK/Maven 版本和命令结果。不能跳过失败阶段获取绿色结果。

## Commit 与评审说明

Commit 标题使用简短祈使句，保持可评审。评审说明至少包含：

- 问题和用户目标；
- 实现与兼容决策；
- 公开 API/默认值/行为变化；
- 安全和稳定性考虑；
- 测试命令与环境；
- 更新的文档；
- 适用时的灰度与回滚。

维护者可能在接受前要求 API 调整、补齐双语文档或增加兼容证据。

## 贡献者许可协议

外部贡献者必须完成 ByteDance Contributor License Agreement 后才能合入。PR 创建后，CLA 检查会显示每位贡献者是否已经签署，并在需要时提供签署说明。CLA 检查通过前不能合并 PR。

主动提交贡献也表示同意在适用 CLA 约束下，按仓库 [MIT License](LICENSE) 提供该贡献。

## 行为准则

所有项目互动必须遵守[行为准则](CODE_OF_CONDUCT.zh.md)。
