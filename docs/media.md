# Media and resource safety

[Documentation index](README.md) | [简体中文](zh-CN/media.md) | English

The Channel SDK can upload outbound images, files, audio, and video from several Java source types, and can download inbound resource keys. Media handling crosses filesystem and network trust boundaries; configure it narrowly.

## Supported outbound sources

The media factories accept:

- `String` beginning with `http://` or `https://`: remote URL;
- any other `String`: local filesystem path;
- `java.io.File`;
- `byte[]`;
- `java.io.InputStream`.

```java
channel.send(chatId, SendInput.image("https://cdn.example.com/a.png"));
channel.send(chatId, SendInput.file("/srv/channel/files/report.pdf", "report.pdf"));
channel.send(chatId, SendInput.audio(audioBytes, 4200));
channel.send(chatId, SendInput.video(videoFile, 15000, coverImageKey));
```

All forms are materialized and uploaded. An existing platform `image_key` or `file_key` is not accepted by the image/file factories. `SendInput.sticker(fileKey)` is the supported direct-key form for stickers.

Each factory call supplies one source object, so there is no cross-field content priority. Runtime source detection checks `File`, then `byte[]`, then `InputStream`, then `String`; unsupported object types fail with `upload_failed`. A String matching HTTP(S) is a URL and every other String is a local path.

## Remote URL protection

SSRF protection is enabled by default. For every initial URL and redirect, the downloader:

- permits only HTTP and HTTPS;
- resolves the hostname and rejects private, loopback, link-local, documentation, multicast, reserved, and embedded-IPv4 address ranges;
- pins the connection to a resolved address to reduce DNS rebinding risk;
- retains TLS and hostname verification for HTTPS;
- uses 15-second connect and read timeouts;
- follows at most five redirects, validating each destination;
- limits the downloaded body to 50 MiB, including chunked responses.

```java
LarkChannelOptions.OutboundConfig outbound = new LarkChannelOptions.OutboundConfig();
outbound.setSsrfGuardEnabled(true);
outbound.setSsrfAllowlist(java.util.Collections.singletonList("media.internal.example"));
```

An allowlisted normalized hostname bypasses private/reserved-address rejection. Use this only for a host controlled by your infrastructure, and enforce equivalent egress policy outside the process. Do not place user-provided hosts on the allowlist. Disabling the guard is strongly discouraged and should require a separate security review.

The 50 MiB cap is an SDK download safety limit, not a guarantee that the platform will accept every file below that size. Follow current platform media limits too.

## Local file protection

Local paths are normalized and resolved to their real path before use. The source must be a readable regular file. On POSIX systems, the SDK always blocks `/etc`, `/proc`, `/sys`, `/dev`, and `/private/etc` trees.

No allowed directories are configured by default, which means any readable path outside the hard-coded blocked trees can pass. For production and for any application that can receive a path indirectly, set a narrow allowlist:

```java
LarkChannelOptions.OutboundConfig outbound = new LarkChannelOptions.OutboundConfig();
outbound.setAllowedFileDirs(java.util.Arrays.asList(
        "/srv/channel/generated",
        "/srv/channel/attachments"));
```

The real source path must equal or be a descendant of an allowed directory. This helps prevent `..` traversal and symlink escapes. Still validate path ownership and authorization in application code; a directory allowlist does not decide which user may read which file.

Do not allow a request to select an arbitrary server path. Prefer an application-owned file ID that is resolved after authorization.

## Bytes and streams

`byte[]` and `InputStream` sources are copied into a temporary file for upload and then deleted. The SDK reads an `InputStream` fully into memory first and does not close the caller-provided stream. The caller owns closing it.

Unlike URL downloads, local files, byte arrays, and streams do not have an SDK size cap in this preview. Enforce a bounded size before calling the SDK to prevent memory, disk, and upload exhaustion.

```java
try (InputStream input = openBoundedInput()) {
    channel.sendSync(chatId, SendInput.file(input, "report.pdf"));
}
```

`openBoundedInput()` is application-owned and should reject content larger than its configured limit.

## Audio and video duration

Audio is uploaded as Opus and video as MP4. Pass duration in milliseconds when known:

```java
SendInput.audio(opusBytes, 4200);
SendInput.video(mp4File, 15000, coverImageKey);
```

If duration is null or non-positive, the SDK attempts to parse Opus/Ogg or MP4 metadata. Other formats or malformed metadata cause `upload_failed` and require an explicit positive duration. The duration parser reads the materialized file, so large media increases memory pressure; enforce application limits.

The optional video cover value is an existing platform image key. The SDK does not upload the cover in `video(...)`.

## File type and name

`SendInput.file(source, fileName)` uses the supplied name for the visible attachment and infers common platform file types from extensions: PDF, Word, Excel, PowerPoint, MP4, and Opus. Other extensions use the generic stream type. Validate and sanitize display names; never use them as filesystem paths after upload.

## Download inbound resources

Normalized inbound media exposes a `ResourceDescriptor` with `type` and `fileKey`:

```java
channel.downloadResource(resource.getFileKey(), resource.getType())
        .whenComplete((bytes, error) -> {
            if (error != null) {
                // Handle failure.
                return;
            }
            // Enforce an application size/content policy before storing or parsing.
        });
```

The helper returns the entire resource as `byte[]`. Do not download unbounded or untrusted content on a request thread. Apply authorization, storage quotas, content-type validation, malware scanning where appropriate, and short retention.

## Failure handling

Media failures use `LarkChannelException` codes:

- `ssrf_blocked`: URL protocol, host, DNS address, redirect, or TLS policy was rejected;
- `upload_failed`: source type/path/read/duration/platform upload failure;
- other outbound codes can still apply when sending the uploaded key.

Do not automatically disable protections after failure. Return a safe user message, record a bounded reason, and have operators inspect the configuration and underlying cause without logging media bytes or secrets.
