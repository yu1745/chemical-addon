# Architecture

## Core solver

- `SystemModel` — compiles species JSON into components, secondaries, minerals.
- `Solver` — master-species Newton solve, phase assembly, degas, projection, kinetics.
- `State` — integer aqueous/solid state.
- `Engine` — public facade.

## Redox

- `e-` is a pseudo-species/component.
- Redox half-reactions are normal equilibria, e.g. `Fe+3 + e- = Fe+2`.
- Closed-system redox uses multi-couple electron conservation (no external pe reservoir).

## Thermodynamics

- `delta_h` drives both Van't Hoff `K(T)` and `energyJ` / `heatRiseC`.
- `molarMass` converts kJ/mol to engine heat units.
- `tools/phreeqc.dat` is the authoritative reference for calibration.

## Tooling

- `tools/import_phreeqc.py` — parse PHREEQC database.
- `tools/apply_phreeqc_delta.py` — apply/check delta_h mappings.
- `Makefile` — `make test` / `make check`.
