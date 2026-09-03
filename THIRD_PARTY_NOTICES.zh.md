# 第三方依赖声明

本项目使用 [MIT License](LICENSE)。本文记录 `1.0.0-beta.1` 版本声明的
第三方运行时和测试依赖。

已发布的 `channel-sdk` JAR 不是 fat JAR，不包含复制进来的第三方 class。
Maven 会将下列依赖作为独立制品解析；每个依赖的分发包仍保留其适用的许可证与
声明。本文不改变任何第三方依赖的许可证。

## 运行时依赖

| Maven 坐标 | 版本 | 许可证 |
| --- | ---: | --- |
| `com.larksuite.oapi:oapi-sdk` | 2.8.5 | Apache-2.0 |
| `commons-codec:commons-codec` | 1.15 | Apache-2.0 |
| `org.apache.httpcomponents:httpclient` | 4.5.13 | Apache-2.0 |
| `org.apache.httpcomponents:httpcore` | 4.4.13 | Apache-2.0 |
| `commons-logging:commons-logging` | 1.2 | Apache-2.0 |
| `org.apache.httpcomponents:httpmime` | 4.5.13 | Apache-2.0 |
| `com.larksuite.oapi:larksuite-oapi-shaded-protobuf` | 2.4.6 | Apache-2.0 |
| `com.google.guava:guava` | 32.0.0-jre | Apache-2.0 |
| `com.google.guava:failureaccess` | 1.0.1 | Apache-2.0 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava | Apache-2.0 |
| `com.google.code.findbugs:jsr305` | 3.0.2 | Apache-2.0 |
| `org.checkerframework:checker-qual` | 3.33.0 | MIT |
| `com.google.errorprone:error_prone_annotations` | 2.18.0 | Apache-2.0 |
| `com.google.j2objc:j2objc-annotations` | 2.8 | Apache-2.0 |
| `com.google.code.gson:gson` | 2.9.0 | Apache-2.0 |
| `org.slf4j:slf4j-api` | 1.7.30 | MIT |

## 仅测试依赖

下列制品使用 Maven 的 `test` scope，不会作为已发布 SDK 的运行时依赖暴露。

| Maven 坐标 | 版本 | 许可证 |
| --- | ---: | --- |
| `junit:junit` | 4.13.2 | Eclipse Public License 1.0 |
| `org.slf4j:slf4j-nop` | 1.7.30 | MIT |

## 维护方式

每次修改 `pom.xml` 时均应复核本文。发布校验会在 `target/bom.json` 生成
CycloneDX SBOM；请根据实际解析出的运行时依赖树及许可证元数据更新清单。
