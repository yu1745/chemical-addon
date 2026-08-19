#!/usr/bin/env python3
"""Minimal self-test for import_phreeqc.py using the bundled sample database."""

import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(__file__))
from import_phreeqc import parse_database

SAMPLE = os.path.join(os.path.dirname(__file__), "sample_phreeqc.dat")


def main():
    entries = parse_database(SAMPLE)
    assert len(entries) == 5, f"expected 5 entries, got {len(entries)}"
    by_reaction = {e["reaction"]: e for e in entries}

    assert "Fe+3 + e- = Fe+2" in by_reaction
    assert by_reaction["Fe+3 + e- = Fe+2"]["log_k"] == 13.0
    assert by_reaction["Fe+3 + e- = Fe+2"]["delta_h"] == 40.0

    assert "Cu+2 + e- = Cu+1" in by_reaction
    assert by_reaction["Cu+2 + e- = Cu+1"]["delta_h"] == -20.0

    print("ok: import_phreeqc.py parsed sample_phreeqc.dat")
    return 0


if __name__ == "__main__":
    sys.exit(main())
