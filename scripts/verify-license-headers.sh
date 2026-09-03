#!/usr/bin/env bash

set -euo pipefail

project_dir=$(cd "$(dirname "$0")/.." && pwd)
expected_header='// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT'
failures=0

fail() {
  echo "license check failed: $*" >&2
  failures=$((failures + 1))
}

while IFS= read -r -d '' java_file; do
  actual_header=$(sed -n '1,2p' "$java_file")
  [[ "$actual_header" == "$expected_header" ]] \
    || fail "missing or incorrect header: ${java_file#"$project_dir/"}"
done < <(find "$project_dir/src" "$project_dir/examples" -type f -name '*.java' -print0)

rg -q '^Copyright \(c\) 2026 Lark Technologies Pte\. Ltd\.$' "$project_dir/LICENSE" \
  || fail 'LICENSE has an unexpected copyright statement'
rg -q '<name>MIT License</name>' "$project_dir/pom.xml" \
  || fail 'pom.xml does not declare the MIT License'

if (( failures > 0 )); then
  exit 1
fi

echo 'verified MIT license metadata and Java source headers'
