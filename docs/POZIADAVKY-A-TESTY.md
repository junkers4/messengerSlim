# Spravaren (Messenger + Tor) — požiadavky a testovací protokol

Stav k 2026-10-24. Zariadenie: Pixel 9a, Android 16, arm64. Messenger 584.1.0.61.98.
Build: originálne APK → `tor-inject/build-all.sh` (lokálne aj GitHub Actions) → `spravaren-signed.apk`.

## Požiadavky

| # | Požiadavka | Kde je riešená |
|---|---|---|
| R1 | Kým Tor nabieha, hore je **pásik** (anglicky, cibuľa, percentá) a appka pod ním je **použiteľná** — staré správy sa dajú čítať hneď, napísaná správa počká vo fronte. Von nejde nič, lebo tunel je fail-closed. Keď sieť počas zaseknutého bootstrapu dočasne ide mimo tunela (stav `OPEN`), pásik zakryje celú appku a zje doteky. | `TorGate.java`, stav cez `TorStatus` (súbor vo `filesDir`, lebo VpnService beží v procese `:tor`) |
| R2 | Po `READY` brána zmizne (krátke „Connected") a **polling sa zastaví** — v bežnom behu nesmie nič cyklicky bežať. | `TorGate.startPolling` / `detach` |
| R3 | 3 min po odchode na pozadie sa zloží Tor aj tunel (VPN kľúč zmizne) a **zabije sa Messenger aj `:tor` proces**. | `LifecycleWatcher.GRACE_MS`, `OrcaTorVpnService.quit()` |
| R4 | Appka sa **nesmie prebudiť na pozadí** (žiadny push, boot, sync adaptér). Ak ju systém aj tak naštartuje, proces sa sám ukončí. | `patches/no_background_manifest.py` (+ `android.content.SyncAdapter`, `android.accounts.AccountAuthenticator`), `TorBootProvider.disableWakeComponents` + `killIfBackgroundWake` |
| R5 | **Emoji, nálepky, GIF a fotky sa musia vykresliť a dať poslať.** Panel sa nesmie vložiť nikam inam než na miesto Stories pásu. | `WeatherPanel` — prísna detekcia Stories pásu (viď nižšie) |
| R6 | Vlákno musí zobraziť históriu aj vlastné odoslané správy. | `nuclear_chatonly.py` → `MSG_WHITELIST` (mqtt/msys/mailbox/…) |
| R7 | Spodná navigácia je **pôvodná Messengerova**, len **bez záložky Stories** — žiadny vlastný bar. | `WeatherPanel` |
| R8 | Chod musí byť plynulý — žiadne periodické prechádzanie view hierarchie. Panel sa rozhoduje len pri stavbe tray-u, sieť ide mimo hlavného vlákna. | `WeatherPanel.install` |
| R9 | Názov appky **Spravaren**, ikonka = pôvodná Messenger bublina, len **čierna** namiesto modrej. Žiadne vlastné obrázky. Názov sa mení **len** v manifeste a na nadpise (nie v kóde: nahradenie každého reťazca „Messenger“ rozbíjalo interné identifikátory produktu). | `patches/branding.py`, `Declutter.Wordmark` |
| R10 | DNS v tuneli ide cez hev `mapdns` (falošné IP 100.64/10 → meno ide do Toru). Build spadne, ak hev mapdns nemá. | `build-all.sh` (`HEV_COMMIT`, kontrola `"mapdns"`) |
| R11 | Po `READY` sa cez Tor hneď otvorí spojenie na `edge-mqtt.facebook.com:443`, aby bol okruh pripravený; keď exit zlyhá, vyžiada sa nový okruh (`NEWNYM`), max 3 pokusy. | `OrcaTorVpnService.startWarmUp`, `TorController.probe` |
| R12 | Ťuknutie na fotku vo vlákne ju otvorí na celú obrazovku (zoom, uloženie). | `MEDIA_WHITELIST` v `nuclear_chatonly.py`, `activityWhitelist` v `cuts.json` |
| R13 | **QUIC (UDP) je vypnutý v proxygen klientovi** — Tor UDP neprenesie, takže fotky/videá/GIFy išli až po timeoute. Teraz idú hneď cez TCP. **DGW brána QUIC vypnutý nemá** (vypnutie spôsobovalo 49 chýb/min `INTERNAL_ERROR`). | `patches/no_quic.py` (`QuicSettings$Builder.build`) |

### Panel, narodeniny, bez Stories (2026-10-24)

| # | Požiadavka | Kde je riešená |
|---|---|---|
| P1 | Na mieste Stories pásu (584: hook v `X/2ne.A03`, hľadaný podľa tvaru) v inboxe je **panel**: mesto + teplota + oblačnosť, pocitová teplota, max/min, dážď, vietor, východ/západ slnka. | `WeatherPanel` (Open-Meteo) |
| P2 | Riadok dátumu: deň, **meniny SK**, **svátek CZ**, fáza mesiaca, **štátny sviatok SK**, ak je. | `WeatherPanel.dateLine`, `holidaySk` |
| P3 | **Narodeniny**: dnes „🎂 Dnes Meno (vek)“, do 7 dní „🎂 o N dni Meno“. Len v appke — žiadne notifikácie, žiadne budenie (R4 ostáva). | `Birthdays.line` |
| P4 | Narodeniny si pridávam sám: ťuk na riadok dátumu/info → zoznam (karty: meno, dátum slovom, o koľko dní, vek, meniny) + **Pridať**. Pridanie/úprava: meno + **kolieska deň / mesiac / rok** (rok voliteľný) + živý náhľad. Ťuk na kartu = upraviť, podržanie = zmazať. Len v telefóne (`SharedPreferences`). | `Birthdays.showDialog/showEdit` |
| P5 | Bežiaci pás **slovenských správ** (Google News SK) + „v tento deň“. | `WeatherPanel` |
| P6 | Všetky dáta panelu idú **cez Tor** a dobehnú, aj keď tunel ešte nestál (opakovanie). Bez siete panel ukáže kalendár a narodeniny, ktoré sú lokálne. | `WeatherPanel.get` (opakovanie) |
| P7 | **Stories preč**: pás nahradí panel, záložka Stories v spodnej lište nie je. | `WeatherPanel` (panel nahradí pás), bez záložky Stories |
| P8 | Ťuk na riadok počasia → obnoví počasie a správy. | `WeatherPanel` |

### Navyše

| # | Požiadavka | Kde je riešená |
|---|---|---|
| X1 | **Upozornenie na dážď**: ak v najbližších 3 h prší (≥ 50 %), v riadku počasia „☔ o 2 h dážď“. | `WeatherPanel` (hourly) |
| X2 | **Zajtra meniny**: „zajtra: Meno“ — nech sa dá pripraviť gratulácia. | `WeatherPanel.dateLine`, `holidaySk` |
| X3 | **Tor značka** za počasím: 🧅✓ = overené cez `check.torproject.org` (prevádzka naozaj ide cez Tor), ⚠️ = nejde. Podržanie panelu → **nový okruh** (NEWNYM) + bublina s novou exit IP. | `WeatherPanel.fetchTorAsync`, `OrcaTorVpnService.ACTION_NEWNYM` |
| X4 | **Meniny ľudí zo zoznamu**: meno sa hľadá v SK, inak CZ kalendári („Miška (Michaela)“ → CZ 19. 10.); do 7 dní „🌸 meniny o N dni Miška“. | `WeatherPanel.namedayOf`, `Birthdays.line` |
| X5 | **Upratený inbox**: bez pásu „Notifications are currently off“ (vypnuté zámerne) a bez plávajúceho tlačidla Meta AI. | `Declutter` |
| X6 | Panel má miesto na **5 riadkov**: počasie+🧅, detail, dátum, info (narodeniny > sviatok > zajtra meniny), správy. | `WeatherPanel.build` |
| X7 | Nadpis inboxu „messenger“ → **„spravaren“** (prekreslený rovnako veľko, farba podľa tmavého/svetlého režimu). | `Declutter.Wordmark` |
| X8 | Pole „Ask Meta AI or search“ je prekryté pilulkou „🔍 Hľadať“ (user nechce Meta AI; ťuk stále otvorí hľadanie); panel sa vkladá len do Stories pásu (výška ≥ 70 dp), nie do pásu filtrov All/Unread/Groups — predtým bol panel 2×. | `Declutter`, `WeatherPanel.geometry` |

### Rýchlosť písania a načítania

| # | Požiadavka | Kde je riešená |
|---|---|---|
| S1 | Meria sa: štart → inbox s dátami, otvorenie vlákna, odoslanie správy (ťuk → „Sent“) — **len v chate so sebou**. | testy T20–T22 |
| S2 | Úpravy Tor/tunela sa robia len s nameraným pred/po. Kandidáti: teplý okruh k `edge-mqtt` + `graph` + `rupload`, rýchlejší prechod na nový exit pri zaseknutí, `CircuitBuildTimeout`. | `TorController`, `OrcaTorVpnService` |

## Testovací protokol

Príprava: `export PATH=$PATH:$HOME/Android/Sdk/platform-tools`, telefón cez USB (`adb devices`).

| # | Test | Postup | Očakávanie |
|---|---|---|---|
| T1 | Tor pásik | `adb shell am force-stop com.facebook.orca`, spustiť appku, screenshot po 2 s | Hore čierny pásik s cibuľou, „Connecting to Tor… NN %“, pod ním inbox |
| T2 | Appka použiteľná počas nábehu | počas pásika ťuknúť na chat | Chat sa otvorí, história sa zobrazí; `adb shell dumpsys connectivity` ani `logcat` neukazujú prevádzku mimo `tun0` |
| T3 | Brána zmizne | screenshot ~15 s po štarte; `adb logcat -d -s OrcaTor:*` | „Messenger je teraz smerovany cez Tor.", brána preč |
| T4 | Názov a ikonka | launcher | Pod ikonkou „Spravaren“, čierna bublina s bielym bleskom |
| T5 | Pôvodná navigácia | screenshot inboxu | Chats · People · Notifications · Menu (natívny; 584 už nemá záložku Stories) |
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

| T16 | Panel | screenshot inboxu | Počasie, detail, dátum + meniny + svátek + mesiac (+ sviatok), narodeniny ak sú, bežiaci pás správ |
| T17 | Narodeniny | ťuk na dátum → Pridať „Test“ s dnešným dátumom → späť | V paneli „🎂 Dnes: Test“; dlhé podržanie v zozname ho zmaže |
| T18 | Bez Stories | screenshot inboxu | Žiadny Stories pás ani záložka Stories |
| T19b | DGW slučka | `adb logcat --pid=$(pidof com.facebook.orca) \| grep -c "Restarting due to reason: StreamError"` po 60 s | 0 (s vypnutým DGW QUIC bolo 49) |
| T19 | DNS v tuneli | `adb logcat --pid=$(pidof com.facebook.orca) \| grep -c getaddrinfo` po nábehu | 0 |
| T20 | Studený štart | `force-stop`, štart, čas do zobrazenia inboxu s menami | zapísať (s) |
| T21 | Otvorenie vlákna | ťuk na chat so sebou, čas do histórie | zapísať (s) |
| T22 | Odoslanie | v chate so sebou napísať text, odoslať, čas do „Sent“ | zapísať (s); len chat so sebou |
| T23 | Tor riadok | screenshot panelu, dlhé podržanie | vlajka exitu; po podržaní nový okruh v `OrcaTor` logu |

## Výsledky posledného behu (2026-10-24, Pixel 9a, Messenger 584.1.0.61.98)

Meralo sa na živom telefóne, správy len do chatu so sebou. „Čas“ z `adb` zahŕňa ~2 s réžie merania.

| Test | Výsledok |
|---|---|
| T1 Tor brána | ✅ „Connecting to Tor… 0 %“ → „Connected through Tor“ |
| T3 brána zmizne | ✅ Tor pripravený ~3,7 s po štarte procesu |
| T4 názov a ikonka | ✅ „Spravaren“, čierna bublina; nadpis inboxu „spravaren“ |
| T5 navigácia | ✅ Chats · People · Notifications · Menu |
| T6 vlákno | ✅ história aj vlastné správy |
| T7/T8 emoji | ✅ mriežka sa vykreslí, 😀 odoslané („Sent“ za ~4,7 s) |
| T9 fotky | ✅ mriežka náhľadov z galérie (nič sa neposielalo) |
| T10 tunel | ✅ `tun0 mtu 8500`, 198.18.0.1 |
| T11 odpojenie po 3 min | ✅ po 3 min 15 s žiadny `tun0` ani proces `orca` |
| T12 žiadne budenie | ✅ v okne po odpojení žiadny štart procesu |
| T14 teplý okruh | ✅ 86–184 ms |
| T15 fotka na celú obrazovku | ✅ otvorí sa za ~1–2 s (3× zmerané), používateľ potvrdil. Kým DGW padalo v slučke (49 chýb/min), v `adb` teste sa neotvorila; po oprave DGW áno. Testované len na fotke z chatu so sebou, nie na fotke stiahnutej od cudzieho kontaktu |
| T16 panel | ✅ počasie, detail, dátum + meniny + svátek + mesiac, info riadok, správy, 🧅✓ |
| T17 narodeniny | ✅ pridať / upraviť (kolieska) / zmazať, meniny podľa mena (Miška (Michaela) → CZ 19. 10.) |
| T18 bez Stories | ✅ pás nahradený panelom |
| T19 DNS v tuneli | ✅ 0 chýb `getaddrinfo` |
| T19b DGW slučka | ✅ 0 reštartov za 60 s (pred opravou 49) |
| T20 studený štart | ✅ inbox s chatmi ≈ 7,7–7,9 s (z toho ~2 s réžia merania) |
| T22 odoslanie textu | ✅ „Sent“ do ≤ 2,7 s |
| T23 nový okruh | ✅ `NEWNYM: 250 OK` + bublina „Nový Tor okruh“ |
| Prijímanie | ✅ nové správy v inboxe naskakujú, kým je appka otvorená (latencia sa nemerala) |
| GIF | ✅ načítajú sa |
| **Nálepky (stickers)** | ❌ karta ostáva na prázdnych štvorcoch; po oprave DGW už bez chýb v logu, ale nič sa nenačíta |
| Pole „Hľadať“ (X8) | ⏳ prekrytie sa ladí (hostiteľ sa nenašiel — opravené hĺbkou hľadania, čaká na overenie) |
| T13 plynulosť, T21 otvorenie vlákna | ➖ dnes nemerané spoľahlivo |
| Odoslanie fotky, hlasovej správy, hovory | ➖ netestované |

## Známe obmedzenia

* Žiadne notifikácie, keď je appka zavretá — to je zámer (bez budenia niet úniku); správy sa
  natiahnu po otvorení.
* Kontakty sa nesynchronizujú (sync adaptér aj authenticator sú vypnuté) — to je cena za R4.
* Nálepky sa nenačítajú (prázdne štvorce, v logu bez chyby); príčina nepotvrdená.
* Ak sa bootstrap nechytí do 90 s, brána ponúkne opakovanie ťuknutím; siet je dovtedy zavretá.

## Build a inštalácia

Postup je v [README](../README.md). Na update bez straty prihlásenia musí byť APK podpísané tým istým kľúčom ako to v telefóne.
