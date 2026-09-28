#!/usr/bin/env bash
# Прогон сценария расхождения к разбору q/races-idempotency.md.
# Вывод последнего прогона лежит рядом в output.md.
set -euo pipefail
cd "$(dirname "$0")"

COROUTINES=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm" \
  -name 'kotlinx-coroutines-core-jvm-*.jar' 2>/dev/null | grep -v sources | sort -V | tail -1)

if [ -z "$COROUTINES" ]; then
  echo "не найден kotlinx-coroutines-core-jvm в кэше gradle" >&2
  exit 1
fi

echo "kotlinc $(kotlinc -version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"
echo "coroutines $(basename "$COROUTINES")"

rm -rf build && mkdir -p build
kotlinc -nowarn -cp "$COROUTINES" races.kt -include-runtime -d build/races.jar
java -cp "build/races.jar:$COROUTINES" RacesKt
