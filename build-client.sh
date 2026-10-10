#!/usr/bin/env bash
#
# Baut den GlowCube-Client (Fabric) mit dem Modul Legendora neu.
#
# Vom Client liegt nur die fertige JAR vor (lib/glowcube-26.3-original.jar), kein Quellcode
# und keine Minecraft-Jars. Deshalb:
#   1. tools/client-stubs/ enthaelt winzige Platzhalter fuer die paar Minecraft-Klassen, die
#      Legendora anfasst (nur zum Uebersetzen - zur Laufzeit gelten die echten Klassen).
#   2. client-src/ wird gegen die Original-JAR plus Platzhalter uebersetzt.
#   3. PatchModuleManager traegt Legendora hinter KillAura in die Modulliste ein.
#   4. Die neuen/geaenderten .class-Dateien kommen in eine Kopie der Original-JAR.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${WORK:-$ROOT/.build/cl}"
ORIGINAL="$ROOT/lib/glowcube-26.3-original.jar"
OUTPUT="${OUTPUT:-$ROOT/dist/glowcube-26.3.jar}"
JAVAC="${JAVAC:-javac}"
JAVA="${JAVA:-java}"
ASM_JAR="${ASM_JAR:-$ROOT/.build/asm.jar}"
ASM_URL="https://repo1.maven.org/maven2/org/ow2/asm/asm/9.9/asm-9.9.jar"

have_version="$("$JAVAC" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')"
if [ "${have_version:-0}" -lt 25 ]; then
  echo "Fehler: javac 25 oder neuer noetig (gefunden: ${have_version:-unbekannt})." >&2
  exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK/stubs" "$WORK/classes" "$WORK/tools" "$(dirname "$ASM_JAR")" "$(dirname "$OUTPUT")"
if ! unzip -tq "$ASM_JAR" >/dev/null 2>&1; then
  echo "==> lade ASM"
  for versuch in 1 2 3 4; do
    curl -sSL -o "$ASM_JAR" "$ASM_URL" && unzip -tq "$ASM_JAR" >/dev/null 2>&1 && break
    sleep $((versuch * 3))
  done
fi

echo "==> Platzhalter uebersetzen"
find "$ROOT/tools/client-stubs" -name '*.java' > "$WORK/stubs.txt"
"$JAVAC" -nowarn -proc:none -d "$WORK/stubs" "@$WORK/stubs.txt"

echo "==> Legendora uebersetzen"
find "$ROOT/client-src" -name '*.java' > "$WORK/sources.txt"
"$JAVAC" -nowarn -proc:none -encoding UTF-8 -cp "$ORIGINAL:$WORK/stubs" -d "$WORK/classes" "@$WORK/sources.txt"

echo "==> ModuleManager patchen"
( cd "$WORK" && unzip -q -o "$ORIGINAL" 'net/glowcube/client/core/ModuleManager.class' -d "$WORK/classes" )
"$JAVAC" -nowarn -cp "$ASM_JAR" -d "$WORK/tools" "$ROOT/tools/client/PatchModuleManager.java"
"$JAVA" -cp "$ASM_JAR:$WORK/tools" PatchModuleManager "$WORK/classes/net/glowcube/client/core/ModuleManager.class"

echo "==> packe JAR"
rm -f "$OUTPUT"
cp "$ORIGINAL" "$OUTPUT"
( cd "$WORK/classes" && zip -q -r -X "$OUTPUT" . )
echo "fertig: $OUTPUT"
