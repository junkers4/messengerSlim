# Spravaren (messengerSlim)

Osekaný a súkromnejší **Messenger pre Android s vstavaným Torom**. Skript vezme
**originálne APK od Facebooku** (tvoje vlastné, legálne získané), upraví ho, vloží do neho Tor a
podpíše tvojím kľúčom. Výsledok je jedna appka s jednou ikonou: celá komunikácia Messengera
ide cez Tor, Facebook vidí exit uzol, nie tvoju IP. Žiadny Orbot.

> **Toto repo neobsahuje žiadne binárky Mety.** Nikdy sem necommituj APK, dekompilovaný kód,
> kľúče ani súkromné exporty (`.gitignore` ich blokuje). Používaj len vlastnú kópiu APK na
> vlastnom zariadení.

Cieľové zariadenie: Pixel 9a, Android 16, arm64. Posledný overený Messenger: **584.1.0.61.98**.

## Obsah

1. [Čo to robí](#čo-to-robí)
2. [Ako to funguje](#ako-to-funguje)
3. [Štruktúra repa](#štruktúra-repa)
4. [Build](#build)
5. [Inštalácia a prvé spustenie](#inštalácia-a-prvé-spustenie)
6. [Ovládanie panelu](#ovládanie-panelu)
7. [Testovanie](#testovanie)
8. [Nová verzia Messengera](#nová-verzia-messengera)
9. [Známe obmedzenia](#známe-obmedzenia)

## Čo to robí

**Súkromie a batéria**

- Všetko, čo Messenger pošle do siete, ide cez **Tor** (vstavaný `libTor.so`).
- **Fail-closed**: kým Tor nebeží, appka nemá sieť; ak Tor spadne uprostred, appka sa odreže,
  nikdy nepustí paket mimo Toru.
- Tor beží **len keď je Messenger v popredí**. 3 minúty po odchode na pozadie sa zloží Tor aj
  tunel a proces sa ukončí. Žiadne budenie na pozadí (preto **žiadne notifikácie** — zámer).
- Chat-only: vypnuté Stories, Reels, Marketplace, Meta AI, reklamy, analytika a pozadové
  služby. Hovory, emoji, GIFy a fotky ostávajú.
- QUIC (UDP) je vypnutý, lebo Tor UDP neprenesie. Všetko ide hneď cez TCP.

**Vzhľad a panel v inboxe**

- Názov **Spravaren**, ikonka je pôvodná bublina, len čierna. Nadpis inboxu „spravaren“.
- Na mieste Stories pásu je **panel**: počasie (mesto, teplota, pocit, max/min, dážď, vietor,
  východ/západ, upozornenie na dážď do 3 h), dátum so **slovenskými meninami**, českým
  svátkom, fázou mesiaca a štátnym sviatkom, **narodeniny** tvojich ľudí, bežiaci pás
  slovenských správ a značka 🧅✓ (overené cez `check.torproject.org`, že to naozaj ide cez Tor).
- Upratané: bez pásu „Notifications are currently off“ a bez plávajúceho tlačidla Meta AI.

## Ako to funguje

Build neprepisuje Messenger ručne. Berie originálne APK a aplikuje na ňom **opakovateľné
patche**, takže sa dá zopakovať na novej verzii:

```
originálne APK ──► overenie podpisu Facebooku ──► APKEditor decode (smali)
   ──► patches/nuclear_chatonly.py   chat-only rezy
   ──► patches/no_quic.py            QUIC off
   ──► patches/branding.py           názov + čierna ikonka
   ──► patches/panel_hook.py         hook panelu na Stories pás
   ──► manifest: Tor komponenty + patches/no_background_manifest.py
   ──► tor-inject/src (Java) ──► javac + d8 ──► classesN.dex
   ──► libTor.so + libhev-socks5-tunnel.so (arm64)
   ──► APKEditor build ──► zipalign ──► apksigner (tvoj kľúč)
```

### Cesta dát

```
Messenger ─► tun (VpnService, 0.0.0.0/0 okrem IP guardov) ─► hev-socks5-tunnel
          ─► Tor SOCKS 127.0.0.1:9050 ─► guard ─► middle ─► exit ─► Facebook
```

Všetko (tunel, SOCKS prevodník aj Tor) beží v **jednej appke** `com.facebook.orca`. DNS ide
cez `mapdns` v hev (meno sa vyrieši cez Tor, nie cez operátora).

**Protismyčka:** Tor beží pod rovnakým UID ako Messenger, takže by tunel zachytil aj vlastný
Tor. Preto sa po štarte prečítajú IP adresy guardov cez ControlPort a z trás tunela sa
vynechajú (`RouteUtil.allExcept`). Tor ide ku guardovi priamo, všetko ostatné cez tunel.

**Kill-switch:** tunel sa postaví hneď pri štarte s poslednými známymi guardmi, takže
Messenger nemá sieť, kým Tor nenabehne. Ak sa bootstrap zasekne, sieť sa na chvíľu otvorí len
natoľko, aby Tor dobehol (appku medzitým zakryje brána), a tunel sa hneď postaví späť.
Watchdog pri páde Toru appku odreže.

**Panel bez zásahu do Messengera:** Java kód sa vkladá ako nový dex a jediný zásah do smali
je jeden `invoke-static` v továrni na Stories pás. `panel_hook.py` tú továreň hľadá **podľa
tvaru kódu**, nie podľa obfuskovaného mena, takže funguje aj po zmene verzie.

### Vložené komponenty (`tor-inject/src`)

| Trieda | Úloha |
|---|---|
| `tor/OrcaTorVpnService` | VpnService: tunel bez guardov, kill-switch, watchdog, nový okruh |
| `tor/TorController` | štart `libTor.so`, bootstrap, guardy cez ControlPort, `torrc` |
| `tor/RouteUtil` | `0.0.0.0/0` bez IP guardov |
| `tor/LifecycleWatcher` | Tor zapnutý v popredí, po 3 min na pozadí vypnutý |
| `tor/TorBootProvider` | hook pri štarte procesu, žiadna druhá ikona |
| `tor/TorGate`, `TorStatus` | pásik „Connecting to Tor…“ a stav medzi procesmi |
| `tor/ConsentActivity` | jednorazový súhlas s VPN |
| `panel/WeatherPanel` | panel: počasie, kalendár, správy, Tor značka |
| `panel/Birthdays` | narodeniny (lokálne), meniny podľa mena |
| `panel/Declutter` | upratanie inboxu, nadpis „spravaren“ |
| `hev/htproxy/TProxyService` | JNI trieda pre `libhev-socks5-tunnel.so` |

Natívne knižnice **nie sú v gite**; `build-all.sh` ich stiahne alebo skompiluje:
`libTor.so` (Tor expert bundle 15.0.26) a `libhev-socks5-tunnel.so`
([hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel), pevný commit, build zlyhá,
ak v ňom chýba `mapdns` alebo nesedí JNI).

## Štruktúra repa

```
README.md
docs/
  POZIADAVKY-A-TESTY.md   požiadavky (R*, P*, X*, S*), testovací protokol, výsledky
patches/                  Python patche (smali a manifest) + dáta rezov
  nuclear_chatonly.py     chat-only rezy; zoznamy sú v cuts.json
  cuts.json               ciele, pravidlá a whitelisty rezov
  no_quic.py              vypnutie QUIC
  no_background_manifest.py   bez budenia na pozadí
  branding.py             názov „Spravaren“ + čierna ikonka
  panel_hook.py           hook panelu na Stories pás
tor-inject/
  build-all.sh            celý build jedným príkazom
  manifest-inject.xml     Tor komponenty do manifestu
  src/                    Java kód vkladaný do APK
  test/                   test logiky panelu na PC (run.sh)
tools/
  find_quic.py            nájde v dekompilovanom Messengeri miesta, kde sa zapína QUIC
.github/workflows/build-apk.yml   build v GitHub Actions
```

## Build

Potrebuješ: Linux, **JDK 17**, Android SDK (build-tools + platforms), **NDK r27**
(`ndk-build`), Python 3 s **Pillow** (`pip install pillow`), `curl`, `git`, `zip`.

### Lokálne

```bash
IN_APK=messenger.apk \
KEYSTORE=spravaren.keystore KS_PASS=heslo \
ANDROID_HOME=~/Android/Sdk \
ANDROID_NDK=~/Android/Sdk/ndk/27.3.13750724 \
bash tor-inject/build-all.sh          # -> tor-inject/spravaren-signed.apk
```

| Premenná | Význam |
|---|---|
| `IN_APK` | **originálne** Messenger APK (podpis Facebooku sa overuje) |
| `KEYSTORE`, `KS_PASS`, `KS_ALIAS` | tvoj podpisový kľúč (`KS_ALIAS` default `spravaren`) |
| `ANDROID_HOME`, `ANDROID_NDK` | SDK a NDK |
| `OUT_APK`, `CACHE` | voliteľné: výstup a priečinok na stiahnuté nástroje |

Kľúč vyrobíš napr. `keytool -genkeypair -keystore spravaren.keystore -alias spravaren
-keyalg RSA -keysize 4096 -validity 36500`. **Ten istý kľúč používaj stále**: nová verzia sa
potom nainštaluje cez starú a ostane prihlásenie.

### GitHub Actions

1. V repe nastav *Settings → Secrets and variables → Actions*:
   `SIGNING_KEYSTORE_B64` (`base64 -w0 spravaren.keystore`), `SIGNING_KEYSTORE_PASS`
   a voliteľne `SIGNING_KEY_ALIAS`.
2. *Actions → Build Spravaren APK → Run workflow* a vlož **priamy odkaz na originálne APK**.
3. Hotové APK je artifact behu `spravaren-apk` (platí 1 deň, ~10 min build).

> V **verejnom** repe si artifact môže stiahnuť ktokoľvek prihlásený na GitHube. Ak ti to
> vadí, nechaj repo súkromné.

## Inštalácia a prvé spustenie

**Z mobilu (bez PC):** stiahni artifact (je to ZIP, v appke *Súbory* ho rozbaľ), ťukni na APK,
povoľ inštaláciu z tohto zdroja a pri Play Protect zvoľ *Napriek tomu nainštalovať*.

**Cez adb:**

```bash
adb install -r spravaren-tor-signed.apk
adb shell appops set com.facebook.orca ACTIVATE_VPN allow   # vopred povolí VPN
```

- Prvý raz Android spýta **povolenie VPN** (cez to ide Tor): potvrď OK. Ak ho zamietneš,
  appka nemá sieť; povolíš ho cez `appops` vyššie.
- Build podpísaný **iným kľúčom** než appka v telefóne sa nenainštaluje cez starú (Android
  hlási „konflikt balíka“). Vtedy starú odinštaluj, **prihlásenie sa stratí**.
- **Prihlásenie cez Tor:** Facebook niektoré exit uzly blokuje. Ak sa captcha nenačíta alebo
  login zlyhá, zavri appku, počkaj ~3 minúty (zmizne 🔑 zo stavovej lišty) a skús znova —
  dostaneš iný exit.

## Ovládanie panelu

| Gesto | Čo urobí |
|---|---|
| ťuk na riadok dátumu / info | zoznam narodenín (**Pridať**) |
| ťuk na kartu v zozname | úprava (meno + kolieska deň / mesiac / rok) |
| podržanie karty | zmazanie |
| ťuk na riadok počasia | obnoví počasie a správy |
| podržanie panelu | **nový Tor okruh** (nová exit IP), bublina s výsledkom |
| potiahnutie zoznamu nadol | filtre All / Unread / Groups (správanie Messengera) |

Narodeniny sú **len v telefóne** (`SharedPreferences`), nikam sa neposielajú. K menám panel
nájde aj meniny (slovenský, inak český kalendár), takže pri „Miška (Michaela)“ ukáže aj
meniny. Upozornenie na narodeniny sa ukáže v paneli, keď appku otvoríš; notifikácie sú
vypnuté zámerne.

## Testovanie

**Logika panelu na PC** (narodeniny, meniny, Veľká noc, sviatky, parsovanie dátumov):

```bash
bash tor-inject/test/run.sh        # očakávaný výstup: VSETKO OK
```

**Na telefóne:** testovací protokol (T1–T23), požiadavky a posledné výsledky sú v
[`docs/POZIADAVKY-A-TESTY.md`](docs/POZIADAVKY-A-TESTY.md). Testuj len **v chate so sebou**,
nikdy nie v chatoch s inými ľuďmi (každý otvorený chat posiela „videné“).

Užitočné príkazy:

```bash
adb logcat -s OrcaTor:*                       # stav Toru a tunela
adb shell ip addr show tun0                   # tunel (198.18.0.1)
adb shell dumpsys activity top | less         # skutočná hierarchia views (Litho)
```

## Nová verzia Messengera

Patche sú písané tak, aby prežili zmenu verzie, ale obfuskácia sa mení. Postup:

1. Skús build s novým APK. Skripty zlyhajú **nahlas** (assert), nie potichu: `panel_hook.py`
   očakáva presne jednu továreň na Stories pás, `no_quic.py` a hev majú vlastné kontroly.
2. Ak `panel_hook.py` nenájde továreň, pozri smali nového APK: hľadá sa statická metóda
   `(Context)` vracajúca obal nad `RecyclerView` (`<init>(Context, RecyclerView)`).
3. Ak sa pokazí niečo vo vlákne (fotky, emoji, história), pozri `MSG_WHITELIST` a
   `MEDIA_WHITELIST` v `patches/nuclear_chatonly.py` a zoznamy v `patches/cuts.json`.
4. `tools/find_quic.py` nájde miesta, kde sa zapína QUIC, ak by sa zmenili.

## Známe obmedzenia

- **Žiadne notifikácie**, kým je appka zavretá. Správy sa natiahnu po otvorení. Je to cena
  za „nič sa nebudí na pozadí“.
- Kontakty sa nesynchronizujú (sync adaptér a authenticator sú vypnuté).
- Facebook blokuje niektoré Tor exit uzly (login, captcha). Pomáha nový okruh alebo počkať.
- **Nálepky (stickers)** sa nenačítajú (karta ostáva na prázdnych štvorcoch). GIFy a emoji
  idú. Príčina nie je potvrdená.
- Ak sa bootstrap Toru nechytí do 90 s, brána ponúkne opakovanie; sieť je dovtedy zavretá.
- Funguje len na **arm64**.

## Bezpečnosť a právne

- Používaj len **vlastné APK** na **vlastnom zariadení**. Nikdy nenahrávaj upravené APK ani
  kód Mety na GitHub.
- Podpisový kľúč a jeho heslo nikdy necommituj (`*.keystore`, `*.jks` sú v `.gitignore`).
- Spravaren je neoficiálna úprava; Meta ju nepodporuje a môže zablokovať účet.
