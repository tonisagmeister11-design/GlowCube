#!/usr/bin/env bash
#
# Baut dist/ClaudeAI.jar - ein eigenstaendiges Paper-Plugin fuer 26.3.
#
# Die Paper-API ist hier nicht herunterladbar, deshalb wird gegen schlanke
# Platzhalter unter stubs/ uebersetzt. Sie enthalten nur die Methoden, die das Plugin
# wirklich aufruft, mit exakt denselben Signaturen wie Paper. In die JAR kommen sie
# nicht - zur Laufzeit liefert der Server die echten Klassen.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$ROOT/.build"
OUTPUT="${OUTPUT:-$ROOT/../dist/ClaudeAI.jar}"

rm -rf "$WORK"
mkdir -p "$WORK/stubs" "$WORK/classes" "$(dirname "$OUTPUT")"

echo "==> uebersetze Platzhalter"
javac -nowarn --release 21 -d "$WORK/stubs" $(find "$ROOT/stubs" -name '*.java')

echo "==> uebersetze Plugin"
javac --release 21 -Xlint:all -Xlint:-serial -encoding UTF-8 -cp "$WORK/stubs" -d "$WORK/classes" \
    $(find "$ROOT/src" -name '*.java')

echo "==> packe JAR"
cp "$ROOT/resources/plugin.yml" "$ROOT/resources/config.yml" "$WORK/classes/"
rm -f "$OUTPUT"
( cd "$WORK/classes" && jar --create --file "$OUTPUT" . )

echo "fertig: $OUTPUT"
