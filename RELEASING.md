# Releasing Lark Channel SDK for Java

[简体中文](RELEASING.zh.md) | English

This runbook is for maintainers publishing `com.larksuite.oapi:channel-sdk` to Maven Central. Releases are performed from a maintainer workstation through OSSRH staging, following the same model as the main Java SDK.

There is intentionally no GitHub Actions publishing workflow. Do not create repository or environment secrets for Maven credentials or GPG material solely to release this project.

## Release model

- Source of truth: a reviewed commit on `main` in `larksuite/channel-sdk-java`.
- Release identity: an annotated Git tag named `v<version>` on that exact commit.
- Artifact coordinates: `com.larksuite.oapi:channel-sdk`.
- Repository: OSSRH staging at `https://ossrh-staging-api.central.sonatype.com/`, which publishes to Maven Central after staging closes.
- Signing: the Maven `release` profile signs the main JAR, sources JAR, and Javadoc JAR with GPG.
- Promotion: the configured Nexus staging plugin closes and releases a successful deployment automatically.

Maven Central artifacts are immutable. Treat `mvn -Prelease deploy` as an external production change: review the version, commit, tests, metadata, and signing identity before running it.

## Required authority and local prerequisites

The release operator needs all of the following:

1. Write access to the GitHub repository, and authority to create the release tag and GitHub Release.
2. OSSRH credentials that are authorized for the `com.larksuite.oapi` namespace.
3. Access to the approved GPG secret key and its passphrase through the local GPG agent.
4. JDK 8 and JDK 11, plus Maven 3.6.3 or later.
5. A clean local checkout of the intended `main` commit.

The Maven server ID must be `ossrh`. A workstation may resolve credentials from `~/.m2/settings.xml`; keep values out of shell history, source control, terminal output, and support logs. This is a safe shape for a local settings entry when the credential values are injected by the environment or a secret manager:

```xml
<server>
  <id>ossrh</id>
  <username>${env.OSSRH_USERNAME}</username>
  <password>${env.OSSRH_PASSWORD}</password>
</server>
```

Before a release, confirm the signing key is available without exporting it:

```bash
gpg --list-secret-keys --keyid-format LONG
```

The public half of the signing key must already be discoverable by consumers. Do not paste the private key or passphrase into GitHub, an issue, a document, a command argument, or a Maven POM.

## 1. Prepare the release commit

1. Start from an up-to-date, clean `main` checkout. Do not release an unreviewed local change.
2. Select the release version. It must be a non-snapshot version and must not already exist in Maven Central.
3. Update the root `pom.xml` version, `CHANGELOG.md`, and every version/compatibility statement that the user-facing documentation requires. Keep the English and Simplified Chinese documents paired.
4. Record intentional API, behavior, dependency, compatibility, or security changes in the changelog and, where applicable, migration documentation.
5. Commit and merge the release preparation to `main`.

The release profile does not choose or rewrite the project version. The version in `pom.xml` is the version that Maven deploys.

## 2. Verify the exact release source

Run the repository verification on both supported JDKs. The build currently supports JDK 8 and 11; JDK 17 and 21 are not release targets until the compatibility matrix says otherwise.

```bash
# Select JDK 8, then run:
./scripts/verify.sh

# Select JDK 11, then run the same command:
./scripts/verify.sh
```

Confirm before continuing:

- `git status --short` is empty after ignoring generated build output;
- all tests, documentation checks, package-isolation checks, SBOM generation, and example builds passed on both JDKs;
- the version is non-snapshot and matches the intended tag name;
- `LICENSE`, dependency metadata, sources, Javadocs, SCM URLs, and the compatibility matrix are correct.

Run this non-publishing release-profile check to confirm that the OSSRH staging extension resolves:

```bash
mvn --batch-mode --no-transfer-progress -Prelease -Dgpg.skip=true validate
```

`-Dgpg.skip=true` is for this local configuration check only. Never use it for the actual deployment. When the GPG agent is available, a final non-publishing signing check is also appropriate:

```bash
mvn --batch-mode --no-transfer-progress -Prelease verify
```

## 3. Tag the verified commit

Use an annotated tag. Substitute the exact approved version; do not move or reuse a published tag.

```bash
git switch main
git pull --ff-only origin main
git tag -a v<version> -m "Release v<version>"
git push origin v<version>
```

Confirm that the tag resolves to the commit that passed verification:

```bash
git show --no-patch --decorate v<version>
mvn -q -DforceStdout help:evaluate -Dexpression=project.version
```

If the tag and POM version differ, stop. Delete only an unpublished, just-created tag after confirming its exact target and coordinating with maintainers; never alter a tag that was used for a public release.

## 4. Deploy through OSSRH staging

Check out the release tag in a clean working tree, then run the one deploy command:

```bash
git switch --detach v<version>
mvn --batch-mode --no-transfer-progress -Prelease deploy
```

The `release` profile performs GPG signing and deploys to the configured OSSRH staging endpoint. Do not pass credentials or passphrases on the command line. Let Maven resolve the `ossrh` server entry and let the GPG agent prompt privately if necessary.

Watch the Maven output for signing and staging errors. A successful command creates a staging deployment, validates it, closes it, and—because this repository configures automatic release—promotes it toward Maven Central. Do not run a second deploy merely because Maven Central search has not updated yet.

## 5. Verify the public release

After Central propagation, verify all of the following from a clean consumer context:

1. Maven Central resolves the exact group, artifact, and version.
2. The POM, main JAR, sources JAR, Javadoc JAR, checksums, and signatures are present.
3. The POM has the MIT license, project URL, SCM URL, developer metadata, and the expected transitive `oapi-sdk` version.
4. A minimal external Maven consumer compiles using the released coordinate rather than a locally installed artifact.
5. The GitHub release tag points to the verified release commit.

For a quick resolution check, use the release version explicitly:

```bash
mvn --batch-mode --no-transfer-progress -U \
  dependency:get \
  -Dartifact=com.larksuite.oapi:channel-sdk:<version>
```

Then create the GitHub Release from the existing tag. Use the matching `CHANGELOG.md` section as release notes, call out the supported JDK 8/11 matrix and main-SDK compatibility, and link migration guidance for user-visible changes.

## Failure handling and rollback

| Situation | Action |
| --- | --- |
| Local verification or signing fails before deploy | Stop, fix the release commit, rerun the full checks, and create a new candidate tag only after review. |
| OSSRH rejects validation or staging | Inspect the specific staging error. Fix the cause and release a new version; do not overwrite the failed coordinate. |
| Deployment succeeds but Central propagation is delayed | Wait and check the staging/Central status. Do not duplicate the deployment. |
| A released artifact has a defect | Maven Central artifacts cannot be replaced. Publish a corrected, higher version; document the issue in release notes and recommend the fixed version. |
| A security issue is found | Follow [SECURITY.md](SECURITY.md) and coordinate a fixed release privately before public disclosure. |

Do not delete, rewrite, or force-push a tag after consumers may have used it. GitHub Release text can be corrected, but the Maven artifact version and its content cannot.

## Release record

For each release, retain a maintainer-only record containing:

- release version, tag, commit SHA, and timestamp;
- JDK 8 and JDK 11 verification evidence;
- Maven and GPG tool versions, plus the public signing-key fingerprint;
- OSSRH staging deployment reference and Central availability check;
- GitHub Release URL;
- known limitations, follow-up work, or rollback guidance.

Never place credentials, passphrases, private keys, raw customer data, or unredacted deployment logs in the record.
