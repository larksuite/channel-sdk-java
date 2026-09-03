# 策略与安全流水线

[文档索引](README.md) | 简体中文 | [English](../policy-and-safety.md)

平台事件在实践中属于至少一次交付，机器人端点还会接收用户可控内容。调用 `message` handler 前，Channel SDK 会执行进程内安全流水线，以减少意外重复处理并提供访问门禁。但它不能替代业务鉴权、分布式幂等、限流和内容安全。

## 消息流水线顺序

对标准化消息依次执行：

1. 过期事件检查；
2. 已处理事件查询；
3. 群聊或私聊策略判断；
4. 尝试获取进程内处理锁；
5. 可选的按会话队列与文本合并；
6. 调用 `message` handler；
7. 标记已处理并释放锁。

过期和重复消息会静默丢弃；策略失败产生 `reject`；处理和 handler 失败通过事件总线产生 `error`。三类结果都应有指标，但不能记录完整消息体。

卡片回调和评论使用去重、进程内锁及作用域内顺序；表情回复只使用已处理事件去重。

## 群聊策略

```java
LarkChannelOptions.PolicyConfig policy = new LarkChannelOptions.PolicyConfig();
policy.setGroupAllowlist("oc_support", "oc_operations");
policy.setRequireMention(true);
policy.setRespondToMentionAll(false);
```

对 `group` 和 `topic_group` 消息：

1. 群白名单非空时，拒绝不在其中的 chat ID。
2. `@所有人` 只有在 `respondToMentionAll=true` 时通过。
3. 其他情况下，如果 `requireMention=true`，消息必须直接 @ 已连接机器人。

空群白名单表示允许所有群，而不是禁止所有群。生产机器人除非产品明确需要广泛安装，否则建议使用显式白名单。即使群被允许，敏感业务操作仍需根据真实发送者和资源做鉴权。

开启 `respondToMentionAll` 后，`@所有人` 无需再直接 @ 机器人即可通过。启用前应评估通知和负载影响。

## 私聊策略

支持的 `dmMode`：

| 值 | 行为 |
| --- | --- |
| `open` | 接受应用可见的任意发送者私聊。 |
| `disabled` | 所有私聊以 `dm_disabled` 拒绝。 |
| `allowlist` | 只接受 `dmAllowlist` 中的发送者 open ID。 |

```java
policy.setDmMode("allowlist");
policy.setDmAllowlist("ou_operator_1", "ou_operator_2");
```

未知值当前会按开放处理。构建或更新策略前必须校验枚举，防止拼写错误扩大访问范围。

## 拒绝原因

| `RejectReason` 值 | 触发条件 |
| --- | --- |
| `group_not_allowed` | 群/话题群不在非空白名单中。 |
| `sender_not_allowed` | 私聊发送者不在 DM 白名单中。 |
| `no_mention` | 群策略要求直接 @ 机器人，但消息没有。 |
| `dm_disabled` | 私聊已关闭。 |
| `mention_all_blocked` | 消息使用 `@所有人`，但策略未开启。 |

拒绝属于预期策略结果，不是异常。只按这个有限枚举计数，避免记录被拒绝的内容。

## 运行时更新

```java
LarkChannelOptions.PolicyConfig next = new LarkChannelOptions.PolicyConfig();
next.setGroupAllowlist("oc_new_group");
next.setDmMode("disabled");
next.setRequireMention(true);
next.setRespondToMentionAll(false);
channel.updatePolicy(next);
```

虽然当前 Java 签名中的参数名是 `partial`，`updatePolicy` 实际会复制所有策略字段。新建 `PolicyConfig` 中未设置的字段会恢复默认值。应把更新视为完整替换：加载并校验完整目标策略，再由业务配置层统一应用。

`getPolicy()` 返回门禁正在使用的可变对象。不要从任意请求线程并发修改，应通过一个受控配置通道串行更新，并记录不含敏感值的审计事件。

## 过期事件过滤

平台创建时间为正、且早于 `staleMessageWindowMs` 的消息会被丢弃，默认窗口为 30 分钟。没有正时间戳的消息不会按过期处理。

服务器需要保持时间同步。窗口过小会丢掉合法延迟消息，过大会在故障恢复后重放旧用户意图。应根据业务可接受的最大延迟设置。

## 去重

进程内已处理缓存使用消息或标准化动作 key，默认：

- TTL 12 小时；
- LRU 风格容量 5,000 条；
- 每 5 分钟清理过期项；
- 命名空间 `channel:seen`。

配置 `ICache` 后，标记也会写入外部缓存，后续检查可跨进程发现。外部缓存写失败会被容忍，进程内标记仍保留。外部查询与之后的标记是两个独立操作，不是原子分布式抢占，两个实例仍可能并发处理同一事件。

因此所有业务副作用都必须幂等。建议用请求/事件 ID 配合数据库唯一约束或事务幂等表。缓存不足以保护资金、权限、通知或不可逆操作。

当前预览版中，`dedupMaxEntries <= 0` 会关闭容量边界，可能导致内存无界增长。业务必须校验其为有合理上限的正数，TTL 和清理间隔同样如此。

## 处理锁与顺序

处理锁用于降低同一 Channel 实例并发处理相同事件 key 的概率，默认 TTL 为 5 分钟，始终只在进程内。分发结束会释放，TTL 用于恢复遗留本地锁。业务不能把它当作原子互斥锁或幂等保证。

`chatQueueEnabled=true` 时，同一 chat 的消息通过单个会话流水线串行；卡片动作按 chat 排序，评论按 file token 排序，不同作用域可并行。

这不是分布式锁。多实例部署必须保留业务幂等；严格顺序场景还需要业务自有的分区队列或事务序列检查。

## 消息合并

开启按会话队列后，同一 chat 中时间接近的消息会在一次 handler 调用前合并。默认：

- 累计内容小于 1,000 字符时，等待 600 毫秒；
- 达到 1,000 字符后，等待 2,000 毫秒；
- 达到 8 条或累计 4,000 字符立即刷新。

非空内容之间使用空行连接。合并事件使用最后一条消息的元数据，合并媒体资源和 @ 列表，并对 @ 标记取 OR。分发后会标记所有来源消息 ID。

因为作用域是 chat ID，同一群内不同发送者的消息也可能合并。不能使用合并事件做逐消息鉴权，也不能假设 `senderId` 描述每段合并内容。要求一事件对应一次 handler 的流程，应关闭 `chatQueueEnabled`，或按合并语义设计业务协议。

批处理配置应为有合理范围的正数。零或负 delay 会立即刷新，过大限制会增加延迟和内存占用。

## 关闭控制的风险

`chatQueueEnabled=false` 会移除作用域内串行和消息合并，但不会关闭过期检查、已处理查询、策略或本地处理锁。宽泛/空白名单、`dmMode=open`、`requireMention=false`、关闭 SSRF、非正去重容量、过大的过期/去重窗口分别削弱不同控制。必须逐项评审，不存在一个安全的“关闭全部安全”模式。

## 运行控制

Channel 流水线还需要宿主控制配合：

- 入站请求与事件限流；
- 下游超时与熔断；
- 有界执行器和队列；
- 每个受保护操作都做业务鉴权；
- 所有写操作幂等；
- 接收、拒绝、重复/过期、失败和处理延迟指标；
- 密钥与原始内容脱敏；
- 先停止入口、再销毁 Channel 的优雅退出。

除非明确校验并最小化使用字段，否则不要通过开启原始事件来替代缺失的鉴权数据。
