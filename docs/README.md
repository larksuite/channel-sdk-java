# Documentation

[简体中文](zh-CN/README.md) | English

This documentation describes the `1.0.0-beta.1` public beta. Start with the quick start, then use the topic guides according to the part of the channel you are integrating.

## Get started

- [Quick start](quickstart.md): build, configure, connect, receive, and reply
- [Configuration](configuration.md): credentials, transport, domain, client assertion, safety, and outbound options
- [Examples](../examples/README.md): runnable consumer-shaped projects

## Receive events

- [Events](events.md): normalized event names, message fields, subscriptions, and raw-event opt-in
- [Webhook](webhook.md): create and host the SDK dispatcher securely
- [Policy and safety](policy-and-safety.md): allowlists, mention rules, deduplication, ordering, and batching

## Send responses

- [Sending messages](sending-messages.md): targets, content kinds, replies, mentions, retry, and errors
- [Streaming](streaming.md): Markdown and card streaming controllers
- [Media](media.md): URLs, local files, byte arrays, streams, duration handling, and SSRF controls

## Operate and extend

- [API reference](reference.md): public facade, model, and low-level method map
- [Migration from `oapi-sdk-java`](migration-from-oapi-sdk-java.md): package and dependency migration checklist
- [Compatibility](compatibility.md): Java, Maven, main SDK, and package guarantees
- [Troubleshooting](troubleshooting.md): common connection, policy, send, and media failures
- [Testing](testing.md): local verification and opt-in integration tests

## Project policies

- [Contributing](../CONTRIBUTING.md)
- [Security](../SECURITY.md)
- [Code of Conduct](../CODE_OF_CONDUCT.md)
- [Changelog](../CHANGELOG.md)

## Suggested reading paths

- First bot: Quick start → Events → Sending messages → Policy and safety
- Existing HTTP service: Quick start → Webhook → Policy and safety → Testing
- AI/Agent response: Quick start → Streaming → Media → Sending messages
- Main-SDK migration: Migration → Compatibility → Troubleshooting → Testing

Each topic links to its Simplified Chinese counterpart. Release artifacts include sources and Javadoc JARs.
