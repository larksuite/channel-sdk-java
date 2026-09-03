# 发布 Lark Channel SDK for Java

简体中文 | [English](RELEASING.md)

本文是维护者将 `com.larksuite.oapi:channel-sdk` 发布到 Maven Central 的操作手册。发布由维护者工作站经 OSSRH staging 执行，与主 Java SDK 使用相同的模式。

本仓库刻意不提供 GitHub Actions 发布工作流。不要仅为了发布而向 GitHub 仓库或 Environment 写入 Maven 凭证、GPG 私钥或口令。

## 发布模型

- 源码基线：`larksuite/channel-sdk-java` 的 `main` 上经过评审的提交。
- 发布标识：指向该提交、命名为 `v<version>` 的 annotated Git tag。
- Maven 坐标：`com.larksuite.oapi:channel-sdk`。
- 仓库：`https://ossrh-staging-api.central.sonatype.com/` 的 OSSRH staging；staging 关闭后进入 Maven Central。
- 签名：Maven `release` profile 通过 GPG 签名主 JAR、源码 JAR 和 Javadoc JAR。
- 晋级：Nexus staging 插件在成功部署后自动关闭并发布 staging 仓库。

Maven Central 产物不可变。把 `mvn -Prelease deploy` 视为面向外部的生产变更：执行前必须复核版本、提交、测试、元数据和签名身份。

## 所需权限与本机前置条件

发布人必须具备：

1. GitHub 仓库写权限，以及创建 release tag 和 GitHub Release 的授权；
2. 对 `com.larksuite.oapi` 命名空间有发布权限的 OSSRH 凭证；
3. 经本机 GPG agent 访问获批准 GPG 私钥及其口令的能力；
4. JDK 8、JDK 11，以及 Maven 3.6.3 或更高版本；
5. 目标 `main` 提交对应的干净本地工作区。

Maven server ID 必须是 `ossrh`。工作站可从 `~/.m2/settings.xml` 解析凭证；严禁把凭证写入 Shell 历史、源码、终端输出或支持日志。若凭证由环境或密钥管理系统注入，本地 settings 可以使用如下安全形态：

```xml
<server>
  <id>ossrh</id>
  <username>${env.OSSRH_USERNAME}</username>
  <password>${env.OSSRH_PASSWORD}</password>
</server>
```

发布前仅确认签名私钥在本机可用，不要导出它：

```bash
gpg --list-secret-keys --keyid-format LONG
```

签名公钥必须已能被使用者发现。不要将私钥、口令写入 GitHub、Issue、文档、命令行参数或 Maven POM。

## 1. 准备发布提交

1. 从最新且干净的 `main` 开始；不得发布未评审的本地修改。
2. 选择发布版本。它必须不是 snapshot，且尚未在 Maven Central 存在。
3. 更新根目录 `pom.xml` 的版本、`CHANGELOG.md`，以及所有用户文档中的版本/兼容性说明。英文与简体中文文件必须同步。
4. 将有意的 API、行为、依赖、兼容性或安全变更写入变更记录；必要时补充迁移文档。
5. 提交并合入 `main`。

`release` profile 不会选择或改写项目版本；Maven 实际部署的是 `pom.xml` 中的版本。

## 2. 验证精确发布源码

分别在两个受支持的 JDK 上运行完整仓库验证。当前发布目标是 JDK 8 和 JDK 11；在兼容性矩阵明确更新前，JDK 17/21 不属于发布目标。

```bash
# 切换到 JDK 8 后执行：
./scripts/verify.sh

# 切换到 JDK 11 后再次执行：
./scripts/verify.sh
```

继续前确认：

- 忽略构建产物后，`git status --short` 为空；
- 两个 JDK 上的测试、文档检查、包隔离检查、SBOM 生成和全部示例构建均已通过；
- 版本不是 snapshot，且和预期 tag 名一致；
- `LICENSE`、依赖元数据、源码包、Javadoc 包、SCM URL 和兼容性矩阵都正确。

执行下面这条不发布的 release profile 检查，确认 OSSRH staging 扩展可被解析：

```bash
mvn --batch-mode --no-transfer-progress -Prelease -Dgpg.skip=true validate
```

`-Dgpg.skip=true` 仅用于上述本地配置检查，绝不能用于实际部署。GPG agent 可用时，建议再做一次不发布的签名检查：

```bash
mvn --batch-mode --no-transfer-progress -Prelease verify
```

## 3. 为已验证提交打 tag

使用 annotated tag。将 `<version>` 替换为已批准的精确版本；不得移动或复用已公开发布的 tag。

```bash
git switch main
git pull --ff-only origin main
git tag -a v<version> -m "Release v<version>"
git push origin v<version>
```

确认 tag 指向通过验证的提交：

```bash
git show --no-patch --decorate v<version>
mvn -q -DforceStdout help:evaluate -Dexpression=project.version
```

若 tag 与 POM 版本不一致，应立即停止。只有在确认目标且与维护者协调后，才可删除刚创建、尚未发布的 tag；绝不能修改已用于公开发布的 tag。

## 4. 经 OSSRH staging 部署

在干净工作区中检出 release tag，然后只执行一次部署命令：

```bash
git switch --detach v<version>
mvn --batch-mode --no-transfer-progress -Prelease deploy
```

`release` profile 会执行 GPG 签名并部署到已配置的 OSSRH staging 地址。不要在命令行传递凭证或口令；让 Maven 读取 `ossrh` server 配置，并让 GPG agent 在必要时私密地提示输入。

关注 Maven 输出中的签名和 staging 错误。成功命令会创建 staging 部署、校验、关闭，并因本仓库开启自动发布而向 Maven Central 晋级。即使 Maven Central 搜索尚未更新，也不要重复执行 deploy。

## 5. 验证公开发布

Central 完成同步后，在干净的消费者环境验证：

1. Maven Central 能解析精确的 group、artifact 与版本；
2. POM、主 JAR、源码 JAR、Javadoc JAR、校验和与签名均存在；
3. POM 中的 MIT 许可证、项目 URL、SCM URL、开发者元数据，以及传递的 `oapi-sdk` 版本符合预期；
4. 最小外部 Maven 消费者通过已发布坐标编译，而不是使用本地 install 的产物；
5. GitHub release tag 指向已验证的发布提交。

可使用精确版本做一次快速解析检查：

```bash
mvn --batch-mode --no-transfer-progress -U \
  dependency:get \
  -Dartifact=com.larksuite.oapi:channel-sdk:<version>
```

然后根据已有 tag 创建 GitHub Release。Release Notes 使用 `CHANGELOG.md` 中对应版本的内容，注明支持的 JDK 8/11 矩阵和主 SDK 兼容性；如有用户可见改动，链接到迁移说明。

## 失败处置与回滚

| 场景 | 处理方式 |
| --- | --- |
| deploy 前本地验证或签名失败 | 停止，修复发布提交，重新完成全量检查；评审后再创建新的候选 tag。 |
| OSSRH 拒绝校验或 staging | 检查该 staging 的精确错误，修复后发布新版本；不要覆盖失败坐标。 |
| 部署成功但 Central 同步延迟 | 等待并检查 staging/Central 状态；不要重复部署。 |
| 已发布产物存在缺陷 | Maven Central 产物不能替换。发布修复后的更高版本，在 Release Notes 中说明问题并建议使用修复版本。 |
| 发现安全问题 | 按 [SECURITY.zh.md](SECURITY.zh.md) 私下协调修复发布，再进行公开披露。 |

一旦使用者可能已消费该版本，就不得删除、重写或强推对应 tag。GitHub Release 文案可以修正，但 Maven 版本及其内容不可修改。

## 发布记录

每次发布保留仅维护者可见的记录，至少包含：

- 发布版本、tag、提交 SHA 和时间；
- JDK 8、JDK 11 的验证证据；
- Maven 和 GPG 工具版本、公开签名指纹；
- OSSRH staging 部署引用与 Central 可用性检查；
- GitHub Release URL；
- 已知限制、后续工作或回滚说明。

记录中绝不能出现凭证、口令、私钥、原始客户数据或未脱敏部署日志。
