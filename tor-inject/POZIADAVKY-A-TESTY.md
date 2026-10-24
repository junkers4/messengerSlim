# Spravaren (Messenger + Tor) — požiadavky a testovací protokol

Stav k 2026-09-03. Zariadenie: Pixel 9a, Android 16, arm64.
Build: `work/slim_wp-signed.apk` (slim + branding) → `tor-inject/build.sh` → `orca-tor-signed.apk`.

## Požiadavky

| # | Požiadavka | Kde je riešená |
|---|---|---|
| R1 | Kým Tor nabieha, hore je **pásik** (anglicky, cibuľa, percentá) a appka pod ním je **použiteľná** — staré správy sa dajú čítať hneď, napísaná správa počká vo fronte. Von nejde nič, lebo tunel je fail-closed. Keď sieť počas zaseknutého bootstrapu dočasne ide mimo tunela (stav `OPEN`), pásik zakryje celú appku a zje doteky. | `TorGate.java`, stav cez `TorStatus` (súbor vo `filesDir`, lebo VpnService beží v procese `:tor`) |
| R2 | Po `READY` brána zmizne (krátke „Connected") a **polling sa zastaví** — v bežnom behu nesmie nič cyklicky bežať. | `TorGate.startPolling` / `detach` |
| R3 | 3 min po odchode na pozadie sa zloží Tor aj tunel (VPN kľúč zmizne) a **zabije sa Messenger aj `:tor` proces**. | `LifecycleWatcher.GRACE_MS`, `OrcaTorVpnService.quit()` |
| R4 | Appka sa **nesmie prebudiť na pozadí** (žiadny push, boot, sync adaptér). Ak ju systém aj tak naštartuje, proces sa sám ukončí. | `scripts/py/no_background_manifest.py` (+ `android.content.SyncAdapter`, `android.accounts.AccountAuthenticator`), `TorBootProvider.disableWakeComponents` + `killIfBackgroundWake` |
| R5 | **Emoji, nálepky, GIF a fotky sa musia vykresliť a dať poslať.** | pôvodný Messenger UI bez zásahu do recyclerov (panel zrušený) |
| R6 | Vlákno musí zobraziť históriu aj vlastné odoslané správy. | `nuclear_chatonly.py` → `MSG_WHITELIST` (mqtt/msys/mailbox/…) |
| R7 | Spodná navigácia zostáva **pôvodná** (Chats / Stories / Notifications / Menu) — žiadny vlastný bar. | `SpravarenTabBar.java` zmazaný |
| R8 | Chod musí byť plynulý — žiadne periodické prechádzanie view hierarchie. | zrušený 400 ms polling tab baru aj panel |
| R9 | Názov appky **Spravaren**, ikonka = pôvodná Messenger bublina, len **čierna** namiesto modrej. Žiadny panel (počasie, meniny, správy) ani vlastné obrázky. | `scripts/py/branding.py` (volá ho `build-all.sh`) |
| R11 | Po `READY` sa cez Tor hneď otvorí spojenie na `edge-mqtt.facebook.com:443`, aby bol okruh pripravený; keď exit zlyhá, vyžiada sa nový okruh (`NEWNYM`), max 3 pokusy. | `OrcaTorVpnService.startWarmUp`, `TorController.probe` |
| R12 | Ťuknutie na fotku vo vlákne ju otvorí na celú obrazovku (zoom, uloženie). | `MEDIA_WHITELIST` v `nuclear_chatonly.py`, `activityWhitelist` + `$mediaKeepRegex` v `apply-nuclear-chatonly.ps1` |
| R13 | **QUIC (UDP) je vypnutý** — Tor UDP neprenesie, takže fotky/videá/DGW išli až po timeoute. Teraz idú hneď cez TCP. | `scripts/py/no_quic.py` (`QuicSettings$Builder.build`, `MNSDGWNetworkStackConfig.<init>`) |

## Testovací protokol

Príprava: `export PATH=$PATH:$HOME/Android/Sdk/platform-tools`, telefón cez USB (`adb devices`).

| # | Test | Postup | Očakávanie |
|---|---|---|---|
| T1 | Tor pásik | `adb shell am force-stop com.facebook.orca`, spustiť appku, screenshot po 2 s | Hore čierny pásik s cibuľou, „Connecting to Tor… NN %“, pod ním inbox |
| T2 | Appka použiteľná počas nábehu | počas pásika ťuknúť na chat | Chat sa otvorí, história sa zobrazí; `adb shell dumpsys connectivity` ani `logcat` neukazujú prevádzku mimo `tun0` |
| T3 | Brána zmizne | screenshot ~15 s po štarte; `adb logcat -d -s OrcaTor:*` | „Messenger je teraz smerovany cez Tor.", brána preč |
| T4 | Názov a ikonka | launcher + `adb shell dumpsys package com.facebook.orca \| grep -i label` | Pod ikonkou „Spravaren", čierna bublina s bielym bleskom; nad inboxom žiadny panel |
| T5 | Pôvodná navigácia | screenshot inboxu | Chats · Stories · Notifications · Menu (modrý, natívny) |
| T6 | Vlákno | otvoriť prvý chat | Vykreslí sa história aj vlastné správy |
| T7 | Emoji | ikonka smajlíka v compose → záložka smajlík | „Recent" + mriežka emoji + spodná lišta kategórií |
| T8 | Vloženie emoji | ťuknúť na emoji | Emoji je v poli „Message", objaví sa šípka Odoslať |
| T9 | Fotky | ikonka galérie | Mriežka náhľadov z telefónu; výber označí fotku číslom a ukáže Edit/Send |
| T10 | Tunel | `adb shell ip addr show tun0` | `tun0 ... mtu 8500`, adresa 198.18.0.1 |
| T11 | Odpojenie po 3 min | HOME, počkať 3 min 10 s, `ip addr show tun0`, `ps -A \| grep orca` | Žiadny `tun0`, žiadny proces `orca` ani `orca:tor`, kľúč zo stavovej lišty zmizne |
| T12 | Žiadne budenie | po T11 počkať 60 s, `adb logcat -d \| grep "Start proc.*orca"` | Žiadny štart. (Ak ho systém predsa naštartuje, v `OrcaTor` logu je „Start z pozadia … ukoncujem proces") |
| T13 | Plynulosť | `adb shell dumpsys gfxinfo com.facebook.orca reset`, 6× swipe, `dumpsys gfxinfo` | Janky frames < 2 %, 90. percentil ≤ ~12 ms |
| T14 | Zahriatie okruhu | po nábehu `adb logcat -d -s OrcaTor:*` | „Okruh k Facebooku pripraveny za … ms“ (alebo „novy okruh“ a potom úspech) |
| T15 | Fotka na celú obrazovku | vo vlákne ťuknúť na fotku | Otvorí sa prehliadač, dá sa zväčšiť |

## Výsledky posledného behu (2026-09-03, 19:26–19:40)

| Test | Výsledok |
|---|---|
| T1 Tor brána | ✅ celoobrazovková, cibuľa, 0 % → 100 % |
| T2 blokovanie dotykov | ✅ ťuknutie do chatu neprešlo |
| T3 brána zmizne | ✅ „smerovany cez Tor" ~5 s po štarte |
| T4 panel | ✅ `Bučovice · 22°C ☁️ zamračené` / `pocit 22° · ↑26° ↓13° · ☔13% · 💨4 km/h · 🌅6:11 · 🌇19:31` / `Št 3. 9. · Belo · CZ Bronislav · 🌗 posl. štvrť` / bežiaci pás |
| T5 pôvodná navigácia | ✅ Chats/Stories/Notifications/Menu |
| T6 vlákno | ✅ história aj vlastné správy |
| T7 emoji mriežka | ✅ (pred opravou úplne čierna) |
| T8 vloženie emoji | ✅ 👫 v poli + šípka Odoslať |
| T9 fotky | ✅ náhľady, výber, Edit/Send (pred opravou čierna plocha) |
| T10 tunel | ✅ `tun0 mtu 8500`, 198.18.0.1 |
| T11 odpojenie po 15 s | ✅ tun0 preč, žiadny proces orca ani orca:tor |
| T12 žiadne budenie | ✅ 60 s ticho (predtým reštart do ~3 s cez `MessengerSyncAdapterService`) |
| T13 plynulosť | ✅ 290 snímok, **0 % janky**, 90. p. 11 ms (predtým 400 ms sken hierarchie) |

## Známe obmedzenia

* Žiadne notifikácie, keď je appka zavretá — to je zámer (bez budenia niet úniku); správy sa
  natiahnu po otvorení.
* Kontakty sa nesynchronizujú (sync adaptér aj authenticator sú vypnuté) — to je cena za R4.
* Ak sa bootstrap nechytí do 90 s, brána ponúkne opakovanie ťuknutím; siet je dovtedy zavretá.

## Build

Jedným príkazom z originálneho APK (lokálne aj v GitHub Actions), viď README:

```bash
IN_APK=messenger.apk KEYSTORE=spravaren.keystore KS_PASS=... \
ANDROID_HOME=~/Android/Sdk ANDROID_NDK=~/Android/Sdk/ndk/27.2.12479018 \
bash tor-inject/build-all.sh
adb install -r tor-inject/spravaren-signed.apk
```

Na update bez straty prihlásenia musí byť APK podpísané tým istým kľúčom ako to v telefóne
(secret `SIGNING_KEYSTORE_B64`), preto sa build pre telefón robí cez Actions.
