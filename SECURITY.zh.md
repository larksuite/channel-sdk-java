# 安全策略

简体中文 | [English](SECURITY.md)

安全由 Channel SDK、底层主 SDK、飞书/Lark 平台和宿主应用共同负责。

## 支持版本

| 版本 | 状态 |
| --- | --- |
| `1.0.0-beta.1` | 受支持的 Beta 版本 |
| 更早预览、复制或 Fork | 本仓库不支持 |

## 私下报告漏洞

禁止在公开 Issue、Discussion、PR 或聊天中发布漏洞细节。请通过 ByteDance [安全中心](https://security.bytedance.com/src)或[漏洞报告邮箱](mailto:src@bytedance.com)私下通知安全团队。

只提供必要信息：

- 受影响的 Channel 和主 SDK 版本；
- 影响和攻击前提；
- 使用合成数据的最小复现；
- 已移除密钥的相关配置；
- 已知时的缓解建议；
- 维护者可安全联系你的方式。

不要发送：

- 应用凭证或访问令牌；
- 客户端断言、私钥或校验/加密值；
- 租户/用户个人数据、原始事件、完整日志或真实攻击流量。

凭证已泄露时，先撤销或轮换，再报告事件。

安全团队会通过私有报告渠道协调验证与披露。在修复或约定披露计划完成前，请勿公开漏洞。修复时间取决于漏洞严重程度和影响范围。

## 范围示例

- Channel 代码导致的鉴权或凭证泄露；
- Channel 集成中的 Webhook 校验/解密绕过；
- 媒体处理中的 SSRF、DNS rebinding、重定向、TLS 或本地路径绕过；
- Channel 自有解析中的不安全反序列化或注入；
- 标准化或路由导致的跨租户数据暴露；
- 加载非预期 Channel/主 SDK 类的包或依赖混淆；
- Channel 自有缓存、队列、解析器或上传路径无界导致的拒绝服务。

平台、主 SDK 或宿主框架漏洞可能应交给对应维护者；无法判断归属时仍应先私下报告。

## 宿主应用责任

SDK 无法覆盖完整业务边界。宿主必须：

- 使用托管密钥系统保存 App Secret、Token、断言、私钥、Verification Token 和 Encrypt Key；
- 校验所有外部目标 ID、策略值、域名、路径、大小及卡片/富文本数据；
- 对每个受保护业务操作和出站目标鉴权；
- 在重复投递下保证写操作幂等；
- 设置 HTTP/body/并发限流和下游超时；
- 保持 SSRF 防护开启，使用严格媒体主机/目录白名单；
- 限制本地文件、字节、输入流、下载和生成内容；
- 不记录原始事件、消息内容、凭证、个人数据或 Base64 媒体；
- 使用带校验的 Webhook 分发器，禁止上线 `doWithoutValidation`；
- 退出时先停止入口，再调用 `disconnect()`；
- 监控错误、拒绝原因、延迟、资源占用和异常发送量。

参见[媒体](docs/zh-CN/media.md)、[Webhook](docs/zh-CN/webhook.md)和[策略与安全](docs/zh-CN/policy-and-safety.md)。

## 凭证处理

严禁在 Java、POM、脚本、示例、测试夹具、Shell 历史或文档中硬编码凭证。本地示例可以使用环境变量，生产环境优先使用托管密钥注入。日志不能包含 Authorization Header、可能嵌入密钥的完整错误响应、私钥材料或原始回调体。

应用权限应最小化，并隔离开发/测试/生产凭证。人员或环境变化后轮换凭证，疑似泄露时立即轮换。

## 媒体边界

远程媒体 SSRF 防护默认开启。主机白名单会绕过地址拦截，因此必须经过安全评审且主机由自身基础设施控制。本地路径必须位于严格 `allowedFileDirs` 下，禁止用户选择任意服务器路径。宿主必须限制本地文件、字节和流的大小，因为预览版只对远程 URL 下载内置上限。

## 依赖与发布完整性

只从 Maven Central 获取产物，并核对 group、artifact、version 和签名。保持单一收敛 `oapi-sdk` 版本。Channel JAR 不应包含复制的主 SDK 实现或旧 Channel 类。

## 漏洞奖励

ByteDance 漏洞奖励计划详见 [ByteDance 安全响应中心](https://src.bytedance.com/home)。
