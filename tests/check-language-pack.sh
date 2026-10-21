#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -Xlint:all -Werror -d "$test_dir" \
    app/src/main/java/dev/chasem/smslauncher/LanguagePack.java \
    app/src/main/java/dev/chasem/smslauncher/TexturePackLayout.java tests/LanguagePackTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$test_dir" dev.chasem.smslauncher.LanguagePackTest "$test_dir"
