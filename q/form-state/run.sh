#!/usr/bin/env bash
# Прогон стенда к разбору q/form-state.md.
# Вывод последнего прогона лежит рядом в output.md.
set -euo pipefail
cd "$(dirname "$0")"

echo "kotlinc $(kotlinc -version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"

rm -rf build && mkdir -p build
kotlinc -nowarn form.kt -include-runtime -d build/form.jar
java -cp build/form.jar FormKt
