#!/usr/bin/env python3
"""
Vypne QUIC (UDP) v Messengeri. Cez Tor UDP nejde: kazdy QUIC pokus (fotky, videa, GIFy,
DGW transport) cakal na timeout a az potom appka presla na TCP. Bez QUIC ide vsetko rovno
cez TCP, teda hned cez Tor.

Ciele nie su obfuskovane (com.facebook.proxygen, com.facebook.distribgw), takze patch sedi
aj na novsie verzie Messengera:
  * QuicSettings$Builder.build(): prepinace mEnableQuicFbStatic/IgStatic/FbDevserver = false
    a zoznam hostitelov s QUIC (mHostsWithQuicEnabled) = "" - pred tym, nez sa precitaju.
  * MNSDGWNetworkStackConfig.<init>: quicEnabled = false na konci konstruktora.

Idempotentne. Pouzitie: no_quic.py <work/decoded>
"""
import re
import sys
from pathlib import Path

MARK = "# messengerslim: no-quic"

QS = "com/facebook/proxygen/QuicSettings$Builder.smali"
QS_T = "Lcom/facebook/proxygen/QuicSettings$Builder;"
DGW = "com/facebook/distribgw/client/mns/MNSDGWNetworkStackConfig.smali"
DGW_T = "Lcom/facebook/distribgw/client/mns/MNSDGWNetworkStackConfig;"

ROOT_SEG = re.compile(r"^(smali|smali_classes\d+|classes\d*)$")


def find(base: Path, rel: str) -> list[Path]:
    out = []
    for f in base.rglob(Path(rel).name):
        parts = list(f.relative_to(base).parts)
        while parts and ROOT_SEG.match(parts[0]):
            parts.pop(0)
        if "/".join(parts) == rel:
            out.append(f)
    return out


def patch_quic_settings(text: str) -> str:
    # build() zacina "move-object/from16 v0, p0" (p0 je pri 88 lokalnych mimo 4-bit rozsahu),
    # takze v0 = this a v1 je volny - hned dalsia instrukcia ho prepise.
    rx = re.compile(r"(\.method public build\(\)Lcom/facebook/proxygen/QuicSettings;\n"
                    r"    \.locals \d+\n\n    move-object(?:/from16)? v0, p0\n)")
    body = (f"\n    {MARK}\n"
            "    const/4 v1, 0x0\n\n"
            f"    iput-boolean v1, v0, {QS_T}->mEnableQuicFbStatic:Z\n\n"
            f"    iput-boolean v1, v0, {QS_T}->mEnableQuicIgStatic:Z\n\n"
            f"    iput-boolean v1, v0, {QS_T}->mEnableQuicFbDevserver:Z\n\n"
            '    const-string v1, ""\n\n'
            f"    iput-object v1, v0, {QS_T}->mHostsWithQuicEnabled:Ljava/lang/String;\n")
    new, n = rx.subn(lambda m: m.group(1) + body, text, count=1)
    if n != 1:
        raise RuntimeError("QuicSettings$Builder.build(): necakany zaciatok metody")
    return new


def patch_dgw(text: str) -> str:
    # hlavny konstruktor (ten, ktory zapisuje quicEnabled) -> pred jeho return-void
    rx = re.compile(r"(\.method public constructor <init>\([^)]*\)V\n(?:(?!\.end method).*\n)*?"
                    rf"    iput-boolean \w+, p0, {re.escape(DGW_T)}->quicEnabled:Z\n"
                    r"(?:(?!\.end method).*\n)*?)(    return-void\n\.end method)")
    body = (f"    {MARK}\n"
            "    const/4 v0, 0x0\n\n"
            f"    iput-boolean v0, p0, {DGW_T}->quicEnabled:Z\n\n")
    new, n = rx.subn(lambda m: m.group(1) + body + m.group(2), text, count=1)
    if n != 1:
        raise RuntimeError("MNSDGWNetworkStackConfig.<init>: quicEnabled sa nenaslo")
    return new


def main(workdir: str) -> int:
    base = Path(workdir).resolve()
    done = 0
    for rel, fn in ((QS, patch_quic_settings), (DGW, patch_dgw)):
        files = find(base, rel)
        if not files:
            print(f"  chyba: {rel} (ina verzia Messengera?)")
            continue
        for f in files:
            text = f.read_text(encoding="utf-8")
            if MARK in text:
                print(f"  uz patchnute: {rel}")
                continue
            f.write_text(fn(text), encoding="utf-8")
            print(f"  QUIC off: {rel}")
            done += 1
    print(f"no-quic: patchnutych={done}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
