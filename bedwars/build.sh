#!/usr/bin/env bash
#
# Baut die GlowCube-Bedwars-Mod (Fabric, Minecraft 26.3).
#
# Ab 26.x liefert Mojang den Spielcode ohne Verschleierung aus. Eine Mod ohne Mixins
# laesst sich deshalb direkt mit javac gegen das Client-JAR, seine Bibliotheken,
# den Fabric Loader und die Fabric API uebersetzen - ganz ohne Gradle/Loom.
#
#   1. laedt (einmalig) Client-JAR, Bibliotheken, Fabric Loader und Fabric API
#   2. uebersetzt src/ mit javac 25
#   3. packt die Klassen mit resources/fabric.mod.json nach dist/
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${WORK:-$ROOT/.build}"
VERSION="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$ROOT/resources/fabric.mod.json" | head -1)"
OUTPUT="${OUTPUT:-$ROOT/dist/glowcube-bedwars-$VERSION.jar}"
JAVAC="${JAVAC:-javac}"

MC_JSON_URL="https://piston-meta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json"
LOADER_URL="https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"
FAPI_URL="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.162.0+26.3/fabric-api-0.162.0+26.3.jar"

have_version="$("$JAVAC" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')"
if [ "${have_version:-0}" -lt 25 ]; then
  echo "Fehler: javac 25 oder neuer noetig (gefunden: ${have_version:-unbekannt})." >&2
  exit 1
fi

mkdir -p "$WORK/lib" "$WORK/fapi" "$(dirname "$OUTPUT")"

if [ ! -f "$WORK/client.jar" ]; then
  echo "==> lade Minecraft 26.3"
  curl -sSL -o "$WORK/26.3.json" "$MC_JSON_URL"
  client_url="$(grep -o '"url": *"https://piston-data.mojang.com/[^"]*/client.jar"' "$WORK/26.3.json" | head -1 | sed 's/.*"\(https[^"]*\)"/\1/')"
  curl -sSL -o "$WORK/client.jar.part" "$client_url"
  mv "$WORK/client.jar.part" "$WORK/client.jar"
fi

if [ ! -f "$WORK/lib/.done" ]; then
  echo "==> lade Bibliotheken"
  grep -o '"url": *"https://libraries.minecraft.net/[^"]*\.jar"' "$WORK/26.3.json" \
    | sed 's/.*"\(https[^"]*\)"/\1/' | grep -v natives | sort -u \
    | while read -r url; do
        f="$WORK/lib/$(basename "$url")"
        [ -f "$f" ] || curl -sSL -o "$f" "$url"
      done
  touch "$WORK/lib/.done"
fi

if [ ! -f "$WORK/fabric-loader.jar" ]; then
  echo "==> lade Fabric Loader und Fabric API"
  curl -sSL -o "$WORK/fabric-loader.jar" "$LOADER_URL"
  curl -sSL -o "$WORK/fabric-api.jar" "$FAPI_URL"
  ( cd "$WORK/fapi" && unzip -q -o "$WORK/fabric-api.jar" 'META-INF/jars/*' )
fi

CP="$WORK/client.jar:$WORK/fabric-loader.jar"
for j in "$WORK"/lib/*.jar "$WORK"/fapi/META-INF/jars/*.jar; do
  CP="$CP:$j"
done

echo "==> uebersetze"
rm -rf "$WORK/classes"
mkdir -p "$WORK/classes"
find "$ROOT/src" -name '*.java' > "$WORK/sources.txt"
"$JAVAC" -nowarn -proc:none -encoding UTF-8 --release 25 -cp "$CP" -d "$WORK/classes" "@$WORK/sources.txt"
cp -r "$ROOT/resources/." "$WORK/classes/"

echo "==> packe JAR"
rm -f "$OUTPUT"
( cd "$WORK/classes" && zip -q -r -X "$OUTPUT" . )

echo "fertig: $OUTPUT"
