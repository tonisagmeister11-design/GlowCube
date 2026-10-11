#!/usr/bin/env bash
#
# Baut den Bedwars-Mod (Fabric, Minecraft 26.3, Java 25) gegen die echten Spieldateien.
# Ab 26.x ist Minecraft unverschleiert, deshalb reicht die client.jar direkt zum Uebersetzen.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${WORK:-$ROOT/.build/bw}"
DEPS="$WORK/deps"
OUTPUT="${OUTPUT:-$ROOT/dist/glowcube-bedwars-1.0.0.jar}"
JAVAC="${JAVAC:-javac}"
MC_VERSION="${MC_VERSION:-26.3}"

have_version="$("$JAVAC" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')"
[ "${have_version:-0}" -ge 25 ] || { echo "Fehler: javac 25 oder neuer noetig." >&2; exit 1; }

mkdir -p "$DEPS/libs" "$DEPS/fapi" "$(dirname "$OUTPUT")"
if [ ! -f "$DEPS/client.jar" ]; then
  echo "==> lade Minecraft $MC_VERSION und Fabric"
  python3 - "$DEPS" "$MC_VERSION" <<'PY'
import json, os, re, sys, urllib.request
deps, ver = sys.argv[1], sys.argv[2]
def get(u): return urllib.request.urlopen(u).read()
def dl(url, path):
    if not (os.path.exists(path) and os.path.getsize(path) > 0):
        urllib.request.urlretrieve(url, path)
manifest = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
url = next(v["url"] for v in manifest["versions"] if v["id"] == ver)
meta = json.loads(get(url))
dl(meta["downloads"]["client"]["url"], deps + "/client.jar")
for lib in meta["libraries"]:
    art = lib.get("downloads", {}).get("artifact")
    rules = lib.get("rules")
    if art and not (rules and not any(r["action"] == "allow" and "os" not in r for r in rules)):
        dl(art["url"], deps + "/libs/" + os.path.basename(art["path"]))
api = re.findall(r"<version>([^<]+)</version>", get("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml").decode())
api = [v for v in api if v.endswith("+" + ver)][-1]
dl(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar", deps + "/fabric-api.jar")
loader = re.findall(r"<version>([^<]+)</version>", get("https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml").decode())
loader = [v for v in loader if re.fullmatch(r"0\.\d+\.\d+", v)][-1]
dl(f"https://maven.fabricmc.net/net/fabricmc/fabric-loader/{loader}/fabric-loader-{loader}.jar", deps + "/fabric-loader.jar")
PY
fi
[ -d "$DEPS/fapi/META-INF/jars" ] || ( cd "$DEPS/fapi" && unzip -q -o ../fabric-api.jar 'META-INF/jars/*' )

CP="$DEPS/client.jar:$(ls "$DEPS"/libs/*.jar | tr '\n' ':')$(ls "$DEPS"/fapi/META-INF/jars/*.jar | tr '\n' ':')$DEPS/fabric-loader.jar"

rm -rf "$WORK/classes"
mkdir -p "$WORK/classes"
echo "==> uebersetze Bedwars-Mod"
"$JAVAC" -nowarn -proc:none -encoding UTF-8 -cp "$CP" -d "$WORK/classes" $(find "$ROOT/bedwars-mod/src" -name '*.java')
cp "$ROOT/bedwars-mod/resources/fabric.mod.json" "$WORK/classes/"

echo "==> packe JAR"
rm -f "$OUTPUT"
( cd "$WORK/classes" && zip -q -r -X "$OUTPUT" . )
echo "fertig: $OUTPUT"
