"""Icônes de l'appli « Magasin » (PWA iPhone) : carton kraft, cadre de visée et code-barres blancs.

Pleine page (sans coins arrondis) : iOS et Android arrondissent eux-mêmes ; le dessin tient dans
la zone sûre centrale (60 %) pour l'usage « maskable ». Relancer après une retouche :
    python3 scripts/make-pwa-icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "public" / "icons"
S = 2048  # dessin en grand, puis réduction (bords propres)


def draw() -> Image.Image:
    top, bottom = (220, 185, 138), (154, 106, 58)  # dégradé kraft de l'IconTile « kraft »
    img = Image.new("RGB", (S, S))
    px = ImageDraw.Draw(img)
    for y in range(S):
        t = y / (S - 1)
        px.line([(0, y), (S, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))

    glyph = Image.new("L", (S, S), 0)
    g = ImageDraw.Draw(glyph)
    lo, hi, arm, w = int(S * 0.24), int(S * 0.76), int(S * 0.13), int(S * 0.045)
    for cx, cy, dx, dy in ((lo, lo, 1, 1), (hi, lo, -1, 1), (lo, hi, 1, -1), (hi, hi, -1, -1)):
        g.rounded_rectangle(sorted_box(cx, cy, cx + dx * arm, cy + dy * w), radius=w // 2, fill=255)
        g.rounded_rectangle(sorted_box(cx, cy, cx + dx * w, cy + dy * arm), radius=w // 2, fill=255)
    bars = [3, 1, 2, 1, 3, 2, 1, 2, 1, 3]
    unit = int(S * 0.0145)
    total = sum(bars) * unit + (len(bars) - 1) * unit
    x = (S - total) // 2
    y0, y1 = int(S * 0.36), int(S * 0.64)
    for b in bars:
        g.rectangle([x, y0, x + b * unit, y1], fill=255)
        x += b * unit + unit

    shadow = glyph.filter(ImageFilter.GaussianBlur(S * 0.012))
    img.paste((92, 60, 28), (0, int(S * 0.008)), shadow.point(lambda v: int(v * 0.45)))
    img.paste((255, 255, 255), (0, 0), glyph)
    return img


def sorted_box(x0, y0, x1, y1):
    return [min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)]


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    big = draw()
    for size, name in ((512, "magasin-512.png"), (192, "magasin-192.png"), (180, "apple-touch-icon.png")):
        big.resize((size, size), Image.LANCZOS).save(OUT / name, optimize=True)
        print("écrit", OUT / name)
