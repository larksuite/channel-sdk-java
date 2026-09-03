# Policy and safety pipeline

[Documentation index](README.md) | [简体中文](zh-CN/policy-and-safety.md) | English

Inbound platform delivery is at-least-once in practice and bot endpoints are exposed to user-controlled content. Before invoking the `message` handler, the Channel SDK applies a process-local safety pipeline. It reduces accidental duplicate work and provides access gates, but it does not replace application authorization, distributed idempotency, rate limiting, or content safety.

## Message pipeline order

For a normalized message, the pipeline performs:

1. stale-event check;
2. seen-event lookup;
3. group or direct-message policy evaluation;
4. best-effort process-local processing-lock acquisition;
5. optional per-chat queue and text batching;
6. `message` handler dispatch;
7. seen-event marking and lock release.

Stale and duplicate messages are dropped silently. Policy failures emit `reject`. Processing and handler failures emit `error` through the event bus. Build metrics for all three outcomes without logging full message bodies.

Card actions and comments use deduplication, a process-local lock, and scoped ordering. Reactions use only seen-event deduplication.

## Group policy

```java
LarkChannelOptions.PolicyConfig policy = new LarkChannelOptions.PolicyConfig();
policy.setGroupAllowlist("oc_support", "oc_operations");
policy.setRequireMention(true);
policy.setRespondToMentionAll(false);
```

For `group` and `topic_group` messages:

1. A non-empty group allowlist rejects chat IDs that are not listed.
2. An `@all` mention is accepted only when `respondToMentionAll=true`.
3. Otherwise, when `requireMention=true`, the message must directly mention the connected bot.

An empty group allowlist means all groups, not no groups. For production bots, prefer an explicit allowlist unless broad installation is an intentional product requirement. Even in an allowed group, authorize sensitive business operations against the actual sender and resource.

If `respondToMentionAll=true`, an `@all` message passes without a separate direct bot mention. Consider the notification and load impact before enabling it.

## Direct-message policy

Supported `dmMode` values are:

| Value | Behavior |
| --- | --- |
| `open` | Accept direct messages from any sender visible to the app. |
| `disabled` | Reject every direct message as `dm_disabled`. |
| `allowlist` | Accept only sender open IDs in `dmAllowlist`. |

```java
policy.setDmMode("allowlist");
policy.setDmAllowlist("ou_operator_1", "ou_operator_2");
```

Unknown values currently fall through as open. Validate this enum before building or updating policy so a typo cannot widen access.

## Rejection reasons

| `RejectReason` value | Trigger |
| --- | --- |
| `group_not_allowed` | Group/topic chat is outside a non-empty allowlist. |
| `sender_not_allowed` | Direct-message sender is outside the DM allowlist. |
| `no_mention` | Group policy requires a direct bot mention and none is present. |
| `dm_disabled` | Direct messages are disabled. |
| `mention_all_blocked` | The message uses `@all` while that behavior is disabled. |

Rejections are expected policy outcomes, not exceptions. Count them by this bounded enum and avoid logging the rejected content.

## Runtime updates

```java
LarkChannelOptions.PolicyConfig next = new LarkChannelOptions.PolicyConfig();
next.setGroupAllowlist("oc_new_group");
next.setDmMode("disabled");
next.setRequireMention(true);
next.setRespondToMentionAll(false);
channel.updatePolicy(next);
```

Despite the parameter name `partial` in the current Java signature, `updatePolicy` copies every policy field. A newly constructed `PolicyConfig` therefore resets unspecified fields to their defaults. Treat updates as complete replacements: load and validate the complete desired policy, then apply it atomically at the application configuration layer.

`getPolicy()` returns the mutable object currently used by the gate. Do not mutate it concurrently from arbitrary request threads. Serialize policy changes through one controlled configuration path and record an audit event without sensitive values.

## Stale-event filtering

Messages with a positive platform create time older than `staleMessageWindowMs` are dropped. The default is 30 minutes. Messages without a positive timestamp are not treated as stale.

Keep hosts time-synchronized. A very small window can drop legitimate delayed deliveries; a very large window can replay old user intent after outages. Choose a value based on the maximum safe delay for the application.

## Deduplication

The in-memory seen cache uses message or normalized action keys with:

- default TTL of 12 hours;
- access-ordered capacity of 5,000 entries;
- periodic expired-entry sweep every 5 minutes;
- namespace `channel:seen`.

When `ICache` is configured, marks are also written there and later checks can observe them across processes. External-cache write failure is tolerated and the process-local mark remains. The external check and later mark are separate operations, not an atomic distributed claim; two instances can process the same event concurrently.

Consequently, all business side effects must be idempotent. Use request/event IDs plus a database unique constraint or transactional idempotency table. A cache alone is not sufficient for financial, permission, notification, or irreversible operations.

`dedupMaxEntries <= 0` disables the capacity bound in the current preview and can lead to unbounded memory. Validate it as a positive bounded value. Likewise, enforce positive bounded TTL and sweep values before construction.

## Processing lock and ordering

The processing lock reduces concurrent handling of the same event key inside one Channel instance. Its default TTL is five minutes and it is always process-local. It is released after dispatch, while TTL provides recovery from an abandoned local lock. Business code must not treat it as an atomic mutex or an idempotency guarantee.

When `chatQueueEnabled=true`, messages from the same chat are serialized through a single per-chat pipeline. Card actions are ordered by chat; comments are ordered by file token. Different scopes can run independently.

This is not a distributed lock. Multi-instance deployments need business idempotency and, where strict ordering is essential, an application-owned partitioned queue or transactional sequence check.

## Message batching

With the per-chat queue enabled, nearby messages in one chat can be merged before one handler call. Defaults are:

- 600 ms debounce for accumulated content below 1,000 characters;
- 2,000 ms debounce once content reaches 1,000 characters;
- immediate flush at 8 messages or 4,000 accumulated characters.

Merged content joins non-empty message text with a blank line. The merged event uses metadata from the latest message, unions media resources and mentions, and ORs mention flags. All source message IDs are marked seen after dispatch.

Batching can combine messages from different senders in the same group because the scope is chat ID. Do not use a merged event for per-message authorization or assume `senderId` describes every merged fragment. Disable `chatQueueEnabled` for workflows that require exact one-event/one-handler semantics, or design the business protocol around the merged behavior.

Set batch values to positive, bounded ranges. Zero or negative delay causes immediate flush, while overly large limits increase latency and memory.

## Disabling controls

Setting `chatQueueEnabled=false` removes per-scope serialization and message batching, but does not disable stale checks, seen lookup, policy, or the local processing lock. Broad/empty allowlists, `dmMode=open`, `requireMention=false`, disabled SSRF guard, non-positive dedup capacity, or very large stale/dedup windows each weaken a different control. Review them separately; there is no single safe “disable safety” mode.

## Operational controls

The Channel pipeline should be combined with host controls:

- ingress request and event rate limits;
- downstream timeouts and circuit breaking;
- bounded executors and queues;
- business authorization on every protected operation;
- idempotency for all writes;
- metrics for accepted, rejected, duplicate/stale, failed, and processed latency;
- secret and raw-content redaction;
- graceful shutdown that stops ingress before disposing the channel.

Do not use raw event inclusion as a substitute for missing authorization data unless you explicitly validate and minimize the fields you consume.
