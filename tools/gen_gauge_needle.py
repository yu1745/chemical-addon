# Generates the needle texture for the S02/S03 gauge dials (VesselGaugeRenderer).
# Pure white so the renderer's per-gauge tint (red thermometer / blue pressure
# gauge) shows through; also strips the BAKED needle out of the four dial
# textures (it was a static 2px bar at 12 o'clock — the live needle replaces it).

from PIL import Image
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "chemicaladdon"

# needle pixels (x7-8, y3-8) -> dial-face colour to repaint
NEEDLE_STRIP = [(x, y) for y in range(3, 9) for x in range(7, 9)]
STRIPS = {
    "thermometer_panel": (0xC42C2C, 0xECEEF2),  # red needle on white face
    "thermometer":       (0xC42C2C, 0xECEEF2),
    "pressure_gauge_panel": (0x486CBC, 0xE2E8F4),  # blue needle on white face
    "pressure_gauge":    (0x486CBC, 0xE2E8F4),
}

def main():
    # 1) white needle tint texture
    tex = Image.new("RGBA", (16, 16), (255, 255, 255, 255))
    tex.save(ROOT / "textures" / "block" / "gauge_needle.png")
    print("wrote textures/block/gauge_needle.png")

    # 2) strip the baked needles
    for name, (needle, face) in STRIPS.items():
        path = ROOT / "textures" / "block" / (name + ".png")
        im = Image.open(path).convert("RGBA")
        px = im.load()
        bad = []
        for (x, y) in NEEDLE_STRIP:
            r, g, b, a = px[x, y]
            if (r << 16) | (g << 8) | b != needle:
                bad.append((x, y, hex((r << 16) | (g << 8) | b)))
        if bad:
            raise SystemExit(f"{name}: unexpected pixels in the needle strip: {bad}")
        for (x, y) in NEEDLE_STRIP:
            px[x, y] = ((face >> 16) & 0xFF, (face >> 8) & 0xFF, face & 0xFF, 255)
        im.save(path)
        print(f"stripped needle from {name}.png")

if __name__ == "__main__":
    main()
