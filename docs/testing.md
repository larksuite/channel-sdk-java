# Testing

[Documentation index](README.md) | [简体中文](zh-CN/testing.md) | English

The repository separates deterministic local verification from credentialed platform integration. Local verification must not require an app secret or network access to a live tenant.

## Full local verification

```bash
./scripts/verify.sh
```

The workflow performs, in order:

1. documentation structure, link, and stale-reference checks;
2. `mvn clean verify` for compilation, unit tests, Javadocs, dependency analysis, and build-time package checks;
3. JAR package-isolation inspection;
4. local artifact installation;
5. compilation and verification of all example consumer modules.

Run this command before handing off a change. It may modify `target/` and the local Maven cache, but it must not deploy or publish artifacts.

## Focused commands

```bash
# Unit/build verification
mvn clean verify

# Documentation only
./scripts/verify-docs.sh

# Package isolation after package/verify
./scripts/verify-package.sh

# Consumer examples
mvn -DskipTests -Dmaven.javadoc.skip=true install
mvn -f examples/pom.xml clean verify
```

Do not document a fixed unit-test count; the suite is expected to grow.

## JDK matrix

The current release targets are JDK 8 and JDK 11. Select each JDK in the environment and run the full workflow:

```bash
java -version
mvn -version
./scripts/verify.sh
```

The Maven output must show the intended Java home. A pass on one JDK does not verify the other. JDK 17 and 21 results can be informational but do not update the compatibility matrix until adopted by the release process.

Before general availability, the project must explicitly decide whether JDK 17 and 21 are release targets and run the same full workflow on every adopted target. Until that decision and evidence exist, they remain unverified rather than implicitly supported.

## Integration tests

Real-platform integration tests are skipped unless explicitly enabled. Use a dedicated test application and tenant with minimum permissions.

```bash
export LARK_CHANNEL_IT_ENABLED=true
export LARK_CHANNEL_IT_APP_ID='test-app-id-from-secret-manager'
export LARK_CHANNEL_IT_APP_SECRET='test-app-secret-from-secret-manager'

mvn -Dtest=TestLarkChannelIntegration test
```

To include the WebSocket integration case:

```bash
export LARK_CHANNEL_IT_ENABLE_WS=true
mvn -Dtest=TestLarkChannelIntegration test
```

Do not commit `.env` files or print environment values in CI logs. Clear shell variables and revoke temporary credentials after use. Integration tests can create platform traffic; run them only in an authorized test environment.

## Channel E2E

`TestChannelE2E` is the credentialed end-to-end suite for the extracted Channel SDK. It exercises the public facade, selected raw SDK calls, media upload/download, streaming, and the event listener boundary. It is skipped unless the process environment explicitly enables it; values in `.env` cannot enable live traffic.

Copy `.env.example` to an ignored `.env` file and populate the approved test application's values. The runner reads it as data (it never sources it as shell code), applies process-environment overrides, and rejects missing, empty, symlinked, or repository-escaping media paths. Keep secrets only in the local file or a secret manager.

```bash
# Validates credentials/identifiers, fixture paths, and the planned case set. No platform request is sent.
LARK_CHANNEL_E2E_ENABLED=true LARK_CHANNEL_E2E_DRY_RUN=true \
  mvn -Dtest=TestChannelE2E test

# Runs the automatic live cases. This creates messages, uploads media, reacts, edits, and streams in the authorized tenant.
LARK_CHANNEL_E2E_ENABLED=true \
  mvn -Dtest=TestChannelE2E test
```

The automatic set covers connection and bot identity, chat/message lookup, text/Markdown/post/card sending, group mention, supported media formats, sharing, replies, edit/card update, add/remove reaction, direct image/file upload and download, and Markdown/card streaming. Sticker coverage is skipped unless `CHANNEL_E2E_STICKER_FILE_KEY` is supplied.

The raw group message-list case requires that the bot belongs to the configured group and that the application version deployed to the test tenant includes `im:message.group_msg`; a platform response code `230027` is reported as a permission failure rather than retried.

Event and policy cases require a real user action after the runner has connected. The runner sends a trace-marked target message/card and writes the exact instructions into its report. Start them only in the approved test tenant:

```bash
LARK_CHANNEL_E2E_ENABLED=true \
LARK_CHANNEL_E2E_MANUAL=true \
LARK_CHANNEL_E2E_ENABLE_POLICY=true \
CHANNEL_E2E_WAIT_SECONDS=180 \
  mvn -Dtest=TestChannelE2E test
```

All enabled manual expectations share the `CHANNEL_E2E_WAIT_SECONDS` window, so missing actions are reported together rather than waiting once per event.

To isolate a document-comment delivery problem, use the focused mode below. It opens only the Java WebSocket listener and reports three distinct outcomes: the configured document was observed, a comment for a different document was observed, or this listener did not observe an event. Do not run another app long connection while using this mode.

```bash
LARK_CHANNEL_E2E_ENABLED=true \
LARK_CHANNEL_E2E_FOCUSED_COMMENT=true \
CHANNEL_E2E_WAIT_SECONDS=45 \
  mvn -Dtest=TestChannelE2E test
```

`event.bot_added` changes group membership and is intentionally excluded. It additionally requires `LARK_CHANNEL_E2E_ENABLE_BOT_ADDED=true` and `CHANNEL_E2E_BOT_ADDED_CHAT_ID`, a dedicated group that does not already contain the bot. Do not use a production group for that case.

Each enabled run writes a redacted Markdown result to `target/e2e/`. The report records pass/fail/skip status, but masks configured identifiers and redacts the app secret, document token, target IDs, and sticker key from errors. Copy only that redacted result into external test reports; never paste `.env`, raw events, message IDs, or credentials.

## What to test for each change

| Change area | Minimum evidence |
| --- | --- |
| Configuration | Defaults, custom values, null/invalid application boundary behavior |
| Events | Normalized fields, raw opt-in, handler replacement, error/reject path |
| Policy | Group/DM matrix, `@all`, runtime full replacement |
| Safety | stale, dedup TTL/capacity, queue, batch boundaries, dispose |
| Sending | each affected kind, target routing, reply fallback, chunk result |
| Media | source types, path traversal/symlink, SSRF/DNS/redirect/TLS, size limits |
| Streaming | initial/update/final/failure, throttle thresholds, ordering |
| Packaging | source/binary/Javadoc artifacts and no copied main-SDK classes |
| Documentation | bilingual pair, links, commands, version/default consistency |

Security tests must use synthetic data and local test servers. Do not embed real access tokens, personal data, or callback payloads in fixtures.

## Example projects as contract tests

The example reactor deliberately declares only `channel-sdk`. A successful build proves the public artifact can compile as an external consumer and that transitive main-SDK types needed by examples resolve correctly.

Keep examples small and compileable. They are not a substitute for unit tests and should not contact live APIs during `verify`.

## Failure triage

When verification fails:

1. identify the first failing stage rather than rerunning with skipped checks;
2. reproduce with the focused command;
3. inspect the relevant dependency/JDK version;
4. fix the cause and rerun the focused test;
5. finish with the full workflow on both target JDKs.

Never make the default verification depend on real credentials, timing sleeps, or an unrestricted public network.
