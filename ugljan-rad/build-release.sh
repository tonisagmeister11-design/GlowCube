#!/bin/bash
# Baut das Android App Bundle (AAB) fuer den Google Play Store, ohne Gradle.
# Signiert wird mit dem Upload-Schluessel, der NICHT im Repository liegt:
#   VELORA_UPLOAD_KS=/pfad/velora-upload.jks VELORA_UPLOAD_PASS=... ./build-release.sh
# Ergebnis: dist/Velora.aab (fuer die Play Console) und dist/Velora-release.apk (zum Testen, gleiche Signatur).
set -e -o pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/opt/android-sdk}
BT=$SDK/build-tools/36.1.0
JAR=$SDK/platforms/android-36/android.jar
KS=${VELORA_UPLOAD_KS:-../release-keys/velora-upload.jks}
PASS=${VELORA_UPLOAD_PASS:-$(cat "${KS%.jks}.pass" 2>/dev/null || true)}
[ -f "$KS" ] && [ -n "$PASS" ] || { echo "Upload-Schluessel fehlt (VELORA_UPLOAD_KS / VELORA_UPLOAD_PASS)"; exit 1; }
BTOOL=${BUNDLETOOL:-/tmp/bundletool-1.18.1.jar}
[ -f "$BTOOL" ] || curl -sSL -f -o "$BTOOL" https://github.com/google/bundletool/releases/download/1.18.1/bundletool-all-1.18.1.jar
[ -f app/lib/arm64-v8a/libvelora_llm.so ] || ./build-native.sh

B=build/release
rm -rf $B && mkdir -p $B/classes $B/dex $B/gen $B/base/manifest $B/base/dex
$BT/aapt2 compile --dir app/res -o $B/res.zip
$BT/aapt2 link --proto-format -o $B/linked.zip -I $JAR --manifest app/AndroidManifest.xml -A app/assets $B/res.zip --java $B/gen
javac -source 11 -target 11 -nowarn -cp $JAR -d $B/classes $(find app/java $B/gen -name '*.java') 2>&1 | { grep -v "bootstrap class path\|JAVA_TOOL_OPTIONS\|deprecat" || true; }
$BT/d8 --release --min-api 24 --lib $JAR --output $B/dex $(find $B/classes -name '*.class')
# Modul "base" im Bundle-Format: manifest/, dex/, res/, assets/, lib/, resources.pb
(cd $B/base && unzip -q ../linked.zip && mv AndroidManifest.xml manifest/ && cp ../dex/classes.dex dex/)
cp -r app/lib $B/base/lib
(cd $B/base && zip -q -r ../base.zip .)
rm -f dist/Velora.aab
java -jar "$BTOOL" build-bundle --modules=$B/base.zip --output=dist/Velora.aab 2>&1 | grep -v JAVA_TOOL || true
jarsigner -keystore "$KS" -storepass "$PASS" -sigalg SHA256withRSA -digestalg SHA-256 dist/Velora.aab upload 2>&1 | grep -v "JAVA_TOOL\|^$" || true
# Pruefen: aus dem Bundle ein Universal-APK erzeugen, wie Google Play es ausliefern wuerde
rm -f $B/out.apks
java -jar "$BTOOL" build-apks --bundle=dist/Velora.aab --output=$B/out.apks --mode=universal --ks="$KS" --ks-pass=pass:"$PASS" --ks-key-alias=upload --key-pass=pass:"$PASS" 2>&1 | grep -v JAVA_TOOL || true
(cd $B && unzip -o -q out.apks universal.apk && cp universal.apk ../../dist/Velora-release.apk)
jarsigner -verify dist/Velora.aab 2>&1 | grep -v JAVA_TOOL | head -1
$BT/apksigner verify dist/Velora-release.apk && echo "Release-APK signiert"
$BT/aapt2 dump badging dist/Velora-release.apk | grep -E "^package|sdkVersion|targetSdk|uses-permission|native-code|application-label:" 
ls -la dist/Velora.aab dist/Velora-release.apk
