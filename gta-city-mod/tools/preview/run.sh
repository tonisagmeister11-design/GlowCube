#!/bin/sh
# Renders the city generator without Minecraft.
#   ./run.sh map <x> <z> <radius> out.png          top-down map
#   ./run.sh iso <x> <z> <radius> out.png [px] [maxY]   isometric 3D view
set -e
cd "$(dirname "$0")"
W=../../src/main/java/de/gtacity/world
mkdir -p out
javac -d out $(find stubs -name "*.java") $W/B.java $W/Buildings.java $W/Column.java $W/CityLayout.java \
  $W/Hash.java $W/Lot.java $W/Nature.java $W/Palette.java $W/Signs.java $W/Streets.java $W/Tower.java \
  $W/ModBlocksRef.java Preview.java
java -cp out Preview "$@"
