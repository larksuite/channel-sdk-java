# Security policy

[简体中文](SECURITY.zh.md) | English

Security is a shared responsibility between the Channel SDK, the underlying main SDK, the Lark/Feishu platform, and the host application.

## Supported versions

| Version | Status |
| --- | --- |
| `1.0.0-beta.1` | Supported beta |
| Older previews, copies, or forks | Not supported by this repository |

## Report a vulnerability privately

Do not open a public issue, discussion, pull request, or chat thread containing vulnerability details. Notify the ByteDance security team through our [security center](https://security.bytedance.com/src) or [vulnerability reporting email](mailto:src@bytedance.com).

Include only what is needed:

- affected Channel and main-SDK versions;
- impact and attack prerequisites;
- minimal reproduction using synthetic data;
- relevant configuration with secrets removed;
- suggested mitigation, if known;
- a safe way for maintainers to contact you.

Do not send:

- application credentials or access tokens;
- client assertions, private keys, or verification/encryption values;
- tenant/user personal data, raw events, full logs, or live exploit traffic.

If a credential was exposed, revoke or rotate it first, then report the incident.

The security team will coordinate validation and disclosure through the private reporting channel. Do not disclose the issue publicly until a fix or agreed disclosure plan is available. Remediation time depends on severity and impact.

## In scope

Examples include:

- authentication or credential exposure caused by Channel code;
- Webhook verification/decryption bypass in Channel integration;
- SSRF, DNS rebinding, redirect, TLS, or local-path bypass in media handling;
- unsafe deserialization or injection in Channel-owned parsing;
- cross-tenant data exposure caused by normalization or routing;
- package/dependency confusion that loads unintended Channel or main-SDK classes;
- denial of service caused by an unbounded Channel-owned cache, queue, parser, or upload path.

Platform vulnerabilities, main-SDK vulnerabilities, and host-framework vulnerabilities may belong to their respective maintainers, but report privately if the correct owner is uncertain.

## Host application responsibilities

The SDK cannot enforce the entire application boundary. Hosts must:

- store app secrets, tokens, assertions, private keys, Verification Tokens, and Encrypt Keys in managed secret storage;
- validate all external target IDs, policy values, domains, paths, sizes, and card/post data;
- authorize every protected business operation and outbound destination;
- make writes idempotent under duplicate delivery;
- apply HTTP/body/concurrency rate limits and downstream timeouts;
- keep SSRF protection enabled and use narrow media host/directory allowlists;
- limit local files, byte arrays, streams, downloads, and generated output;
- avoid logging raw events, message content, credentials, personal data, or Base64 media;
- use the verified Webhook dispatcher and never ship `doWithoutValidation`;
- stop ingress before calling `disconnect()` during shutdown;
- monitor errors, rejection reasons, latency, resource use, and unusual send volume.

See [Media](docs/media.md), [Webhook](docs/webhook.md), and [Policy and safety](docs/policy-and-safety.md).

## Credential handling

Never hard-code credentials in Java, POM files, scripts, examples, test fixtures, shell history, or documentation. Environment variables are acceptable for local examples but managed secret injection is preferred in production. Logs must not contain authorization headers, full error responses that embed secrets, private key material, or raw callback bodies.

Use the minimum application permissions and separate development/test/production credentials. Rotate credentials after staff or environment changes and immediately after suspected exposure.

## Media boundary

Remote media SSRF protection is enabled by default. Host allowlists bypass address rejection and therefore require security review and infrastructure ownership. Local file paths must resolve within narrow `allowedFileDirs`; never allow a user to select an arbitrary server path. The host must enforce size limits for local files, byte arrays, and streams because the preview only caps remote URL downloads.

## Dependency and release integrity

Consume artifacts only from Maven Central and verify the expected group, artifact, version, and signatures. Keep one converged `oapi-sdk` version. The Channel JAR must not contain copied main-SDK implementations or legacy main-SDK Channel classes.

## Bug bounty reward

For information about ByteDance's bug bounty program, visit the [ByteDance Security Response Center](https://src.bytedance.com/home).
