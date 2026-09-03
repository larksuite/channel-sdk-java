#!/usr/bin/env bash

set -euo pipefail

project_dir=$(cd "$(dirname "$0")/.." && pwd)
jar_path=$(find "$project_dir/target" -maxdepth 1 -type f -name 'channel-sdk-*.jar' \
  ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)

if [[ -z "$jar_path" ]]; then
  echo "channel-sdk JAR not found; run mvn package first" >&2
  exit 1
fi

jar_entries=$(jar tf "$jar_path")

if grep -q '^com/lark/oapi/channel/' <<<"$jar_entries"; then
  echo "legacy Channel package found in $jar_path" >&2
  exit 1
fi

if grep -Eq '^com/lark/oapi/(core|event|service)/' <<<"$jar_entries"; then
  echo "copied main SDK classes found in $jar_path" >&2
  exit 1
fi

if ! grep -q '^com/lark/channel/' <<<"$jar_entries"; then
  echo "new Channel package not found in $jar_path" >&2
  exit 1
fi

if ! grep -q '^META-INF/LICENSE$' <<<"$jar_entries"; then
  echo "project license not found in $jar_path" >&2
  exit 1
fi

if ! unzip -p "$jar_path" META-INF/LICENSE | cmp -s - "$project_dir/LICENSE"; then
  echo "packaged license does not match repository LICENSE: $jar_path" >&2
  exit 1
fi

for classified_jar in \
  "$project_dir/target/$(basename "$jar_path" .jar)-sources.jar" \
  "$project_dir/target/$(basename "$jar_path" .jar)-javadoc.jar"; do
  if [[ ! -f "$classified_jar" ]]; then
    echo "required release artifact not found: $classified_jar" >&2
    exit 1
  fi
  if ! jar tf "$classified_jar" | grep -q '^META-INF/LICENSE$'; then
    echo "project license not found in $classified_jar" >&2
    exit 1
  fi
  if ! unzip -p "$classified_jar" META-INF/LICENSE | cmp -s - "$project_dir/LICENSE"; then
    echo "packaged license does not match repository LICENSE: $classified_jar" >&2
    exit 1
  fi
done

echo "verified package isolation: $jar_path"
