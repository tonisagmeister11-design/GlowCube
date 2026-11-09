#!/bin/bash
# Baut das Android App Bundle (AAB) fuer Google Play mit Gradle (inkl. Google Play Billing).
# Signiert mit dem Upload-Schluessel, der NICHT im Repository liegt:
#   VELORA_UPLOAD_KS=/pfad/velora-upload.jks VELORA_UPLOAD_PASS=... ./build-release.sh
# (ohne Variablen: ../release-keys/velora-upload.jks und .pass)
# Ergebnis: dist/BikeOra.aab (Play Console) und dist/BikeOra-release.apk (gleiche Signatur, zum Testen)
set -e -o pipefail
cd "$(dirname "$0")"
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
GRADLE=${GRADLE:-$(command -v gradle || echo /opt/gradle/bin/gradle)}
[ -f app/lib/arm64-v8a/libvelora_llm.so ] || ./build-native.sh
"$GRADLE" --no-daemon -q :app:bundleRelease :app:assembleRelease 2>&1 | grep -v "JAVA_TOOL_OPTIONS\|^$" || true
mkdir -p dist
cp app/build/outputs/bundle/release/app-release.aab dist/BikeOra.aab
cp app/build/outputs/apk/release/app-release.apk dist/BikeOra-release.apk
BT=$ANDROID_HOME/build-tools/36.1.0
jarsigner -verify dist/BikeOra.aab 2>&1 | grep -v JAVA_TOOL | head -1
$BT/apksigner verify dist/BikeOra-release.apk && echo "Release-APK signiert"
$BT/aapt2 dump badging dist/BikeOra-release.apk | grep -E "^package|sdkVersion|targetSdk|uses-permission|native-code|application-label:"
ls -la dist/BikeOra.aab dist/BikeOra-release.apk
