#!/usr/bin/env python3
"""
Track B: self-consistency vs the llnl.dat backbone (llnl_backbone.py).

The acceptance bar is SELF-CONSISTENCY. llnl.dat is internally consistent by construction,
so the strongest check we can make is: does our catalog ΔG_f° set (NBS anchors + derived
values) REPRODUCE llnl.dat's logK for every reaction whose species all live in our catalog?
If yes, our set is consistent with the backbone reference frame.

It also reports, per catalog ion, whether it is a llnl MASTER species (backbone anchor) or
must be derived, and whether it is a llnl species at all.

Usage: python3 tools/llnl_backbone.py [--llnl tools/llnl.dat] [--out DIR]
"""

import argparse
import json
import math
import os
import re
import sys

R, T = 8.314e-3, 298.15
RT_LN10 = R * T * math.log(10.0)

_SPECIAL = {"Cyanide-": "CN-1", "Cyanide": "CN-1"}


def canon(token):
    m = re.match(r"^(.*?)([+-])(\d*)$", token)
    if not m:
        return _SPECIAL.get(token, token)
    return f"{m.group(1)}{m.group(2)}{m.group(3) or '1'}"


def charge_of(key):
    if key in ("H2O(l)", "O2(aq)", "NH3(aq)"):
        return 0
    if key == "e-":
        return -1
    m = re.match(r"^(.*?)([+-])(\d*)$", key)
    return 0 if not m else (int(m.group(3)) if m.group(3) else 1) * (1 if m.group(2) == "+" else -1)


def parse_formula(s):
    stack = [{}]; i, n = 0, len(s)
    while i < n:
        c = s[i]
        if c == "(":
            stack.append({}); i += 1
        elif c == ")":
            j = i + 1; num = ""
            while j < n and s[j].isdigit(): num += s[j]; j += 1
            m = int(num) if num else 1
            scope = stack.pop()
            for e, cnt in scope.items(): stack[-1][e] = stack[-1].get(e, 0) + cnt * m
            i = j
        elif c.isupper():
            sym = c; i += 1
            while i < n and s[i].islower(): sym += s[i]; i += 1
            num = ""
            while i < n and s[i].isdigit(): num += s[i]; i += 1
            stack[-1][sym] = stack[-1].get(sym, 0) + (int(num) if num else 1)
        else:
            i += 1
    return stack[0]


def elements_of(key):
    if key in ("H2O(l)", "O2(aq)", "NH3(aq)", "e-"):
        return {}
    m = re.match(r"^(.*?)([+-])(\d*)$", key)
    return parse_formula(m.group(1) if m else key)


def parse_reaction(side):
    out = []
    for raw in side.split("+"):
        raw = raw.strip()
        if not raw:
            continue
        m = re.match(r"^([0-9.eE+-]+)\s+(.+)$", raw)
        if m:
            out.append((canon(m.group(2).strip()), float(m.group(1))))
        else:
            out.append((canon(raw), 1.0))
    return out


def load_llnl(path):
    masters = {}
    reactions = []
    in_m = in_s = False
    cur = None
    for line in open(path, encoding="utf-8", errors="replace"):
        s = line.strip()
        if s == "SOLUTION_MASTER_SPECIES":
            in_m, in_s = True, False; continue
        if s == "SOLUTION_SPECIES":
            in_s, in_m = True, False; cur = None; continue
        if s in ("EXCHANGE_MASTER_SPECIES", "EXCHANGE_SPECIES", "SURFACE_MASTER_SPECIES",
                 "SURFACE_SPECIES", "PHASES", "RATES", "KNOBS", "END", "PRINT"):
            in_m = in_s = False; cur = None; continue
        if not s or s.startswith("#"):
            continue
        if in_m:
            p = s.split()
            if len(p) >= 2:
                masters[canon(p[1])] = True
            continue
        if in_s:
            if "=" in s:
                lhs, _, rhs = s.partition("=")
                lt, rt = parse_reaction(lhs), parse_reaction(rhs)
                if [t[0] for t in lt] == [t[0] for t in rt]:
                    cur = None; continue
                cur = (lt, rt, None); continue
            m = re.match(r"^-?\s*log_k\s+([^\s]+)", s, re.IGNORECASE)
            if m and cur is not None:
                cur = (cur[0], cur[1], float(m.group(1)))
                reactions.append(cur); cur = None
    if cur is not None:
        reactions.append(cur)
    return masters, reactions


def load_catalog_dg():
    """Parse ΔG_f° from InorganicIonCatalog.basis() and return {key: dg}."""
    src = os.path.join(os.path.dirname(__file__), "..", "src", "main", "java",
                       "com", "yu1745", "chemengine", "solver", "InorganicIonCatalog.java")
    dg = {}
    for line in open(src):
        m = re.search(r'\.basis\("([^"]+)",\s*([+-]?\d+),\s*([+-]?[\d.]+)', line)
        if m:
            dg[m.group(1)] = float(m.group(3))
    return dg


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--llnl", default="tools/llnl.dat")
    ap.add_argument("--out", default="build/inorganic_ions/llnl")
    args = ap.parse_args()

    masters, reactions = load_llnl(args.llnl)
    dg = load_catalog_dg()
    print(f"llnl.dat: {len(masters)} masters, {len(reactions)} reactions; catalog: {len(dg)} ions")

    # classify catalog ions vs llnl
    is_master = [k for k in dg if k in masters]
    is_llnl_species = set()
    for lt, rt, _ in reactions:
        for k, _ in lt + rt:
            is_llnl_species.add(k)

    # check each reaction whose species ALL in catalog -> reproduce logK
    check_ok, check_fail, skipped = 0, 0, 0
    fails = []
    for lt, rt, logk in reactions:
        if logk is None:
            skipped += 1; continue
        terms = [(k, c) for k, c in lt] + [(k, -c) for k, c in rt]
        if not all(k in dg for k, _ in terms):
            skipped += 1; continue
        s = sum(c * dg[k] for k, c in terms)
        lc = -s / RT_LN10
        if abs(lc - logk) < 0.5:  # logK tolerance ~0.5 (~3 kJ/mol)
            check_ok += 1
        else:
            check_fail += 1
            fails.append(([k for k, _ in terms], logk, lc))

    print(f"\ncatalog ions that are llnl MASTER species: {len(is_master)}")
    print(f"catalog ions present as llnl species (incl. non-master): "
          f"{sum(1 for k in dg if k in is_llnl_species)}")
    print(f"\nreactions fully within catalog: OK={check_ok} FAIL={check_fail} skipped={skipped}")
    for terms, logk, lc in fails[:20]:
        print(f"  MISMATCH {terms}: llnl logK={logk:.2f} vs ours={lc:.2f} (Δ={lc-logk:+.2f})")

    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "llnl_consistency.json"), "w") as fh:
        json.dump({
            "llnl_master_ions": is_master,
            "reactions_checked": check_ok + check_fail,
            "reactions_ok": check_ok,
            "reactions_fail": check_fail,
            "fails": [{"terms": t, "llnl_logk": l, "ours": c} for t, l, c in fails],
        }, fh, indent=2)
    print("\n[llnl_backbone] wrote", args.out)


if __name__ == "__main__":
    sys.exit(main())
