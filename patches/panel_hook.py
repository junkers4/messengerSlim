#!/usr/bin/env python3
"""Hook panelu na miesto Stories pasu: v Messengerovej zdielanej factory na tray s recyclerom
zavola WeatherPanel.install(tray) tesne pred `return-object`.

Factory sa hlada podla tvaru, nie podla obfuskovaneho mena (575: X/2ml.A04, 584: X/2ne.A03):
  .method public static final A0N(Landroid/content/Context;)LX/<tray>;
      ... RecyclerView;-><init>(Context;AttributeSet;I)V ...
      invoke-direct {vT, p0, vR}, LX/<tray>;-><init>(Landroid/content/Context;Landroidx/recyclerview/widget/RecyclerView;)V
      ...
      return-object vT

Pouzitie: panel_hook.py <decoded_dir>
"""
import re
import sys
from pathlib import Path

HOOK = "Lcom/messengerslim/panel/WeatherPanel;->install(Landroid/view/ViewGroup;)V"
WRAP = re.compile(r"invoke-direct \{(v\d+), p0, v\d+\}, (LX/[^;]+;)-><init>"
                  r"\(Landroid/content/Context;Landroidx/recyclerview/widget/RecyclerView;\)V")
METHOD = re.compile(r"(\.method public static final \w+\(Landroid/content/Context;\)(LX/[^;]+;)\n)(.*?)(\.end method)", re.S)


def main() -> None:
    root = Path(sys.argv[1])
    hits = []
    for f in root.glob("smali/*/X/*.smali"):
        s = f.read_text(encoding="utf-8")
        if "RecyclerView;)V" not in s:
            continue
        for m in METHOD.finditer(s):
            body = m.group(3)
            w = WRAP.search(body)
            if not w or w.group(2) != m.group(2):
                continue
            reg = w.group(1)
            ret = f"    return-object {reg}\n"
            assert body.count(ret) == 1, f"{f.name}: return-object {reg} {body.count(ret)}x"
            if HOOK in body:
                hits.append((f, "uz je"))
                continue
            body = body.replace(ret, f"    invoke-static {{{reg}}}, {HOOK}\n\n{ret}")
            s = s[:m.start(3)] + body + s[m.end(3):]
            f.write_text(s, encoding="utf-8")
            hits.append((f, m.group(2)))
    assert len(hits) == 1, f"panel hook: cakal som 1 factory, nasiel {len(hits)}: {hits}"
    print(f"panel hook -> {hits[0][0].relative_to(root)} ({hits[0][1]})")


if __name__ == "__main__":
    main()
