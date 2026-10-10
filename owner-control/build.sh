#!/usr/bin/env bash
# Baut dist/OwnerControl.jar. Die Paper-API ist hier nicht erreichbar, deshalb wird gegen die
# handgeschriebenen Platzhalter in stubs/ uebersetzt. Zur Laufzeit zaehlt die echte Paper-API.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
rm -rf .build && mkdir -p .build/stubs .build/classes dist
find stubs -name '*.java' > .build/stubs.txt
javac -nowarn -proc:none --release 21 -d .build/stubs @.build/stubs.txt
find src -name '*.java' > .build/src.txt
javac -nowarn -proc:none --release 21 -encoding UTF-8 -cp .build/stubs -d .build/classes @.build/src.txt
cp resources/plugin.yml resources/config.yml .build/classes/
rm -f dist/OwnerControl.jar
( cd .build/classes && jar cf ../../dist/OwnerControl.jar . )
echo "fertig: dist/OwnerControl.jar"
