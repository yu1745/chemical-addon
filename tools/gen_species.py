#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Chemical Addon species/resource generator (M0).
Single source of truth for the 38 fluid species + 18 solid species from
plans/08-substance-catalog.md. Generates:
  - fluid textures   (assets/chemicaladdon/textures/fluid/<id>_still.png + _flow.png)
  - item textures    (assets/chemicaladdon/textures/item/<id>.png, <id>_bucket.png)
  - block textures   (assets/chemicaladdon/textures/block/*.png)
  - atlas            (assets/minecraft/atlases/blocks.json — stitches fluid sprites
                      into the block atlas; Registrate datagen does NOT emit this)
  - lang             (zh_cn.json fully; lang/default/extra.json = English EXTRA keys
                      fed into Registrate datagen's lang provider — see ChemicalDataGen)
  - Java sources     (registry/AllFluids.java, registry/AllItems.java)

Model JSONs (blockstates / block models / item models / en_us lang) are produced
by Registrate datagen instead: ./gradlew runData writes them to
src/generated/resources. This file no longer writes those.

Re-run: python3 tools/gen_species.py   (regenerates everything deterministically)
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/chemicaladdon")
JAVA = os.path.join(ROOT, "src/main/java/com/yu1745/chemicaladdon/registry")

# ---------------------------------------------------------------- species data
# (id, cn, en, color, density, viscosity, temperature_K, is_gas)
FLUIDS = [
    # --- gases (negative density, low viscosity, translucent) ---
    ("air",                "空气",     "Air",                 0xC8D8E8, -500, 200, 293, True),
    ("hydrogen",           "氢气",     "Hydrogen",            0xD8E0F0, -100, 200, 293, True),
    ("oxygen",             "氧气",     "Oxygen",              0xB0C8E8, -200, 200, 293, True),
    ("nitrogen",           "氮气",     "Nitrogen",            0xC0D0E0, -200, 200, 293, True),
    ("chlorine",           "氯气",     "Chlorine",            0x9ED44D, -400, 200, 293, True),
    ("carbon_dioxide",     "二氧化碳", "Carbon Dioxide",      0xB8B8B0, -300, 200, 293, True),
    ("carbon_monoxide",    "一氧化碳", "Carbon Monoxide",     0xC0C0C8, -250, 200, 293, True),
    ("sulfur_dioxide",     "二氧化硫", "Sulfur Dioxide",      0xE0E0B0, -400, 200, 293, True),
    ("sulfur_trioxide",    "三氧化硫", "Sulfur Trioxide",     0xE8D8C8, -500, 200, 293, True),
    ("nitric_oxide",       "一氧化氮", "Nitric Oxide",        0xC8A8A8, -350, 200, 293, True),
    ("nitrogen_dioxide",   "二氧化氮", "Nitrogen Dioxide",    0xB84A2A, -400, 200, 293, True),
    ("ammonia",            "氨气",     "Ammonia",             0xC8E0C8, -350, 200, 293, True),
    ("hydrogen_chloride",  "氯化氢",   "Hydrogen Chloride",   0xD8E8D0, -400, 200, 293, True),
    # --- liquids (pure fluids only; solutions/slurries are species "modes", not fluids) ---
    # water is vanilla minecraft:water (the aqueous solvent) — NOT registered here
    # --- heat transfer medium ---
    ("thermal_oil",                "导热油", "Thermal Oil",        0xC89030, 900, 1500, 400, False),
]

# (id, cn, en, color)
SOLIDS = [
    ("rock_salt",              "岩盐",   "Rock Salt",       0xE8E0D0),
    ("limestone",              "石灰石", "Limestone",       0xD0D0C0),
    ("quicklime",              "生石灰", "Quicklime",       0xE0E0E0),
    ("slaked_lime",            "熟石灰", "Slaked Lime",     0xE8E8E0),
    ("sodium_bicarbonate",     "重碱",   "Sodium Bicarbonate", 0xE0E8E0),
    ("soda_ash",               "纯碱",   "Soda Ash",        0xF0F0F0),
    ("gypsum",                 "石膏",   "Gypsum",          0xE0E0D0),
    ("sulfur",                 "硫磺",   "Sulfur",          0xD8D838),
    ("bauxite",                "铝土矿", "Bauxite",         0xB85030),
    ("aluminium_hydroxide",    "氢氧化铝", "Aluminium Hydroxide", 0xE8E8E8),
    ("alumina",                "氧化铝", "Alumina",         0xF0F0F0),
    ("phosphate_rock",         "磷矿",   "Phosphate Rock",  0xC8B088),
    ("phosphogypsum",          "磷石膏", "Phosphogypsum",   0xD8D0C0),
    ("ammonium_sulfate",       "硫酸铵", "Ammonium Sulfate", 0xE8E8E0),
    ("ammonium_nitrate",       "硝酸铵", "Ammonium Nitrate", 0xE8E8F0),
    ("urea",                   "尿素",   "Urea",            0xF0F0F0),
    ("calcium_chloride",       "氯化钙", "Calcium Chloride", 0xE0E8F0),
    ("calcium_sulfite",        "亚硫酸钙", "Calcium Sulfite", 0xE0E8E0),
    ("filter_cake",            "滤渣",   "Filter Cake",     0x908878),
]

# (id, cn, en, color)
BLOCKS = [
    ("chemical_brick",    "化工砖", "Chemical Brick",    0x8E8478),
    ("decant_port",       "分液口", "Decant Port",       0x8E8478),
    ("decant_hose",       "分液软管", "Decant Hose",       0xB87333),
    ("reactor_controller", "反应釜控制器", "Reactor Controller", 0x6E6E6E),
    ("filter_press",      "过滤机", "Filter Press",      0x7A7A8A),
    ("settling_basin",    "沉淀池控制器", "Settling Basin", 0x5E6E7A),
    ("electrolyzer",      "电解槽", "Electrolyzer",     0x5E7A8A),
    ("thermometer",       "温度计", "Thermometer",      0x5A5A62),
]

# Solution modes ("species = mode", plans/03 §4): NOT registered fluids — only a
# creative "packed mixture" bucket per mode. Ions/solventRatio live in the species
# JSON (data/chemistry/species/*.json); this table only drives the bucket item's
# model + lang (the item registration lives in registry/AllContainers.java).
# (id, cn, en)
SOLUTIONS = [
    ("sulfuric_acid",            "硫酸",   "Sulfuric Acid"),
    ("hydrochloric_acid",        "盐酸",   "Hydrochloric Acid"),
    ("nitric_acid",              "硝酸",   "Nitric Acid"),
    ("brine",                    "饱和盐水", "Saturated Brine"),
    ("caustic_soda_solution",    "烧碱液", "Caustic Soda Solution"),
    ("soda_ash_solution",        "纯碱液", "Soda Ash Solution"),
    ("ammonium_chloride_solution", "氯化铵液", "Ammonium Chloride Solution"),
    ("calcium_chloride_solution",  "氯化钙液", "Calcium Chloride Solution"),
    ("ammonia_water",            "氨水",   "Ammonia Water"),
    ("ammonium_sulfate_solution", "硫酸铵液", "Ammonium Sulfate Solution"),
    ("ammonium_nitrate_solution", "硝酸铵液", "Ammonium Nitrate Solution"),
]

# Slurry modes (plans/03 §12): water + a suspended solid (NOT dissolved ions).
# Same "packed mixture" bucket treatment as SOLUTIONS; the difference is only the
# species JSON (a "suspended" array instead of "ions").
# (id, cn, en)
SLURRIES = [
    ("milk_of_lime",             "石灰乳", "Milk of Lime"),
    ("gypsum_slurry",            "石膏浆", "Gypsum Slurry"),
    ("sodium_bicarbonate_slurry", "重碱浆", "Sodium Bicarbonate Slurry"),
    ("calcium_sulfite_slurry",   "亚硫酸钙浆", "Calcium Sulfite Slurry"),
]

# ---------------------------------------------------------------- png writer
def write_png(path, rgba_rows):
    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)
    w = len(rgba_rows[0]) // 4
    h = len(rgba_rows)
    raw = b"".join(b"\x00" + bytes(row) for row in rgba_rows)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)

def hex_rgba(rgb, alpha):
    return ((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)

def read_indexed_png(path):
    """Decode a paletted PNG (with optional tRNS alpha) into RGBA rows. Used to
    load the grayscale fluid base sprite (Create's potion texture)."""
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    i = 8
    w = h = bd = ct = None
    plte = None
    trns = b""
    idat = b""
    while i < len(data):
        ln = struct.unpack(">I", data[i:i+4])[0]
        tag = data[i+4:i+8]
        body = data[i+8:i+8+ln]
        if tag == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            plte = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        i += 12 + ln
    assert ct == 3 and plte is not None, "expected indexed (palette) PNG"
    raw = zlib.decompress(idat)
    stride = (w * bd + 7) // 8
    pos = 0
    prev = bytearray(stride)
    scanlines = []
    for _ in range(h):
        ft = raw[pos]; pos += 1
        line = bytearray(raw[pos:pos+stride]); pos += stride
        out = bytearray(stride)
        for x in range(stride):
            a = out[x-1] if x >= 1 else 0
            b = prev[x]
            c = prev[x-1] if x >= 1 else 0
            if ft == 0:
                v = line[x]
            elif ft == 1:
                v = (line[x] + a) & 255
            elif ft == 2:
                v = (line[x] + b) & 255
            elif ft == 3:
                v = (line[x] + ((a + b) >> 1)) & 255
            else:
                p = a + b - c
                pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                v = (line[x] + pr) & 255
            out[x] = v
        prev = out
        scanlines.append(out)
    mask = (1 << bd) - 1
    rgba_rows = []
    for scan in scanlines:
        row = []
        for x in range(w):
            if bd < 8:
                bit = x * bd
                idx = (scan[bit // 8] >> (8 - bd - (bit % 8))) & mask
            else:
                idx = scan[x]
            row += [plte[idx*3], plte[idx*3+1], plte[idx*3+2],
                    trns[idx] if idx < len(trns) else 255]
        rgba_rows.append(row)
    return w, h, rgba_rows


_FLUID_BASES = {}
def load_fluid_base(which):
    """Load the desaturated fluid base sprite (still/flow) shipped under tools/.
    This is Create's potion sprite — already neutral grey (R==G==B==luminance),
    seamlessly tileable, 32-frame animated — used as the neutral base we tint per
    species instead of hand-drawing gradients (which produced seam bands when
    stacked)."""
    if which not in _FLUID_BASES:
        _FLUID_BASES[which] = read_indexed_png(
            os.path.join(ROOT, "tools", f"fluid_base_{which}.png"))
    return _FLUID_BASES[which]


def tint_fluid(base, rgb, gas):
    """Multiply-tint the grayscale base by the species colour, preserving the
    base's luminance structure (waves/highlights/animation) and edge alpha. This
    is the same multiply blend Create applies at runtime to potion fluids, baked
    into the sprite so each species renders self-contained. Gases are made more
    translucent so they read as diffuse rather than liquid."""
    w, h, rows = base
    tr = (rgb >> 16) & 0xFF
    tg = (rgb >> 8) & 0xFF
    tb = rgb & 0xFF
    alpha_scale = 0.6 if gas else 1.0
    out = []
    for row in rows:
        new = []
        for x in range(w):
            i = x * 4
            lum = row[i]  # base is pure gray: R==G==B
            new += [lum * tr // 255, lum * tg // 255, lum * tb // 255,
                    int(row[i+3] * alpha_scale)]
        out.append(new)
    return out

def make_item_texture(rgb):
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            edge = x == 0 or y == 0 or x == 15 or y == 15
            if edge:
                row += [int(r * 0.7), int(g * 0.7), int(b * 0.7), 255]
            else:
                row += [r, g, b, 255]
        rows.append(row)
    return rows


def make_vial_texture():
    """16x16 glass sample-vial base: a translucent beaker outline (rim + walls +
    floor), transparent interior where the fluid tint shows through. Paired with
    make_vial_mask_texture() for the DynamicFluidContainerModel."""
    glass = (170, 185, 205, 210)  # semi-transparent glass
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if y == 3 and 2 <= x <= 13:                    # rim (top bar)
                row += [glass[0], glass[1], glass[2], 255]
            elif y == 14 and 2 <= x <= 13:                 # floor
                row += [glass[0], glass[1], glass[2], 255]
            elif 4 <= y <= 13 and (x == 2 or x == 13):     # walls
                row += [glass[0], glass[1], glass[2], 255]
            else:
                row += [0, 0, 0, 0]                        # transparent interior
        rows.append(row)
    return rows


def make_vial_mask_texture():
    """16x16 fluid-region mask: opaque where the liquid fills the vial body,
    transparent elsewhere. The DynamicFluidContainerModel bakes this region with
    the fluid's tinted still sprite."""
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if 4 <= y <= 13 and 3 <= x <= 12:
                row += [255, 255, 255, 255]                # opaque mask
            else:
                row += [0, 0, 0, 0]
        rows.append(row)
    return rows

# ---------------------------------------------------------------- generators
def gen_textures():
    still_base = load_fluid_base("still")
    flow_base = load_fluid_base("flow")
    d = os.path.join(ASSETS, "textures/fluid")
    os.makedirs(d, exist_ok=True)
    anim_mcmeta = '{"animation": {"frametime": 1}}\n'
    for sid, _, _, color, _, _, _, gas in FLUIDS:
        write_png(os.path.join(d, f"{sid}_still.png"), tint_fluid(still_base, color, gas))
        write_png(os.path.join(d, f"{sid}_flow.png"), tint_fluid(flow_base, color, gas))
        # animation metadata: base sprite is a vertical sheet of 32 frames (16x512
        # still / 32x1024 flow); without this Minecraft would render the whole
        # strip as one tall image. Mirrors Create's potion_still.png.mcmeta.
        for kind in ("still", "flow"):
            with open(os.path.join(d, f"{sid}_{kind}.png.mcmeta"), "w", encoding="utf-8") as f:
                f.write(anim_mcmeta)
    # mixture fluid: raw neutral base (no baked colour — the colour comes from
    # the per-stack NBT tint blended from components, see MixtureFluidType).
    # Animated like the species sprites (same vertical-sheet base).
    write_png(os.path.join(d, "mixture_still.png"), still_base[2])
    write_png(os.path.join(d, "mixture_flow.png"), flow_base[2])
    for kind in ("still", "flow"):
        with open(os.path.join(d, f"mixture_{kind}.png.mcmeta"), "w", encoding="utf-8") as f:
            f.write(anim_mcmeta)
    d = os.path.join(ASSETS, "textures/item")
    os.makedirs(d, exist_ok=True)
    for sid, _, _, color in SOLIDS:
        write_png(os.path.join(d, f"{sid}.png"), make_item_texture(color))
    # the generic sample vial (hand-written item, not a species): glass base +
    # fluid mask for the DynamicFluidContainerModel
    write_png(os.path.join(d, "fluid_vial.png"), make_vial_texture())
    write_png(os.path.join(d, "fluid_vial_mask.png"), make_vial_mask_texture())

def gen_atlas():
    """Register every fluid still/flow texture as a sprite source for the block
    atlas. In 1.20.1 the block atlas (minecraft:blocks) is composed from sprite
    sources in assets/minecraft/atlases/blocks.json — block-model texture refs
    alone do NOT get fluid textures stitched, so without this the in-world fluid
    renders as the missing-texture sprite (purple/black). Forge's fluid renderer
    resolves still/flow sprites via getTextureAtlas(LOCATION_BLOCKS).apply(...),
    which returns the missing sprite for any unstitched texture.

    Registrate datagen does NOT emit this file; Create and createaddition both
    hand-author their assets/minecraft/atlases/blocks.json for the same reason."""
    d = os.path.join(ROOT, "src/main/resources/assets/minecraft/atlases")
    os.makedirs(d, exist_ok=True)
    import json as _json
    sources = []
    for sid, *_ in FLUIDS:
        sources.append({"type": "single", "resource": f"chemicaladdon:fluid/{sid}_still"})
        sources.append({"type": "single", "resource": f"chemicaladdon:fluid/{sid}_flow"})
    # the mixture fluid's neutral base textures must be stitched too
    for kind in ("still", "flow"):
        sources.append({"type": "single", "resource": f"chemicaladdon:fluid/mixture_{kind}"})
    with open(os.path.join(d, "blocks.json"), "w", encoding="utf-8") as f:
        _json.dump({"sources": sources}, f, indent=2)
        f.write("\n")


def gen_bucket_models():
    """Write the per-species bucket item models as forge:fluid_container
    (DynamicFluidContainerModel). The fluid is rendered from the still sprite +
    per-stack tint, so no hand-drawn <fluid>_bucket.png is needed — just vanilla's
    empty-bucket base + Forge's bucket_fluid mask. The .bucket() registration is
    told to skip its default item/generated model (see fluid_entry)."""
    d = os.path.join(ASSETS, "models/item")
    os.makedirs(d, exist_ok=True)
    import json as _json
    for sid, _, _, _, _, _, _, is_gas in FLUIDS:
        model = {
            "parent": "forge:item/default",
            "loader": "forge:fluid_container",
            "textures": {
                "base": "minecraft:item/bucket",
                "fluid": "forge:item/mask/bucket_fluid"
            },
            "fluid": f"chemicaladdon:{sid}"
        }
        if is_gas:
            # lighter-than-air fluids render flipped (upside-down) in the bucket
            model["flip_gas"] = True
        with open(os.path.join(d, f"{sid}_bucket.json"), "w", encoding="utf-8") as f:
            _json.dump(model, f, indent=2)
            f.write("\n")

def gen_solution_bucket_models():
    """Write the per-solution creative "packed mixture" bucket models. They use
    Forge's DynamicFluidContainerModel (the same bucket base + fluid mask as the
    species buckets) with `fluid: minecraft:empty` — the actual mixture is read
    from the item's FluidHandlerItemStack NBT and tinted by the per-stack colour
    (see ChemicalAddonClient's colour-provider registration)."""
    d = os.path.join(ASSETS, "models/item")
    os.makedirs(d, exist_ok=True)
    import json as _json
    for sid, *_ in SOLUTIONS + SLURRIES:
        model = {
            "parent": "forge:item/default",
            "loader": "forge:fluid_container",
            "textures": {
                "base": "minecraft:item/bucket",
                "fluid": "forge:item/mask/bucket_fluid"
            },
            "fluid": "minecraft:empty"
        }
        with open(os.path.join(d, f"{sid}_bucket.json"), "w", encoding="utf-8") as f:
            _json.dump(model, f, indent=2)
            f.write("\n")

def make_brick_texture(rgb):
    """Brick pattern: 8x8 brick rows with dark mortar lines."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    rows = []
    for y in range(16):
        row = []
        mortar_y = (y % 8) == 7
        for x in range(16):
            offset = 4 if (y // 8) % 2 else 0
            mortar = mortar_y or (x + offset) % 8 == 7
            if mortar:
                row += [int(r * 0.55), int(g * 0.55), int(b * 0.55), 255]
            else:
                row += [r, g, b, 255]
        rows.append(row)
    return rows


def make_decant_port_texture(rgb):
    """Decant port: a brick wall face with a central pipe fitting — a dark
    circular bore ringed by a metallic flange (and an outer seam), so the block
    reads as a drain tap on the vessel shell rather than a plain brick."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            dx = x - 7.5
            dy = y - 7.5
            d2 = dx * dx + dy * dy
            if d2 <= 10.0:
                row += [24, 26, 30, 255]    # bore (dark opening)
            elif d2 <= 20.0:
                row += [118, 124, 134, 255]  # flange (metallic ring)
            elif d2 <= 28.0:
                row += [78, 82, 90, 255]     # flange outer seam
            else:
                mortar_y = (y % 8) == 7
                offset = 4 if (y // 8) % 2 else 0
                mortar = mortar_y or (x + offset) % 8 == 7
                if mortar:
                    row += [int(r * 0.55), int(g * 0.55), int(b * 0.55), 255]
                else:
                    row += [r, g, b, 255]
        rows.append(row)
    return rows


def make_panel_texture(rgb):
    """Machine panel: metal base, dark display strip, status lamp."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if y in (0, 15) or x in (0, 15):
                row += [int(r * 1.2), int(g * 1.2), int(b * 1.2), 255]  # edge highlight
            elif 7 <= y <= 9 and 3 <= x <= 12:
                row += [46, 92, 62, 255]  # display strip
            elif y == 4 and 12 <= x <= 14:
                row += [200, 60, 60, 255]  # status lamp
            else:
                row += [r, g, b, 255]
        rows.append(row)
    return rows


def make_open_panel_texture(rgb):
    """Machine panel for the open-topped vessel variant: a bright lip along the
    top edge marks the open rim."""
    rows = make_panel_texture(rgb)
    rim = [255, 214, 90, 255] * 16  # flat row: 16 px of gold rim
    rows[0] = rim
    rows[1] = rim
    return rows


def make_thermometer_texture(rgb):
    """Thermometer dial: a light circular gauge face with a red needle and a
    dark tick ring, on a metallic panel."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            dx = x - 7.5
            dy = y - 7.5
            d2 = dx * dx + dy * dy
            if abs(dx) <= 0.6 and -5.0 <= dy <= 0.5:
                row += [196, 44, 44, 255]          # red needle (centre, pointing up)
            elif d2 <= 6.5 * 6.5:
                if d2 >= 4.5 * 4.5:
                    row += [40, 42, 48, 255]       # tick ring (rim)
                else:
                    row += [236, 238, 242, 255]    # dial face
            else:
                row += [int(r * 0.75), int(g * 0.75), int(b * 0.75), 255]  # panel
        rows.append(row)
    return rows


def gen_block_textures():
    d = os.path.join(ASSETS, "textures/block")
    os.makedirs(d, exist_ok=True)
    write_png(os.path.join(d, "chemical_brick.png"), make_brick_texture(0x8E8478))
    write_png(os.path.join(d, "decant_port.png"), make_decant_port_texture(0x8E8478))
    write_png(os.path.join(d, "reactor_controller.png"), make_panel_texture(0x6E6E6E))
    write_png(os.path.join(d, "reactor_controller_open.png"), make_open_panel_texture(0x6E6E6E))
    write_png(os.path.join(d, "filter_press.png"), make_panel_texture(0x7A7A8A))
    write_png(os.path.join(d, "settling_basin.png"), make_panel_texture(0x5E6E7A))
    write_png(os.path.join(d, "electrolyzer.png"), make_panel_texture(0x5E7A8A))
    write_png(os.path.join(d, "thermometer.png"), make_thermometer_texture(0x5A5A62))


# Extra lang keys added by hand (GUIs, goggles, diagnostics, assemble messages).
# These survive regeneration; edit them here, never in the generated json.
EXTRA_LANG_ZH = {
    "itemGroup.chemicaladdon": "化学附属",
    "item.chemicaladdon.fluid_vial": "样品瓶",
    "goggles.chemicaladdon.temperature": "温度：%s°C",
    "goggles.chemicaladdon.heat.none": "无热级",
    "goggles.chemicaladdon.heat.heated": "加热",
    "goggles.chemicaladdon.heat.superheated": "超级加热",
    "goggles.chemicaladdon.contents": "釜内：",
    "goggles.chemicaladdon.solution": "溶液",
    "goggles.chemicaladdon.bucket_empty": "空",
    "goggles.chemicaladdon.items": "物品：",
    "goggles.chemicaladdon.progress": "进度：%s%%（%s）",
    "goggles.chemicaladdon.status": "状态：",
    "goggles.chemicaladdon.thermometer_threshold": "报警阈值：%s°C",
    "goggles.chemicaladdon.thermometer_alarm": "报警：超温",
    "goggles.chemicaladdon.thermometer_no_vessel": "未连接反应釜",
    "thermometer.chemicaladdon.threshold": "报警阈值",
    "status.chemicaladdon.not_assembled": "未成型",
    "status.chemicaladdon.reacting": "反应中",
    "status.chemicaladdon.temperature": "温度不满足",
    "status.chemicaladdon.output_full": "输出已满",
    "status.chemicaladdon.no_recipe": "无匹配配方",
    "assemble.chemicaladdon.ok": "§a反应釜结构成型！右键打开面板存取物品",
    "assemble.chemicaladdon.fail": "§c结构不完整（%s）：%s",
    "assemble.chemicaladdon.bottom_gap": "底面缺少化工砖",
    "assemble.chemicaladdon.top_gap": "顶面缺少化工砖",
    "assemble.chemicaladdon.ring_gap": "壁层缺少化工砖",
    "assemble.chemicaladdon.interior_blocked": "内部空间被占用",
    "assemble.chemicaladdon.too_short": "壁层至少需要 1 层（总高 3）",
    "assemble.chemicaladdon.partial_top": "顶面必须全封（9 块砖）或全开（0 块砖）",
    "assemble.chemicaladdon.north_side": "北侧",
    "assemble.chemicaladdon.south_side": "南侧",
    "assemble.chemicaladdon.east_side": "东侧",
    "assemble.chemicaladdon.west_side": "西侧",
    "gui.chemicaladdon.hint": "状态请戴工程师护目镜查看",
}

EXTRA_LANG_EN = {
    "itemGroup.chemicaladdon": "Chemical Addon",
    "goggles.chemicaladdon.temperature": "Temperature: %s°C",
    "goggles.chemicaladdon.heat.none": "No heat",
    "goggles.chemicaladdon.heat.heated": "Heated",
    "goggles.chemicaladdon.heat.superheated": "Superheated",
    "goggles.chemicaladdon.contents": "Contents:",
    "goggles.chemicaladdon.solution": "Solution",
    "goggles.chemicaladdon.bucket_empty": "Empty",
    "goggles.chemicaladdon.items": "Items:",
    "goggles.chemicaladdon.progress": "Progress: %s%% (%s)",
    "goggles.chemicaladdon.status": "Status:",
    "goggles.chemicaladdon.thermometer_threshold": "Threshold: %s°C",
    "goggles.chemicaladdon.thermometer_alarm": "ALARM",
    "goggles.chemicaladdon.thermometer_no_vessel": "Not attached to a reactor",
    "thermometer.chemicaladdon.threshold": "Alarm Threshold",
    "status.chemicaladdon.not_assembled": "Not assembled",
    "status.chemicaladdon.reacting": "Reacting",
    "status.chemicaladdon.temperature": "Temperature not met",
    "status.chemicaladdon.output_full": "Output full",
    "status.chemicaladdon.no_recipe": "No matching recipe",
    "assemble.chemicaladdon.ok": "§aReactor assembled! Right-click to open the panel",
    "assemble.chemicaladdon.fail": "§cStructure incomplete (%s): %s",
    "assemble.chemicaladdon.bottom_gap": "brick missing in the bottom layer",
    "assemble.chemicaladdon.top_gap": "brick missing in the top layer",
    "assemble.chemicaladdon.ring_gap": "brick missing in the wall",
    "assemble.chemicaladdon.interior_blocked": "interior is blocked",
    "assemble.chemicaladdon.too_short": "need at least 1 wall layer (height 3)",
    "gui.chemicaladdon.hint": "Wear engineer goggles to see reactor state",
}


def gen_lang():
    # zh_cn stays fully py-generated (single source of truth, survives regeneration).
    zh = dict(EXTRA_LANG_ZH)
    for sid, cn, _, _, _, _, _, _ in FLUIDS:
        zh[f"fluid.chemicaladdon.{sid}"] = cn
        zh[f"item.chemicaladdon.{sid}_bucket"] = cn + "桶"
    for sid, cn, _, _ in SOLIDS:
        zh[f"item.chemicaladdon.{sid}"] = cn
    for sid, cn, _ in SOLUTIONS + SLURRIES:
        zh[f"item.chemicaladdon.{sid}_bucket"] = cn + "桶"
    for sid, cn, _, _ in BLOCKS:
        zh[f"block.chemicaladdon.{sid}"] = cn
    import json as _json
    with open(os.path.join(ASSETS, "lang/zh_cn.json"), "w", encoding="utf-8") as f:
        _json.dump(zh, f, ensure_ascii=False, indent=2)

    # en_us is owned by Registrate datagen (generated -> src/generated/resources).
    # The English EXTRA keys (goggles/status/assemble/gui/itemGroup) have no
    # `.lang()` call anywhere, so they are exported here and fed into datagen's
    # lang provider via ChemicalDataGen (Create's lang/default/ pattern).
    # NOTE: en_us must NOT exist in src/main/resources or it would collide with
    # the datagen output on the runtime classpath.
    default_dir = os.path.join(ASSETS, "lang/default")
    os.makedirs(default_dir, exist_ok=True)
    with open(os.path.join(default_dir, "extra.json"), "w", encoding="utf-8") as f:
        _json.dump(EXTRA_LANG_EN, f, ensure_ascii=False, indent=2)

def fluid_entry(sid, en_name, density, viscosity, temp, gas):
    return ("\n\tpublic static final FluidEntry<ForgeFlowingFluid.Flowing> "
            f"{sid.upper()} = REGISTRATE.standardFluid(\"{sid}\",\n"
            f"\t\t\t(props, still, flow) -> new ChemFluidType(props, still, flow, {str(gas).lower()}))\n"
            f"\t\t.lang(\"{en_name}\")\n"
            f"\t\t.properties(b -> b.density({density})\n"
            f"\t\t\t.viscosity({viscosity})\n"
            f"\t\t\t.temperature({temp}))\n"
            "\t\t.source(ForgeFlowingFluid.Source::new)\n"
            "\t\t.block()\n"
            f"\t\t.lang(\"{en_name}\")\n"
            "\t\t.build()\n"
            f"\t\t.bucket().lang(\"{en_name} Bucket\").model((ctx, prov) -> {{}}).build()\n"
            "\t\t.register();")

def gen_fluids_java():
    parts = ["package com.yu1745.chemicaladdon.registry;",
             "",
             "import com.simibubi.create.foundation.data.CreateRegistrate;",
             "import com.tterrag.registrate.util.entry.FluidEntry;",
             "import com.yu1745.chemicaladdon.ChemicalAddon;",
             "import com.yu1745.chemicaladdon.fluid.ChemFluidType;",
             "import com.yu1745.chemicaladdon.fluid.MixtureFluidType;",
             "import net.minecraftforge.fluids.ForgeFlowingFluid;",
             "",
             "public class AllFluids {",
             "\tpublic static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();",
             ""]
    for sid, _, en_name, _, density, viscosity, temp, gas in FLUIDS:
        parts.append(fluid_entry(sid, en_name, density, viscosity, temp, gas))
    # the mixture meta-fluid: a single registered Forge fluid whose FluidStack
    # NBT carries the component composition + blended colour (see Mixture /
    # MixtureFluidType). No block/bucket — it lives in tanks/pipes only.
    parts.append(
        "\n\tpublic static final FluidEntry<ForgeFlowingFluid.Flowing> MIXTURE = REGISTRATE.standardFluid(\"mixture\",\n"
        "\t\t\t(props, still, flow) -> new MixtureFluidType(props, still, flow))\n"
        "\t\t.lang(\"Mixture\")\n"
        "\t\t.properties(b -> b.density(1000)\n"
        "\t\t\t.viscosity(1000)\n"
        "\t\t\t.temperature(300))\n"
        "\t\t.source(ForgeFlowingFluid.Source::new)\n"
        "\t\t.register();")
    parts += ["", "\tpublic static void register() {", "\t}", "}", ""]
    with open(os.path.join(JAVA, "AllFluids.java"), "w", encoding="utf-8") as f:
        f.write("\n".join(parts))

def gen_fluid_colors_java():
    """Emit FluidColors.java: the per-species RGB colour (same source column as
    the texture tint) so Java can blend mixture colours at runtime. Without this
    Java has no access to species colours (they're otherwise only baked into the
    PNGs by tint_fluid)."""
    parts = ["package com.yu1745.chemicaladdon.fluid;",
             "",
             "import java.util.HashMap;",
             "import java.util.Map;",
             "import net.minecraft.resources.ResourceLocation;",
             "import com.yu1745.chemicaladdon.ChemicalAddon;",
             "",
             "/** Species colour table (single source of truth: tools/gen_species.py FLUIDS).",
             " *  Used to weight-blend mixture colours at runtime. ARGB, fully opaque. */",
             "public final class FluidColors {",
             "\tprivate FluidColors() {}",
             "\tprivate static final Map<ResourceLocation, Integer> COLORS = new HashMap<>();",
             "\tstatic {"]
    for sid, _, _, color, *_ in FLUIDS:
        argb = 0xFF000000 | color  # fully opaque alpha over the 0xRRGGBB value
        parts.append(f'\t\tCOLORS.put(new ResourceLocation(ChemicalAddon.MODID, "{sid}"), 0x{argb:08X});')
    parts += ["\t}", "",
              "\t/** @return the species' ARGB colour, or -1 (white) if unknown. */",
              "\tpublic static int of(ResourceLocation id) {",
              "\t\treturn COLORS.getOrDefault(id, 0xFFFFFFFF);",
              "\t}", "}", ""]
    with open(os.path.join(JAVA, "..", "fluid", "FluidColors.java"), "w", encoding="utf-8") as f:
        f.write("\n".join(parts))

def gen_solid_colors_java():
    """Emit SolidColors.java: the per-species RGB colour of SOLIDS (single source
    of truth, same column as the item texture tint) so Java can weight-blend the
    tint of suspended solids in a mixture at runtime."""
    parts = ["package com.yu1745.chemicaladdon.fluid;",
             "",
             "import java.util.HashMap;",
             "import java.util.Map;",
             "import net.minecraft.resources.ResourceLocation;",
             "import com.yu1745.chemicaladdon.ChemicalAddon;",
             "",
             "/** Solid species colour table (single source of truth: tools/gen_species.py SOLIDS).",
             " *  Used to weight-blend suspended-solid tint in a mixture. ARGB, fully opaque. */",
             "public final class SolidColors {",
             "\tprivate SolidColors() {}",
             "\tprivate static final Map<ResourceLocation, Integer> COLORS = new HashMap<>();",
             "\tstatic {"]
    for sid, _, _, color in SOLIDS:
        argb = 0xFF000000 | color
        parts.append(f'\t\tCOLORS.put(new ResourceLocation(ChemicalAddon.MODID, "{sid}"), 0x{argb:08X});')
    parts += ["\t}", "",
              "\t/** @return the solid's ARGB colour, or -1 (white) if unknown. */",
              "\tpublic static int of(ResourceLocation id) {",
              "\t\treturn COLORS.getOrDefault(id, 0xFFFFFFFF);",
              "\t}", "}", ""]
    with open(os.path.join(JAVA, "..", "fluid", "SolidColors.java"), "w", encoding="utf-8") as f:
        f.write("\n".join(parts))

def gen_items_java():
    parts = ["package com.yu1745.chemicaladdon.registry;",
             "",
             "import com.simibubi.create.foundation.data.CreateRegistrate;",
             "import com.tterrag.registrate.util.entry.ItemEntry;",
             "import com.yu1745.chemicaladdon.ChemicalAddon;",
             "import net.minecraft.world.item.Item;",
             "",
             "public class AllItems {",
             "\tpublic static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();",
             ""]
    for sid, _, en_name, _ in SOLIDS:
        parts.append(f"\tpublic static final ItemEntry<Item> {sid.upper()} =\n"
                     f"\t\tREGISTRATE.item(\"{sid}\", Item::new)\n"
                     f"\t\t\t.lang(\"{en_name}\")\n"
                     "\t\t\t.register();")
    parts += ["", "\tpublic static void register() {", "\t}", "}", ""]
    with open(os.path.join(JAVA, "AllItems.java"), "w", encoding="utf-8") as f:
        f.write("\n".join(parts))

if __name__ == "__main__":
    gen_textures()
    gen_atlas()
    gen_bucket_models()
    gen_solution_bucket_models()
    gen_block_textures()
    gen_lang()
    gen_fluids_java()
    gen_fluid_colors_java()
    gen_solid_colors_java()
    gen_items_java()
    print(f"OK: {len(FLUIDS)} fluids, {len(SOLIDS)} solids, {len(SOLUTIONS)} solutions, {len(SLURRIES)} slurries, {len(BLOCKS)} blocks -> textures/atlas/lang/Java generated")
