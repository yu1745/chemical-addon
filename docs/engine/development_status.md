# Development Status

`PLAN.md` is the guiding development plan. This file summarizes the current status.

## Completed

- PHREEQC-style master-species Newton solver
- `molarMass` and `delta_h` for all species
- Redox via `e-` pseudo-species and multi-couple electron conservation
- Common-ion salting-out
- Industrial scenarios: Solvay/Hou, SO3, HCl, NO2, causticisation,
  hydrometallurgy, nitrate reduction, oxygen oxidation
- Data integrity: no `heat_kj`, no `delta_h = 0`

## Tooling

- `tools/import_phreeqc.py` — parse PHREEQC database
- `tools/apply_phreeqc_delta.py` — apply known authoritative `delta_h`
- `tools/apply_phreeqc_delta.py --check` — verify data invariants
- `tools/phreeqc.dat` — reference PHREEQC database
- `tools/test_import_phreeqc.py` — parser self-test

## Known limitations

See `docs/known_limitations.md`.
