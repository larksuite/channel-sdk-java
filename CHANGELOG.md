# Changelog

All notable user-visible changes to this project will be documented in this file.

The project is currently in beta. It has not published a stable release, and the `Unreleased` section may contain changes planned for a future version.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Stable releases are expected to follow semantic versioning once the public release policy is finalized.

## [Unreleased]

## [1.0.0-beta.1] - 2026-08-19

### Added

- Standalone Maven artifact `com.larksuite.oapi:channel-sdk`.
- New public Java namespace rooted at `com.lark.channel`.
- WebSocket and Webhook inbound transports with a shared normalized event layer.
- Inbound policy, stale-event filtering, deduplication, per-chat ordering, and message batching.
- Text, Markdown, post, card, media, share, sticker, reply, and thread sending.
- Markdown and interactive-card streaming.
- Media URL SSRF protection and local file directory controls.
- Main SDK raw Client and WebSocket Client escape hatches.
- External-consumer examples for echo, Webhook wiring, streaming, and raw OpenAPI calls.
- English and Simplified Chinese topic documentation, governance files, and automated documentation checks.

### Changed

- Extracted Channel-owned code from the `larksuite/oapi-sdk-java` baseline.
- Channel packages moved from `com.lark.oapi.channel` to `com.lark.channel`.
- Main SDK classes are consumed through `com.larksuite.oapi:oapi-sdk:2.8.5` instead of copied into the Channel artifact.
- Raw event inclusion uses `includeRawEvent(...)`; the first-preview name `includeRawInMessage(...)` remains deprecated.

### Security

- Remote media downloads validate schemes, DNS addresses, redirects, TLS hostnames, timeouts, and a 50 MiB response limit.
- Local media paths resolve real paths, reject sensitive POSIX system trees, and support allowed-directory restriction.
- Documentation requires secret-manager credentials, verified Webhook dispatching, bounded uploads, and idempotent business writes.

[Unreleased]: https://github.com/larksuite/channel-sdk-java/compare/v1.0.0-beta.1...HEAD
[1.0.0-beta.1]: https://github.com/larksuite/channel-sdk-java/releases/tag/v1.0.0-beta.1
