#!/usr/bin/env bash
set -euo pipefail
cd /work
# Remove only generated outputs so an old runtime cannot leak into the APK.
rm -rf build/classes build/dex build/stage
mkdir -p build/classes build/dex build/stage/lib/arm64-v8a artifacts results
android_jar=/toolchain/android-35/android.jar
test -f "$android_jar"
python3 prepare_markdown.py --check
javac --release 8 -encoding UTF-8 -classpath "$android_jar:downloads/markdown/*" -d build/classes src/dev/androidagent/probe/*.java
jar cf build/classes.jar -C build/classes .
java -cp /toolchain/r8.jar com.android.tools.r8.D8 --lib "$android_jar" --min-api 29 --output build/dex build/classes.jar downloads/markdown/*.jar
# Debian's ARM64 aapt cannot parse API 35's resource table. This UI only uses
# API <= 26 resources; link API 30 resources while retaining targetSdk=35.
# Package the existing licensed font assets as Android font resources as well,
# so framework-owned dialog/menu TextViews inherit the same family.
rm -rf build/resources
cp -R res build/resources
cp assets/fonts/Pretendard-Regular.otf build/resources/font/pretendard_regular.otf
cp assets/fonts/Pretendard-Medium.otf build/resources/font/pretendard_medium.otf
cp assets/fonts/Pretendard-SemiBold.otf build/resources/font/pretendard_semibold.otf
aapt package -f -M AndroidManifest.xml -S build/resources -A assets -I /toolchain/android-11/android.jar -F build/probe-unsigned.apk
cp payload/*.so build/stage/lib/arm64-v8a/
cp build/dex/classes.dex build/stage/
(cd build/stage && zip -q -r ../probe-unsigned.apk classes.dex lib)
if [ ! -f artifacts/debug.keystore ]; then
  keytool -genkeypair -keystore artifacts/debug.keystore -storepass android -keypass android -alias probe -dname 'CN=Android Agent Diagnostic' -keyalg RSA -keysize 2048 -validity 3650
fi
zipalign -f 4 build/probe-unsigned.apk build/probe-aligned.apk
apksigner sign --ks artifacts/debug.keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias probe --out artifacts/android-agent-0.10.0-arm64.apk build/probe-aligned.apk
apksigner verify --verbose artifacts/android-agent-0.10.0-arm64.apk
aapt dump badging artifacts/android-agent-0.10.0-arm64.apk > results/apk-badging.txt
sha256sum artifacts/android-agent-0.10.0-arm64.apk payload/*.so > results/sha256.txt
