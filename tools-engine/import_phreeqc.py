#!/usr/bin/env python3
"""
Track A2: PHREEQC database import helper.

Parses the three reference databases we keep in tools/ and extracts
reaction / log_k / delta_h blocks into JSON for manual review:

  * phreeqc.dat    — generic reference database (reaction-led blocks, delta_h kcal)
  * llnl.dat       — internally consistent thermodynamic database
                     (blocks named by phase/species, "-delta_H [kJ/mol]")
  * minteq.v4.dat  — MINTEQA2 database (named blocks, "delta_h [kJ]")

Usage:
    python3 tools/import_phreeqc.py path/to/database.dat [--out mapping.json]

The output is NOT automatically merged into src/test/resources/species/*.json.
It is meant to produce a candidate mapping that a human/developer reviews.
"""

import argparse
import json
import re
import sys


def _parse_number(raw):
    """Parse a numeric literal possibly preceded by a sign."""
    if raw is None:
        return None
    try:
        return float(raw)
    except ValueError:
        m = re.match(r"^[+-]?\s*([0-9.eE+-]+)", raw)
        if m:
            try:
                return float(m.group(1))
            except ValueError:
                return None
    return None


def _to_kj(value, unit):
    """Convert a delta value to kJ/mol (kcal/mol -> kJ/mol)."""
    unit = (unit or "").lower()
    if "kcal" in unit:
        return value * 4.184
    if "cal" in unit and "kcal" not in unit:
        return value * 4.184 / 1000.0
    return value


def parse_database(path):
    """Very small PHREEQC database parser (phreeqc.dat style).

    Handles the common block style:

        reaction equation
        log_k  value
        delta_h value    kJ/mol
        ...

    Complex databases (llnl.dat / minteq.v4.dat) use parse_named_blocks instead.
    """
    entries = []
    current = None

    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#") or line.startswith("//"):
                continue

            # A new reaction block starts with a line containing '=' and no
            # leading keyword.
            if "=" in line and not line.lower().startswith(("log_k", "delta_h", "-log_k", "-delta_h")):
                if current:
                    entries.append(current)
                current = {"reaction": line, "log_k": None, "delta_h": None, "raw": []}
                continue

            if current is None:
                continue

            current["raw"].append(line)

            m = re.match(r"^-?\s*log_k\s+([^\s]+)", line, re.IGNORECASE)
            if m:
                v = _parse_number(m.group(1))
                if v is not None:
                    current["log_k"] = v

            m = re.match(r"^-?\s*delta_h\s+([^\s]+)\s*([a-zA-Z/]*)", line, re.IGNORECASE)
            if m:
                v = _parse_number(m.group(1))
                if v is not None:
                    current["delta_h"] = _to_kj(v, m.group(2))
                    current["delta_h_unit"] = "kJ/mol"

    if current:
        entries.append(current)

    return entries


def parse_named_blocks(path):
    """Parse llnl.dat / minteq.v4.dat / phreeqc.dat into reaction blocks.

    The three databases mix two block styles, handled uniformly here:

      * reaction-led (SOLUTION_SPECIES in all three): the reaction line sits at
        column 0 and its log_k / delta_h properties follow indented
        (e.g. "H2O = OH- + H+" / "NH4+ = NH3 + H+").
      * name-led (PHASES / EXCHANGE_SPECIES / SURFACE_SPECIES in llnl.minteq,
        PHASES in phreeqc): a bare block-name line is followed by the indented
        reaction and its properties (e.g. "Brucite" / "Mg(OH)2 +2H+ = ...").

    Master-species identity lines ("X = X") and RATES/KINETICS bodies are
    discarded. Returns a list of:

        {"name": <str|None>, "reaction": ..., "log_k": ..., "delta_h": kJ/mol}

    delta_h values are converted to kJ/mol when authored in kcal.
    """
    entries = []
    current = None
    pending_name = None
    name_re = re.compile(r"^[A-Za-z0-9][A-Za-z0-9()._+\-]*$")
    section_re = re.compile(
        r"^(SOLUTION_MASTER_SPECIES|SOLUTION_SPECIES|EXCHANGE_MASTER_SPECIES|"
        r"EXCHANGE_SPECIES|SURFACE_MASTER_SPECIES|SURFACE_SPECIES|PHASES|RATES|"
        r"KNOBS|END|PRINT|SELECTED_OUTPUT|SOLUTION|GAS_PHASE|KINETICS|"
        r"INCREMENTAL_REACTIONS|USER_PUNCH|USER_PRINT|DATABASE|TITLE|PITZER|SIT|"
        r"EXCHANGE|SURFACE|EQUILIBRIUM_PHASES|USE|SAVE|DELETE|DUMP|MIX|REACTION|"
        r"TEMPERATURE|ISOTOPES|ISOTOPE_RATES|ISOTOPE_ALPHAS|"
        r"LLNL_AQUEOUS_MODEL_PARAMETERS|NAMED_EXPRESSIONS|CALCULATE_VALUES)\b"
    )

    def close_block():
        nonlocal current, pending_name
        if current is not None:
            entries.append(current)
        current = None
        pending_name = None

    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            stripped = line.strip()
            if not stripped or stripped.startswith("#") or stripped.startswith("//"):
                continue

            # database section headers close any open block.
            if section_re.match(stripped):
                close_block()
                continue

            if "=" in stripped:
                # identity lines ("X = X") are master-species definitions, not
                # reactions; skip them and any open block state.
                left, _, right = stripped.partition("=")
                if left.strip() == right.strip():
                    close_block()
                    continue
                if line[0].isspace():
                    # indented reaction under a pending block name (name-led)
                    if current is None and pending_name is not None:
                        current = {"name": pending_name, "reaction": stripped,
                                   "log_k": None, "delta_h": None}
                        pending_name = None
                    # otherwise: an indented '=' property line (RATES bodies
                    # etc.) — ignored.
                    continue
                # column-0 reaction line: reaction-led block
                close_block()
                current = {"name": pending_name, "reaction": stripped,
                           "log_k": None, "delta_h": None}
                pending_name = None
                continue

            # bare block-name line (name-led style): closes the previous block.
            if not line[0].isspace() and name_re.match(stripped):
                close_block()
                pending_name = stripped
                continue

            if current is None:
                continue

            m = re.match(r"^-?\s*log_k\s+([^\s]+)", stripped, re.IGNORECASE)
            if m:
                v = _parse_number(m.group(1))
                if v is not None:
                    current["log_k"] = v
                continue

            m = re.match(r"^-?\s*delta_?h\s+([^\s]+)\s*([a-zA-Z/]*)", stripped, re.IGNORECASE)
            if m:
                v = _parse_number(m.group(1))
                if v is not None:
                    current["delta_h"] = _to_kj(v, m.group(2))
                continue

    if current is not None:
        entries.append(current)

    return [e for e in entries
            if e["reaction"] is not None
            and (e["log_k"] is not None or e["delta_h"] is not None)]


def load_database(path):
    """Load any of the three reference databases (format auto-detected).

    All three are handled by the unified parser; this function exists so the
    exact format choice stays in one place if it ever needs to diverge.
    """
    return parse_named_blocks(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", help="Path to PHREEQC database file")
    parser.add_argument("--out", default=None, help="Output JSON path")
    args = parser.parse_args()

    try:
        entries = load_database(args.database)
    except FileNotFoundError:
        print(f"error: file not found: {args.database}", file=sys.stderr)
        return 1

    result = {
        "source": args.database,
        "format": "named-blocks" if entries and "name" in entries[0] else "reaction-blocks",
        "count": len(entries),
        "entries": entries,
    }

    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            json.dump(result, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        print(f"wrote {len(entries)} entries to {args.out}")
    else:
        print(json.dumps(result, indent=2, ensure_ascii=False))

    return 0


if __name__ == "__main__":
    sys.exit(main())
