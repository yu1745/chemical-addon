#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Chemical Addon species/resource generator (M0).
Single source of truth for the fluid/solid species tables (see the FLUIDS /
SOLIDS / GRAINS / SOLUTIONS / SLURRIES / BLOCKS tables below and
plans/08-substance-catalog.md). Generates:
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
    ("copper_sulfate",         "硫酸铜", "Copper Sulfate",  0x2285D6),
    ("copper_carbonate",       "碱式碳酸铜", "Basic Copper Carbonate", 0x2FA896),
    ("potassium_nitrate",      "硝酸钾", "Potassium Nitrate", 0xE8E8E8),
    ("potassium_chloride",     "氯化钾", "Potassium Chloride", 0xE8E8E0),
    ("ammonium_chloride",      "氯化铵", "Ammonium Chloride", 0xE8F0E8),
    ("magnesium_chloride",     "氯化镁", "Magnesium Chloride", 0xE8E8F0),
    ("potassium_alum",         "钾明矾", "Potassium Alum",  0xF0F0F8),
    ("filter_cake",            "滤渣",   "Filter Cake",     0x908878),
    # B3 catalyst: contact-process vanadium catalyst carrier item (catalysts item tag)
    ("vanadium_pentoxide",      "五氧化二钒催化剂", "Vanadium Pentoxide Catalyst", 0xC8963C),
]

# Crystallisable solids that get a "grain" item variant (U15, plans/03 §5): a grain
# is the intermediate denomination across the item↔fluid boundary — 1/16 item =
# 62.5 mB — so seeding a metastable solution and small-batch dosing never hit the
# "either 0 or a whole bucket" cliff. 1 item -> 16 grains via Create crushing.
GRAINS = [
    "rock_salt",
    "potassium_nitrate",
    "potassium_chloride",
    "ammonium_chloride",
    "copper_sulfate",
    "calcium_chloride",
    "magnesium_chloride",
    "potassium_alum",
]

# (id, cn, en, color)
BLOCKS = [
    ("chemical_brick",    "化工砖", "Chemical Brick",    0x8E8478),
    ("decant_port",       "分液口", "Decant Port",       0x8E8478),
    ("decant_hose",       "分液软管", "Decant Hose",       0xB87333),
    ("reactor_controller", "反应釜控制器", "Reactor Controller", 0x6E6E6E),
    ("filter_press",      "过滤机", "Filter Press",      0x7A7A8A),
    ("settling_basin",    "沉淀池控制器", "Settling Basin", 0x5E6E7A),
    ("furnace_controller", "煅烧炉控制器", "Furnace Controller", 0xB05828),
    ("tower_controller",   "吸收塔控制器", "Tower Controller", 0x3868A8),
    ("tower_packing",      "塔填料",       "Tower Packing",    0x8A6A48),
    ("electrolyzer",      "电解槽", "Electrolyzer",     0x5E7A8A),
    ("heat_exchanger",    "换热器", "Heat Exchanger",   0x7A7A8A),
    ("thermometer",       "温度计", "Thermometer",      0x5A5A62),
    ("thermometer_panel",  "温度计面板", "Thermometer Panel", 0x6A6A72),
    ("pressure_gauge",     "压力表", "Pressure Gauge",      0x5A6272),
    ("pressure_gauge_panel", "压力表面板", "Pressure Gauge Panel", 0x6A7282),
    ("conductivity_gauge",     "电导率计", "Conductivity Gauge",      0x5A7262),
    ("conductivity_gauge_panel", "电导率计面板", "Conductivity Gauge Panel", 0x6A8272),
    ("ph_gauge",               "pH 计",   "pH Gauge",           0x5A5A72),
    ("ph_gauge_panel",         "pH 计面板", "pH Gauge Panel",   0x6A6A82),
    ("baume_gauge",            "波美计",  "Baumé Gauge",        0x6E6252),
    ("baume_gauge_panel",      "波美计面板", "Baumé Gauge Panel", 0x7E7262),
    ("turbidity_gauge",        "浊度计",  "Turbidity Gauge",    0x62685A),
    ("turbidity_gauge_panel",  "浊度计面板", "Turbidity Gauge Panel", 0x72786A),
    ("liquid_level_gauge",      "液位计",  "Liquid Level Gauge",      0x5A6E7E),
    ("liquid_level_gauge_panel", "液位计面板", "Liquid Level Gauge Panel", 0x6A7E8E),
    ("crystallizer_controller", "终点结晶器", "Crystallizer Controller", 0x6E7A6E),
    ("stirring_head",      "搅拌头", "Stirring Head",      0x707880),
    ("gas_distributor",    "气体分布器", "Gas Distributor",  0x68747A),
    ("catalyst_tray",      "催化托盘", "Catalyst Tray",    0x6E5A46),
    ("status_port",        "状态口",   "Status Port",      0x76695E),
    ("metering_inlet",     "计量投料口", "Metering Inlet",   0x5E6E8A),
]

# Consumable test papers / qualitative reagents (U17, plans/12 §2.2): one-time
# "what is in there" probes — the chemistry the continuous gauges never do.
# (id, cn, en, TestPaperItem.Kind, indicator colour)
TEST_PAPERS = [
    ("litmus_paper",               "石蕊试纸",     "Litmus Paper",                  "LITMUS", 0x9B4DCA),
    ("phenolphthalein_paper",      "酚酞试纸",     "Phenolphthalein Paper",         "PHENOLPHTHALEIN", 0xE89BB8),
    ("wide_ph_paper",              "广泛pH试纸",   "Wide-Range pH Paper",           "WIDE_PH", 0x6BB86B),
    ("silver_nitrate_paper",       "硝酸银试纸",   "Silver Nitrate Paper",          "SILVER_NITRATE", 0xC8C8D8),
    ("barium_chloride_paper",      "氯化钡试纸",   "Barium Chloride Paper",         "BARIUM_CHLORIDE", 0xD8D8C0),
    ("potassium_thiocyanate_paper", "硫氰酸钾试纸", "Potassium Thiocyanate Paper",  "POTASSIUM_THIOCYANATE", 0xA03030),
    ("cobalt_glass",               "蓝钴玻璃焰色镜", "Cobalt-Glass Flame Scope",    "COBALT_GLASS", 0x3A5AC8),
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
    ("copper_sulfate_solution",   "硫酸铜液", "Copper Sulfate Solution"),
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

def make_grain_texture(rgb):
    """Grain item: a cluster of three small crystals (diamonds) with darker
    facets on a transparent background — visually the '1/16 shard' of the solid
    item above, tinted the same colour (baked; grains have no runtime tint)."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    dark = (int(r * 0.7), int(g * 0.7), int(b * 0.7))
    rows = [[0, 0, 0, 0] * 16 for _ in range(16)]

    def crystal(cx, cy, s):
        for y in range(16):
            for x in range(16):
                d = abs(x - cx) + abs(y - cy)
                if d <= s:
                    lit = (x - cx) + (y - cy) <= 0  # upper-left facet brighter
                    rows[y][x * 4:x * 4 + 4] = [r, g, b, 255] if lit else list(dark) + [255]

    crystal(6, 6, 3)
    crystal(10, 9, 2)
    crystal(4, 10, 2)
    return rows


def make_paper_texture(rgb):
    """Test paper item (U17): a pale paper strip with a coloured indicator pad
    across its middle — the pad is the dipped reagent zone."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    rows = [[0, 0, 0, 0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            # strip: diagonal-ish band from (3,2)..(12,13)
            if 3 <= x <= 12 and 2 <= y <= 13:
                edge = x in (3, 12) or y in (2, 13)
                rows[y][x * 4:x * 4 + 4] = (
                    [214, 210, 198, 255] if edge else [238, 234, 222, 255])
    for y in range(6, 10):
        for x in range(5, 11):
            rows[y][x * 4:x * 4 + 4] = [r, g, b, 255]
    return rows


def make_cobalt_glass_texture():
    """The cobalt-glass flame scope (U17): a deep-blue glass square with a
    lighter bezel — look through it and sodium's yellow disappears."""
    rows = [[0, 0, 0, 0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if 2 <= x <= 13 and 2 <= y <= 13:
                edge = x in (2, 13) or y in (2, 13)
                if edge:
                    rows[y][x * 4:x * 4 + 4] = [42, 52, 96, 255]
                else:
                    rows[y][x * 4:x * 4 + 4] = [58, 90, 200, 200]
    for x in range(6, 10):  # glint
        rows[4][x * 4:x * 4 + 4] = [120, 150, 230, 220]
    return rows


def make_residue_texture(rgb):
    """Mixed-residue item: an irregular mottled lump (multi-species salt cake)
    in neutral grey — the real colour is a runtime blend of the NBT composition
    (see MixedResidueItem/ItemColor), the baked grey only shows without NBT."""
    rows = []
    import random as _rand
    rng = _rand.Random(1745)
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    for y in range(16):
        row = []
        for x in range(16):
            dx, dy = x - 7.5, y - 8.5
            d = (dx * dx + dy * dy) ** 0.5
            if x == 0 or y == 0 or x == 15 or y == 15:
                row += [0, 0, 0, 0]
            elif d <= 5.2:
                shade = 0.75 + rng.random() * 0.4
                row += [min(255, int(r * shade)), min(255, int(g * shade)), min(255, int(b * shade)), 255]
            else:
                row += [0, 0, 0, 0]
        rows.append(row)
    return rows


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
    # grain variants (U15): 1/16 denomination of the crystallisable solids
    solid_colors = {sid: color for sid, _, _, color in SOLIDS}
    for sid in GRAINS:
        write_png(os.path.join(d, f"{sid}_grain.png"), make_grain_texture(solid_colors[sid]))
    # mixed residue: neutral lump, tinted at runtime by its NBT composition
    write_png(os.path.join(d, "mixed_residue.png"), make_residue_texture(0x9A9A94))
    # test papers / reagents (U17): white strip + indicator pad; the cobalt
    # glass scope is a blue glass square instead
    for pid, _, _, _, color in TEST_PAPERS:
        write_png(os.path.join(d, f"{pid}.png"),
                  make_paper_texture(color) if pid != "cobalt_glass" else make_cobalt_glass_texture())
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
    # the mixture meta-fluid's auto-registered bucket item needs a model too
    # (creative-only: the mixture fluid has no block, so it can never be scooped;
    # the .bucket() build in gen_fluids_java suppresses registrate's default
    # item/generated model, which would demand a hand-drawn texture)
    with open(os.path.join(d, "mixture_bucket.json"), "w", encoding="utf-8") as f:
        _json.dump({
            "parent": "forge:item/default",
            "loader": "forge:fluid_container",
            "textures": {
                "base": "minecraft:item/bucket",
                "fluid": "forge:item/mask/bucket_fluid"
            },
            "fluid": "chemicaladdon:mixture"
        }, f, indent=2)
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


def make_dial_texture(rgb, needle=(196, 44, 44), dial=(236, 238, 242)):
    """Circular gauge dial: a light face with a needle and a dark tick ring,
    on a metallic panel. The thermometer uses the red-needle default; the
    pressure gauge passes a steel-blue needle."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            dx = x - 7.5
            dy = y - 7.5
            d2 = dx * dx + dy * dy
            if abs(dx) <= 0.6 and -5.0 <= dy <= 0.5:
                row += list(needle) + [255]        # needle (centre, pointing up)
            elif d2 <= 6.5 * 6.5:
                if d2 >= 4.5 * 4.5:
                    row += [40, 42, 48, 255]       # tick ring (rim)
                else:
                    row += list(dial) + [255]      # dial face
            else:
                row += [int(r * 0.75), int(g * 0.75), int(b * 0.75), 255]  # panel
        rows.append(row)
    return rows


def make_status_port_texture(rgb):
    """B status port: metal shell face with a dark status window and a four-step
    indicator bar (the fixed comparator mapping made visible — 0/4/8/12/15)."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if y in (0, 15) or x in (0, 15):
                row += [int(r * 1.2), int(g * 1.2), int(b * 1.2), 255]  # edge highlight
            elif 5 <= y <= 7 and 3 <= x <= 12:
                row += [34, 38, 44, 255]  # dark status window
            elif y == 6 and 4 <= x <= 11:
                row += [120, 200, 160, 255]  # window text strip
            elif 10 <= y <= 12 and 4 <= x <= 11:
                lit = (x - 4) // 2  # 4 lamp segments, left-lit
                row += ([110, 190, 120, 255] if lit <= 1 else [50, 56, 50, 255])
            else:
                row += [r, g, b, 255]
        rows.append(row)
    return rows


def make_gas_distributor_textures(rgb):
    """B2 directional gas distributor face set.

    The block's FACING points into the vessel.  Its front face is therefore a
    porous diffuser plate, while the opposite back face is the external pipe
    inlet.  The four remaining faces are plain ribbed casing.  Keeping these
    as separate baked sprites makes the block's process direction legible even
    before a player checks its blockstate or goggles.
    """
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    side = make_panel_texture(rgb)
    front = []
    back = []
    for y in range(16):
        front_row = []
        back_row = []
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d2 = dx * dx + dy * dy
            # Internal face: bright flange around a dark porous diffuser plate.
            if d2 <= 49:
                if d2 <= 36:
                    if (x in (5, 7, 9, 11) and y in (5, 7, 9, 11)):
                        front_row += [18, 22, 25, 255]  # diffuser holes
                    elif d2 <= 30:
                        front_row += [68, 92, 102, 255]  # perforated plate
                    else:
                        front_row += [42, 55, 62, 255]    # plate recess
                else:
                    front_row += [156, 166, 174, 255]      # nozzle flange
            else:
                front_row += [r, g, b, 255]

            # External face: a pipe coupling with a clearly open central bore.
            if d2 <= 42:
                if d2 <= 16:
                    back_row += [20, 23, 27, 255]         # inlet bore
                elif d2 <= 28:
                    back_row += [174, 181, 188, 255]       # coupling ring
                else:
                    back_row += [82, 90, 98, 255]          # outer seam
            else:
                back_row += [r, g, b, 255]
        front.append(front_row)
        back.append(back_row)
    return front, back, side


def make_catalyst_tray_textures(rgb):
    """B3 catalyst tray face set (see gen_block_textures for orientation)."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    front = []
    back = []
    for y in range(16):
        front_row = []
        back_row = []
        for x in range(16):
            # Internal face: a shallow grid tray — lattice bars every 4 px with
            # amber catalyst grains sitting in the cells (deterministic pattern).
            if (y % 4) == 3 or (x % 4) == 3:
                front_row += [int(r * 0.6), int(g * 0.6), int(b * 0.6), 255]  # tray bars
            elif (x * 7 + y * 13) % 5 < 2:
                front_row += [200, 150, 60, 255]   # catalyst grains (vanadium amber)
            else:
                front_row += [34, 30, 26, 255]     # tray recess
            # External face: flanged access plate with four bolt dots.
            if x in (2, 13) and y in (2, 13):
                back_row += [170, 178, 186, 255]   # bolts
            elif x in (1, 14) or y in (1, 14):
                back_row += [140, 128, 108, 255]   # flange
            elif x in (4, 5, 10, 11) and y in (6, 7, 8, 9):
                back_row += [48, 44, 40, 255]      # slot opening
            else:
                back_row += [r, g, b, 255]
        front.append(front_row)
        back.append(back_row)
    return front, back


def make_metering_inlet_textures(rgb):
    """B4 inlet face set: internal nozzle, external valved coupling."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    front = []
    back = []
    for y in range(16):
        front_row = []
        back_row = []
        for x in range(16):
            if (4 <= x <= 11) and (5 <= y <= 10):
                front_row += [22, 26, 32, 255] if (6 <= x <= 9 and 7 <= y <= 8) else [150, 158, 168, 255]
            else:
                front_row += [r, g, b, 255]
            dx, dy = x - 7.5, y - 7.5
            d2 = dx * dx + dy * dy
            if d2 <= 20:
                back_row += [20, 23, 27, 255] if d2 <= 9 else [176, 183, 190, 255]
            elif y in (3, 4) and x in (3, 4, 11, 12):
                back_row += [235, 200, 60, 255]
            elif y in (12, 13) and 4 <= x <= 11 and (x - 4) % 2 == 0:
                back_row += [240, 240, 240, 255]
            else:
                back_row += [r, g, b, 255]
        front.append(front_row)
        back.append(back_row)
    return front, back


def make_coil_texture(rgb):
    """Decant hose block placeholder: concentric coil rings of hose on a dark
    mounting plate (the visible block is mostly the BE renderer's 3D coil;
    this is the fallback blockitem / world face)."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            dx = x - 7.5
            dy = y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 1.6:
                row += [30, 30, 34, 255]          # hose bore
            elif d <= 6.4 and int(d * 2) % 2 == 0:
                row += [int(r * 0.8), int(g * 0.8), int(b * 0.8), 255]  # coil shadow side
            elif d <= 6.4:
                row += [r, g, b, 255]              # coil lit side
            else:
                row += [64, 60, 58, 255]           # mounting plate
        rows.append(row)
    return rows


def make_stirring_head_textures(rgb):
    """B1 stirring head (搅拌头): a roof shell block with a shaft coupling on
    the UP face (dark bore + flange, so a vertical shaft visually docks), a
    plain underside plate with the shaft stub on the DOWN face (the dynamic
    shaft + enlarged impeller below it are BE-rendered partials — the static
    face no longer pretends to be the paddle), and a flanged metal casing on
    the sides (bolt band + panel)."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    top = []
    bottom = []
    side = []
    for y in range(16):
        top_row = []
        bottom_row = []
        side_row = []
        for x in range(16):
            dx = x - 7.5
            dy = y - 7.5
            d2 = dx * dx + dy * dy
            # UP: shaft coupling
            if d2 <= 4.0:
                top_row += [28, 30, 34, 255]        # shaft bore
            elif d2 <= 14.0:
                top_row += [122, 128, 138, 255]     # coupling collar
            elif d2 <= 22.0:
                top_row += [82, 88, 96, 255]        # collar seam
            else:
                top_row += [r, g, b, 255]           # casing plate
            # DOWN: underside plate with the shaft stub collar (the spinning
            # shaft itself hangs out of this bore as a rendered partial)
            if d2 <= 4.0:
                bottom_row += [40, 42, 48, 255]     # shaft stub bore (4px, the shaft cross-section)
            elif d2 <= 13.0:
                bottom_row += [118, 124, 134, 255]  # stub collar
            elif d2 <= 22.0:
                bottom_row += [84, 90, 98, 255]     # collar seam ring
            else:
                bottom_row += [int(r * 0.72), int(g * 0.72), int(b * 0.72), 255]  # ceiling underside
            # SIDES: flanged casing with a bolt band
            if y in (0, 15):
                side_row += [int(r * 1.15), int(g * 1.15), int(b * 1.15), 255]  # top/bottom edge
            elif y == 7 or y == 8:
                side_row += [int(r * 0.55), int(g * 0.55), int(b * 0.55), 255]  # bolt band
            elif (y == 5 or y == 10) and x % 4 == 2:
                side_row += [168, 174, 184, 255]    # bolt heads
            else:
                side_row += [r, g, b, 255]
        top.append(top_row)
        bottom.append(bottom_row)
        side.append(side_row)
    return top, bottom, side


def make_stir_shaft_texture():
    """B1 dynamic stirring shaft (BE-rendered partial, 4px column): alternating
    light/dark uv bands (the classic Create shaft flats) so the kinetic rotation
    reads, plus a small darker cap region for the segment's lower end face."""
    rows = []
    base = 0x8A9099  # polished steel
    for y in range(16):
        # subtle vertical machining streaks (rows of the uv band)
        streak = 1.0 + 0.05 * (((y % 4) - 1.5) / 1.5)
        row = []
        for x in range(16):
            if x < 4:
                f = 1.08 if x % 4 < 3 else 0.98      # light flat with a soft edge
            elif x < 8:
                f = 0.72                              # dark flat
            else:
                f = 0.88                              # spare / mid
            rgb = tuple(max(0, min(255, int(c * f * streak))) for c in
                        (base >> 16 & 255, base >> 8 & 255, base & 255))
            row += [rgb[0], rgb[1], rgb[2], 255]
        rows.append(row)
    # cap region (uv x 8..11, y 0..3): plate with a darker centre bore
    for y in range(4):
        for x in range(8, 12):
            inner = 9 <= x <= 10 and 1 <= y <= 2
            c = 0x4E545C if inner else 0x7E848D
            rows[y][x * 4:x * 4 + 4] = [c >> 16 & 255, c >> 8 & 255, c & 255, 255]
    return rows


def make_stir_impeller_texture():
    """B1 enlarged impeller (BE-rendered partial): hub column (uv x 0..1,
    y 0..9), hub caps (uv x 4..5, y 0..1), blade plate with a bright leading
    edge (uv y 10..15), blade end faces (uv x 2, y 6..11) — uniform along the
    blade length so the spin does not strobe."""
    rows = []
    base = 0x767E88  # brushed steel
    for y in range(16):
        row = []
        for x in range(16):
            if y < 10 and x < 2:
                f = 1.0 + 0.04 * ((y % 3) - 1)        # hub: vertical brushing
            elif y < 2 and 4 <= x < 6:
                f = 0.62                              # hub cap bore
            elif x == 2 and 6 <= y < 12:
                f = 0.66                              # blade end faces
            elif y >= 10:
                edge = y - 10                         # blade plate strip
                f = 1.12 if edge == 0 else 0.92 if edge == 5 else 0.78 if edge == 1 else 1.0
            else:
                f = 0.85                              # spare fill
            rgb = tuple(max(0, min(255, int(c * f))) for c in
                        (base >> 16 & 255, base >> 8 & 255, base & 255))
            row += [rgb[0], rgb[1], rgb[2], 255]
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
    write_png(os.path.join(d, "furnace_controller.png"), make_panel_texture(0xB05828))
    write_png(os.path.join(d, "tower_controller.png"), make_panel_texture(0x3868A8))
    write_png(os.path.join(d, "tower_packing.png"), make_brick_texture(0x8A6A48))
    write_png(os.path.join(d, "electrolyzer.png"), make_panel_texture(0x5E7A8A))
    write_png(os.path.join(d, "heat_exchanger.png"), make_panel_texture(0x7A7A8A))
    write_png(os.path.join(d, "thermometer.png"), make_dial_texture(0x5A5A62))
    write_png(os.path.join(d, "thermometer_panel.png"), make_dial_texture(0x6A6A72))
    write_png(os.path.join(d, "pressure_gauge.png"), make_dial_texture(0x5A6272, needle=(72, 108, 188), dial=(226, 232, 244)))
    write_png(os.path.join(d, "pressure_gauge_panel.png"), make_dial_texture(0x6A7282, needle=(72, 108, 188), dial=(226, 232, 244)))
    write_png(os.path.join(d, "conductivity_gauge.png"), make_dial_texture(0x5A7262, needle=(62, 158, 110), dial=(228, 240, 232)))
    write_png(os.path.join(d, "conductivity_gauge_panel.png"), make_dial_texture(0x6A8272, needle=(62, 158, 110), dial=(228, 240, 232)))
    # U17 gauge trio: pH (magenta needle, center-zero scale), Baumé (amber),
    # turbidity (olive, 4 bins) + the M08 crystalliser's panel block
    write_png(os.path.join(d, "ph_gauge.png"), make_dial_texture(0x5A5A72, needle=(170, 60, 120), dial=(240, 234, 240)))
    write_png(os.path.join(d, "ph_gauge_panel.png"), make_dial_texture(0x6A6A82, needle=(170, 60, 120), dial=(240, 234, 240)))
    write_png(os.path.join(d, "baume_gauge.png"), make_dial_texture(0x6E6252, needle=(196, 124, 44), dial=(242, 234, 222)))
    write_png(os.path.join(d, "baume_gauge_panel.png"), make_dial_texture(0x7E7262, needle=(196, 124, 44), dial=(242, 234, 222)))
    write_png(os.path.join(d, "turbidity_gauge.png"), make_dial_texture(0x62685A, needle=(150, 133, 78), dial=(236, 238, 228)))
    write_png(os.path.join(d, "turbidity_gauge_panel.png"), make_dial_texture(0x72786A, needle=(150, 133, 78), dial=(236, 238, 228)))
    # S11 liquid-level gauge (cyan needle): liquid-only fill percent
    write_png(os.path.join(d, "liquid_level_gauge.png"), make_dial_texture(0x5A6E7E, needle=(60, 160, 190), dial=(224, 238, 244)))
    write_png(os.path.join(d, "liquid_level_gauge_panel.png"), make_dial_texture(0x6A7E8E, needle=(60, 160, 190), dial=(224, 238, 244)))
    write_png(os.path.join(d, "crystallizer_controller.png"), make_panel_texture(0x6E7A6E))
    # decant_hose was missing from here since D18.5 — runData's blockstate
    # provider for it failed on the absent texture (U1 fix)
    write_png(os.path.join(d, "decant_hose.png"), make_coil_texture(0xB87333))
    # B1 stirring head: coupling / underside stub / casing face set
    head_top, head_bottom, head_side = make_stirring_head_textures(0x707880)
    write_png(os.path.join(d, "stirring_head_top.png"), head_top)
    write_png(os.path.join(d, "stirring_head_bottom.png"), head_bottom)
    write_png(os.path.join(d, "stirring_head_side.png"), head_side)
    # B1 stirring head dynamic partials: rotating shaft flats + enlarged impeller
    write_png(os.path.join(d, "stirring_shaft.png"), make_stir_shaft_texture())
    write_png(os.path.join(d, "stirring_impeller.png"), make_stir_impeller_texture())
    # B2 gas distributor: FACING points inward.  The inward face is a porous
    # diffuser, the opposite face is the sole external pipe inlet, and the
    # other four faces are ordinary metal casing.
    gas_front, gas_back, gas_side = make_gas_distributor_textures(0x68747A)
    write_png(os.path.join(d, "gas_distributor_front.png"), gas_front)
    write_png(os.path.join(d, "gas_distributor_back.png"), gas_back)
    write_png(os.path.join(d, "gas_distributor_side.png"), gas_side)
    # B3 catalyst tray: FACING points into the vessel. The inward face is a
    # perforated tray bed speckled with catalyst grains, the outward face is a
    # flanged access plate (the sole item endpoint), the other four are casing.
    tray_front, tray_back = make_catalyst_tray_textures(0x6E5A46)
    write_png(os.path.join(d, "catalyst_tray_front.png"), tray_front)
    write_png(os.path.join(d, "catalyst_tray_back.png"), tray_back)
    write_png(os.path.join(d, "catalyst_tray_side.png"), make_panel_texture(0x6E5A46))
    # B status port: shell casing with status window + step indicator
    write_png(os.path.join(d, "status_port.png"), make_status_port_texture(0x76695E))
    inlet_front, inlet_back = make_metering_inlet_textures(0x5E6E8A)
    write_png(os.path.join(d, "metering_inlet_front.png"), inlet_front)
    write_png(os.path.join(d, "metering_inlet_back.png"), inlet_back)
    write_png(os.path.join(d, "metering_inlet_side.png"), make_panel_texture(0x5E6E8A))


# Extra lang keys added by hand (GUIs, goggles, diagnostics, assemble messages).
# These survive regeneration; edit them here, never in the generated json.
EXTRA_LANG_ZH = {
    "itemGroup.chemicaladdon": "化学附属",
    "item.chemicaladdon.fluid_vial": "样品瓶",
    "item.chemicaladdon.temperature_debug": "温度调试棒",
    "item.chemicaladdon.mixed_residue": "混合盐渣",
    "goggles.chemicaladdon.temperature": "温度：%s°C",
    "goggles.chemicaladdon.heat.none": "无热级",
    "goggles.chemicaladdon.heat.heated": "加热",
    "goggles.chemicaladdon.heat.superheated": "超级加热",
    "goggles.chemicaladdon.contents": "釜内：",
    "goggles.chemicaladdon.solution": "溶液",
    "goggles.chemicaladdon.suspended": "混悬",
    "goggles.chemicaladdon.sediment": "沉底",
    "goggles.chemicaladdon.bucket_empty": "空",
    "goggles.chemicaladdon.items": "物品：",
    "goggles.chemicaladdon.progress": "进度：%s%%（%s）",
    "goggles.chemicaladdon.status": "状态：",
    "goggles.chemicaladdon.thermometer_threshold": "报警阈值：%s°C",
    "goggles.chemicaladdon.thermometer_alarm": "报警：超温",
    "goggles.chemicaladdon.thermometer_no_vessel": "未连接反应釜",
    "goggles.chemicaladdon.pressure": "压力：%s kPa",
    "goggles.chemicaladdon.pressure_ambient": "开口容器 · 常压",
    "goggles.chemicaladdon.pressure_gauge_threshold": "报警阈值：%s kPa",
    "goggles.chemicaladdon.pressure_gauge_alarm": "报警：超压",
    "goggles.chemicaladdon.pressure_gauge_no_vessel": "未连接反应釜",
    "goggles.chemicaladdon.conductivity": "电导率：%s mS",
    "goggles.chemicaladdon.conductivity_gauge_threshold": "设定点：%s mS",
    "goggles.chemicaladdon.conductivity_gauge_clean": "达标：电导率已降至设定点",
    "goggles.chemicaladdon.conductivity_gauge_no_vessel": "未连接反应釜",
    # U17 instrument trio + M08 crystalliser + test papers
    "goggles.chemicaladdon.ph": "pH：%s",
    "goggles.chemicaladdon.ph_gauge_threshold": "阈值：pH %s（%s报警）",
    "goggles.chemicaladdon.ph_gauge_below": "跌破",
    "goggles.chemicaladdon.ph_gauge_above": "升破",
    "goggles.chemicaladdon.ph_gauge_no_vessel": "未连接反应釜",
    "goggles.chemicaladdon.ph_gauge_endpoint": "报警：终点到达",
    "goggles.chemicaladdon.baume": "波美度：%s°Bé",
    "goggles.chemicaladdon.baume_gauge_threshold": "设定点：%s°Bé",
    "goggles.chemicaladdon.baume_gauge_no_vessel": "未连接反应釜",
    "goggles.chemicaladdon.baume_gauge_endpoint": "报警：已达设定浓度",
    "goggles.chemicaladdon.turbidity": "浊度：%s",
    "goggles.chemicaladdon.turbidity_bin_0": "清",
    "goggles.chemicaladdon.turbidity_bin_1": "微浑",
    "goggles.chemicaladdon.turbidity_bin_2": "浑",
    "goggles.chemicaladdon.turbidity_bin_3": "浆",
    "goggles.chemicaladdon.turbidity_gauge_threshold": "报警阈值：%s",
    "goggles.chemicaladdon.turbidity_gauge_no_vessel": "未连接反应釜",
    "goggles.chemicaladdon.turbidity_gauge_alarm": "报警：初浑",
    # S11 liquid-level gauge
    "goggles.chemicaladdon.liquid_level": "液位：%s%%",
    "goggles.chemicaladdon.liquid_level_gauge_threshold": "报警阈值：%s%%",
    "goggles.chemicaladdon.liquid_level_gauge_no_vessel": "未连接反应釜",
    "goggles.chemicaladdon.liquid_level_gauge_alarm": "报警：高液位",
    "goggles.chemicaladdon.status_port": "状态：%s",
    "goggles.chemicaladdon.status_port_progress": "进度：%s%%",
    "message.chemicaladdon.status_port": "状态口：%s",
    "status_port.chemicaladdon.unbound": "未连接反应釜",
    "goggles.chemicaladdon.metering_inlet": "计量投料口",
    "goggles.chemicaladdon.metering_inlet.progress": "本批：%s/%s mB（余 %s mB）",
    "metering_inlet.chemicaladdon.dose": "投料量",
    "metering_inlet.chemicaladdon.status.unbound": "未绑定反应釜",
    "metering_inlet.chemicaladdon.status.misplaced": "位置或朝向错误（需侧壁、朝内）",
    "metering_inlet.chemicaladdon.status.non_liquid": "仅接受液体流体（气体走分布器）",
    "metering_inlet.chemicaladdon.status.done": "本批已达投料量",
    "metering_inlet.chemicaladdon.status.no_capacity": "反应釜无容量",
    "metering_inlet.chemicaladdon.status.metering": "计量投料中",
    "metering_inlet.chemicaladdon.status.ready": "待投料（空手右键重置批次）",
    "goggles.chemicaladdon.crystallizer_condensate": "馏出水量：%s mB",
    "goggles.chemicaladdon.crystallizer_state": "状态：",
    "goggles.chemicaladdon.crystallizer_endpoint": "已到终点（停热）",
    "goggles.chemicaladdon.crystallizer_concentrating": "蒸发浓缩中",
    "paper.chemicaladdon.litmus_red": "石蕊试纸：变红（酸性）",
    "paper.chemicaladdon.litmus_blue": "石蕊试纸：变蓝（碱性）",
    "paper.chemicaladdon.litmus_purple": "石蕊试纸：紫色（中性）",
    "paper.chemicaladdon.phenolphthalein_pink": "酚酞试纸：粉红（pH ≥ 8，碱性）",
    "paper.chemicaladdon.phenolphthalein_clear": "酚酞试纸：无色（非碱性）",
    "paper.chemicaladdon.wide_ph": "广泛pH试纸：pH ≈ %s",
    "paper.chemicaladdon.agno3_positive": "硝酸银试纸：白色浑浊——检出氯离子",
    "paper.chemicaladdon.agno3_negative": "硝酸银试纸：无变化——未检出氯离子",
    "paper.chemicaladdon.bacl2_positive": "氯化钡试纸：白色沉淀——检出硫酸根",
    "paper.chemicaladdon.bacl2_negative": "氯化钡试纸：无变化——未检出硫酸根",
    "paper.chemicaladdon.kscn_positive": "KSCN 试纸：血红色——检出铁离子",
    "paper.chemicaladdon.kscn_negative": "KSCN 试纸：无色——未检出铁离子",
    "paper.chemicaladdon.flame_potassium": "透过蓝钴玻璃：紫色火焰——含钾",
    "paper.chemicaladdon.flame_sodium": "焰色反应：黄色火焰——含钠",
    "paper.chemicaladdon.flame_calcium": "焰色反应：砖红色火焰——含钙",
    "paper.chemicaladdon.flame_none": "焰色反应：无特征焰色",
    "paper.chemicaladdon.hint": "%s：对反应釜控制器或壁砖右键蘸取",
    "goggles.chemicaladdon.saturation": "饱和态：",
    "thermometer.chemicaladdon.threshold": "报警阈值",
    "pressure_gauge.chemicaladdon.threshold": "报警阈值",
    "conductivity_gauge.chemicaladdon.threshold": "设定点",
    "ph_gauge.chemicaladdon.threshold": "报警阈值",
    "baume_gauge.chemicaladdon.threshold": "设定点",
    "turbidity_gauge.chemicaladdon.threshold": "报警阈值",
    "liquid_level_gauge.chemicaladdon.threshold": "报警阈值",
    "crystallizer.chemicaladdon.setpoint": "终点设定（°Bé）",
    "status.chemicaladdon.underheated": "欠烧（温度不足）",
    "status.chemicaladdon.calcining": "煅烧中",
    "status.chemicaladdon.overheated": "过热警告",
    "status.chemicaladdon.tower_not_assembled": "未成型",
    "status.chemicaladdon.tower_no_stages": "空塔（无有效段）",
    "status.chemicaladdon.tower_idle": "待料（需气液同在）",
    "status.chemicaladdon.tower_absorbing": "吸收中",
    "status.chemicaladdon.tower_flooded": "液泛",
    "goggles.chemicaladdon.tower_stages": "有效段数：%s",
    "goggles.chemicaladdon.tower_liquid": "液体",
    "goggles.chemicaladdon.tower_gas": "气体",
    "goggles.chemicaladdon.energy": "储能：%s / %s FE",
    "status.chemicaladdon.cell_idle": "待料",
    "status.chemicaladdon.cell_no_recipe": "无匹配电解配方",
    "status.chemicaladdon.cell_no_power": "断电",
    "status.chemicaladdon.cell_running": "电解中",
    "status.chemicaladdon.cell_output_full": "输出已满",
    "goggles.chemicaladdon.hx_hot": "热侧：%s°C / %s mB",
    "goggles.chemicaladdon.hx_cold": "冷侧：%s°C / %s mB",
    "goggles.chemicaladdon.hx_recovered": "累计回收：%s J",
    "goggles.chemicaladdon.hx_delta": "ΔT：%s°C",
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
    "goggles.chemicaladdon.gas_distributor": "气体分布器",
    "goggles.chemicaladdon.gas_distributor.rate": "窗口流量：%s/%s mB",
    "gas_distributor.chemicaladdon.status.unbound": "未绑定反应釜",
    "gas_distributor.chemicaladdon.status.wrong_position_or_facing": "位置或朝向错误",
    "gas_distributor.chemicaladdon.status.not_submerged": "出口未浸没（至少需要 0.25 格）",
    "gas_distributor.chemicaladdon.status.non_gas": "仅接受气体流体",
    "gas_distributor.chemicaladdon.status.no_capacity": "反应釜无容量",
    "gas_distributor.chemicaladdon.status.rate_limited": "达到 250 mB/10 tick 限流",
    "gas_distributor.chemicaladdon.status.accepting": "可接受气体",
    "goggles.chemicaladdon.catalyst_tray": "催化托盘",
    "goggles.chemicaladdon.catalyst_tray.charge": "催化剂：%s ×%s（剩余 %s 批）",
    "goggles.chemicaladdon.catalyst_tray.empty": "催化剂槽空",
    "catalyst_tray.chemicaladdon.status.unbound": "未绑定反应釜",
    "catalyst_tray.chemicaladdon.status.wrong_position_or_facing": "位置或朝向错误（需侧壁、朝内）",
    "catalyst_tray.chemicaladdon.status.empty": "未装催化剂",
    "catalyst_tray.chemicaladdon.status.active": "催化床工作中",
}

EXTRA_LANG_EN = {
    "itemGroup.chemicaladdon": "Chemical Addon",
    # NOTE: no "item.chemicaladdon.mixed_residue" here — the registrate .lang()
    # call on the item already emits the en_us key; a duplicate crashes datagen.
    # The zh name lives in EXTRA_LANG_ZH (zh_cn.json is fully py-generated).
    "goggles.chemicaladdon.temperature": "Temperature: %s°C",
    "goggles.chemicaladdon.heat.none": "No heat",
    "goggles.chemicaladdon.heat.heated": "Heated",
    "goggles.chemicaladdon.heat.superheated": "Superheated",
    "goggles.chemicaladdon.contents": "Contents:",
    "goggles.chemicaladdon.solution": "Solution",
    "goggles.chemicaladdon.suspended": "suspended",
    "goggles.chemicaladdon.sediment": "sediment",
    "goggles.chemicaladdon.bucket_empty": "Empty",
    "goggles.chemicaladdon.items": "Items:",
    "goggles.chemicaladdon.progress": "Progress: %s%% (%s)",
    "goggles.chemicaladdon.status": "Status:",
    "goggles.chemicaladdon.thermometer_threshold": "Threshold: %s°C",
    "goggles.chemicaladdon.thermometer_alarm": "ALARM",
    "goggles.chemicaladdon.thermometer_no_vessel": "Not attached to a reactor",
    "goggles.chemicaladdon.pressure": "Pressure: %s kPa",
    "goggles.chemicaladdon.pressure_ambient": "Open vessel — ambient pressure",
    "goggles.chemicaladdon.pressure_gauge_threshold": "Threshold: %s kPa",
    "goggles.chemicaladdon.pressure_gauge_alarm": "ALARM",
    "goggles.chemicaladdon.pressure_gauge_no_vessel": "Not attached to a reactor",
    "goggles.chemicaladdon.conductivity": "Conductivity: %s mS",
    "goggles.chemicaladdon.conductivity_gauge_threshold": "Setpoint: %s mS",
    "goggles.chemicaladdon.conductivity_gauge_clean": "CLEAN: conductivity at/below setpoint",
    "goggles.chemicaladdon.conductivity_gauge_no_vessel": "Not attached to a reactor",
    # U17 instrument trio + M08 crystalliser + test papers
    "goggles.chemicaladdon.ph": "pH: %s",
    "goggles.chemicaladdon.ph_gauge_threshold": "Threshold: pH %s (alarm when reading %s)",
    "goggles.chemicaladdon.ph_gauge_below": "falls below",
    "goggles.chemicaladdon.ph_gauge_above": "rises above",
    "goggles.chemicaladdon.ph_gauge_no_vessel": "Not attached to a reactor",
    "goggles.chemicaladdon.ph_gauge_endpoint": "ALARM: endpoint reached",
    "goggles.chemicaladdon.baume": "Density: %s°Bé",
    "goggles.chemicaladdon.baume_gauge_threshold": "Setpoint: %s°Bé",
    "goggles.chemicaladdon.baume_gauge_no_vessel": "Not attached to a reactor",
    "goggles.chemicaladdon.baume_gauge_endpoint": "ALARM: setpoint density reached",
    "goggles.chemicaladdon.turbidity": "Turbidity: %s",
    "goggles.chemicaladdon.turbidity_bin_0": "clear",
    "goggles.chemicaladdon.turbidity_bin_1": "slight haze",
    "goggles.chemicaladdon.turbidity_bin_2": "turbid",
    "goggles.chemicaladdon.turbidity_bin_3": "slurry",
    "goggles.chemicaladdon.turbidity_gauge_threshold": "Threshold: %s",
    "goggles.chemicaladdon.turbidity_gauge_no_vessel": "Not attached to a reactor",
    "goggles.chemicaladdon.turbidity_gauge_alarm": "ALARM: first clouding",
    # S11 liquid-level gauge
    "goggles.chemicaladdon.liquid_level": "Level: %s%%",
    "goggles.chemicaladdon.liquid_level_gauge_threshold": "Threshold: %s%%",
    "goggles.chemicaladdon.liquid_level_gauge_no_vessel": "Not attached to a reactor",
    "goggles.chemicaladdon.liquid_level_gauge_alarm": "ALARM: high level",
    "goggles.chemicaladdon.status_port": "Status: %s",
    "goggles.chemicaladdon.status_port_progress": "Progress: %s%%",
    "message.chemicaladdon.status_port": "Status port: %s",
    "status_port.chemicaladdon.unbound": "Not attached to a reactor",
    "goggles.chemicaladdon.metering_inlet": "Metering Inlet",
    "goggles.chemicaladdon.metering_inlet.progress": "Batch: %s/%s mB (%s mB left)",
    "metering_inlet.chemicaladdon.dose": "Batch Dose",
    "metering_inlet.chemicaladdon.status.unbound": "Not bound to a vessel",
    "metering_inlet.chemicaladdon.status.misplaced": "Wrong position or facing (side wall, inward)",
    "metering_inlet.chemicaladdon.status.non_liquid": "Liquids only (gases go to the distributor)",
    "metering_inlet.chemicaladdon.status.done": "Batch dose reached",
    "metering_inlet.chemicaladdon.status.no_capacity": "Vessel has no capacity",
    "metering_inlet.chemicaladdon.status.metering": "Metering batch",
    "metering_inlet.chemicaladdon.status.ready": "Ready (empty-hand click resets)",
    "goggles.chemicaladdon.crystallizer_condensate": "Distillate: %s mB",
    "goggles.chemicaladdon.crystallizer_state": "Status:",
    "goggles.chemicaladdon.crystallizer_endpoint": "endpoint reached (heat cut)",
    "goggles.chemicaladdon.crystallizer_concentrating": "evaporating",
    "paper.chemicaladdon.litmus_red": "Litmus paper: red (acidic)",
    "paper.chemicaladdon.litmus_blue": "Litmus paper: blue (alkaline)",
    "paper.chemicaladdon.litmus_purple": "Litmus paper: purple (neutral)",
    "paper.chemicaladdon.phenolphthalein_pink": "Phenolphthalein paper: pink (pH ≥ 8)",
    "paper.chemicaladdon.phenolphthalein_clear": "Phenolphthalein paper: colourless",
    "paper.chemicaladdon.wide_ph": "Wide-range pH paper: pH ≈ %s",
    "paper.chemicaladdon.agno3_positive": "AgNO₃ paper: white turbidity — chloride detected",
    "paper.chemicaladdon.agno3_negative": "AgNO₃ paper: no change — no chloride",
    "paper.chemicaladdon.bacl2_positive": "BaCl₂ paper: white precipitate — sulfate detected",
    "paper.chemicaladdon.bacl2_negative": "BaCl₂ paper: no change — no sulfate",
    "paper.chemicaladdon.kscn_positive": "KSCN paper: blood red — ferric iron detected",
    "paper.chemicaladdon.kscn_negative": "KSCN paper: colourless — no ferric iron",
    "paper.chemicaladdon.flame_potassium": "Through cobalt glass: lilac flame — potassium",
    "paper.chemicaladdon.flame_sodium": "Flame test: yellow flame — sodium",
    "paper.chemicaladdon.flame_calcium": "Flame test: brick-red flame — calcium",
    "paper.chemicaladdon.flame_none": "Flame test: no characteristic flame",
    "paper.chemicaladdon.hint": "%s: right-click a reactor controller or wall brick to dip",
    "goggles.chemicaladdon.saturation": "Saturation:",
    "thermometer.chemicaladdon.threshold": "Alarm Threshold",
    "pressure_gauge.chemicaladdon.threshold": "Alarm Threshold",
    "conductivity_gauge.chemicaladdon.threshold": "Setpoint",
    "ph_gauge.chemicaladdon.threshold": "Alarm Threshold",
    "baume_gauge.chemicaladdon.threshold": "Setpoint",
    "turbidity_gauge.chemicaladdon.threshold": "Alarm Threshold",
    "liquid_level_gauge.chemicaladdon.threshold": "Alarm Threshold",
    "crystallizer.chemicaladdon.setpoint": "Endpoint Setpoint (°Bé)",
    "status.chemicaladdon.underheated": "Underheated (raw charge)",
    "status.chemicaladdon.calcining": "Calcining",
    "status.chemicaladdon.overheated": "Overheated",
    "status.chemicaladdon.tower_not_assembled": "Not assembled",
    "status.chemicaladdon.tower_no_stages": "Empty shell (no stages)",
    "status.chemicaladdon.tower_idle": "Idle (needs gas and liquid)",
    "status.chemicaladdon.tower_absorbing": "Absorbing",
    "status.chemicaladdon.tower_flooded": "Flooded",
    "goggles.chemicaladdon.tower_stages": "Stages: %s",
    "goggles.chemicaladdon.tower_liquid": "liquid",
    "goggles.chemicaladdon.tower_gas": "gas",
    "goggles.chemicaladdon.energy": "Energy: %s / %s FE",
    "status.chemicaladdon.cell_idle": "Idle",
    "status.chemicaladdon.cell_no_recipe": "No electrolysis recipe",
    "status.chemicaladdon.cell_no_power": "No power",
    "status.chemicaladdon.cell_running": "Running",
    "status.chemicaladdon.cell_output_full": "Output full",
    "goggles.chemicaladdon.hx_hot": "Hot side: %s°C / %s mB",
    "goggles.chemicaladdon.hx_cold": "Cold side: %s°C / %s mB",
    "goggles.chemicaladdon.hx_recovered": "Recovered: %s J",
    "goggles.chemicaladdon.hx_delta": "ΔT: %s°C",
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
    "goggles.chemicaladdon.gas_distributor": "Gas Distributor",
    "goggles.chemicaladdon.gas_distributor.rate": "Window flow: %s/%s mB",
    "gas_distributor.chemicaladdon.status.unbound": "Not bound to a vessel",
    "gas_distributor.chemicaladdon.status.wrong_position_or_facing": "Wrong position or facing",
    "gas_distributor.chemicaladdon.status.not_submerged": "Outlet not submerged (0.25 block required)",
    "gas_distributor.chemicaladdon.status.non_gas": "Gas fluids only",
    "gas_distributor.chemicaladdon.status.no_capacity": "Vessel has no capacity",
    "gas_distributor.chemicaladdon.status.rate_limited": "Rate limit reached: 250 mB/10 ticks",
    "gas_distributor.chemicaladdon.status.accepting": "Accepting gas",
    "goggles.chemicaladdon.catalyst_tray": "Catalyst Tray",
    "goggles.chemicaladdon.catalyst_tray.charge": "Catalyst: %s x%s (%s batches left)",
    "goggles.chemicaladdon.catalyst_tray.empty": "Catalyst slot empty",
    "catalyst_tray.chemicaladdon.status.unbound": "Not bound to a vessel",
    "catalyst_tray.chemicaladdon.status.wrong_position_or_facing": "Wrong position or facing (side wall, inward)",
    "catalyst_tray.chemicaladdon.status.empty": "No catalyst loaded",
    "catalyst_tray.chemicaladdon.status.active": "Catalyst bed active",
}


def gen_lang():
    # zh_cn stays fully py-generated (single source of truth, survives regeneration).
    zh = dict(EXTRA_LANG_ZH)
    for sid, cn, _, _, _, _, _, _ in FLUIDS:
        zh[f"fluid.chemicaladdon.{sid}"] = cn
        zh[f"item.chemicaladdon.{sid}_bucket"] = cn + "桶"
    for sid, cn, _, _ in SOLIDS:
        zh[f"item.chemicaladdon.{sid}"] = cn
    solid_names = {sid: cn for sid, cn, _, _ in SOLIDS}
    for sid in GRAINS:
        zh[f"item.chemicaladdon.{sid}_grain"] = solid_names[sid] + "晶粒"
    for sid, cn, _ in SOLUTIONS + SLURRIES:
        zh[f"item.chemicaladdon.{sid}_bucket"] = cn + "桶"
    for sid, cn, _, _ in BLOCKS:
        zh[f"block.chemicaladdon.{sid}"] = cn
    for pid, cn, _, _, _ in TEST_PAPERS:
        zh[f"item.chemicaladdon.{pid}"] = cn
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
    # MixtureFluidType). No block — it lives in tanks/pipes only; the auto bucket
    # item stays (suppressed default model, see gen_bucket_models).
    parts.append(
        "\n\tpublic static final FluidEntry<ForgeFlowingFluid.Flowing> MIXTURE = REGISTRATE.standardFluid(\"mixture\",\n"
        "\t\t\t(props, still, flow) -> new MixtureFluidType(props, still, flow))\n"
        "\t\t.lang(\"Mixture\")\n"
        "\t\t.properties(b -> b.density(1000)\n"
        "\t\t\t.viscosity(1000)\n"
        "\t\t\t.temperature(300))\n"
        "\t\t.source(ForgeFlowingFluid.Source::new)\n"
        "\t\t.bucket().lang(\"Mixture Bucket\").model((ctx, prov) -> {}).build()\n"
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
             "import com.yu1745.chemicaladdon.item.MixedResidueItem;",
             "import com.yu1745.chemicaladdon.item.TestPaperItem;",
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
    solid_names = {sid: en for sid, _, en, _ in SOLIDS}
    for sid in GRAINS:
        parts.append(f"\tpublic static final ItemEntry<Item> {sid.upper()}_GRAIN =\n"
                     f"\t\tREGISTRATE.item(\"{sid}_grain\", Item::new)\n"
                     f"\t\t\t.lang(\"{solid_names[sid]} Grains\")\n"
                     "\t\t\t.register();")
    # the mixed-solid item (U15, plans/03 §12): NBT composition ratio, unified
    # name, runtime colour blend, whole-lump extraction target for any solid
    # domain that holds more than one species ("mixed salt is the default; pure
    # is what the player earns")
    parts.append("\tpublic static final ItemEntry<MixedResidueItem> MIXED_RESIDUE =\n"
                 "\t\tREGISTRATE.item(\"mixed_residue\", MixedResidueItem::new)\n"
                 "\t\t\t.lang(\"Mixed Salt Residue\")\n"
                 "\t\t\t.register();")
    # test papers / qualitative reagents (U17, plans/12 §2.2): consumable
    # "what is in there" probes — dip on a reactor, read the colour, lose the paper
    for pid, _, en, kind, _ in TEST_PAPERS:
        parts.append(f"\tpublic static final ItemEntry<TestPaperItem> {kind} =\n"
                     f"\t\tREGISTRATE.item(\"{pid}\", p -> new TestPaperItem(p, TestPaperItem.Kind.{kind}))\n"
                     f"\t\t\t.lang(\"{en}\")\n"
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
