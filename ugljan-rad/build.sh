#!/bin/bash
# Baut die APK ohne Gradle direkt mit den Android-SDK-Werkzeugen.
set -e -o pipefail
SDK=${ANDROID_HOME:-/opt/android-sdk}
BT=$SDK/build-tools/36.1.0
JAR=$SDK/platforms/android-36/android.jar
cd "$(dirname "$0")"
rm -rf build/classes build/dex build/gen build/res.zip build/*.apk && mkdir -p build/classes build/dex
$BT/aapt2 compile --dir app/res -o build/res.zip
mkdir -p build/gen
# Paketname, Version und SDK-Stufen aus app/build.gradle in eine Kopie des Manifests setzen
VC=$(grep -o "appVersionCode = [0-9]*" app/build.gradle | grep -o "[0-9]*"); VN=$(grep -o "appVersionName = '[^']*'" app/build.gradle | cut -d"'" -f2)
sed "s#<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">#<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"app.velora.bike\" android:versionCode=\"$VC\" android:versionName=\"$VN\"><uses-sdk android:minSdkVersion=\"24\" android:targetSdkVersion=\"36\" />#; s#<application #<application android:extractNativeLibs=\"false\" #" app/AndroidManifest.xml > build/AndroidManifest.xml
$BT/aapt2 link -o build/base.apk -I $JAR --manifest build/AndroidManifest.xml -A app/assets build/res.zip --java build/gen
javac -source 11 -target 11 -nowarn -cp $JAR -d build/classes $(find app/java build/gen -name '*.java' ! -name 'Billing.java') 2>&1 | { grep -v "bootstrap class path\|JAVA_TOOL_OPTIONS" || true; }
$BT/d8 --release --min-api 24 --lib $JAR --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
# Soul-Engine (llama.cpp), unkomprimiert damit Android sie direkt aus der APK laden kann
[ -f app/lib/arm64-v8a/libvelora_llm.so ] || ./build-native.sh
(cd app && zip -q -0 -r ../build/unsigned.apk lib)
$BT/zipalign -f -P 16 4 build/unsigned.apk build/aligned.apk
KS=${KEYSTORE:-$HOME/.ugljan-rad.keystore}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass ugljanrad -keypass ugljanrad -alias rad -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Ugljan Rad" >/dev/null 2>&1
$BT/apksigner sign --ks "$KS" --ks-pass pass:ugljanrad --key-pass pass:ugljanrad --out dist/BikeOra.apk build/aligned.apk
$BT/apksigner verify dist/BikeOra.apk && ls -la dist/BikeOra.apk
