#!/usr/bin/env python3
"""
Najde v dekompilovanom Messengeri miesta, kde sa zapina QUIC (UDP). Cez Tor UDP nejde, takze
kazdy QUIC pokus (fotky, videa, GIFy) caka na timeout, kym appka prejde na TCP. Vystup sluzi
na najdenie prepinaca, ktory sa potom vypne smali patchom (QUIC off -> rovno TCP).

Nic nemeni. Pouzitie: find_quic.py <work/decoded> [--out quic-report.txt]
"""
import argparse
import re
from collections import defaultdict
from pathlib import Path

PATH_RX = re.compile(r"(quic|mvfst|http3|liger)", re.IGNORECASE)
STR_RX = re.compile(r'const-string(?:/jumbo)?\s+\w+,\s+"([^"]*(?:quic|mvfst|http3|h3_|_h3)[^"]*)"',
                    re.IGNORECASE)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("workdir")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    base = Path(args.workdir).resolve()
    by_class = defaultdict(set)
    classes = []
    for f in base.rglob("*.smali"):
        rel = f.relative_to(base).as_posix()
        if PATH_RX.search(rel):
            classes.append(rel)
        try:
            text = f.read_text(errors="ignore")
        except OSError:
            continue
        if "uic" not in text and "UIC" not in text and "mvfst" not in text:
            continue
        for m in STR_RX.finditer(text):
            by_class[rel].add(m.group(1))

    lines = [f"== triedy s quic/mvfst/http3/liger v ceste: {len(classes)}"]
    lines += sorted(classes)
    lines.append(f"\n== retazce (MobileConfig kluce, nazvy prepinacov) v {len(by_class)} triedach")
    for rel in sorted(by_class):
        lines.append(rel)
        lines += [f"    {s}" for s in sorted(by_class[rel])]
    report = "\n".join(lines) + "\n"
    print(report)
    if args.out:
        Path(args.out).write_text(report, encoding="utf-8")
        print(f"report -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
