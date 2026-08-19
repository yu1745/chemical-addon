# tools

## import_phreeqc.py

Track A2 helper for importing PHREEQC thermodynamic data.

Parses all three bundled reference databases (format auto-detected):

- `phreeqc.dat` — generic reference database (reaction-led blocks)
- `llnl.dat` — internally consistent thermodynamic database (`-delta_H`)
- `minteq.v4.dat` — MINTEQA2 database (named blocks)

The parser handles both block styles the databases mix (reaction-led
`SOLUTION_SPECIES` entries and name-led `PHASES`/`EXCHANGE_SPECIES` blocks),
converts `kcal` to `kJ`, and discards master-species identity lines and
RATES/KINETICS bodies.

```bash
python3 tools/import_phreeqc.py tools/llnl.dat --out mapping.json
```

## apply_phreeqc_delta.py

Applies authoritative `delta_h` values to `src/test/resources/species/*.json`.

Each mapping entry records the source database block and a conversion recipe:

- `direct` — our reaction matches the DB reaction verbatim (e.g. AgCl, Nahcolite)
- `acid:N` — DB acid-dissolution form converted via Hess with the water
  autoionisation enthalpy (e.g. Fe(OH)3, Portlandite)
- `acid:N+hco3` — plus bicarbonate dissociation (e.g. Magnesite, Malachite)
- `nist:V` — no database coverage; value from NIST/NBS formation enthalpies
- `estimated` — no authoritative value exists; value kept but tagged

Helper enthalpies are read live from `tools/phreeqc.dat`. Written equilibria
gain `delta_h_source` / `delta_h_derivation` fields for provenance.

```bash
python3 tools/apply_phreeqc_delta.py            # dry-run table
python3 tools/apply_phreeqc_delta.py --apply    # write species JSONs
python3 tools/apply_phreeqc_delta.py --check    # integrity check
```

## Reference databases

All three come from the official USGS PHREEQC repository `database/` folder:

- `phreeqc.dat` — generic reference database (Track A3 calibration base)
- `llnl.dat` — Lawrence Livermore thermodynamic database (primary supplement)
- `minteq.v4.dat` — MINTEQA2 database (Ag2CO3 / Cu(OH)2 / Ag(NH3)2+)

Coverage decisions per species are tracked in `docs/known_limitations.md`.
