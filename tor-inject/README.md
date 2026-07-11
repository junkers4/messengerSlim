# tor-inject — Tor priamo v Messenger APK (jedna appka)

Vpichne Tor + tunel priamo do Facebook Messenger APK (`com.facebook.orca`), takze
**cez Tor ide iba Messenger** a je to **jedna appka, jedna ikona** — ziadna druha
appka (Orbot / companion). Otvoris normalny Messenger a sam sa pretlaci cez Tor.

## Ako to funguje

```
Messenger -> tun (VpnService, 0.0.0.0/0 OKREM guard IP) -> hev-socks5-tunnel
          -> Tor SOCKS 127.0.0.1:9050 -> guard uzol (priamo) -> exit
```

- **Hook bez editovania Messenger smali:** injektovany `<provider>` (TorBootProvider)
  sa spusti pri starte procesu a zaregistruje `LifecycleWatcher`.
- **Slucka (Tor v rovnakom UID) je vyriesena guard-exclusion:** Tor sa spusti a
  nabootstrapuje PRED `establish()`, cez jeho **ControlPort** (`orconn-status` +
  `ns/id/<fp>`) sa zistia IP guard uzlov a tie sa **vynechaju z trass tunela**
  (`RouteUtil.allExcept`). Torov vlastny traffic na guard ide priamo, Messenger tunelom.
  `ClientUseIPv6 0` => guardy su IPv4, IPv6 sa cele routuje do tunela.
- **Baterie:** `LifecycleWatcher` zapne Tor len ked je Messenger v popredi a vypne
  ho ~25s po prechode na pozadie. Tor idle = 0% CPU. Ziadna VPN ked Messenger nebezi.
- **DNS cez Tor:** hev `mapdns` (SOCKS by-name resolve), Tor UDP netreba.

## Subory

- `src/com/messengerslim/tor/` — injektovane triedy (VpnService, TorController,
  RouteUtil, LifecycleWatcher, ConsentActivity, TorBootProvider)
- `src/hev/htproxy/TProxyService.java` — JNI trieda ktoru vyzaduje libhev-socks5-tunnel.so
- `manifest-inject.xml` — 3 komponenty na vlozenie pred `</application>`
- `build.sh` — cely build pipeline (Linux)

## Nativne kniznice (nie su v git — stiahnu/skompiluju sa)

- **libTor.so** — z `tor-expert-bundle-android-aarch64` (dist.torproject.org),
  `tor/libTor.so`. Spusta sa ako proces (extractNativeLibs=true).
- **libhev-socks5-tunnel.so** — skompilovane z github.com/heiher/hev-socks5-tunnel
  cez Android NDK (`ndk-build`, APP_ABI=arm64-v8a). Ma vlastne JNI_OnLoad registrovane
  na triedu `hev/htproxy/TProxyService`.
- geoip / geoip6 z bundle -> assety (volitelne).

## Build (Linux, arm64-v8a)

Pozri `build.sh`. V skratke:

1. **APKEditor** (`d -dex`) dekompiluje Messenger APK (raw dex; obchadza apktool
   aapt2 res bug). `b` zbali spat bez aapt2.
2. `javac` + `d8` -> `classes14.dex` z `src/`.
3. Do stromu pridaj `classes14.dex` (dex/) + `libTor.so` a `libhev-socks5-tunnel.so`
   (lib/arm64-v8a/), a vloz `manifest-inject.xml` pred `</application>`.
4. APKEditor `b` -> `zipalign` -> `apksigner` (debug keystore).

## Instalacia na zariadenie

```
adb uninstall com.facebook.orca        # PRVY krat: re-sign != FB podpis -> STRATA prihlasenia
adb install --no-incremental orca-tor-signed.apk
adb shell appops set com.facebook.orca ACTIVATE_VPN allow   # predautorizuj VPN suhlas
```

Dalsie updaty rovnakym debug klucom: `adb install -r ...` (prihlasenie ostane).

## Overenie ze len Messenger ide cez Tor

- Prehliadac na `check.torproject.org` = "not Tor" (spravne — mobil nie je na Tore).
- Messengerov SOCKS: `adb forward tcp:19050 tcp:9050; curl --socks5-hostname
  127.0.0.1:19050 https://check.torproject.org/api/ip` => `{"IsTor":true,...}`.
- FB -> Settings -> Where you're logged in => ukaze krajinu Tor exitu.

## Znama limitacia

Facebook **blokuje Tor exit uzly** — login/pripojenie cez niektore exity zlyha
("unable to log in"). Retry alebo `ExitNodes {xx}` v torrc na stabilnejsi exit.
Toto je politika Meta, nie chyba injektaze.
