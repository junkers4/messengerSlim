# Spravaren (Messenger + Tor) — požiadavky a testovací protokol

Stav k 2026-09-03. Zariadenie: Pixel 9a, Android 16, arm64.
Build: `work/slim_wp-signed.apk` (slim + branding) → `tor-inject/build.sh` → `orca-tor-signed.apk`.

## Požiadavky

| # | Požiadavka | Kde je riešená |
|---|---|---|
| R1 | Kým Tor nabieha, appka je **celá zakrytá** bránou (anglicky, cibuľa, percentá) a **neprepustí žiadny dotyk** — počas bootstrapu nesmie ísť von nič. | `TorGate.java`, stav cez `TorStatus` (súbor vo `filesDir`, lebo VpnService beží v procese `:tor`) |
| R2 | Po `READY` brána zmizne (krátke „Connected") a **polling sa zastaví** — v bežnom behu nesmie nič cyklicky bežať. | `TorGate.startPolling` / `detach` |
| R3 | 15 s po odchode na pozadie sa zloží Tor aj tunel (VPN kľúč zmizne) a **zabije sa Messenger aj `:tor` proces**. | `LifecycleWatcher.GRACE_MS`, `OrcaTorVpnService.quit()` |
| R4 | Appka sa **nesmie prebudiť na pozadí** (žiadny push, boot, sync adaptér). Ak ju systém aj tak naštartuje, proces sa sám ukončí. | `scripts/py/no_background_manifest.py` (+ `android.content.SyncAdapter`, `android.accounts.AccountAuthenticator`), `TorBootProvider.disableWakeComponents` + `killIfBackgroundWake` |
| R5 | **Emoji, nálepky, GIF a fotky sa musia vykresliť a dať poslať.** | `WeatherPanel` — prísna detekcia Stories pásu (viď nižšie) |
| R6 | Vlákno musí zobraziť históriu aj vlastné odoslané správy. | `nuclear_chatonly.py` → `MSG_WHITELIST` (mqtt/msys/mailbox/…) |
| R7 | Spodná navigácia zostáva **pôvodná** (Chats / Stories / Notifications / Menu) — žiadny vlastný bar. | `SpravarenTabBar.java` zmazaný |
| R8 | Chod musí byť plynulý — žiadne periodické prechádzanie view hierarchie. | zrušený 400 ms polling tab baru; panel sa rozhoduje len pri stavbe tray-u |
| R9 | Panel na mieste Stories pásu: počasie, detail dňa, dátum + meniny (SK) + svátek (CZ) + fáza mesiaca, bežiaci pás správ + „v tento deň". | `WeatherPanel.build()` |
| R10 | Dáta panelu sa ťahajú **cez Tor** a musia dobehnúť aj keď tunel ešte nestál. | `TRIES`/`RETRY_DELAY_MS` v `WeatherPanel` |

### Prečo je detekcia Stories pásu prísna

`X/2ml.A04` je **zdieľaná** factory na *každý* recycler v appke — zoznam správ, inbox,
emoji mriežka, mriežka fotiek aj Stories pás. Pôvodná podmienka („nízky tray") trafila aj
emoji/foto tray a tie sa skryli → **prázdne emoji a fotky**. Teraz musí tray splniť všetko:

1. výška 48–260 dp (Stories pás má ~128 dp),
2. horný okraj v hornej polovici okna (emoji/foto tray vyskakujú zdola),
3. vodorovný recycler — a keďže Litho nasadí vodorovný layout manager až pri mountnutí,
   čaká sa naň do 2 s a až potom rozhodne geometria.

## Testovací protokol

Príprava: `export PATH=$PATH:$HOME/Android/Sdk/platform-tools`, telefón cez USB (`adb devices`).

| # | Test | Postup | Očakávanie |
|---|---|---|---|
| T1 | Tor brána | `adb shell am force-stop com.facebook.orca`, spustiť appku, screenshot po 2 s | Čierna obrazovka, fialová cibuľa, „Connecting to Tor", percentá, text o tom, že nič neuniká |
| T2 | Brána blokuje doteky | počas brány `adb shell input tap 445 900` (miesto chatu), po nábehu screenshot | Zostane inbox, chat sa neotvoril |
| T3 | Brána zmizne | screenshot ~15 s po štarte; `adb logcat -d -s OrcaTor:*` | „Messenger je teraz smerovany cez Tor.", brána preč |
| T4 | Panel | screenshot inboxu | 4 riadky: mesto+teplota / pocit+max-min+dážď+vietor+východ-západ / dátum+meniny+CZ+mesiac / bežiaci pás správ |
| T5 | Pôvodná navigácia | screenshot inboxu | Chats · Stories · Notifications · Menu (modrý, natívny) |
| T6 | Vlákno | otvoriť prvý chat | Vykreslí sa história aj vlastné správy |
| T7 | Emoji | ikonka smajlíka v compose → záložka smajlík | „Recent" + mriežka emoji + spodná lišta kategórií |
| T8 | Vloženie emoji | ťuknúť na emoji | Emoji je v poli „Message", objaví sa šípka Odoslať |
| T9 | Fotky | ikonka galérie | Mriežka náhľadov z telefónu; výber označí fotku číslom a ukáže Edit/Send |
| T10 | Tunel | `adb shell ip addr show tun0` | `tun0 ... mtu 8500`, adresa 198.18.0.1 |
| T11 | Odpojenie po 15 s | HOME, počkať 25 s, `ip addr show tun0`, `ps -A \| grep orca` | Žiadny `tun0`, žiadny proces `orca` ani `orca:tor`, kľúč zo stavovej lišty zmizne |
| T12 | Žiadne budenie | po T11 počkať 60 s, `adb logcat -d \| grep "Start proc.*orca"` | Žiadny štart. (Ak ho systém predsa naštartuje, v `OrcaTor` logu je „Start z pozadia … ukoncujem proces") |
| T13 | Plynulosť | `adb shell dumpsys gfxinfo com.facebook.orca reset`, 6× swipe, `dumpsys gfxinfo` | Janky frames < 2 %, 90. percentil ≤ ~12 ms |

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
* Panel ťahá dáta z `api.open-meteo.com`, `news.google.com` a `cs.wikipedia.org` cez Tor;
  keď exit uzol niektorý zdroj blokuje, riadok jednoducho ostane bez dát (skúša 5× po 8 s).
* Ak sa bootstrap nechytí do 90 s, brána ponúkne opakovanie ťuknutím; siet je dovtedy zavretá.

## Build

```bash
cd tor-inject
B=~/Desktop/messenger/.build
APKEDITOR=$B/dl/APKEditor.jar \
IN_APK=~/Desktop/messenger/messengerSlim/work/slim_wp-signed.apk \
LIBTOR=$B/dl/tor-eb/tor/libTor.so \
LIBHEV=$B/jni/libs/arm64-v8a/libhev-socks5-tunnel.so \
bash build.sh
adb install -r orca-tor-signed.apk
```

Slim základ (`work/slim_wp-signed.apk`) sa prestavuje len keď sa menia cuts alebo branding —
zdroj je strom `work/decoded_fresh` (obsahuje aj hook `X/2ml.A04 → WeatherPanel.install`).
