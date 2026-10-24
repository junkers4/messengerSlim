#!/usr/bin/env python3
"""Nuclear chat-only patch - Linux/Python port of scripts/apply-nuclear-chatonly.ps1.

Rovnaka semantika ako PowerShell verzia, ale:
  * bezi na Linuxe,
  * je verzio-agnosticka: ciele sa hladaju podla package cesty (com/facebook/...),
    nie podla smali_classesN/ prefixu, ktory sa medzi verziami Messengera meni,
  * hook metody (obfuskovane onCreate v FbFragmentActivity/FbPreferenceActivity)
    sa detegujú z APK, nie natvrdo A32/A0F,
  * HOVORY (RTC) ZOSTAVAJU ZIVE - viď CALL_WHITELIST.

Datove zoznamy (uiTargets, batteryRules, whitelisty, bgPathRegex) sa citaju priamo
z apply-nuclear-chatonly.ps1, aby ostal jeden zdroj pravdy.
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

# ── ps1 parsing helpers ──────────────────────────────────────────────────

def ps_block(text: str, var: str) -> str:
    """Vrati telo `$var = @( ... )`."""
    start = text.index(f"${var} = @(")
    i = text.index("(", start)
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "(":
            depth += 1
        elif text[j] == ")":
            depth -= 1
            if depth == 0:
                return text[i + 1:j]
    raise ValueError(f"unterminated block for ${var}")


def ps_unescape(s: str) -> str:
    return s.replace("`r`n", "\n").replace("`n", "\n").replace("`r", "").replace("``", "`")


def parse_targets(text: str, var: str) -> list[str]:
    return [m.group(1) for m in re.finditer(r"'([^']*\.smali)'", ps_block(text, var))]


def parse_rules(text: str, var: str) -> list[tuple[str, str, str]]:
    block = ps_block(text, var)
    out = []
    for m in re.finditer(r"@\{\s*P='([^']+)';\s*H='([^']+)';\s*R=\"(.*?)\"\s*\}", block, re.S):
        out.append((m.group(1), m.group(2), ps_unescape(m.group(3))))
    return out


def parse_regex_list(text: str, var: str) -> list[str]:
    return [m.group(1) for m in re.finditer(r"'((?:[^']|'')*)'", ps_block(text, var))]


def parse_scalar_regex(text: str, var: str) -> str:
    m = re.search(rf"\${var} = '(.*)'\s*$", text, re.M)
    if not m:
        raise ValueError(f"${var} not found")
    return m.group(1)


def ps_rx(pattern: str) -> re.Pattern:
    """PowerShell regex literal -> Python (cesty su porovnavane case-insensitive)."""
    return re.compile(pattern.replace("\\\\", "\\\\"), re.IGNORECASE)

# ── smali tree ───────────────────────────────────────────────────────────

ROOT_SEG = re.compile(r"^(smali|smali_classes\d+|classes\d*)$")


def class_rel(path: Path, base: Path) -> str:
    """work/decoded/smali/classes5/com/facebook/X.smali -> com/facebook/X.smali"""
    parts = list(path.relative_to(base).parts)
    while parts and ROOT_SEG.match(parts[0]):
        parts.pop(0)
    return "/".join(parts)


def strip_prefix(target: str) -> str:
    parts = target.replace("\\", "/").split("/")
    while parts and ROOT_SEG.match(parts[0]):
        parts.pop(0)
    return "/".join(parts)

# ── method no-op bodies ──────────────────────────────────────────────────

NOOP_SPECS = [
    (r"(\.method(?![^\n]*abstract)[^\n]* A05\(Landroid/content/Context;Landroid/content/Intent;LX/0NL;\)V)\n.*?\.end method",
     "\\1\n    .locals 0\n\n    return-void\n.end method"),
    (r"(\.method(?![^\n]*abstract)[^\n]* A03\(Landroid/content/Intent;\)V)\n.*?\.end method",
     "\\1\n    .locals 0\n\n    return-void\n.end method"),
    (r"(\.method(?![^\n]*abstract)[^\n]* onStartCommand\(Landroid/content/Intent;II\)I)\n.*?\.end method",
     "\\1\n    .locals 1\n\n    const/4 v0, 0x2\n\n    return v0\n.end method"),
    (r"(\.method(?![^\n]*abstract)[^\n]* onStartJob\(Landroid/app/job/JobParameters;\)Z)\n.*?\.end method",
     "\\1\n    .locals 1\n\n    const/4 v0, 0x0\n\n    return v0\n.end method"),
    (r"(\.method(?![^\n]*abstract)[^\n]* onStopJob\(Landroid/app/job/JobParameters;\)Z)\n.*?\.end method",
     "\\1\n    .locals 1\n\n    const/4 v0, 0x0\n\n    return v0\n.end method"),
    (r"(\.method(?![^\n]*abstract)[^\n]* onBind\(Landroid/content/Intent;\)Landroid/os/IBinder;)\n.*?\.end method",
     "\\1\n    .locals 1\n\n    const/4 v0, 0x0\n\n    return-object v0\n.end method"),
    (r"(\.method(?![^\n]*abstract)[^\n]* onGetChooserTargets\(Landroid/content/ComponentName;Landroid/content/IntentFilter;\)Ljava/util/List;)\n.*?\.end method",
     "\\1\n    .locals 1\n\n    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;\n\n    move-result-object v0\n\n    return-object v0\n.end method"),
    (r"(\.method(?![^\n]*abstract)[^\n]* A01\(\)Landroid/os/IBinder;)\n.*?\.end method",
     "\\1\n    .locals 1\n\n    const/4 v0, 0x0\n\n    return-object v0\n.end method"),
    (r"(\.method(?![^\n]*abstract)[^\n]* A04\(\)V)\n.*?\.end method",
     "\\1\n    .locals 0\n\n    return-void\n.end method"),
    (r"(\.method(?![^\n]*abstract)[^\n]* onReceive\(Landroid/content/Context;Landroid/content/Intent;\)V)\n.*?\.end method",
     "\\1\n    .locals 0\n\n    return-void\n.end method"),
]
NOOP_SPECS = [(re.compile(p, re.S), r) for p, r in NOOP_SPECS]

SVC_SPECS = [(p, r) for p, r in NOOP_SPECS
             if any(s in p.pattern for s in ("onStartCommand", "onStartJob", "onStopJob", "onBind",
                                             "A05(Landroid/content/Context", "A03(Landroid/content/Intent",
                                             "onReceive"))]

PROVIDER_SPEC = (re.compile(r"(\.method(?![^\n]*abstract)[^\n]* onCreate\(\)Z)\n.*?\.end method", re.S),
                 "\\1\n    .locals 1\n\n    const/4 v0, 0x1\n\n    return v0\n.end method")

# Hovory: tieto cesty sa NIKDY neposkodia (nad ramec whitelistov v .ps1).
CALL_WHITELIST = re.compile(
    r"(rtc[/])|([Rr]tc[A-Z])|(voip)|(calling[/])|(FbnsLiteRtc)"
    # runtimepermissions: cez RequestPermissionsActivity si appka pyta mikrofon/kameru
    r"|(runtimepermissions[/])"
)

# Sprava (messaging core): realtime transport + MSYS runtime + mailbox sa NIKDY nesmu
# poskodit plosnymi sweepmi (fazy 3-6). Inak sa mailbox/runtime neinicializuje a zoznam
# sprav vo vlakne ostane PRAZDNY (aj pre vlastnu odoslanu spravu) - UI nema odkial brat data.
# Terminy su volene tak, aby nezasiahli vypinane feature (reels/stories/marketplace/ads/...).
MSG_WHITELIST = re.compile(
    r"(mqtt)"                          # mqttlite / multiusermqtt - realtime transport sprav
    r"|(msys)"                         # MSYS runtime + mailbox sync
    r"|(mailbox)"
    r"|(omnistore)"
    r"|(realtime)"
    r"|(presence)"
    r"|(/xplat/)"                      # cross-platform messaging runtime
    r"|(messaging/[^/]*sync)"          # messaging delta/message sync
    r"|(graphql/(subscription|realtime|executor))"
    r"|(pando)",                       # relay/pando data layer
    re.IGNORECASE,
)

# Prehliadac fotiek/videi: tuknutie na fotku vo vlakne ju otvori na celu obrazovku (zoom,
# ulozenie, preposlanie). Plosne sweepy (fazy 3-5) ho zabijali - fotka bola vidno len ako
# nahlad v chate a nedala sa otvorit.
MEDIA_WHITELIST = re.compile(
    r"(messaging/media/)|(mediaviewer)|(photoview)|(messaging/photos/)"
    r"|(messaging/video)|(videoplayer)"
    r"|(sharedcontent/)",                  # galeria "Fotky a videa" z detailu chatu
    re.IGNORECASE,
)

# Aktivity, ktore vyzeraju ako prehliadac - ak ich faza 4 predsa zabije, vypise sa varovanie.
MEDIA_SUSPECT = re.compile(r"(media|photo|image|video|viewer|gallery|zoom)", re.IGNORECASE)


def keep(rel: str) -> bool:
    return bool(CALL_WHITELIST.search(rel) or MSG_WHITELIST.search(rel) or MEDIA_WHITELIST.search(rel))


def finish_body(hook: str, sup: str) -> str:
    return (f".method public {hook}(Landroid/os/Bundle;)V\n"
            f"    .locals 0\n"
            f"    invoke-super {{p0, p1}}, L{sup};->{hook}(Landroid/os/Bundle;)V\n"
            f"    invoke-virtual {{p0}}, L{sup};->finish()V\n"
            f"    return-void\n"
            f".end method\n")


FBFRAG = "com/facebook/base/activity/FbFragmentActivity"
FBPREF = "com/facebook/base/activity/FbPreferenceActivity"


def detect_hook(files: list[Path], superclass: str, fallback: str) -> str:
    """Najde obfuskovanu 'onCreate' hook metodu, ktoru prekryvaju podtriedy."""
    sup_re = re.compile(r"\.super\s+L" + re.escape(superclass) + ";")
    meth_re = re.compile(r"\.method\s+(?:public|protected)\s+(A[0-9A-Za-z]{2})\(Landroid/os/Bundle;\)V")
    counts = Counter()
    for f in files:
        try:
            head = f.read_text(errors="ignore")
        except OSError:
            continue
        if not sup_re.search(head[:4000]):
            continue
        for m in meth_re.finditer(head):
            counts[m.group(1)] += 1
    if not counts:
        return fallback
    return counts.most_common(1)[0][0]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("workdir", help="decompiled tree (APKEditor alebo apktool)")
    ap.add_argument("--ps1", default=None, help="cesta k apply-nuclear-chatonly.ps1")
    ap.add_argument("--report", default=None)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    base = Path(args.workdir).resolve()
    if not base.is_dir():
        print(f"chyba: {base} neexistuje", file=sys.stderr)
        return 1
    ps1 = Path(args.ps1) if args.ps1 else Path(__file__).resolve().parents[1] / "apply-nuclear-chatonly.ps1"
    text = ps1.read_text(encoding="utf-8", errors="ignore")

    files = sorted(base.rglob("*.smali"))
    print(f"smali suborov: {len(files)}")
    index: dict[str, list[Path]] = defaultdict(list)
    rel_of: dict[Path, str] = {}
    for f in files:
        r = class_rel(f, base)
        rel_of[f] = r
        index[r].append(f)

    hook_frag = detect_hook(files, FBFRAG, "A32")
    hook_pref = detect_hook(files, FBPREF, "A0F")
    print(f"hook FbFragmentActivity  = {hook_frag}")
    print(f"hook FbPreferenceActivity = {hook_pref}")

    a32 = finish_body(hook_frag, FBFRAG)
    a0f = finish_body(hook_pref, FBPREF)
    oncreate = finish_body("onCreate", FBFRAG)

    kill_specs = [
        (re.compile(r"\.method public " + hook_frag + r"\(Landroid/os/Bundle;\)V.*?\.end method", re.S), a32),
        (re.compile(r"\.method public onCreate\(Landroid/os/Bundle;\)V.*?\.end method", re.S), oncreate),
        (re.compile(r"\.method public " + hook_pref + r"\(Landroid/os/Bundle;\)V.*?\.end method", re.S), a0f),
    ]

    modified: set[Path] = set()

    def write(f: Path, content: str) -> None:
        modified.add(f)
        if not args.dry_run:
            f.write_text(content, encoding="utf-8")

    def kill_activity(f: Path) -> bool:
        content = f.read_text(errors="ignore")
        if "->finish()V" in content:
            return False
        for rx, body in kill_specs:
            updated = rx.sub(lambda _m: body, content, count=1)
            if updated != content:
                write(f, updated)
                return True
        return False

    # ── PHASE 1: cielene UI aktivity ─────────────────────────────────────
    p1 = p1miss = p1skip = 0
    for target in dict.fromkeys(parse_targets(text, "uiTargets")):
        paths = index.get(strip_prefix(target))
        if not paths:
            p1miss += 1
            continue
        for f in paths:
            if kill_activity(f):
                p1 += 1
            else:
                p1skip += 1
    print(f"Phase 1: patched={p1} skipped={p1skip} missing={p1miss}")

    # ── PHASE 2: cielene battery/background kills ────────────────────────
    p2 = p2miss = p2skip = 0
    for target, header, repl in parse_rules(text, "batteryRules"):
        paths = index.get(strip_prefix(target))
        if not paths:
            p2miss += 1
            continue
        rx = re.compile(re.escape(header) + r".*?\.end method", re.S)
        for f in paths:
            content = f.read_text(errors="ignore")
            updated = rx.sub(lambda _m: repl, content, count=1)
            if updated != content:
                write(f, updated)
                p2 += 1
            else:
                p2skip += 1
    print(f"Phase 2: patched={p2} missing={p2miss} skipped={p2skip}")

    # ── PHASE 3: plosny background sweep ─────────────────────────────────
    bg_rx = ps_rx(parse_scalar_regex(text, "bgPathRegex"))
    p3f = p3m = 0
    for f in files:
        rel = rel_of[f]
        # obfuskovane X/<nahodne>.smali nenesu vyznam v ceste - regex (ar\b, nna, job, ...) by
        # trafil napr. X/1Ar a vynuloval metody v nesuvisiacich triedach (DI kontajner, session,
        # RTC launcher). Komponenty (Service/Receiver/Provider) pokryvaju fazy 4 a 5 podla super.
        if rel.startswith("X/"):
            continue
        if not bg_rx.search(rel) or keep(rel):
            continue
        content = orig = f.read_text(errors="ignore")
        for rx, repl in NOOP_SPECS:
            updated = rx.sub(repl, content)
            if updated != content:
                content = updated
                p3m += 1
        if content != orig:
            write(f, content)
            p3f += 1
    print(f"Phase 3: files={p3f} methodGroups={p3m}")

    # ── PHASE 4: nuclear activity sweep ──────────────────────────────────
    act_wl = re.compile("|".join(f"({p})" for p in parse_regex_list(text, "activityWhitelist")), re.IGNORECASE)
    p4 = p4wl = p4already = p4nomatch = 0
    for f in files:
        head = "\n".join(f.read_text(errors="ignore").split("\n")[:15])
        if not re.search(r"\.super\s+L[^\n]*Activity;", head):
            continue
        rel = rel_of[f]
        if act_wl.search(rel) or keep(rel):
            p4wl += 1
            continue
        content = f.read_text(errors="ignore")
        if "->finish()V" in content:
            p4already += 1
            continue
        if kill_activity(f):
            p4 += 1
            if MEDIA_SUSPECT.search(rel):
                print(f"  ! faza 4 zatvara (moze byt prehliadac fotiek): {rel}")
        else:
            p4nomatch += 1
    print(f"Phase 4: patched={p4} whitelisted={p4wl} already={p4already} nomatch={p4nomatch}")

    # ── PHASE 5: nuclear service/receiver sweep ──────────────────────────
    svc_wl = re.compile("|".join(f"({p})" for p in parse_regex_list(text, "serviceWhitelist")), re.IGNORECASE)
    p5s = p5r = p5wl = 0
    for f in files:
        head = "\n".join(f.read_text(errors="ignore").split("\n")[:15])
        is_svc = re.search(r"\.super\s+L[^\n]*(Service|JobService);", head)
        is_rcv = re.search(r"\.super\s+L[^\n]*Receiver;", head)
        if not is_svc and not is_rcv:
            continue
        rel = rel_of[f]
        if svc_wl.search(rel) or keep(rel):
            p5wl += 1
            continue
        content = orig = f.read_text(errors="ignore")
        for rx, repl in SVC_SPECS:
            content = rx.sub(repl, content)
        if content != orig:
            write(f, content)
            if is_svc:
                p5s += 1
            else:
                p5r += 1
    print(f"Phase 5: services={p5s} receivers={p5r} whitelisted={p5wl}")

    # ── PHASE 6: provider neutralizacia ──────────────────────────────────
    prov_wl = re.compile("|".join(f"({p})" for p in parse_regex_list(text, "providerWhitelist")), re.IGNORECASE)
    p6 = p6wl = 0
    rx6, repl6 = PROVIDER_SPEC
    for f in files:
        head = "\n".join(f.read_text(errors="ignore").split("\n")[:15])
        if not re.search(r"\.super\s+L[^\n]*Provider;", head):
            continue
        rel = rel_of[f]
        if prov_wl.search(rel) or MSG_WHITELIST.search(rel):
            p6wl += 1
            continue
        content = f.read_text(errors="ignore")
        updated = rx6.sub(repl6, content)
        if updated != content:
            write(f, updated)
            p6 += 1
    print(f"Phase 6: providers={p6} whitelisted={p6wl}")

    # ── PHASE 7: (zrusene) branding "Messenger" -> "Spravaren" vo VSETKYCH const-string ──
    # Menilo aj interne identifikatory produktu (X/7Tk, X/1Ar, X/6Wi, DI kontajnery) a mohlo
    # rozbit spravanie voci serveru. Nazov riesi branding.py (manifest) a Declutter (nadpis).

    print(f"\nSPOLU zmenenych suborov: {len(modified)}")
    if args.report:
        Path(args.report).write_text("\n".join(sorted(rel_of[f] for f in modified)) + "\n")
        print(f"report -> {args.report}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
