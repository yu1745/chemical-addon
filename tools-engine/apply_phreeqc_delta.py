#!/usr/bin/env python3
"""Apply authoritative PHREEQC delta_h values to species JSON (Track A0/A2).

Each mapping entry records our reaction string, the reference database block it
was calibrated from, and the conversion recipe that turns the database enthalpy
(as written) into the enthalpy of OUR reaction:

  * "direct"      — our reaction matches the DB reaction verbatim.
  * "acid:N"      — DB reaction is the acid-dissolution form
                    "solid + N H+ = ions + m H2O"; ours is the direct
                    dissolution "solid = ions + N OH-". Hess conversion:
                    ΔH_ours = ΔH_db + N * ΔH(H2O = H+ + OH-).
  * "acid:N+hco3" — additionally the DB product is HCO3- instead of CO3-2:
                    ΔH_ours = ΔH_db + N * ΔH_auto + ΔH(HCO3- = CO3-2 + H+).
  * "nist:V"      — no database coverage; V computed from NIST/NBS standard
                    formation enthalpies (Hess), derivation recorded.
  * "estimated"   — no authoritative value available anywhere; value kept and
                    explicitly tagged so it cannot be mistaken for data.

Helper enthalpies (water autoionisation, bicarbonate dissociation) are read
live from tools/phreeqc.dat so the arithmetic is always verifiable.

Usage:
    python3 tools/apply_phreeqc_delta.py            # dry-run: print the table
    python3 tools/apply_phreeqc_delta.py --apply    # write species JSONs
    python3 tools/apply_phreeqc_delta.py --check    # data-integrity check
"""

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from import_phreeqc import load_database

ROOT = Path(__file__).resolve().parent
SPECIES = ROOT.parent / "src/test/resources/species"

# (species file, our reaction, source db, DB block lookup, recipe)
MAPPING = [
    # --- batch 1: direct use (our reaction == DB reaction) ---
    ("silver_chloride.json",      "silver_chloride(s) = Ag+1 + Cl-1",                  "llnl",       "Chlorargyrite",    "direct"),
    ("silver_carbonate.json",     "silver_carbonate(s) = 2 Ag+1 + CO3-2",              "minteq.v4",  "Ag2CO3",           "direct"),
    ("sodium_bicarbonate.json",   "sodium_bicarbonate(s) = Na+1 + HCO3-1",             "llnl",       "Nahcolite",        "direct"),
    ("zinc_sulfate_solution.json","Zn+2 + 4 chemicaladdon:ammonia = [Zn(NH3)4]+2",     "llnl",       "Zn(NH3)4++",       "direct"),

    # --- batch 2: acid-dissolution form converted via Hess ---
    ("magnesium_carbonate.json",  "magnesium_carbonate(s) = Mg+2 + CO3-2",             "llnl",       "Magnesite",        "acid:0+hco3"),
    ("magnesium_hydroxide.json",  "magnesium_hydroxide(s) = Mg+2 + 2 OH-1",            "llnl",       "Brucite",          "acid:2"),
    ("slaked_lime.json",          "slaked_lime(s) = Ca+2 + 2 OH-1",                    "llnl",       "Portlandite",      "acid:2"),
    ("iron_hydroxide.json",       "iron_hydroxide(s) = Fe+3 + 3 OH-1",                 "llnl",       "Fe(OH)3",          "acid:3"),
    ("aluminium_hydroxide.json",  "aluminium_hydroxide(s) = Al+3 + 3 OH-1",            "llnl",       "Gibbsite",         "acid:3"),
    ("copper_hydroxide.json",     "copper_hydroxide(s) = Cu+2 + 2 OH-1",               "minteq.v4",  "Cu(OH)2",          "acid:2"),
    ("copper_carbonate.json",     "copper_carbonate(s) = 2 Cu+2 + CO3-2 + 2 OH-1",     "llnl",       "Malachite",        "acid:2+hco3"),
    ("zinc_hydroxide.json",       "zinc_hydroxide(s) = Zn+2 + 2 OH-1",                 "llnl",       "Zn(OH)2(epsilon)", "acid:2"),

    # --- batch 3: NIST/NBS formation-enthalpy Hess (no database coverage) ---
    ("sulfur_trioxide.json",
     "chemicaladdon:sulfur_trioxide + water = 2 H+1 + SO4-2",
     "nist", "-227.72",
     "SO3(g)+H2O(l)=H2SO4(aq): dHf SO3(g) -395.72, H2O(l) -285.83, SO4-2(aq) -909.27 kJ/mol (NBS tables)"),
    ("nitrogen_dioxide.json",
     "3 chemicaladdon:nitrogen_dioxide + water = 2 H+1 + 2 NO3-1 + chemicaladdon:nitric_oxide",
     "nist", "-138.18",
     "3NO2(g)+H2O(l)=2HNO3(aq)+NO(g): dHf NO2(g) +33.18, NO(g) +90.25, NO3-(aq) -207.36, H2O(l) -285.83 kJ/mol (NBS tables)"),
    ("aluminium_hydroxide.json",
     "aluminium_hydroxide(s) + OH-1 = [Al(OH)4]-1",
     "nist", "20.6",
     "Al(OH)3(s,gibbsite)+OH-(aq)=Al(OH)4-(aq): dHf Al(OH)4-(aq) -1502.5, gibbsite -1293.13, OH-(aq) -229.99 kJ/mol (NBS tables)"),
    ("silver_nitrate_solution.json",
     "Ag+1 + 2 chemicaladdon:ammonia = [Ag(NH3)2]+1",
     "nist", "-56.3",
     "Ag+(aq)+2NH3(aq)=Ag(NH3)2+(aq): dHf Ag(NH3)2+(aq) -111.29, Ag+(aq) +105.58, NH3(aq) -80.29 kJ/mol (NBS tables)"),

    # --- batch 4: phreeqc.dat calibration batch (early merge, now annotated) ---
    ("ammonia.json",                  "chemicaladdon:ammonia + water = NH4+1 + OH-1",     "phreeqc",    "NH4+ = NH3 + H+",           "rev+autoion"),
    ("ammonium_chloride_solution.json","NH4+1 + water = chemicaladdon:ammonia + H+1",     "phreeqc",    "NH4+ = NH3 + H+",           "direct"),
    ("soda_ash_solution.json",        "CO3-2 + water = HCO3-1 + OH-1",                    "phreeqc",    "CO3-2 + H+ = HCO3-",        "addautoion"),
    ("soda_ash_solution.json",        "HCO3-1 = chemicaladdon:carbon_dioxide + OH-1",     "phreeqc",    "CO3-2 + 2 H+ = CO2 + H2O",  "addautoion+hco3"),
    ("limestone.json",                "limestone(s) = Ca+2 + CO3-2",                      "phreeqc",    "CaCO3 = CO3-2 + Ca+2",      "direct"),
    ("gypsum.json",                   "gypsum(s) = Ca+2 + SO4-2",                         "phreeqc",    "CaSO4:2H2O = Ca+2 + SO4-2 + 2 H2O", "direct"),
    ("barium_sulfate.json",           "barium_sulfate(s) = Ba+2 + SO4-2",                 "phreeqc",    "BaSO4 = Ba+2 + SO4-2",      "direct"),
    ("barium_carbonate.json",         "barium_carbonate(s) = Ba+2 + CO3-2",               "phreeqc",    "BaCO3 = Ba+2 + CO3-2",      "direct"),

    # --- no authoritative value anywhere: keep value, tag as estimated ---
    ("ferric_chloride_solution.json",
     "Fe+3 + SCN-1 = [FeSCN]+2",
     "estimated", None,
     "no PHREEQC-family database or NIST entry for FeSCN2+; value is an estimate"),
    ("copper_sulfate_solution.json",
     "Cu+2 + 4 chemicaladdon:ammonia = [Cu(NH3)4]+2",
     "estimated", None,
     "llnl.dat has Cu(NH3)2+2 (-45.1) / Cu(NH3)3+2 (-67.3) but no 4th step; overall value is an estimate"),
    ("hydrogen_chloride.json",
     "chemicaladdon:hydrogen_chloride + water = H+1 + Cl-1",
     "estimated", None,
     "legacy heat_kj value; authoritative HCl(aq) speciation ΔH ≈ 0 (llnl.dat 'H+ + Cl- = HCl' has ΔH = 0, strong electrolyte) but the engine forbids delta_h = 0, so the legacy value is kept"),
]


def helper_enthalpies():
    """Water autoionisation and bicarbonate dissociation enthalpies (kJ/mol),
    read live from phreeqc.dat."""
    blocks = load_database(str(ROOT / "phreeqc.dat"))
    auto = hco3 = None
    for b in blocks:
        r = b["reaction"]
        if r == "H2O = OH- + H+":
            auto = b["delta_h"]
        elif r == "CO3-2 + H+ = HCO3-":
            hco3 = -b["delta_h"]  # ours is HCO3- = CO3-2 + H+
    if auto is None or hco3 is None:
        raise RuntimeError("could not locate helper enthalpies in phreeqc.dat")
    return auto, hco3


def db_lookup(db_name, key):
    """Find the database block by phase/species name or by reaction substring."""
    blocks = load_database(str(ROOT / f"{db_name}.dat"))
    for b in blocks:
        if b.get("name") == key:
            return b
    for b in blocks:
        if b["reaction"] and key in b["reaction"]:
            return b
    raise KeyError(f"{db_name}.dat: block {key!r} not found")


def compute(entry, auto, hco3):
    """Return (value, source, derivation) for one mapping entry."""
    name, reaction, db, key, recipe = entry
    if db == "nist":
        return float(key), "NIST/NBS (dHf Hess)", recipe
    if db == "estimated":
        return None, "estimated", recipe
    b = db_lookup(db, key)
    if b["delta_h"] is None:
        raise KeyError(f"{db}.dat: block {key!r} has no delta_h")
    v = b["delta_h"]
    if recipe == "direct":
        deriv = f"{db}.dat {key} ΔH = {v:.4g} kJ/mol (as written)"
        return v, f"{db}.dat ({key})", deriv
    if recipe == "addautoion":
        # our reaction = DB reaction + (H2O = H+ + OH-)
        deriv = f"{db}.dat {key} ΔH = {v:.4g} + autoion = {v + auto:.4g} kJ/mol"
        return round(v + auto, 2), f"{db}.dat ({key}) + Hess", deriv
    if recipe == "rev+autoion":
        # our reaction = -(DB reaction) + (H2O = H+ + OH-)
        deriv = f"{db}.dat {key} ΔH = {v:.4g} (reversed) + autoion = {-v + auto:.4g} kJ/mol"
        return round(-v + auto, 2), f"{db}.dat ({key}) + Hess", deriv
    if recipe == "addautoion+hco3":
        # our reaction = DB reaction + autoion + (HCO3- = CO3-2 + H+)
        deriv = (f"{db}.dat {key} ΔH = {v:.4g} + autoion = {auto:.4g}"
                 f" + HCO3- diss = {hco3:.4g} = {v + auto + hco3:.4g} kJ/mol")
        return round(v + auto + hco3, 2), f"{db}.dat ({key}) + Hess", deriv
    m = re.fullmatch(r"acid:(\d+)(\+hco3)?", recipe)
    if not m:
        raise ValueError(f"bad recipe {recipe!r}")
    n = int(m.group(1))
    terms = [f"{db}.dat {key} ΔH = {v:.4g}"]
    if m.group(2):
        v += hco3
        terms.append(f"+ HCO3- diss = {hco3:.4g}")
    if n:
        v += n * auto
        terms.append(f"+ {n} x autoion = {n * auto:.4g}")
    deriv = " kJ/mol; ".join(terms) + f" = {v:.4g} kJ/mol"
    return round(v, 2), f"{db}.dat ({key}) + Hess", deriv


def check_all():
    """Verify every equilibrium has a non-zero delta_h, no heat_kj, and the full
    provenance annotations (delta_h_source / delta_h_derivation)."""
    bad = []
    for path in sorted(SPECIES.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for eq in data.get("equilibria", []):
            if "heat_kj" in eq:
                bad.append(f"{path.name}: heat_kj still present")
            if "delta_h" not in eq:
                bad.append(f"{path.name}: delta_h missing")
            elif eq["delta_h"] == 0.0:
                bad.append(f"{path.name}: delta_h is zero placeholder")
            if "delta_h" in eq:
                src = eq.get("delta_h_source")
                deriv = eq.get("delta_h_derivation")
                if not isinstance(src, str) or not src.strip():
                    bad.append(f"{path.name}: delta_h_source missing/blank")
                if not isinstance(deriv, str) or not deriv.strip():
                    bad.append(f"{path.name}: delta_h_derivation missing/blank")
    if bad:
        for msg in bad:
            print("ERROR:", msg)
        return 1
    print("ok: all equilibria have non-zero delta_h, no heat_kj, and provenance annotations")
    return 0


def main():
    args = sys.argv[1:]
    if "--check" in args:
        return check_all()
    apply = "--apply" in args
    auto, hco3 = helper_enthalpies()
    print(f"helpers: H2O = H+ + OH- ΔH = {auto:.4f}; HCO3- = CO3-2 + H+ ΔH = {hco3:.4f} kJ/mol\n")

    for entry in MAPPING:
        name, reaction, db, key, recipe = entry
        path = SPECIES / name
        data = json.loads(path.read_text(encoding="utf-8"))
        value, source, deriv = compute(entry, auto, hco3)
        target = None
        for eq in data.get("equilibria", []):
            if eq["reaction"] == reaction:
                target = eq
                break
        if target is None:
            print(f"!! {name}: reaction not found: {reaction}")
            continue
        old = target.get("delta_h")
        if value is None:
            print(f"== {name:34s} keep {old:>9}  [{source}]  {deriv}")
            if apply:
                target["delta_h_source"] = source
                target["delta_h_derivation"] = deriv
        else:
            print(f"-> {name:34s} {old:>9} -> {value:>9}  [{source}]")
            print(f"      {deriv}")
            if apply:
                target["delta_h"] = value
                target["delta_h_source"] = source
                target["delta_h_derivation"] = deriv
        if apply:
            path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    if apply:
        print("\napplied; run: python3 tools/apply_phreeqc_delta.py --check")
    else:
        print("\ndry-run (no files written); use --apply to write")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
