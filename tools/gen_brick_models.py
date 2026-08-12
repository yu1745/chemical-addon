#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
One-off conversion tool: copies Create's 8 hollow-wall FluidTank models
(block_single/top/bottom/middle + _window) into this mod's chemical_brick,
adding "tintindex": 0 to every face so the client-side metal-grey BlockColors
tint applies. Textures are left pointing at create:block/fluid_tank* — the
shell reuses Create's tank sheets (zero hand-drawn material), and the CTM
connected sheets (fluid_tank_connected etc.) are auto-stitched into the block
atlas by vanilla's directory source.

Run: python3 tools/gen_brick_models.py
    (needs the Create source checkout for the source models)
"""
import json
import os

CREATE_MODELS = r"C:\Users\wangyu\Desktop\server\create-forge_1.20.1\src\main\resources\assets\create\models\block\fluid_tank"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/chemicaladdon/models/block/chemical_brick")

NAMES = ["block_single", "block_single_window", "block_top", "block_top_window",
         "block_bottom", "block_bottom_window", "block_middle", "block_middle_window"]


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in NAMES:
        src = os.path.join(CREATE_MODELS, name + ".json")
        if not os.path.exists(src):
            raise FileNotFoundError("Create model missing: " + src)
        with open(src, encoding="utf-8") as f:
            model = json.load(f)
        for elem in model.get("elements", []):
            for face in elem.get("faces", {}).values():
                face["tintindex"] = 0
        with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(model, f, indent=2)
        print("wrote", name)
    print(f"OK: {len(NAMES)} models, textures kept as create:block/fluid_tank*")


if __name__ == "__main__":
    main()
