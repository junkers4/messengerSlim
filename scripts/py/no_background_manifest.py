#!/usr/bin/env python3
"""
Zhasne background-wake komponenty v AndroidManifest.xml: kazdy <service>/<receiver>,
ktoreho <intent-filter> obsahuje niektory z "budicich" akcii (FCM/FBNS push, BOOT_COMPLETED,
MQTT wakeup, ...) dostane android:enabled="false". Tym padom Messengera uz nema co zobudit
na pozadi -> nemoze bezat ani leaknut, ked je zavrety. Spravy sa syncnu az pri otvoreni appky.

Idempotentne. Pouzitie: no_background_manifest.py <AndroidManifest.xml>
"""
import sys, xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)
NAME = f"{{{ANDROID}}}name"
ENABLED = f"{{{ANDROID}}}enabled"

WAKE_ACTIONS = {
    "com.google.firebase.MESSAGING_EVENT",
    "com.google.firebase.INSTANCE_ID_EVENT",
    "com.google.android.c2dm.intent.RECEIVE",
    "com.google.android.c2dm.intent.REGISTRATION",
    "android.intent.action.BOOT_COMPLETED",
    "android.intent.action.LOCKED_BOOT_COMPLETED",
    "android.intent.action.QUICKBOOT_POWERON",
    "com.htc.intent.action.QUICKBOOT_POWERON",
    "android.intent.action.MY_PACKAGE_REPLACED",
    "com.facebook.rti.fbns.intent.RECEIVE",
    "com.facebook.rti.fbns.intent.RECEIVE_RTC",
    "com.facebook.rti.fbns.intent.RELIABLE_SERVICE_RECEIVE",
    "com.facebook.rti.mqtt.intent.ACTION_WAKEUP",
    "com.nokia.pushnotifications.intent.RECEIVE",
    "com.nokia.pushnotifications.intent.REGISTRATION",
    "android.net.conn.CONNECTIVITY_CHANGE",
    "android.intent.action.USER_PRESENT",
}

def filter_has_wake(comp):
    for f in comp.findall("intent-filter"):
        for a in f.findall("action"):
            if a.get(NAME) in WAKE_ACTIONS:
                return True
    return False

def main(path):
    tree = ET.parse(path)
    app = tree.getroot().find("application")
    disabled, already = [], 0
    for comp in list(app):
        if comp.tag not in ("service", "receiver"):
            continue
        if not filter_has_wake(comp):
            continue
        if comp.get(ENABLED) == "false":
            already += 1
            continue
        comp.set(ENABLED, "false")
        disabled.append(comp.get(NAME))
    tree.write(path, encoding="utf-8", xml_declaration=True)
    print(f"no-bg manifest: zhasnutych={len(disabled)}, uz vypnute={already}")
    for n in disabled:
        print("  OFF:", n)

if __name__ == "__main__":
    main(sys.argv[1])
