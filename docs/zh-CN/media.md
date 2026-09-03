# 媒体与资源安全

[文档索引](README.md) | 简体中文 | [English](../media.md)

Channel SDK 可以从多种 Java 来源上传图片、文件、音频和视频，也可以下载入站资源 key。媒体处理跨越文件系统与网络信任边界，必须采用严格配置。

## 支持的出站来源

媒体工厂接受：

- 以 `http://` 或 `https://` 开头的 `String`：远程 URL；
- 其他 `String`：本地文件路径；
- `java.io.File`；
- `byte[]`；
- `java.io.InputStream`。

```java
channel.send(chatId, SendInput.image("https://cdn.example.com/a.png"));
channel.send(chatId, SendInput.file("/srv/channel/files/report.pdf", "report.pdf"));
channel.send(chatId, SendInput.audio(audioBytes, 4200));
channel.send(chatId, SendInput.video(videoFile, 15000, coverImageKey));
```

上述形式都会先准备内容再上传。图片/文件工厂不接受已有平台 `image_key` 或 `file_key`。表情包可使用 `SendInput.sticker(fileKey)` 直接传 key。

每次工厂调用只传一个来源对象，不存在跨字段内容优先级。运行时依次识别 `File`、`byte[]`、`InputStream`、`String`，其他对象类型返回 `upload_failed`。匹配 HTTP(S) 的 String 是 URL，其他 String 是本地路径。

## 远程 URL 防护

SSRF 防护默认开启。对于初始 URL 及每次重定向，下载器都会：

- 只允许 HTTP 和 HTTPS；
- 解析域名，并拒绝私网、回环、链路本地、文档保留、组播、其他保留范围及嵌入 IPv4 的地址；
- 把连接固定到已解析地址，降低 DNS rebinding 风险；
- HTTPS 保留 TLS 和主机名校验；
- 使用 15 秒连接和读取超时；
- 最多跟随 5 次重定向，并逐个校验目标；
- 包括 chunked 响应在内，下载体上限为 50 MiB。

```java
LarkChannelOptions.OutboundConfig outbound = new LarkChannelOptions.OutboundConfig();
outbound.setSsrfGuardEnabled(true);
outbound.setSsrfAllowlist(java.util.Collections.singletonList("media.internal.example"));
```

白名单中的标准化主机名会绕过私有/保留地址拒绝。仅能用于自身基础设施控制的主机，并在进程外配置等效出站策略。不要把用户输入的主机加入白名单。强烈不建议关闭防护；如确有需要，应单独进行安全评审。

50 MiB 是 SDK 下载安全上限，不保证平台接受所有小于该值的文件；还需遵守平台当前媒体限制。

## 本地文件防护

SDK 会规范化本地路径并解析真实路径，且要求来源是可读普通文件。POSIX 系统始终禁止 `/etc`、`/proc`、`/sys`、`/dev` 和 `/private/etc` 目录树。

默认没有目录白名单，这意味着系统硬编码禁区之外的任意可读路径都可能通过。生产环境以及任何可能间接接收路径的应用都应设置严格白名单：

```java
LarkChannelOptions.OutboundConfig outbound = new LarkChannelOptions.OutboundConfig();
outbound.setAllowedFileDirs(java.util.Arrays.asList(
        "/srv/channel/generated",
        "/srv/channel/attachments"));
```

真实来源路径必须等于允许目录或位于其下，从而阻止 `..` 路径穿越和符号链接逃逸。但业务仍需校验文件归属与访问授权；目录白名单不能决定哪个用户有权读取哪个文件。

不要允许请求直接选择任意服务器路径。更推荐让请求携带业务文件 ID，完成授权后再解析路径。

## 字节与输入流

`byte[]` 和 `InputStream` 会复制到临时文件，上传后删除。SDK 会先把整个 `InputStream` 读入内存，而且不会关闭调用方传入的流，关闭责任属于调用方。

与 URL 下载不同，当前预览版没有为本地文件、字节数组和输入流设置 SDK 大小上限。调用 SDK 前必须限制大小，避免内存、磁盘和上传资源耗尽。

```java
try (InputStream input = openBoundedInput()) {
    channel.sendSync(chatId, SendInput.file(input, "report.pdf"));
}
```

`openBoundedInput()` 由业务实现，并应拒绝超过配置上限的内容。

## 音视频时长

音频按 Opus 上传，视频按 MP4 上传。已知时应传入毫秒时长：

```java
SendInput.audio(opusBytes, 4200);
SendInput.video(mp4File, 15000, coverImageKey);
```

时长为 null 或非正数时，SDK 会尝试解析 Opus/Ogg 或 MP4 元数据。其他格式或非法元数据会产生 `upload_failed`，需要显式正时长。时长解析会读取整个已准备文件，大媒体会增加内存压力，业务必须限流限大。

视频封面参数是已有平台 image key，`video(...)` 不负责上传封面。

## 文件类型与名称

`SendInput.file(source, fileName)` 使用给定名称作为可见附件名，并从扩展名推断 PDF、Word、Excel、PowerPoint、MP4 和 Opus 等平台类型，其他扩展名使用通用 stream 类型。应校验并清理展示名称；上传后不能再把它当作文件系统路径。

## 下载入站资源

标准化入站媒体通过 `ResourceDescriptor` 提供 `type` 和 `fileKey`：

```java
channel.downloadResource(resource.getFileKey(), resource.getType())
        .whenComplete((bytes, error) -> {
            if (error != null) {
                // 处理失败。
                return;
            }
            // 存储或解析前执行业务大小和内容策略。
        });
```

该方法把整个资源返回为 `byte[]`。不要在请求线程下载无界或不可信内容。按需执行授权、存储配额、内容类型校验、恶意文件扫描和短期留存。

## 失败处理

媒体错误使用 `LarkChannelException` code：

- `ssrf_blocked`：URL 协议、主机、DNS 地址、重定向或 TLS 策略被拒绝；
- `upload_failed`：来源类型、路径、读取、时长或平台上传失败；
- 上传 key 后发送消息时，仍可能出现其他出站错误码。

失败后不要自动关闭防护。向用户返回安全提示，只记录有限原因，由运维检查配置和底层 cause；日志中不能出现媒体字节或密钥。
