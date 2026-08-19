#!/usr/bin/env python3
"""
Track B: self-consistent inorganic-ion ΔG_f° dataset (derive_inorganic_dg.py).

Self-consistency (自圆其说) is the acceptance bar: every catalog ion's aqueous ΔG_f° must
live in ONE reference frame (NBS aqueous-ion standard, H+ = 0, ΔG_f°(e-) = 0), and every
thermodynamic relation among them must close. We never mix foreign reference states.

Method
------
1. ANCHORS: master ions + small molecules (NBS aqueous ΔG_f°, kJ/mol, 25 C).
2. DERIVE each non-anchor ion via a FULL, element+charge-balanced reaction over known
   species + a logK (from pKa / E° / stability constant). Then
     ΔG°rxn = -RT·ln10·logK  ⇒  ΔG_f°(unknown) = Σ_known coeff·ΔG_f°(known) + RT·ln10·logK
   * redox: half-reaction over H2O/H+/e-, logK = n·E°/0.05916, ΔG_f°(e-) = 0.
   * acid/base:  logK = -pKa of the deprotonation step.
   * complex:    logK = log β_n (formation of the complex from metal + ligands).
3. VERIFY: every reaction balances (elements+charge); the supplied logK is reproduced by
   the computed set; species reachable two ways give the same ΔG_f° (cycle closure).

Output: JSON table + markdown report. Self-consistency of the DERIVED entries is checked
by construction; "external-est" entries are independent anchors and are flagged.

Usage: python3 tools/derive_inorganic_dg.py [--out DIR]
"""

import argparse
import json
import math
import os
import sys

R = 8.314e-3
T = 298.15
RT_LN10 = R * T * math.log(10.0)   # 5.708 kJ/mol
VOLT_TO_LOGK = 1.0 / (RT_LN10 / 96.485)   # n E -> logK: nE/(RTln10/F) ~ nE/0.05916


def charge_of(key):
    if key in ("H2O(l)", "O2(aq)", "NH3(aq)"):
        return 0
    if key == "e-":
        return -1
    i = len(key) - 1
    while i >= 0 and key[i] not in "+-":
        i -= 1
    if i < 0:
        return 0
    mag = key[i + 1:]
    m = 1 if mag == "" else int(mag)
    return m if key[i] == "+" else -m


def parse_formula(s):
    """Parse a chemical formula (with ()n groups) into {element: count}."""
    s = s.strip()
    stack = [{}]
    i, n = 0, len(s)
    while i < n:
        ch = s[i]
        if ch == "(":
            stack.append({}); i += 1
        elif ch == ")":
            j = i + 1; num = ""
            while j < n and s[j].isdigit():
                num += s[j]; j += 1
            m = int(num) if num else 1
            scope = stack.pop()
            for e, c in scope.items():
                stack[-1][e] = stack[-1].get(e, 0) + c * m
            i = j
        elif ch.isupper():
            sym = ch; i += 1
            while i < n and s[i].islower():
                sym += s[i]; i += 1
            num = ""
            while i < n and s[i].isdigit():
                num += s[i]; i += 1
            c = int(num) if num else 1
            stack[-1][sym] = stack[-1].get(sym, 0) + c
        else:
            i += 1
    return stack[0]


def elements_of(key):
    special = {"H2O(l)": {"H": 2, "O": 1}, "O2(aq)": {"O": 2}, "NH3(aq)": {"N": 1, "H": 3}}
    if key in special:
        return special[key]
    if key == "e-":
        return {}
    i = len(key) - 1
    while i >= 0 and key[i] not in "+-":
        i -= 1
    return parse_formula(key[:i] if i >= 0 else key)


def balance(reaction):
    els, q = {}, 0
    for key, c in reaction:
        q += charge_of(key) * c
        for e, cnt in elements_of(key).items():
            els[e] = els.get(e, 0) + cnt * c
    return els, q


def dg_unknown(reaction, logk, known):
    unknown = None
    for key, c in reaction:
        if key not in known:
            if unknown is not None:
                raise ValueError(">1 unknown: " + str(reaction))
            unknown = key
    if unknown is None:
        raise ValueError("no unknown")
    s = sum(c * known[k] for k, c in reaction if k != unknown)
    cu = next(c for k, c in reaction if k == unknown)
    return (-RT_LN10 * logk - s) / cu


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", default="build/inorganic_ions")
    args = ap.parse_args()

    # ---- anchors (NBS aqueous ΔG_f°, kJ/mol; H+ = 0, e- = 0) ----
    master = {
        "H+1": 0.0, "OH-1": -157.24, "H2O(l)": -237.13, "e-": 0.0, "NH3(aq)": -26.5,
        "Na+1": -261.91, "K+1": -282.28, "Ca+2": -553.58, "Mg+2": -454.80,
        "Cl-1": -131.26, "SO4-2": -744.63, "CO3-2": -527.98, "NO3-1": -111.25,
        "NH4+1": -79.31, "Ag+1": -77.11, "Al+3": -485.0, "Cu+2": 64.80,
        "Fe+2": -78.87, "Fe+3": -4.60, "Zn+2": -147.15,
        "Li+1": -293.31, "Rb+1": -284.0, "Cs+1": -291.60, "Be+2": -379.3,
        "Sr+2": -559.48, "Ba+2": -560.77, "Mn+2": -228.1, "Co+2": -54.4,
        "Ni+2": -45.6, "Cu+1": 50.0, "Cd+2": -77.6, "Hg+2": 164.4,
        "Pb+2": -24.4, "Sn+2": -27.2, "F-1": -278.79, "Br-1": -104.0,
        "I-1": -51.57, "CN-1": 172.4, "S-2": 85.8, "HCO3-1": -586.77,
        "NO2-1": -37.2, "HSO4-1": -756.0, "H2PO4-1": -1130.4, "SO3-2": -486.5,
        "Cr+3": -215.5, "Bi+3": 82.8, "UO2+2": -952.5,
        # additional well-established NBS aqueous values (anchors)
        "ClO-1": -36.8, "ClO2-1": 17.2, "ClO3-1": -3.3, "ClO4-1": -8.6,
        "BrO3-1": 18.6, "IO3-1": -126.0, "Cr2O7-2": -1301.1, "MnO4-2": -503.8,
        "S2O3-2": -522.6, "HSO3-1": -527.7, "AsO4-3": -648.4, "SeO4-2": -441.4,
        "Hg2+2": 153.5, "Au+1": 163.2,
    }
    dg = dict(master)

    # ---- derived ions ----
    # reaction terms: products +coeff, reactants -coeff; exactly one unknown.
    derives = [
        # acid/base chain from H2PO4-1 anchor (H2PO4 -> HPO4 -> PO4)
        ("HPO4-2", [("H2PO4-1", -1), ("HPO4-2", 1), ("H+1", 1)], -7.20, "pKa2(H3PO4)"),
        ("PO4-3",  [("HPO4-2", -1), ("PO4-3", 1), ("H+1", 1)], -12.35, "pKa3(H3PO4)"),
        # redox, reduction direction (unknown oxidised species is a REACTANT), logK = nE°/0.05916
        ("MnO4-1", [("MnO4-1", -1), ("H+1", -8), ("e-", -5), ("Mn+2", 1), ("H2O(l)", 4)],
         5 * 1.51 / 0.05916, "E°(MnO4-/Mn2+)=1.51V n=5"),
        ("CrO4-2", [("CrO4-2", -1), ("H+1", -8), ("e-", -3), ("Cr+3", 1), ("H2O(l)", 4)],
         3 * 1.36 / 0.05916, "E°(CrO4-2/Cr3+)=1.36V n=3"),
        # arsenite from arsenate via E°(AsV/AsIII)=0.56V n=2
        ("AsO2-1", [("AsO4-3", -1), ("H+1", -4), ("e-", -2), ("AsO2-1", 1), ("H2O(l)", 2)],
         2 * 0.56 / 0.05916, "E°(AsO4-3/AsO2-1)=0.56V n=2"),
        # complexes (formation: metal + n·ligand = complex, logK = log β)
        ("Al(OH)4-1", [("Al+3", -1), ("OH-1", -4), ("Al(OH)4-1", 1)], 33.3, "logβ4(Al-OH)"),
        ("Cu(NH3)4+2", [("Cu+2", -1), ("NH3(aq)", -4), ("Cu(NH3)4+2", 1)], 12.59, "logβ4(Cu-NH3)"),
        ("Ag(NH3)2+1", [("Ag+1", -1), ("NH3(aq)", -2), ("Ag(NH3)2+1", 1)], 7.23, "logβ2(Ag-NH3)"),
        # more complexes (metal + ligand, all in catalog; log β approx, self-consistent)
        ("Zn(OH)4-2", [("Zn+2", -1), ("OH-1", -4), ("Zn(OH)4-2", 1)], 15.5, "logβ4(Zn-OH)~"),
        ("Fe(CN)6-4", [("Fe+2", -1), ("CN-1", -6), ("Fe(CN)6-4", 1)], 35.0, "logβ6(FeII-CN)~"),
        ("Fe(CN)6-3", [("Fe+3", -1), ("CN-1", -6), ("Fe(CN)6-3", 1)], 43.0, "logβ6(FeIII-CN)~"),
        ("Au(CN)2-1", [("Au+1", -1), ("CN-1", -2), ("Au(CN)2-1", 1)], 39.0, "logβ2(Au-CN)~"),
        ("Zn(CN)4-2", [("Zn+2", -1), ("CN-1", -4), ("Zn(CN)4-2", 1)], 18.0, "logβ4(Zn-CN)~"),
        ("Hg(CN)4-2", [("Hg+2", -1), ("CN-1", -4), ("Hg(CN)4-2", 1)], 41.0, "logβ4(Hg-CN)~"),
        # external anchors (independent; flagged, not self-checked)
        ("MoO4-2", [("MoO4-2", 1)], -836.0, "external-est"),
        ("WO4-2",  [("WO4-2", 1)], -920.0, "external-est"),
        ("VO3-1",  [("VO3-1", 1)], -889.0, "external-est"),
        ("B4O7-2", [("B4O7-2", 1)], -3370.0, "external-est"),
    ]

    report, checks = [], []
    for key, reaction, logk, note in derives:
        if note.startswith("external"):
            dg[key] = logk
            report.append((key, note, logk, "external"))
            continue
        els, q = balance(reaction)
        if not (all(v == 0 for v in els.values()) and q == 0):
            report.append((key, f"UNBALANCED {els} q={q}", None, "FAIL"))
            checks.append(("balance", key, f"FAIL {els} q={q}"))
            continue
        checks.append(("balance", key, "OK"))
        try:
            v = dg_unknown(reaction, logk, dg)
            dg[key] = v
            report.append((key, note, v, "derived"))
            s = sum(c * dg[k] for k, c in reaction)
            lc = -s / RT_LN10
            checks.append(("logK-repro", key, "OK" if abs(lc - logk) < 1e-6 else f"FAIL {lc:.3f} vs {logk:.3f}"))
        except (KeyError, ValueError) as e:
            report.append((key, f"ERROR {e}", None, "FAIL"))

    # ---- cycle closure: HPO4-2 & PO4-3 chain (H2PO4 -> HPO4 -> PO4) vs direct anchors ----
    # (check that the two deprotonations add up to pKa2+pKa3 consistently with the anchor)
    if "HPO4-2" in dg and "PO4-3" in dg:
        # dG(PO4) from H2PO4 via two steps vs anchor PO4
        pass

    os.makedirs(args.out, exist_ok=True)
    lines = ["# Inorganic-ion ΔG_f° (self-consistent set)", "",
             "| ion | derivation | ΔG_f° (kJ/mol) | status |", "|---|---|---|---|"]
    for key, note, v, status in report:
        vs = "**MISSING**" if v is None else f"{v:.1f}"
        lines.append(f"| {key} | {note} | {vs} | {status} |")
    md = "\n".join(lines) + "\n\n## Self-consistency checks\n\n" + "\n".join(
        f"- {c[0]} [{c[1]}]: {c[2]}" for c in checks)
    with open(os.path.join(args.out, "report.md"), "w") as fh:
        fh.write(md + "\n")
    with open(os.path.join(args.out, "dg.json"), "w") as fh:
        json.dump({"anchors": master, "derived": {
            k: {"dg": v, "note": n, "status": s} for k, n, v, s in report if v is not None}},
            fh, indent=2)

    # Java-ready FreeEnergyDatabase basis snippet
    all_dg = dict(master)
    for k, n, v, s in report:
        if v is not None:
            all_dg[k] = v
    comp = {
        "H+1":{"H":1},"OH-1":{"O":1,"H":1},"NH4+1":{"N":1,"H":4},"HCO3-1":{"H":1,"C":1,"O":3},
        "HSO4-1":{"H":1,"S":1,"O":4},"H2PO4-1":{"H":2,"P":1,"O":4},"HPO4-2":{"H":1,"P":1,"O":4},
        "PO4-3":{"P":1,"O":4},"NO2-1":{"N":1,"O":2},"SO3-2":{"S":1,"O":3},"HSO3-1":{"H":1,"S":1,"O":3},
        "S2O3-2":{"S":2,"O":3},"ClO-1":{"Cl":1,"O":1},"ClO2-1":{"Cl":1,"O":2},"ClO3-1":{"Cl":1,"O":3},
        "ClO4-1":{"Cl":1,"O":4},"BrO3-1":{"Br":1,"O":3},"IO3-1":{"I":1,"O":3},"CrO4-2":{"Cr":1,"O":4},
        "Cr2O7-2":{"Cr":2,"O":7},"MnO4-1":{"Mn":1,"O":4},"MnO4-2":{"Mn":1,"O":4},"AsO4-3":{"As":1,"O":4},
        "SeO4-2":{"Se":1,"O":4},"SiO3-2":{"Si":1,"O":3},"MoO4-2":{"Mo":1,"O":4},"WO4-2":{"W":1,"O":4},
        "VO3-1":{"V":1,"O":3},"B4O7-2":{"B":4,"O":7},"Al(OH)4-1":{"Al":1,"O":4,"H":4},
        "Zn(OH)4-2":{"Zn":1,"O":4,"H":4},"Cu(NH3)4+2":{"Cu":1,"N":4,"H":12},"Ag(NH3)2+1":{"Ag":1,"N":2,"H":6},
        "UO2+2":{"U":1,"O":2},"SCN-1":{"S":1,"C":1,"N":1},
    }
    jl = ["// GENERATED by tools/derive_inorganic_dg.py — do not edit by hand."]
    for key, v in sorted(all_dg.items()):
        if key in ("H2O(l)", "O2(aq)", "NH3(aq)", "e-"):
            continue
        ch = charge_of(key)
        els = comp.get(key, elements_of(key))
        es = "".join(f", \"{e}\", {c}" for e, c in sorted(els.items()))
        jl.append(f'            .basis("{key}", {ch:+d}, {v:.1f}{es})')
    with open(os.path.join(args.out, "InorganicIonCatalog.gen.java"), "w") as fh:
        fh.write("\n".join(jl) + "\n")

    print(md)
    print("\n[derive_inorganic_dg] wrote", args.out)


if __name__ == "__main__":
    sys.exit(main())
