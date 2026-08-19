# PHREEQC reaction mapping (Track A0/A2)

This file tracks how current `species/*.json` equilibria map to PHREEQC-style
reactions. It is a manual mapping aid for the converter.

| Current reaction | PHREEQC-style reaction | Notes |
|---|---|---|
| `chemicaladdon:ammonia + water = NH4+1 + OH-1` | `NH3 + H2O = NH4+ + OH-` | NH3 gas/aqueous |
| `NH4+1 + water = chemicaladdon:ammonia + H+1` | `NH4+ = NH3 + H+` | reverse of above |
| `CO3-2 + water = HCO3-1 + OH-1` | `CO3-2 + H2O = HCO3- + OH-` | carbonate hydrolysis |
| `HCO3-1 = chemicaladdon:carbon_dioxide + OH-1` | `HCO3- = CO2 + OH-` | bicarbonate deprotonation |
| `limestone(s) = Ca+2 + CO3-2` | `CaCO3 = Ca+2 + CO3-2` | calcite/limestone |
| `gypsum(s) = Ca+2 + SO4-2` | `CaSO4 = Ca+2 + SO4-2` | may need hydrate form |
| `barium_sulfate(s) = Ba+2 + SO4-2` | `BaSO4 = Ba+2 + SO4-2` | barite |
| `silver_chloride(s) = Ag+1 + Cl-1` | `AgCl = Ag+ + Cl-` | |
| `Fe+3 + e- = Fe+2` | `Fe+3 + e- = Fe+2` | redox |
| `Cu+2 + e- = Cu+1` | `Cu+2 + e- = Cu+1` | redox |
