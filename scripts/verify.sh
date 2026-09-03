#!/usr/bin/env bash

set -euo pipefail

project_dir=$(cd "$(dirname "$0")/.." && pwd)

cd "$project_dir"
"$project_dir/scripts/verify-license-headers.sh"
"$project_dir/scripts/verify-docs.sh"
mvn clean verify
"$project_dir/scripts/verify-package.sh"

# Consumer examples intentionally resolve the SDK exactly as an external Maven project would.
mvn -q -DskipTests -Dmaven.javadoc.skip=true install
mvn -f "$project_dir/examples/pom.xml" clean verify
