#!/usr/bin/env bash
# Simulationstest: ClaudeAI spielt eine Runde in einer nachgebauten Welt.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$ROOT/.build/test"
rm -rf "$WORK" && mkdir -p "$WORK"
# Platzhalter, die die Testwelt durch echte Nachbauten ersetzt, weglassen
( cd "$ROOT/stubs" && find . -name '*.java' ) | while read -r f; do
  [ -f "$ROOT/test/fakes/$f" ] || echo "$ROOT/stubs/$f"
done > "$WORK/sources.txt"
find "$ROOT/test/fakes" "$ROOT/src" -name '*.java' >> "$WORK/sources.txt"
echo "$ROOT/test/SimTest.java" >> "$WORK/sources.txt"
javac -nowarn --release 21 -encoding UTF-8 -d "$WORK" "@$WORK/sources.txt"
java -cp "$WORK" SimTest
