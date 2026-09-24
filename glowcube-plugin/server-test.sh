#!/usr/bin/env bash
# Startet einen echten Paper-Server der Fassung $1 mit dem gebauten Plugin $2,
# wartet bis "Done", prueft, ob das Plugin sauber geladen ist, und stoppt ihn.
set -u
FASSUNG="$1"
PLUGIN="$2"
ORDNER=/tmp/paper-server
rm -rf "$ORDNER"; mkdir -p "$ORDNER/plugins"
cp "$PLUGIN" "$ORDNER/plugins/"
cd "$ORDNER"
URL=$(python3 - "$FASSUNG" <<'PY'
import json, sys, urllib.request
f = sys.argv[1]
req = urllib.request.Request(f"https://fill.papermc.io/v3/projects/paper/versions/{f}/builds",
                             headers={"User-Agent": "GlowCube-CI (github.com/tonisagmeister11-design/GlowCube)"})
builds = json.load(urllib.request.urlopen(req, timeout=60))
b = builds[0] if isinstance(builds, list) else builds
print(b["downloads"]["server:default"]["url"])
PY
)
if [ -z "$URL" ]; then
  echo "::error title=Server-Test $FASSUNG::Kein Paper-Server fuer $FASSUNG gefunden."
  exit 1
fi
echo "Paper-Server: $URL"
curl -sSL -A "GlowCube-CI" -o paper.jar "$URL"
echo "eula=true" > eula.txt
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nview-distance=4\n' > server.properties
mkfifo eingabe
( sleep 600; echo stop ) > eingabe &
tail -f /dev/null > eingabe &
HALTER=$!
timeout 600 java -Xmx2G -jar paper.jar --nogui < eingabe > server.log 2>&1 &
SERVER=$!
for i in $(seq 1 480); do
  if grep -q "Done (" server.log; then break; fi
  if ! kill -0 $SERVER 2>/dev/null; then break; fi
  sleep 1
done
sleep 5
echo stop > eingabe
wait $SERVER
kill $HALTER 2>/dev/null
echo "----- Server-Log (Auszug) -----"
grep -E "GlowCube|ERROR|Exception|Could not load|Error occurred|Done \(" server.log | head -60
OK=0
grep -q "GlowCube-Agenten bereit" server.log && OK=1
if grep -qE "Error occurred while enabling GlowCubeAgent|Could not load 'plugins/glowcube" server.log; then OK=0; fi
if [ "$OK" = 1 ]; then
  echo "::notice title=Server-Test $FASSUNG::Paper $FASSUNG startet, GlowCubeAgent geladen und aktiv."
else
  echo "::error title=Server-Test $FASSUNG::GlowCubeAgent ist auf Paper $FASSUNG NICHT sauber geladen."
  tail -80 server.log
  exit 1
fi
