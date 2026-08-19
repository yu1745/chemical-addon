#!/usr/bin/env python3
"""
Track B: self-consistency audit of the inorganic-ion ΔG_f° set (audit_selfconsistency.py).

The acceptance bar is SELF-CONSISTENCY, not absolute accuracy. This script loads the
catalog ΔG_f° (from InorganicIonCatalog.java) and checks that it REPRODUCES a set of known
thermodynamic relations among the catalog ions:

  * acid-base   : pKa of HA = H+ + A-   (pKa = ΔG°rxn / RT·ln10)
  * redox       : E° of ox + ne- = red  (E° = -ΔG°rxn / (n·F), full half-reaction over H2O/H+/e-)
  * complex     : log β_n of metal + n·ligand = complex

Every relation that closes (within tolerance) confirms the set is self-consistent on that
path. Relations that fail identify ions whose ΔG_f° must be DERIVED from the anchor rather
than taken independently.

Usage: python3 tools/audit_selfconsistency.py [--tolerance 0.5]
"""

import argparse
import math
import os
import re

R, T = 8.314e-3, 298.15
RT_LN10 = R * T * math.log(10.0)
F = 96.485

ANCHORS = {"H+1": 0.0, "OH-1": -157.24, "H2O(l)": -237.13, "e-": 0.0, "O2(aq)": 16.4,
           "NH3(aq)": -26.5}


def charge_of(key):
    if key in ANCHORS:
        return 0
    if key == "e-":
        return -1
    m = re.match(r"^(.*?)([+-])(\d*)$", key)
    return 0 if not m else (int(m.group(3)) if m.group(3) else 1) * (1 if m.group(2) == "+" else -1)


def load_catalog():
    p = os.path.join(os.path.dirname(__file__), "..", "src", "main", "java",
                     "com", "yu1745", "chemengine", "solver", "InorganicIonCatalog.java")
    dg = {}
    for line in open(p):
        m = re.search(r'\.basis\("([^"]+)",\s*([+-]?\d+),\s*([+-]?[\d.]+)', line)
        if m:
            dg[m.group(1)] = float(m.group(3))
    dg.update(ANCHORS)
    return dg


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--tolerance", type=float, default=0.5, help="logK/E tolerance")
    args = ap.parse_args()
    dg = load_catalog()
    tol = args.tolerance

    # ---- relations: (kind, [species with coeff, product +, reactant -], expected, name) ----
    rels = [
        # acid-base (HA = H+ + A- ; expected = pKa)
        ("acid", [("H2PO4-1", -1), ("HPO4-2", 1), ("H+1", 1)], 7.20, "pKa2 H3PO4"),
        ("acid", [("HPO4-2", -1), ("PO4-3", 1), ("H+1", 1)], 12.35, "pKa3 H3PO4"),
        ("acid", [("HCO3-1", -1), ("CO3-2", 1), ("H+1", 1)], 10.33, "pKa2 H2CO3"),
        ("acid", [("HSO4-1", -1), ("SO4-2", 1), ("H+1", 1)], 1.99, "pKa2 H2SO4"),
        ("acid", [("HSO3-1", -1), ("SO3-2", 1), ("H+1", 1)], 7.20, "pKa2 H2SO3"),
        ("acid", [("H2O(l)", -1), ("OH-1", 1), ("H+1", 1)], 14.0, "pKw (H2O=H+OH)"),
        # redox (ox + ne- + ... = red + ... ; expected = E° V)
        ("redox", [("MnO4-1", -1), ("H+1", -8), ("e-", -5), ("Mn+2", 1), ("H2O(l)", 4)],
         1.51, "E° MnO4-/Mn2+"),
        ("redox", [("CrO4-2", -1), ("H+1", -8), ("e-", -3), ("Cr+3", 1), ("H2O(l)", 4)],
         1.36, "E° CrO4-2/Cr3+"),
        ("redox", [("Cr2O7-2", -1), ("H+1", -14), ("e-", -6), ("Cr+3", 2), ("H2O(l)", 7)],
         1.33, "E° Cr2O7-2/Cr3+"),
        ("redox", [("Fe+3", -1), ("e-", -1), ("Fe+2", 1)], 0.77, "E° Fe3+/Fe2+"),
        ("redox", [("Cu+2", -1), ("e-", -1), ("Cu+1", 1)], 0.15, "E° Cu2+/Cu+"),
        ("redox", [("AsO4-3", -1), ("H+1", -4), ("e-", -2), ("AsO2-1", 1), ("H2O(l)", 2)],
         0.56, "E° AsO4-3/AsO2-1"),
        # complex (metal + n·ligand = complex ; expected = log β_n)
        ("complex", [("Al+3", -1), ("OH-1", -4), ("Al(OH)4-1", 1)], 33.3, "logβ4 Al(OH)4-"),
        ("complex", [("Cu+2", -1), ("NH3(aq)", -4), ("Cu(NH3)4+2", 1)], 12.59, "logβ4 Cu(NH3)4"),
        ("complex", [("Ag+1", -1), ("NH3(aq)", -2), ("Ag(NH3)2+1", 1)], 7.23, "logβ2 Ag(NH3)2"),
        ("complex", [("Zn+2", -1), ("OH-1", -4), ("Zn(OH)4-2", 1)], 15.5, "logβ4 Zn(OH)4-"),
        ("complex", [("Fe+2", -1), ("CN-1", -6), ("Fe(CN)6-4", 1)], 35.0, "logβ6 Fe(CN)6-4"),
        ("complex", [("Fe+3", -1), ("CN-1", -6), ("Fe(CN)6-3", 1)], 43.0, "logβ6 Fe(CN)6-3"),
        ("complex", [("Au+1", -1), ("CN-1", -2), ("Au(CN)2-1", 1)], 39.0, "logβ2 Au(CN)2-"),
        ("complex", [("Zn+2", -1), ("CN-1", -4), ("Zn(CN)4-2", 1)], 18.0, "logβ4 Zn(CN)4-"),
        ("complex", [("Hg+2", -1), ("CN-1", -4), ("Hg(CN)4-2", 1)], 41.0, "logβ4 Hg(CN)4-"),
    ]

    ok = 0
    print(f"{'relation':<26} {'expected':>9} {'computed':>9}  status")
    for kind, terms, expected, name in rels:
        if not all(k in dg for k, _ in terms):
            print(f"{name:<26} {expected:>9} {'n/a':>9}  (missing species)")
            continue
        dg_rxn = sum(c * dg[k] for k, c in terms)  # ΔG°rxn, kJ/mol
        if kind == "acid":
            computed = dg_rxn / RT_LN10
        elif kind == "redox":
            n = -next(c for k, c in terms if k == "e-")
            computed = -dg_rxn / (n * F)
        else:
            computed = -dg_rxn / RT_LN10
        okv = abs(computed - expected) < tol
        if okv:
            ok += 1
        print(f"{name:<26} {expected:>9.2f} {computed:>9.2f}  {'OK' if okv else 'FAIL'}")
    print(f"\n{ok}/{len(rels)} relations close within ±{tol}  (self-consistent)")


if __name__ == "__main__":
    main()
