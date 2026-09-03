# 从 `oapi-sdk-java` 迁移

[文档索引](README.md) | 简体中文 | [English](../migration-from-oapi-sdk-java.md)

独立 Channel SDK 将高层会话能力从生成式 OpenAPI SDK 中拆分出来，使用新的 Java 命名空间，同时继续通过 Maven 依赖主 SDK。本文适用于原先直接使用 `oapi-sdk-java` 中 Channel 类，或把这些类复制到业务中的应用。

## 迁移结果

完成后：

- Channel API 来自 `com.larksuite.oapi:channel-sdk` 和 `com.lark.channel.*`。
- 生成的 OpenAPI、鉴权、HTTP、事件分发与 WebSocket 仍来自 `com.larksuite.oapi:oapi-sdk` 和 `com.lark.oapi.*`。
- Channel JAR 不包含旧 `com/lark/oapi/channel` 类，也不复制主 SDK 实现包。
- 应用需要针对独立预览版重新验证行为，而不是只完成包名替换。

## 1. 建立迁移基线

修改依赖前记录：

- 当前 `oapi-sdk` 版本和依赖树；
- JDK 与 Maven 版本；
- 入站方式和订阅事件；
- Channel 配置及业务依赖的默认值；
- 原始 OpenAPI 调用；
- 发送类型、流式、媒体来源和外层重试；
- 集成与退出测试。

先完整构建运行一次现有应用，并保留最后一个可用产物及依赖锁定信息用于回滚。

## 2. 添加独立产物

将仅依赖主 SDK 的配置替换为独立 Channel 产物：

```diff
 <dependencies>
   <dependency>
     <groupId>com.larksuite.oapi</groupId>
-    <artifactId>oapi-sdk</artifactId>
-    <version>your-verified-main-sdk-version</version>
+    <artifactId>channel-sdk</artifactId>
+    <version>1.0.0-beta.1</version>
   </dependency>
 </dependencies>
```

等价最终 XML：

```xml
<dependency>
    <groupId>com.larksuite.oapi</groupId>
    <artifactId>channel-sdk</artifactId>
    <version>1.0.0-beta.1</version>
</dependency>
```

该基线中 `channel-sdk` 传递依赖 `oapi-sdk:2.8.5`。业务源码如果直接 import 主 SDK 类型，可以为清晰保留显式 `oapi-sdk` 依赖，但最终版本必须收敛到[已验证组合](compatibility.md)。

检查：

```bash
mvn dependency:tree -Dincludes=com.larksuite.oapi
mvn dependency:analyze
```

应显式解决版本仲裁，不能排除主 SDK 后把它的类复制进业务。

## 3. 修改 Channel import

包名映射是机械式的：

| 修改前 | 修改后 |
| --- | --- |
| `com.lark.oapi.channel` | `com.lark.channel` |
| `com.lark.oapi.channel.config` | `com.lark.channel.config` |
| `com.lark.oapi.channel.model` | `com.lark.channel.model` |
| `com.lark.oapi.channel.exception` | `com.lark.channel.exception` |

例如：

```diff
-import com.lark.oapi.channel.LarkChannel;
-import com.lark.oapi.channel.config.LarkChannelOptions;
+import com.lark.channel.LarkChannel;
+import com.lark.channel.config.LarkChannelOptions;
```

主 SDK import 不变：

```java
import com.lark.oapi.Client;
import com.lark.oapi.core.cache.ICache;
import com.lark.oapi.event.EventDispatcher;
```

除源码 import 外，还要搜索反射、DI 配置、序列化元数据、测试和文档中的全限定字符串：

```bash
rg 'com\.lark\.oapi\.channel|com/lark/oapi/channel' .
```

生成代码、shaded JAR 规则、native-image 配置和 ProGuard/relocation 规则也需要检查。

只有在精确主 SDK 版本确实提供旧包时，才可以把新旧包短期放在同一测试 classpath 做迁移对比。不要长期用两套 Channel 实例消费同一应用/事件流，否则重复投递、连接竞争和重复发送都会更难推理。

抽取基线的目标是在改变代码归属、命名空间和依赖打包的同时保持高层行为，但这不是无条件二进制兼容声明。仍必须完成下述检查，因为业务假设、主 SDK 仲裁和预览版修复都可能改变实际表现。

## 4. 重新检查构建与生命周期

独立门面通过以下方式创建：

```java
LarkChannel channel = LarkChannelFactory.createLarkChannel(
        LarkChannelOptions.newBuilder(appId, appSecret)
                .transport("websocket")
                .build());
```

然后验证：

- 入站流量前完成 handler 注册；
- 消息处理前 `connect()` 已成功；
- Webhook 宿主也调用 `connect()` 获取机器人身份；
- 异步 send/stream Future 有错误处理；
- 最终退出只调用一次 `disconnect()`；
- 组件重启时创建新的 Channel 实例。

独立预览版异步方法使用 `CompletableFuture`，常用操作提供 `*Sync` 同步版本。不能忽略失败 Future。

## 5. 重新检查事件

标准事件名是 `message`、`reaction`、`botAdded`、`cardAction`、`comment`、`reject`、`error`、`reconnecting` 和 `reconnected`。卡片事件使用驼峰 `cardAction`。

每个事件名只保存一个 handler，后注册会覆盖之前的。如果旧代码依赖多个监听器，需要在业务中增加组合 handler。

原始事件默认关闭。应把旧 raw 字段读取改为标准 getter；确有字段缺口时显式配置：

```java
.includeRawEvent(true)
```

同时更新日志和留存控制。

## 6. 重新检查策略与合并

会显著影响行为的默认值包括：

- 群白名单为空时允许所有群；
- 群中必须直接 @ 机器人；
- `@所有人` 被拒绝；
- 私聊开放；
- 超过 30 分钟的旧消息被丢弃；
- 按会话队列和消息合并开启；
- 已处理事件 TTL 12 小时，本地容量 5,000。

请阅读[策略与安全](policy-and-safety.md)，尤其注意同一群中不同发送者消息可能被合并。`updatePolicy` 会复制所有字段，尽管当前参数名像是 partial，也应视为完整替换。

## 7. 重新检查出站行为

不能只验证编译，应逐个测试使用中的类型：

- 3,500 字符拆分边界附近的文本和 Markdown；
- 回复与 `replyInThread`；
- @ 与原始 post；
- 卡片发送/更新；
- URL、本地文件、字节和输入流媒体；
- 音视频时长与封面 key；
- 流式完成与中断；
- 表情回复和撤回辅助方法；
- 原始 Client 逃生口调用。

业务仍可通过传递解析的主 SDK 或显式收敛依赖使用生成 OpenAPI；独立门面不会移除这项能力。

远程媒体包含 SSRF 检查和 50 MiB 下载上限。本地路径应配置 `allowedFileDirs`；字节、流和本地文件需要业务大小限制。不能为了保持不安全的旧行为而放宽控制。

## 8. 验证包隔离

构建消费者并检查全部解析产物：

```bash
mvn clean verify
mvn dependency:tree
jar tf target/your-app.jar | rg 'com/lark/(channel|oapi/channel)/'
```

预期归属：

- `com/lark/channel/**` 属于 `channel-sdk`；
- `com/lark/oapi/**` 属于 `oapi-sdk`；
- 独立 Channel 产物不能打包 `com/lark/oapi/channel/**`。

业务如果生成 uber-JAR，需要确认 shading 没有复制主 SDK 类，也没有静默保留两套 Channel 命名空间。

## 9. 测试与灰度

仓库至少执行：

```bash
./scripts/verify.sh
```

业务应用应执行单测、一次 WebSocket 或 Webhook 冒烟、发送/回复、策略拒绝、媒体安全和优雅退出测试。灰度期间对比事件量、发送延迟、错误码、拒绝原因和重复业务动作。

当前产物是 Beta 版本，应先在非生产租户和有限机器人/群范围使用。观察完成前保留旧产物的部署能力。

## 回滚

回滚应恢复完整的已知可用依赖与 import，不能混用新旧 Channel 实现：

1. 停止或排空已迁移实例；
2. 重新部署旧应用产物；
3. 恢复其固定主 SDK 版本和旧 Channel 源码/依赖；
4. 确认运行时只有一套 Channel 命名空间；
5. 检查事件和发送健康后再恢复流量。

某个主 SDK 版本是否仍带旧 Channel 类，必须针对该精确版本验证，不能依赖无限期兼容窗口。

## 完成清单

- [ ] 只选择一个 `channel-sdk` 版本
- [ ] 只选择一个兼容 `oapi-sdk` 版本
- [ ] 业务代码/配置不再引用 `com.lark.oapi.channel`
- [ ] 已测试 WebSocket 或 Webhook 生命周期
- [ ] 已测试所有使用的事件名和模型字段
- [ ] 已接受策略与消息合并行为
- [ ] 每个异步失败都可观察
- [ ] 已配置媒体信任边界
- [ ] JAR/classpath 不含重复 SDK 实现包
- [ ] 重复投递下业务写操作仍幂等
- [ ] 已验证回滚产物与步骤
