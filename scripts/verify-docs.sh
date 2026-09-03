#!/usr/bin/env bash

set -euo pipefail

project_dir=$(cd "$(dirname "$0")/.." && pwd)
cd "$project_dir"

failures=0

fail() {
  echo "documentation check failed: $*" >&2
  failures=$((failures + 1))
}

topics=(
  README
  quickstart
  configuration
  events
  webhook
  sending-messages
  streaming
  media
  policy-and-safety
  reference
  migration-from-oapi-sdk-java
  compatibility
  troubleshooting
  testing
)

root_pairs=(
  'README.md|README.zh.md'
  'CONTRIBUTING.md|CONTRIBUTING.zh.md'
  'SECURITY.md|SECURITY.zh.md'
  'CODE_OF_CONDUCT.md|CODE_OF_CONDUCT.zh.md'
  'examples/README.md|examples/README.zh.md'
)

for pair in "${root_pairs[@]}"; do
  left=${pair%%|*}
  right=${pair#*|}
  [[ -f "$left" ]] || fail "required English file is missing: $left"
  [[ -f "$right" ]] || fail "required Chinese file is missing: $right"
done

[[ -f CHANGELOG.md ]] || fail "required file is missing: CHANGELOG.md"

for topic in "${topics[@]}"; do
  [[ -f "docs/$topic.md" ]] || fail "English topic is missing: docs/$topic.md"
  [[ -f "docs/zh-CN/$topic.md" ]] || fail "Chinese topic is missing: docs/zh-CN/$topic.md"
done

removed_paths=(README.zh-CN.md MIGRATION.md docs/guide.md docs/guide.zh-CN.md)
for path in "${removed_paths[@]}"; do
  [[ ! -e "$path" ]] || fail "obsolete file still exists: $path"
done

public_markdown=()
while IFS= read -r -d '' path; do
  public_markdown+=("$path")
done < <(find . -type f -name '*.md' \
  -not -path './.git/*' \
  -not -path './docs/internal/*' \
  -print0)

stale_patterns=(
  'README\.zh-CN\.md'
  'docs/guide\.md'
  'docs/guide\.zh-CN\.md'
  'sample/src/main/java/com/lark/oapi/sample/channel'
  'com\.lark\.oapi\.sample\.channel'
  'mvn -pl larksuite-oapi'
  'channel-test-plan-status\.md'
  'docs/internal/'
  'code\.byted'
  'Apache License 2\.0'
  '1\.0\.0-beta\.1-SNAPSHOT'
  'established private internal project channel'
  'approved internal clone'
  'internal Maven repository'
)

guard_output=$(mktemp)
link_records=$(mktemp)
trap 'rm -f "$guard_output" "$link_records"' EXIT

for pattern in "${stale_patterns[@]}"; do
  if rg -n --no-heading "$pattern" "${public_markdown[@]}" >"$guard_output" 2>/dev/null; then
    while IFS= read -r match; do
      fail "stale reference: $match"
    done <"$guard_output"
  fi
done

legacy_guard_files=()
for path in "${public_markdown[@]}"; do
  case "$path" in
    ./CHANGELOG.md|./docs/compatibility.md|./docs/zh-CN/compatibility.md|./docs/migration-from-oapi-sdk-java.md|./docs/zh-CN/migration-from-oapi-sdk-java.md)
      ;;
    *)
      legacy_guard_files+=("$path")
      ;;
  esac
done
if rg -n --no-heading 'com\.lark\.oapi\.channel' "${legacy_guard_files[@]}" >"$guard_output" 2>/dev/null; then
  while IFS= read -r match; do
    fail "legacy Channel package outside migration/compatibility context: $match"
  done <"$guard_output"
fi

for path in "${public_markdown[@]}"; do
  fence_count=$(rg -c '^```' "$path" 2>/dev/null || true)
  (( fence_count % 2 == 0 )) || fail "unbalanced fenced code blocks: ${path#./}"

  if rg -n '[[:blank:]]+$' "$path" >"$guard_output" 2>/dev/null; then
    while IFS= read -r match; do
      fail "trailing whitespace in ${path#./}: $match"
    done <"$guard_output"
  fi

  last_byte=$(tail -c 1 "$path" | od -An -t x1 | tr -d '[:space:]')
  [[ "$last_byte" == 0a ]] || fail "file must end with a newline: ${path#./}"
done

LC_ALL=C perl -ne 'while (/\]\(([^)]+)\)/g) { print "$ARGV\t$1\n" }' \
  "${public_markdown[@]}" >"$link_records"

while IFS=$'\t' read -r source raw_target; do
  if [[ "$raw_target" == \#* ]]; then
    continue
  fi
  if [[ "$raw_target" == \<* ]]; then
    target=${raw_target#<}
    target=${target%%>*}
  else
    target=${raw_target%%[[:space:]]*}
  fi
  target=${target%%#*}

  [[ -n "$target" ]] || continue
  case "$target" in
    http://*|https://*|mailto:*|tel:*)
      continue
      ;;
  esac

  source=${source#./}
  if [[ "$target" == /* ]]; then
    resolved=$target
  else
    resolved="$(dirname "$source")/$target"
  fi
  [[ -e "$resolved" ]] || fail "broken local link in $source: $target"
done <"$link_records"

for topic in "${topics[@]}"; do
  if [[ "$topic" == README ]]; then
    rg -q '\[简体中文\]\(zh-CN/README\.md\)' docs/README.md \
      || fail "docs/README.md lacks the Chinese navigation link"
    rg -q '\[English\]\(\.\./README\.md\)' docs/zh-CN/README.md \
      || fail "docs/zh-CN/README.md lacks the English navigation link"
    continue
  fi

  head -n 8 "docs/$topic.md" | rg -q '\[Documentation index\]\(README\.md\)' \
    || fail "docs/$topic.md lacks the documentation index link near the top"
  head -n 8 "docs/$topic.md" | rg -q "\\[简体中文\\]\\(zh-CN/$topic\\.md\\)" \
    || fail "docs/$topic.md lacks the Chinese counterpart link near the top"
  head -n 8 "docs/zh-CN/$topic.md" | rg -q '\[文档索引\]\(README\.md\)' \
    || fail "docs/zh-CN/$topic.md lacks the documentation index link near the top"
  head -n 8 "docs/zh-CN/$topic.md" | rg -q "\\[English\\]\\(\\.\\./$topic\\.md\\)" \
    || fail "docs/zh-CN/$topic.md lacks the English counterpart link near the top"
done

language_checks=(
  'README.md|\[简体中文\]\(README\.zh\.md\)'
  'README.zh.md|\[English\]\(README\.md\)'
  'CONTRIBUTING.md|\[简体中文\]\(CONTRIBUTING\.zh\.md\)'
  'CONTRIBUTING.zh.md|\[English\]\(CONTRIBUTING\.md\)'
  'SECURITY.md|\[简体中文\]\(SECURITY\.zh\.md\)'
  'SECURITY.zh.md|\[English\]\(SECURITY\.md\)'
  'CODE_OF_CONDUCT.md|\[简体中文\]\(CODE_OF_CONDUCT\.zh\.md\)'
  'CODE_OF_CONDUCT.zh.md|\[English\]\(CODE_OF_CONDUCT\.md\)'
  'examples/README.md|\[简体中文\]\(README\.zh\.md\)'
  'examples/README.zh.md|\[English\]\(README\.md\)'
)
for check in "${language_checks[@]}"; do
  path=${check%%|*}
  pattern=${check#*|}
  head -n 8 "$path" | rg -q "$pattern" || fail "$path lacks its language counterpart link near the top"
done

project_version=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -n 1)
[[ -n "$project_version" ]] || fail "could not read the project version from pom.xml"
version_files=(
  README.md
  README.zh.md
  docs/migration-from-oapi-sdk-java.md
  docs/zh-CN/migration-from-oapi-sdk-java.md
  docs/compatibility.md
  docs/zh-CN/compatibility.md
)
for path in "${version_files[@]}"; do
  rg -q -F "$project_version" "$path" || fail "$path does not contain current project version $project_version"
done

credential_files=("${public_markdown[@]}")
while IFS= read -r -d '' path; do
  credential_files+=("$path")
done < <(find examples -type f -name '*.java' -print0)

sensitive_patterns=(
  'cli_[A-Za-z0-9_-]{12,}'
  'Bearer[[:space:]]+[A-Za-z0-9._~+/-]{12,}'
  '-----BEGIN ([A-Z ]+ )?PRIVATE KEY-----'
  "(?i)(app[_-]?secret|access[_-]?token)[[:space:]]*[:=][[:space:]]*[\"']?[A-Za-z0-9+/=_-]{12,}"
)
for pattern in "${sensitive_patterns[@]}"; do
  if rg -n --no-heading -e "$pattern" "${credential_files[@]}" \
      | rg -v '(cli_xxx|your_|replace-|secret-manager|System\.getenv|requiredEnv|<[^>]+>)' \
      >"$guard_output" 2>/dev/null; then
    while IFS= read -r match; do
      fail "possible credential in documentation/example: $match"
    done <"$guard_output"
  fi
done

if ! git diff --check; then
  fail "git diff --check reported whitespace errors"
fi

if (( failures > 0 )); then
  echo "$failures documentation check(s) failed" >&2
  exit 1
fi

echo "verified bilingual document pairs and language navigation"
echo "verified local Markdown links"
echo "verified documentation guard and credential patterns"
echo "verified documentation version consistency and formatting"
