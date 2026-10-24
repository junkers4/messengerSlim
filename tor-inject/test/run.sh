#!/usr/bin/env bash
# Test logiky panelu na PC: bash tor-inject/test/run.sh  (potrebuje JDK 17 + ANDROID_HOME)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
: "${ANDROID_HOME:=$HOME/Android/Sdk}"
AJ="$(ls -d "$ANDROID_HOME"/platforms/android-*/android.jar | sort -V | tail -1)"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -nowarn -encoding UTF-8 -cp "$AJ" -d "$T" \
    $(find "$HERE/../src/com/messengerslim/panel" "$HERE" -name '*.java')
java -cp "$T:$AJ" com.messengerslim.panel.PanelLogicTest
