#!/usr/bin/env bash
# Build Messenger APK s injektovanym Torom (jedna appka). Linux, arm64-v8a.
#
# Predpoklady (uprav cesty podla seba):
#   JDK 17, Android SDK (build-tools + platform-35), Android NDK (r27+)
#   APKEditor.jar (github.com/REAndroid/APKEditor)
#   tor-expert-bundle-android-aarch64 (dist.torproject.org) -> libTor.so
#   hev-socks5-tunnel (github.com/heiher/hev-socks5-tunnel) -> libhev-socks5-tunnel.so
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"

: "${JAVA_HOME:=/usr/lib/jvm/java-17-openjdk-amd64}"
: "${SDK:=$HOME/Android/Sdk}"
: "${BT:=$SDK/build-tools/36.1.0}"
: "${ANDROID_JAR:=$SDK/platforms/android-35/android.jar}"
: "${APKEDITOR:?nastav APKEDITOR=/cesta/APKEditor.jar}"
: "${IN_APK:?nastav IN_APK=/cesta/messenger-...-signed.apk}"
: "${LIBTOR:?nastav LIBTOR=/cesta/libTor.so}"
: "${LIBHEV:?nastav LIBHEV=/cesta/libhev-socks5-tunnel.so}"
: "${KEYSTORE:=$HOME/.android/debug.keystore}"

export PATH="$JAVA_HOME/bin:$BT:$SDK/platform-tools:$PATH"
WORK="$HERE/build"; rm -rf "$WORK"; mkdir -p "$WORK"

echo "== 1) APKEditor decode (-dex raw) =="
java -jar "$APKEDITOR" d -dex -i "$IN_APK" -o "$WORK/decoded"

echo "== 2) javac + d8 -> dalsi volny classesN.dex =="
mkdir -p "$WORK/cls" "$WORK/dex"
javac -source 17 -target 17 -cp "$ANDROID_JAR" -d "$WORK/cls" $(find "$HERE/src" -name '*.java')
d8 --min-api 28 --output "$WORK/dex" $(find "$WORK/cls" -name '*.class')
# index NEsmie kolidovat s existujucimi dex (550 mal 13, 575 ma 15)
IDX=2; while [ -f "$WORK/decoded/dex/classes${IDX}.dex" ]; do IDX=$((IDX+1)); done
echo "   -> classes${IDX}.dex"
cp "$WORK/dex/classes.dex" "$WORK/decoded/dex/classes${IDX}.dex"

echo "== 3) pridaj nativne kniznice =="
cp "$LIBTOR" "$WORK/decoded/root/lib/arm64-v8a/libTor.so"
cp "$LIBHEV" "$WORK/decoded/root/lib/arm64-v8a/libhev-socks5-tunnel.so"

echo "== 4) vloz komponenty do manifestu (pred </application>) =="
python3 - "$WORK/decoded/AndroidManifest.xml" "$HERE/manifest-inject.xml" <<'PY'
import sys, re
mf, snip = sys.argv[1], sys.argv[2]
s = open(mf, encoding="utf-8").read()
# cely XML komentar prec - jeho telo obsahuje "</application>" a zavrelo by element priskoro
inj = re.sub(r"<!--.*?-->", "", open(snip, encoding="utf-8").read(), flags=re.S).strip()
assert "</application>" not in inj, "snippet stale obsahuje </application>"
assert s.count("</application>") == 1
open(mf, "w", encoding="utf-8").write(s.replace("</application>", inj + "\n  </application>", 1))
print("manifest injected")
PY

echo "== 5) build + zipalign + sign =="
java -jar "$APKEDITOR" b -i "$WORK/decoded" -o "$WORK/unsigned.apk"
zipalign -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
cp "$WORK/aligned.apk" "$HERE/orca-tor-signed.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey "$HERE/orca-tor-signed.apk"

echo "HOTOVO -> $HERE/orca-tor-signed.apk"
