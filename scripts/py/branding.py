#!/usr/bin/env python3
"""Branding Spravarne v strome z APKEditor decode:

  * nazov appky pod ikonkou a v Nedavnych -> "Spravaren"
    (android:label na <application> a na spustacom aliase StartScreenActivity),
  * ikonka: povodna Messenger bublina, len modra -> cierna (biely blesk ostava).
    Prefarbuje sa podla sytosti pixelu, takze hrany ostanu vyhladene.

Pouzitie: branding.py <decoded_dir>
"""
import re
import sys
from pathlib import Path

from PIL import Image

NAME = "Spravaren"
ICONS = ("launcher_icon.png", "launcher_icon_foreground.png")  # monochrome nechavame


def relabel(manifest: Path) -> None:
    s = manifest.read_text(encoding="utf-8")
    # <application ... android:label="@string/..."
    s, n_app = re.subn(r'(<application\b[^>]*?android:label=")[^"]*(")', rf"\g<1>{NAME}\g<2>", s, count=1, flags=re.S)
    # spustaci alias nema vlastny label -> dedi z <application>, ale pre istotu mu ho dame
    s, n_alias = re.subn(r'(<activity-alias)(\s+android:name="com\.facebook\.orca\.auth\.StartScreenActivity")',
                         rf'\1 android:label="{NAME}"\2', s, count=1)
    assert n_app == 1 and n_alias == 1, f"label: application={n_app} alias={n_alias}"
    manifest.write_text(s, encoding="utf-8")
    print(f"label -> {NAME}")


def blacken(png: Path) -> None:
    im = Image.open(png).convert("RGBA")
    px = im.load()
    # najsytejsia farba v obrazku = Messenger modra
    sat = lambda r, g, b: max(r, g, b) - min(r, g, b)
    top = max(sat(*px[x, y][:3]) for y in range(im.height) for x in range(im.width)) or 1
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            t = min(1.0, sat(r, g, b) / top)          # 0 = biela/seda, 1 = cista modra
            v = round(min(r, g, b) * (1 - t))         # modra -> cierna, biela ostava biela
            px[x, y] = (v, v, v, a)
    im.save(png)
    print(f"ikonka -> cierna: {png.name}")


def main() -> None:
    root = Path(sys.argv[1])
    relabel(root / "AndroidManifest.xml")
    found = [p for p in (root / "resources").rglob("*.png") if p.name in ICONS]
    assert found, "launcher ikonky nenajdene"
    for p in found:
        blacken(p)


if __name__ == "__main__":
    main()
