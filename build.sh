#!/usr/bin/env bash
#
# Baut AdminField mit der Mob-Verkleidung neu.
#
# Vom Original-Plugin liegt nur die JAR vor, kein Quellcode und keine Paper-API.
# Deshalb:
#   1. StubGen liest die Original-JAR und schreibt kompilierbare Platzhalter fuer
#      jede Klasse, die die JAR benutzt aber nicht enthaelt (Bukkit, Paper, NMS, ...).
#   2. Nur die geaenderten/neuen Klassen unter src/ werden uebersetzt - gegen die
#      Original-Klassen plus die Platzhalter. Alles andere bleibt Byte fuer Byte
#      so, wie es war.
#   3. Die neuen .class-Dateien werden in eine Kopie der Original-JAR gelegt.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${WORK:-$ROOT/.build}"
ORIGINAL="$ROOT/lib/AdminField-original.jar"
OUTPUT="${OUTPUT:-$ROOT/dist/AdminField.jar}"

JAVAC="${JAVAC:-javac}"
JAVA="${JAVA:-java}"
ASM_JAR="${ASM_JAR:-$WORK/asm.jar}"
ASM_URL="https://repo1.maven.org/maven2/org/ow2/asm/asm/9.9/asm-9.9.jar"

need_version=69   # Java 25 - so ist die Original-JAR uebersetzt
have_version="$("$JAVAC" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')"
if [ "${have_version:-0}" -lt 25 ]; then
  echo "Fehler: javac 25 oder neuer noetig (gefunden: ${have_version:-unbekannt})." >&2
  echo "Die Original-Klassen sind Class-File-Version $need_version." >&2
  exit 1
fi

rm -rf "$WORK/stubsrc" "$WORK/stubs" "$WORK/classes" "$WORK/jar"
mkdir -p "$WORK/stubsrc" "$WORK/stubs" "$WORK/classes" "$WORK/jar" "$WORK/tools" "$(dirname "$OUTPUT")"

if [ ! -f "$ASM_JAR" ]; then
  echo "==> lade ASM"
  curl -sSL -o "$ASM_JAR" "$ASM_URL"
fi

echo "==> entpacke Original-JAR"
( cd "$WORK/jar" && unzip -q -o "$ORIGINAL" )

echo "==> baue StubGen"
"$JAVAC" -nowarn -cp "$ASM_JAR" -d "$WORK/tools" "$ROOT/tools/StubGen.java" "$ROOT/tools/Overrides.java"

echo "==> erzeuge API-Platzhalter"
"$JAVA" -cp "$ASM_JAR:$WORK/tools" StubGen "$ORIGINAL" "$WORK/stubsrc"
find "$WORK/stubsrc" -name '*.java' > "$WORK/stubs.txt"
"$JAVAC" -nowarn -proc:none -d "$WORK/stubs" "@$WORK/stubs.txt"

echo "==> uebersetze geaenderte und neue Klassen"
find "$ROOT/src" -name '*.java' > "$WORK/sources.txt"
"$JAVAC" -nowarn -proc:none -encoding UTF-8 \
    -cp "$WORK/jar:$WORK/stubs" \
    -d "$WORK/classes" "@$WORK/sources.txt"

echo "==> packe JAR"
cp -r "$WORK/classes/." "$WORK/jar/"
rm -f "$OUTPUT"
( cd "$WORK/jar" && zip -q -r -X "$OUTPUT" . )

echo "fertig: $OUTPUT"
