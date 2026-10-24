#!/usr/bin/env bash
# Cele zostavenie Spravarne z ORIGINALNEHO Messenger APK (od Facebooku) jednym prikazom:
#   overenie podpisu -> APKEditor decode -> chat-only rezy -> QUIC off -> branding
#   (nazov Spravaren, cierna ikonka) -> manifest
#   (Tor komponenty, bez budenia na pozadi) -> nas Java kod -> libTor + libhev -> build,
#   zipalign, podpis.
#
# Linux, arm64-v8a. Bezi lokalne aj v GitHub Actions (.github/workflows/build-apk.yml).
#
# Povinne:
#   IN_APK      originalne Messenger APK (com.facebook.orca, podpis Facebooku)
#   KEYSTORE    podpisovy kluc;  KS_PASS heslo;  KS_ALIAS alias (default: spravaren)
#   ANDROID_HOME  Android SDK s build-tools a platforms/android-NN
#   ANDROID_NDK   Android NDK (r27+)
# Volitelne:
#   OUT_APK     vystup (default: tor-inject/spravaren-signed.apk)
#   CACHE       priecinok na stiahnute nastroje (default: tor-inject/.cache)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"

: "${IN_APK:?nastav IN_APK=/cesta/messenger.apk}"
: "${KEYSTORE:?nastav KEYSTORE=/cesta/spravaren.keystore}"
: "${KS_PASS:?nastav KS_PASS=heslo ku klucu}"
: "${KS_ALIAS:=spravaren}"
: "${ANDROID_HOME:?nastav ANDROID_HOME=/cesta/Android/Sdk}"
: "${ANDROID_NDK:?nastav ANDROID_NDK=/cesta/android-ndk}"
: "${OUT_APK:=$HERE/spravaren-signed.apk}"
: "${CACHE:=$HERE/.cache}"

APKEDITOR_VER=1.4.9
APKEDITOR_SHA=a9cd40df818845456be6d696de6110c89edf4b0a0580cb83438ed6b25a366e67
TOR_VER=15.0.26
TOR_SHA=a1a67ec77ce0a7c4fca446fd9f915fbb0302c3107c2d56ab213196187cfd894a
# hev z main (2026-07-06): ma mapdns (DNS cez Tor) a JNI s void TProxyStartService, ako cakaju
# nase triedy. 2.9.3 mapdns NEMA (DNS v tuneli nejde), 2.10+ ma JNI s boolean navratom.
HEV_COMMIT=c6e4c72246fb0f20bda299f0efc7814bb3098d57

# Facebook / Meta podpisove certifikaty Messengera (SHA-256)
FB_CERTS="e3f9e1e0cf99d0e56a055ba65e241b3399f7cea524326b0cdd6ec1327ed0fdc1 911d604446084ca7f4760b775bfc160fa8702441240a7258645d7a72c4312d27"

BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
ANDROID_JAR="$(ls -d "$ANDROID_HOME"/platforms/android-*/android.jar | sort -V | tail -1)"
echo "build-tools: $BT"; echo "android.jar: $ANDROID_JAR"; echo "NDK: $ANDROID_NDK"

WORK="$HERE/build"; rm -rf "$WORK"; mkdir -p "$WORK" "$CACHE"

fetch() {  # url sha256 cielovy-subor
    if [ ! -f "$3" ] || ! echo "$2  $3" | sha256sum -c --quiet - 2>/dev/null; then
        curl -sSfL -o "$3" "$1"
    fi
    echo "$2  $3" | sha256sum -c --quiet - || { echo "ZLY HASH: $3" >&2; exit 1; }
}

echo "== 0) nastroje =="
APKEDITOR="$CACHE/APKEditor-$APKEDITOR_VER.jar"
fetch "https://github.com/REAndroid/APKEditor/releases/download/V$APKEDITOR_VER/APKEditor-$APKEDITOR_VER.jar" \
      "$APKEDITOR_SHA" "$APKEDITOR"
TORTGZ="$CACHE/tor-$TOR_VER.tar.gz"
fetch "https://archive.torproject.org/tor-package-archive/torbrowser/$TOR_VER/tor-expert-bundle-android-aarch64-$TOR_VER.tar.gz" \
      "$TOR_SHA" "$TORTGZ"
mkdir -p "$WORK/tor" && tar xzf "$TORTGZ" -C "$WORK/tor"
LIBTOR="$WORK/tor/tor/libTor.so"

LIBHEV="$CACHE/libhev-$HEV_COMMIT/libs/arm64-v8a/libhev-socks5-tunnel.so"
if [ ! -f "$LIBHEV" ]; then
    H="$CACHE/libhev-$HEV_COMMIT"; rm -rf "$H"; mkdir -p "$H/jni"
    git -C "$H/jni" init -q
    git -C "$H/jni" fetch -q --depth 1 https://github.com/heiher/hev-socks5-tunnel "$HEV_COMMIT"
    git -C "$H/jni" checkout -q FETCH_HEAD
    git -C "$H/jni" submodule -q update --init --recursive --depth 1
    [ "$(git -C "$H/jni" rev-parse HEAD)" = "$HEV_COMMIT" ] || { echo "hev: necakany commit" >&2; exit 1; }
    grep -q '"mapdns"' "$H/jni/src/hev-config.c" || { echo "hev: chyba mapdns -> DNS v tuneli by neslo" >&2; exit 1; }
    grep -q '"TProxyStartService", "(Ljava/lang/String;I)V"' "$H/jni/src/hev-jni.c" \
        || { echo "hev: JNI nesedi s hev/htproxy/TProxyService.java" >&2; exit 1; }
    (cd "$H" && "$ANDROID_NDK/ndk-build" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=jni/Android.mk \
        NDK_APPLICATION_MK=jni/Application.mk APP_ABI=arm64-v8a APP_PLATFORM=android-28 -j"$(nproc)")
fi

echo "== 1) overenie originalneho APK =="
"$BT/apksigner" verify --print-certs "$IN_APK" > "$WORK/certs.txt"
ok=0
for c in $FB_CERTS; do grep -q "certificate SHA-256 digest: $c" "$WORK/certs.txt" && ok=1; done
[ "$ok" = 1 ] || { echo "APK NIE JE podpisane Facebookom - koncim." >&2; cat "$WORK/certs.txt" >&2; exit 1; }
"$BT/aapt2" dump badging "$IN_APK" | sed -n 1p

echo "== 2) APKEditor decode (smali) =="
java -jar "$APKEDITOR" d -i "$IN_APK" -o "$WORK/decoded"

echo "== 3) chat-only rezy + QUIC off + branding + panel =="
python3 "$REPO/scripts/py/nuclear_chatonly.py" "$WORK/decoded" --report "$WORK/cuts.txt"
python3 "$REPO/scripts/py/no_quic.py" "$WORK/decoded"
python3 "$REPO/scripts/py/branding.py" "$WORK/decoded"
python3 "$REPO/scripts/py/panel_hook.py" "$WORK/decoded"

echo "== 4) manifest: Tor komponenty + bez budenia na pozadi =="
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
python3 "$REPO/scripts/py/no_background_manifest.py" "$WORK/decoded/AndroidManifest.xml"

echo "== 5) nas Java kod -> dalsi volny smali/classesN =="
mkdir -p "$WORK/cls" "$WORK/dex"
javac -nowarn -encoding UTF-8 -source 17 -target 17 -cp "$ANDROID_JAR" -d "$WORK/cls" \
    $(find "$HERE/src" -name '*.java')
"$BT/d8" --min-api 28 --lib "$ANDROID_JAR" --output "$WORK/dex" $(find "$WORK/cls" -name '*.class')
# APKEditor si vlastny dex rozbali do smali s dex-file.json, ktory potom vie zostavit
(cd "$WORK/dex" && zip -q ../inject.apk classes.dex)
java -jar "$APKEDITOR" d -i "$WORK/inject.apk" -o "$WORK/inject"
IDX=2; while [ -d "$WORK/decoded/smali/classes${IDX}" ]; do IDX=$((IDX+1)); done
cp -r "$WORK/inject/smali/classes" "$WORK/decoded/smali/classes${IDX}"
echo "   -> smali/classes${IDX}"

echo "== 6) nativne kniznice =="
cp "$LIBTOR" "$WORK/decoded/root/lib/arm64-v8a/libTor.so"
cp "$LIBHEV" "$WORK/decoded/root/lib/arm64-v8a/libhev-socks5-tunnel.so"

echo "== 7) build + zipalign + podpis =="
java -jar "$APKEDITOR" b -i "$WORK/decoded" -o "$WORK/unsigned.apk"
"$BT/zipalign" -f -P 16 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
    --out "$OUT_APK" "$WORK/aligned.apk"
rm -f "$OUT_APK.idsig"
"$BT/apksigner" verify "$OUT_APK"
sha256sum "$OUT_APK"
echo "HOTOVO -> $OUT_APK"
