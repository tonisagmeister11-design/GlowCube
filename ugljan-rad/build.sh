#!/bin/bash
# Baut die APK ohne Gradle direkt mit den Android-SDK-Werkzeugen.
set -e -o pipefail
SDK=${ANDROID_HOME:-/opt/android-sdk}
BT=$SDK/build-tools/36.1.0
JAR=$SDK/platforms/android-36/android.jar
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/classes build/dex
$BT/aapt2 compile --dir app/res -o build/res.zip
mkdir -p build/gen
$BT/aapt2 link -o build/base.apk -I $JAR --manifest app/AndroidManifest.xml -A app/assets build/res.zip --java build/gen
javac -source 11 -target 11 -nowarn -cp $JAR -d build/classes $(find app/java build/gen -name '*.java') 2>&1 | { grep -v "bootstrap class path\|JAVA_TOOL_OPTIONS" || true; }
$BT/d8 --release --min-api 24 --lib $JAR --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
$BT/zipalign -f -p 4 build/unsigned.apk build/aligned.apk
KS=${KEYSTORE:-$HOME/.ugljan-rad.keystore}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass ugljanrad -keypass ugljanrad -alias rad -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Ugljan Rad" >/dev/null 2>&1
$BT/apksigner sign --ks "$KS" --ks-pass pass:ugljanrad --key-pass pass:ugljanrad --out dist/Velora.apk build/aligned.apk
$BT/apksigner verify dist/Velora.apk && ls -la dist/Velora.apk
