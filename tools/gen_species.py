#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Chemical Addon species/resource generator (M0).
Single source of truth for the 38 fluid species + 18 solid species from
plans/08-substance-catalog.md. Generates:
  - fluid textures   (assets/chemicaladdon/textures/fluid/<id>_still.png + _flow.png)
  - item textures    (assets/chemicaladdon/textures/item/<id>.png)
  - item models      (assets/chemicaladdon/models/item/<id>.json)
  - lang             (zh_cn.json / en_us.json)
  - Java sources     (registry/AllFluids.java, registry/AllItems.java)

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
    # --- liquids / solutions ---
    ("water",                      "水",     "Water",              0x3F76E4, 1000, 1000, 300, False),
    ("brine",                      "饱和盐水", "Saturated Brine",   0x8FB4E8, 1200, 1300, 300, False),
    ("ammoniated_brine",           "氨盐水", "Ammoniated Brine",   0xA8C8E8, 1150, 1200, 300, False),
    ("dilute_hydrochloric_acid",   "稀盐酸", "Dilute Hydrochloric Acid", 0xD8F0D8, 1050, 1000, 300, False),
    ("concentrated_hydrochloric_acid", "浓盐酸", "Concentrated Hydrochloric Acid", 0xC8E8C0, 1190, 1100, 300, False),
    ("dilute_sulfuric_acid",       "稀硫酸", "Dilute Sulfuric Acid", 0xE8E8D0, 1080, 1000, 300, False),
    ("concentrated_sulfuric_acid", "浓硫酸", "Concentrated Sulfuric Acid", 0xF0E0B0, 1840, 2000, 320, False),
    ("oleum",                      "发烟硫酸", "Oleum",           0xF0C890, 1900, 2500, 320, False),
    ("dilute_nitric_acid",         "稀硝酸", "Dilute Nitric Acid", 0xE8E8F0, 1060, 1000, 300, False),
    ("concentrated_nitric_acid",   "浓硝酸", "Concentrated Nitric Acid", 0xE8D8A0, 1400, 1500, 300, False),
    ("caustic_soda_solution",      "烧碱液", "Caustic Soda Solution", 0xD8E0F0, 1300, 1500, 300, False),
    ("soda_ash_solution",          "纯碱液", "Soda Ash Solution", 0xE0E8E0, 1100, 1000, 300, False),
    ("ammonium_chloride_solution", "氯化铵液", "Ammonium Chloride Solution", 0xD0E0D0, 1050, 1000, 300, False),
    ("calcium_chloride_solution",  "氯化钙液", "Calcium Chloride Solution", 0xE0E8F0, 1200, 1200, 300, False),
    ("ammonia_water",              "氨水",   "Ammonia Water",      0xC8E0D0, 950, 1000, 300, False),
    ("milk_of_lime",               "石灰乳", "Milk of Lime",       0xE8E8E0, 1150, 3000, 300, False),
    ("bleach_solution",            "漂白液", "Bleach Solution",    0xC8F0E8, 1100, 1000, 300, False),
    ("phosphoric_acid",            "磷酸",   "Phosphoric Acid",    0xE8E0D8, 1700, 1800, 300, False),
    ("ammonium_sulfate_solution",  "硫酸铵液", "Ammonium Sulfate Solution", 0xE0E8D8, 1150, 1100, 300, False),
    ("ammonium_nitrate_solution",  "硝酸铵液", "Ammonium Nitrate Solution", 0xE0E8E8, 1200, 1100, 300, False),
    ("sodium_aluminate_solution",  "铝酸钠液", "Sodium Aluminate Solution", 0xD8E0E8, 1250, 1400, 330, False),
    ("sodium_bicarbonate_slurry",  "重碱浆", "Sodium Bicarbonate Slurry", 0xE0E8E0, 1300, 3000, 300, False),
    ("gypsum_slurry",              "石膏浆", "Gypsum Slurry",      0xE0E0D0, 1400, 4000, 300, False),
    ("calcium_sulfite_slurry",     "亚硫酸钙浆", "Calcium Sulfite Slurry", 0xD8E8D8, 1400, 4000, 300, False),
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
    ("filter_cake",            "滤渣",   "Filter Cake",     0x908878),
]

# (id, cn, en, color)
BLOCKS = [
    ("chemical_brick",    "化工砖", "Chemical Brick",    0x8E8478),
    ("reactor_controller", "反应釜控制器", "Reactor Controller", 0x6E6E6E),
    ("filter_press",      "过滤机", "Filter Press",      0x7A7A8A),
    ("settling_basin",    "沉淀池控制器", "Settling Basin", 0x5E6E7A),
]

# ---------------------------------------------------------------- png writer
def write_png(path, rgba_rows):
    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)
    raw = b"".join(b"\x00" + bytes(row) for row in rgba_rows)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)

def hex_rgba(rgb, alpha):
    return ((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)

def make_fluid_texture(rgb, gas):
    """16x16 vertical gradient; gases get a lighter, more transparent look."""
    alpha = 140 if gas else 220
    rows = []
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    for y in range(16):
        f = 1.0 - 0.18 * (y / 15.0)  # slightly darker at bottom
        rr, gg, bb = int(r * f), int(g * f), int(b * f)
        if gas and (y + 4) % 7 == 0:  # faint banding to suggest gas
            rr, gg, bb = min(255, int(rr * 1.15)), min(255, int(gg * 1.15)), min(255, int(bb * 1.15))
        rows.append([rr, gg, bb, alpha] * 16)
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

# ---------------------------------------------------------------- generators
def gen_textures():
    for sid, _, _, color, _, _, _, gas in FLUIDS:
        d = os.path.join(ASSETS, "textures/fluid")
        os.makedirs(d, exist_ok=True)
        write_png(os.path.join(d, f"{sid}_still.png"), make_fluid_texture(color, gas))
        write_png(os.path.join(d, f"{sid}_flow.png"), make_fluid_texture(color, gas))
    d = os.path.join(ASSETS, "textures/item")
    os.makedirs(d, exist_ok=True)
    for sid, _, _, color in SOLIDS:
        write_png(os.path.join(d, f"{sid}.png"), make_item_texture(color))

def gen_item_models():
    d = os.path.join(ASSETS, "models/item")
    os.makedirs(d, exist_ok=True)
    for sid, _, _, _ in SOLIDS:
        with open(os.path.join(d, f"{sid}.json"), "w", encoding="utf-8") as f:
            f.write('{\n  "parent": "minecraft:item/generated",\n  "textures": {\n'
                    f'    "layer0": "chemicaladdon:item/{sid}"\n  }}\n}}\n')

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


def gen_block_textures():
    d = os.path.join(ASSETS, "textures/block")
    os.makedirs(d, exist_ok=True)
    write_png(os.path.join(d, "chemical_brick.png"), make_brick_texture(0x8E8478))
    write_png(os.path.join(d, "reactor_controller.png"), make_panel_texture(0x6E6E6E))
    write_png(os.path.join(d, "filter_press.png"), make_panel_texture(0x7A7A8A))
    write_png(os.path.join(d, "settling_basin.png"), make_panel_texture(0x5E6E7A))


def gen_lang():
    zh = {"itemGroup.chemicaladdon": "化学附属"}
    en = {"itemGroup.chemicaladdon": "Chemical Addon"}
    for sid, cn, en_name, _, _, _, _, _ in FLUIDS:
        zh[f"fluid.chemicaladdon.{sid}"] = cn
        en[f"fluid.chemicaladdon.{sid}"] = en_name
    for sid, cn, en_name, _ in SOLIDS:
        zh[f"item.chemicaladdon.{sid}"] = cn
        en[f"item.chemicaladdon.{sid}"] = en_name
    for sid, cn, en_name, _ in BLOCKS:
        zh[f"block.chemicaladdon.{sid}"] = cn
        en[f"block.chemicaladdon.{sid}"] = en_name
    import json as _json
    with open(os.path.join(ASSETS, "lang/zh_cn.json"), "w", encoding="utf-8") as f:
        _json.dump(zh, f, ensure_ascii=False, indent=2)
    with open(os.path.join(ASSETS, "lang/en_us.json"), "w", encoding="utf-8") as f:
        _json.dump(en, f, ensure_ascii=False, indent=2)

def fluid_entry(sid, en_name, density, viscosity, temp, gas):
    return ("\n\tpublic static final FluidEntry<ForgeFlowingFluid.Flowing> "
            f"{sid.upper()} = REGISTRATE.standardFluid(\"{sid}\",\n"
            f"\t\t\t(props, still, flow) -> new ChemFluidType(props, still, flow, {str(gas).lower()}))\n"
            f"\t\t.lang(\"{en_name}\")\n"
            f"\t\t.properties(b -> b.density({density})\n"
            f"\t\t\t.viscosity({viscosity})\n"
            f"\t\t\t.temperature({temp}))\n"
            "\t\t.register();")

def gen_fluids_java():
    parts = ["package com.yu1745.chemicaladdon.registry;",
             "",
             "import com.simibubi.create.foundation.data.CreateRegistrate;",
             "import com.tterrag.registrate.util.entry.FluidEntry;",
             "import com.yu1745.chemicaladdon.ChemicalAddon;",
             "import com.yu1745.chemicaladdon.fluid.ChemFluidType;",
             "import net.minecraftforge.fluids.ForgeFlowingFluid;",
             "",
             "public class AllFluids {",
             "\tpublic static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();",
             ""]
    for sid, _, en_name, _, density, viscosity, temp, gas in FLUIDS:
        parts.append(fluid_entry(sid, en_name, density, viscosity, temp, gas))
    parts += ["", "\tpublic static void register() {", "\t}", "}", ""]
    with open(os.path.join(JAVA, "AllFluids.java"), "w", encoding="utf-8") as f:
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
    gen_item_models()
    gen_block_textures()
    gen_lang()
    gen_fluids_java()
    gen_items_java()
    print(f"OK: {len(FLUIDS)} fluids, {len(SOLIDS)} solids, {len(BLOCKS)} blocks -> textures/models/lang/Java generated")
