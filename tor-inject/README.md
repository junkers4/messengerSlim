<div align="center">

# 🧅 Spravaren — Tor priamo v Messenger APK

**Cez Tor ide iba Messenger. Jedna appka, jedna ikona. Žiadny Orbot.**

Vpichne Tor + tunel priamo do Facebook Messenger APK (`com.facebook.orca`),
takže otvoríš normálny Messenger a on sám sa pretlačí cez Tor — a Facebook
nevidí, odkiaľ si.

</div>

---

## 🗺️ Ako to funguje

![architektura](docs/architecture.png)

```
Messenger → tun (VpnService, 0.0.0.0/0 MINUS guard IP) → hev-socks5-tunnel
         → Tor SOCKS 127.0.0.1:9050 → guard (priamo) → exit → Facebook
```

- **Hook bez editovania Messenger smali:** injektovaný `<provider>` (`TorBootProvider`)
  sa spustí pri štarte procesu a zaregistruje `LifecycleWatcher`.
- **DNS cez Tor:** hev `mapdns` (SOCKS by-name resolve) — Tor UDP netreba.

### 🔑 Loop-breaker (prečo to vôbec ide)

Tor beží pod **rovnakým UID** ako Messenger. Keby tunel zachytil *všetko*, chytil
by aj Torov vlastný traffic → nekonečná slučka. Riešenie: Tor sa nabootstrapuje,
cez jeho **ControlPort** sa zistia IP **guard uzlov** a tie sa **vynechajú z trás
tunela** (`RouteUtil.allExcept`). Torov traffic na guard ide priamo, Messenger tunelom.
`ClientUseIPv6 0` → guardy sú IPv4, IPv6 sa celé routuje do tunela.

---

## 🛡️ Kill-switch (fail-closed)

Aby pri štarte neunikol ani jeden paket mimo Tor:

- Pri štarte sa tunel postaví **hneď**, s vynechaním **minule známych guardov**
  (cache `guards.txt`) → Messenger **nemá sieť, kým Tor nestojí**. Žiadny startup leak.
- **Poistka:** ak sa bootstrap zasekne (nový guard / starý consensus), na chvíľu sa
  sieť otvorí, nech Tor dobehne, a hneď sa tunel prestaví.
- **Watchdog:** ak Tor spadne počas behu → Messenger sa **zablackholuje** (stratí
  sieť), nie leak.

---

## 🔋 Batéria — Tor beží len keď treba

![zivotny cyklus](docs/lifecycle.png)

`LifecycleWatcher` zapne Tor len keď je Messenger v **popredí** a vypne ho ~25 s po
prechode na **pozadie**.

| | Tor beží stále | **Tor len keď treba** (toto riešenie) |
|---|---|---|
| Spotreba na pozadí | stála (VPN + Tor) | **nulová** — Tor OFF |
| Tor v pokoji | 0 % CPU, 94 MB | 0 % CPU, 94 MB (len počas používania) |
| Notifikácie na pozadí | áno | nie (push ide cez tunel) |

> Kompromis: keďže sa Tor po zavretí vypne, správy prídu až keď Messenger otvoríš.

---

## 🧩 Čo sme pridali (injektované triedy)

| Súbor | Úloha |
|---|---|
| `OrcaTorVpnService` | VpnService: tunel s guard-exclusion, kill-switch, watchdog |
| `TorController` | spustí `libTor.so`, async bootstrap, guard IP cez ControlPort, cache guardov |
| `RouteUtil` | `0.0.0.0/0` MINUS guard IP (loop-breaker) |
| `LifecycleWatcher` | Tor ON v popredí / OFF na pozadí (batéria) |
| `TorBootProvider` | hook pri štarte Messengera (žiadny druhý launcher) |
| `ConsentActivity` | jednorazový VPN súhlas |
| `hev/htproxy/TProxyService` | JNI trieda pre `libhev-socks5-tunnel.so` |

**Natívne knižnice** (nie sú v git — stiahnu/skompilujú sa cez `build.sh`):
`libTor.so` (tor-expert-bundle), `libhev-socks5-tunnel.so` (NDK build z
[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)).

---

## ✅ Overené na zariadení (Pixel 9a)

| | IP | Poloha |
|---|---|---|
| **Reálna sieť** (bez Toru) | `[redacted-ip]` | [redacted-location] |
| **Čo vidí Facebook** | `[redacted-ip]` (IsTor:true) | New York, USA |

Messenger sa pripája **výhradne na `127.0.0.1:9050` (Tor)** — žiadne priame spojenie
na Facebook. Meta vidí Tor exit, nie teba.

**Ako si to overíš sám:** prehliadač na `check.torproject.org` = *„not Tor"* (správne —
mobil nie je na Tore, len Messenger). Alebo FB → *Settings → Where you're logged in* →
ukáže krajinu Tor exitu.

---

## 🔨 Build (Linux, arm64-v8a)

Pozri [`build.sh`](build.sh). Vyžaduje JDK 17, Android SDK (build-tools + platform-35),
NDK, [APKEditor](https://github.com/REAndroid/APKEditor). V skratke:

1. `APKEditor d -dex` dekompiluje APK (raw dex; obchádza apktool aapt2 bug).
2. `javac` + `d8` → `classes14.dex` z `src/`.
3. Pridaj `classes14.dex` + `libTor.so` + `libhev-socks5-tunnel.so`; vlož
   `manifest-inject.xml` pred `</application>`.
4. `APKEditor b` → `zipalign` → `apksigner` (debug keystore).

Diagramy: `docs/*.tex` (TikZ) → `lualatex` → `docs/*.png`.

## 📲 Inštalácia

```bash
adb uninstall com.facebook.orca        # PRVÝ krát: re-sign != FB podpis → STRATA prihlásenia
adb install --no-incremental orca-tor-signed.apk
adb shell appops set com.facebook.orca ACTIVATE_VPN allow   # predautorizuj VPN súhlas
```

Ďalšie updaty rovnakým debug kľúčom: `adb install -r ...` (prihlásenie ostane).

## ⚠️ Známa limitácia

Facebook **blokuje Tor exit uzly** — login/pripojenie cez niektoré exity zlyhá
(*„unable to log in"*). Skús znova, alebo pripni exit: `ExitNodes {us}` v `torrc`.
Je to politika Meta, nie chyba injektáže.
