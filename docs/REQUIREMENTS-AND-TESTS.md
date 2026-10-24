# Spravaren (Messenger + Tor) — requirements and test protocol

State as of 2026-10-24. Device: Pixel 9a, Android 16, arm64. Messenger 584.1.0.61.98.
Build: original APK → `tor-inject/build-all.sh` (locally or GitHub Actions) → `spravaren-signed.apk`.

Note: log lines and on-screen strings quoted below (e.g. "Okruh k Facebooku pripraveny…") are
quoted verbatim from the engine, which still logs in Slovak.

## Requirements

| # | Requirement | Where it is solved |
|---|---|---|
| R1 | While Tor is coming up there is a **bar** at the top (English, onion, percent) and the app below it is **usable** — old messages can be read right away, a written message waits in the queue. Nothing leaves, because the tunnel is fail-closed. When the network temporarily goes outside the tunnel during a stuck bootstrap (state `OPEN`), the bar covers the whole app and eats touches. | `TorGate.java`, state via `TorStatus` (a file in `filesDir`, because the VpnService runs in the `:tor` process) |
| R2 | After `READY` the gate disappears (a brief "Connected") and **polling stops** — in normal operation nothing may run cyclically. | `TorGate.startPolling` / `detach` |
| R3 | 3 min after going to the background, Tor and the tunnel are torn down (the VPN key disappears) and **both the Messenger and the `:tor` process are killed**. | `LifecycleWatcher.GRACE_MS`, `OrcaTorVpnService.quit()` |
| R4 | The app **must not wake in the background** (no push, boot, sync adapter). If the system starts it anyway, the process exits by itself. | `patches/no_background_manifest.py` (+ `android.content.SyncAdapter`, `android.accounts.AccountAuthenticator`), `TorBootProvider.disableWakeComponents` + `killIfBackgroundWake` |
| R5 | **Emoji, stickers, GIFs and photos must render and be sendable.** The panel must not be inserted anywhere but the Stories strip's place. | `WeatherPanel` — strict Stories strip detection (see below) |
| R6 | A thread must show its history and the user's own sent messages. | `nuclear_chatonly.py` → `MSG_WHITELIST` (mqtt/msys/mailbox/…) |
| R7 | The bottom navigation is **Messenger's original**, just **without the Stories tab** — no custom bar. | `WeatherPanel` |
| R8 | Operation must be smooth — no periodic walking of the view hierarchy. The panel decides only when the tray is built, network work is off the main thread. | `WeatherPanel.install` |
| R9 | App name **Spravaren**, icon = the original Messenger bubble, just **black** instead of blue. No custom images. The name changes **only** in the manifest and on the title (not in code: replacing every "Messenger" string broke internal product identifiers). | `patches/branding.py`, `Declutter.Wordmark` |
| R10 | DNS in the tunnel goes through hev `mapdns` (fake IPs 100.64/10 → the name goes into Tor). The build fails if hev lacks mapdns. | `build-all.sh` (`HEV_COMMIT`, check for `"mapdns"`) |
| R11 | After `READY` a connection to `edge-mqtt.facebook.com:443` is opened over Tor right away so the circuit is ready; if the exit fails, a new circuit (`NEWNYM`) is requested, max 3 attempts. | `OrcaTorVpnService.startWarmUp`, `TorController.probe` |
| R12 | Tapping a photo in a thread opens it full screen (zoom, save). | `MEDIA_WHITELIST` in `nuclear_chatonly.py`, `activityWhitelist` in `cuts.json` |
| R13 | **QUIC (UDP) is disabled in the proxygen client** — Tor does not carry UDP, so photos/videos/GIFs only went after a timeout. Now they go straight over TCP. **The DGW gateway does not have QUIC disabled** (disabling it caused 49 `INTERNAL_ERROR` errors/min). | `patches/no_quic.py` (`QuicSettings$Builder.build`) |

### Panel, birthdays, no Stories (2026-10-24)

| # | Requirement | Where it is solved |
|---|---|---|
| P1 | In the Stories strip's place in the inbox (584: hook in `X/2ne.A03`, found by code shape) there is a **panel**: city + temperature + cloudiness, feels-like temperature, max/min, rain, wind, sunrise/sunset. | `WeatherPanel` (Open-Meteo) |
| P2 | Date row: day, **Slovak name day**, **Czech name day**, moon phase, **Slovak public holiday** if any. | `WeatherPanel.dateLine`, `holidaySk` |
| P3 | **Birthdays**: today "🎂 Dnes Name (age)", within 7 days "🎂 o N dni Name". In the app only — no notifications, no wake-ups (R4 stands). | `Birthdays.line` |
| P4 | I add birthdays myself: tap the date/info row → list (cards: name, date in words, days to go, age, name days) + **Add**. Add/edit: name + **day / month / year wheels** (year optional) + live preview. Tap a card = edit, long-press = delete. Only on the phone (`SharedPreferences`). | `Birthdays.showDialog/showEdit` |
| P5 | A scrolling strip of **Slovak news** (Google News SK) + "on this day". | `WeatherPanel` |
| P6 | All panel data goes **over Tor** and arrives even if the tunnel was not up yet (retry). Without network the panel shows the calendar and the birthdays, which are local. | `WeatherPanel.get` (retry) |
| P7 | **Stories gone**: the strip is replaced by the panel, there is no Stories tab in the bottom bar. | `WeatherPanel` (panel replaces the strip), no Stories tab |
| P8 | Tap the weather row → refreshes weather and news. | `WeatherPanel` |

### Extras

| # | Requirement | Where it is solved |
|---|---|---|
| X1 | **Rain alert**: if it rains (≥ 50 %) within the next 3 h, the weather row shows "☔ o 2 h dážď". | `WeatherPanel` (hourly) |
| X2 | **Name day tomorrow**: "zajtra: Name" — so a greeting can be prepared. | `WeatherPanel.dateLine`, `holidaySk` |
| X3 | **Tor mark** after the weather: 🧅✓ = verified via `check.torproject.org` (traffic really goes through Tor), ⚠️ = it does not. Long-press the panel → **new circuit** (NEWNYM) + a bubble with the new exit IP. | `WeatherPanel.fetchTorAsync`, `OrcaTorVpnService.ACTION_NEWNYM` |
| X4 | **Name days of people on the list**: the name is looked up in the SK calendar, otherwise CZ ("Miška (Michaela)" → CZ 19. 10.); within 7 days "🌸 meniny o N dni Miška". | `WeatherPanel.namedayOf`, `Birthdays.line` |
| X5 | **Decluttered inbox**: no "Notifications are currently off" banner (disabled on purpose) and no floating Meta AI button. | `Declutter` |
| X6 | The panel has room for **5 rows**: weather+🧅, detail, date, info (birthday > holiday > name day tomorrow), news. | `WeatherPanel.build` |
| X7 | Inbox title "messenger" → **"spravaren"** (redrawn at the same size, colour per dark/light mode). | `Declutter.Wordmark` |
| X8 | The "Ask Meta AI or search" field is covered by a "🔍 Hľadať" pill (the user does not want Meta AI; a tap still opens search); the panel is inserted only into the Stories strip (height ≥ 70 dp), not into the All/Unread/Groups filter strip — previously the panel appeared twice. | `Declutter`, `WeatherPanel.geometry` |

### Typing and loading speed

| # | Requirement | Where it is solved |
|---|---|---|
| S1 | Measured: start → inbox with data, opening a thread, sending a message (tap → "Sent") — **only in a chat with yourself**. | tests T20–T22 |
| S2 | Tor/tunnel changes are made only with measured before/after. Candidates: a warm circuit to `edge-mqtt` + `graph` + `rupload`, a faster switch to a new exit when stuck, `CircuitBuildTimeout`. | `TorController`, `OrcaTorVpnService` |

## Test protocol

Setup: `export PATH=$PATH:$HOME/Android/Sdk/platform-tools`, phone over USB (`adb devices`).

| # | Test | Steps | Expected |
|---|---|---|---|
| T1 | Tor bar | `adb shell am force-stop com.facebook.orca`, start the app, screenshot after 2 s | A black bar at the top with an onion, "Connecting to Tor… NN %", the inbox below it |
| T2 | App usable while coming up | tap a chat during the bar | The chat opens, history shows; neither `adb shell dumpsys connectivity` nor `logcat` show traffic outside `tun0` |
| T3 | Gate disappears | screenshot ~15 s after start; `adb logcat -d -s OrcaTor:*` | "Messenger je teraz smerovany cez Tor.", gate gone |
| T4 | Name and icon | launcher | "Spravaren" under the icon, a black bubble with a white bolt |
| T5 | Original navigation | inbox screenshot | Chats · People · Notifications · Menu (native; 584 no longer has a Stories tab) |
| T6 | Thread | open the first chat | History and own messages render |
| T7 | Emoji | smiley icon in compose → smiley tab | "Recent" + emoji grid + category bar at the bottom |
| T8 | Inserting emoji | tap an emoji | The emoji is in the "Message" field, a Send arrow appears |
| T9 | Photos | gallery icon | Grid of thumbnails from the phone; selecting marks a photo with a number and shows Edit/Send |
| T10 | Tunnel | `adb shell ip addr show tun0` | `tun0 ... mtu 8500`, address 198.18.0.1 |
| T11 | Disconnect after 3 min | HOME, wait 3 min 10 s, `ip addr show tun0`, `ps -A \| grep orca` | No `tun0`, no `orca` or `orca:tor` process, the key leaves the status bar |
| T12 | No wake-ups | after T11 wait 60 s, `adb logcat -d \| grep "Start proc.*orca"` | No start. (If the system does start it, the `OrcaTor` log has "Start z pozadia … ukoncujem proces") |
| T13 | Smoothness | `adb shell dumpsys gfxinfo com.facebook.orca reset`, 6× swipe, `dumpsys gfxinfo` | Janky frames < 2 %, 90th percentile ≤ ~12 ms |
| T14 | Circuit warm-up | after coming up `adb logcat -d -s OrcaTor:*` | "Okruh k Facebooku pripraveny za … ms" (or "novy okruh" and then success) |
| T15 | Photo full screen | tap a photo in a thread | A viewer opens, zoom works |
| T16 | Panel | inbox screenshot | Weather, detail, date + name day + Czech name day + moon (+ holiday), birthdays if any, scrolling news strip |
| T17 | Birthdays | tap the date → Add "Test" with today's date → back | The panel shows "🎂 Dnes: Test"; long-press in the list deletes it |
| T18 | No Stories | inbox screenshot | No Stories strip and no Stories tab |
| T19b | DGW loop | `adb logcat --pid=$(pidof com.facebook.orca) \| grep -c "Restarting due to reason: StreamError"` after 60 s | 0 (with DGW QUIC disabled it was 49) |
| T19 | DNS in the tunnel | `adb logcat --pid=$(pidof com.facebook.orca) \| grep -c getaddrinfo` after coming up | 0 |
| T20 | Cold start | `force-stop`, start, time until the inbox shows names | record (s) |
| T21 | Opening a thread | tap the chat with yourself, time until history | record (s) |
| T22 | Sending | in the chat with yourself write text, send, time until "Sent" | record (s); chat with yourself only |
| T23 | Tor row | panel screenshot, long-press | exit flag; after the long-press a new circuit in the `OrcaTor` log |

## Results of the last run (2026-10-24, Pixel 9a, Messenger 584.1.0.61.98)

Measured on a live phone, messages only to the chat with yourself. The `adb` "time" includes
~2 s of measurement overhead.

| Test | Result |
|---|---|
| T1 Tor gate | ✅ "Connecting to Tor… 0 %" → "Connected through Tor" |
| T3 gate disappears | ✅ Tor ready ~3.7 s after process start |
| T4 name and icon | ✅ "Spravaren", black bubble; inbox title "spravaren" |
| T5 navigation | ✅ Chats · People · Notifications · Menu |
| T6 thread | ✅ history and own messages |
| T7/T8 emoji | ✅ grid renders, 😀 sent ("Sent" in ~4.7 s) |
| T9 photos | ✅ grid of thumbnails from the gallery (nothing was sent) |
| T10 tunnel | ✅ `tun0 mtu 8500`, 198.18.0.1 |
| T11 disconnect after 3 min | ✅ after 3 min 15 s no `tun0` and no `orca` process |
| T12 no wake-ups | ✅ no process start in the window after disconnect |
| T14 warm circuit | ✅ 86–184 ms |
| T15 photo full screen | ✅ opens in ~1–2 s (measured 3×), user confirmed. While DGW looped (49 errors/min) it did not open in the `adb` test; after the DGW fix it does. Tested only on a photo from the chat with yourself, not on a photo downloaded from an outside contact |
| T16 panel | ✅ weather, detail, date + name day + Czech name day + moon, info row, news, 🧅✓ |
| T17 birthdays | ✅ add / edit (wheels) / delete, name days by name (Miška (Michaela) → CZ 19. 10.) |
| T18 no Stories | ✅ strip replaced by the panel |
| T19 DNS in the tunnel | ✅ 0 `getaddrinfo` errors |
| T19b DGW loop | ✅ 0 restarts in 60 s (49 before the fix) |
| T20 cold start | ✅ inbox with chats ≈ 7.7–7.9 s (of which ~2 s measurement overhead) |
| T22 sending text | ✅ "Sent" in ≤ 2.7 s |
| T23 new circuit | ✅ `NEWNYM: 250 OK` + bubble "Nový Tor okruh" |
| Receiving | ✅ new messages pop into the inbox while the app is open (latency not measured) |
| GIF | ✅ they load |
| **Stickers** | ❌ the card stays on empty squares; after the DGW fix there are no errors in the log, but nothing loads |
| "Hľadať" field (X8) | ⏳ the overlay is being tuned (host not found — fixed by search depth, awaiting verification) |
| T13 smoothness, T21 opening a thread | ➖ not reliably measured today |
| Sending a photo, a voice message, calls | ➖ untested |

## Known limitations

* No notifications while the app is closed — by design (no wake-ups, no leaks); messages load
  once you open it.
* Contacts are not synced (the sync adapter and authenticator are disabled) — the price of R4.
* Stickers do not load (empty squares, no error in the log); cause not confirmed.
* If bootstrap does not catch within 90 s, the gate offers a retry on tap; the network is
  closed until then.

## Build and install

The procedure is in the [README](../README.md). To update without losing the login, the APK
must be signed with the same key as the one on the phone.
