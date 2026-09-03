# Third-party notices

This project is licensed under the [MIT License](LICENSE). This document records
the third-party runtime and test dependencies declared for version
`1.0.0-beta.1`.

The published `channel-sdk` JAR is not a fat JAR: it contains no copied
third-party classes. Maven resolves the dependencies below as separate
artifacts, and their own distributions retain their applicable license texts
and notices. This document does not change their licenses.

## Runtime dependencies

| Maven coordinate | Version | License |
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

## Test-only dependencies

These artifacts use Maven `test` scope and are not exposed as runtime
dependencies of the published SDK.

| Maven coordinate | Version | License |
| --- | ---: | --- |
| `junit:junit` | 4.13.2 | Eclipse Public License 1.0 |
| `org.slf4j:slf4j-nop` | 1.7.30 | MIT |

## Maintenance

Review this file whenever `pom.xml` changes. The release verification
generates a CycloneDX SBOM at `target/bom.json`; use the resolved runtime
dependency tree and its license metadata to update this inventory.
