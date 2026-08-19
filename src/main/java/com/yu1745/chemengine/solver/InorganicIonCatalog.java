package com.yu1745.chemengine.solver;

import java.util.Map;
import java.util.Set;

/**
 * Inorganic-ion catalog (Track B/E): inorganic-chemistry ions for the free-energy engine,
 * excluding rare-earth smelting/separation.
 *
 * <p>Each ion is defined by charge + ΔG_f°(kJ/mol, aq, 25 C) + elemental composition, so its
 * equilibrium constants (with other species/solids) are DERIVED from ΔG_f° — no reaction
 * strings, and gas-liquid equilibria drop out of the same free-energy machinery.
 *
 * <p>Track E (small basis): {@link #masterKeys()} selects the small master-ion basis — one
 * dominant ion per element plus H+ and the electron pseudo-ion (ΔG_f° = 0, NBS reference) —
 * and {@link #secondaries()} turns every other catalog ion into a DERIVED secondary species
 * (protonated forms, redox couples, complexes, oxyacids), so acid-base/redox/complexation
 * equilibria actually enter the solve instead of being independent conserved quantities.
 * {@link #basis()} keeps the legacy full master-ion basis (all 96 ions as components) for the
 * tooling (data-source audit scripts) and for cross-checking.
 *
 * <p>ΔG_f° values are standard aqueous formation free energies. Values marked {@code ~} are
 * best-known estimates that should be refined against NIST/JANAF before production use.
 */
public final class InorganicIonCatalog {

    private InorganicIonCatalog() {}

    /** The electron pseudo-ion key: NBS reference state, ΔG_f°(e-,aq) = 0 (same convention as
     *  ΔG_f°(H+,aq) = 0). As a BASIS ion it is the conserved electron pool of the input; every
     *  non-master-oxidation-state ion carries an implicit electron coefficient. */
    public static final String ELECTRON = "e-";

    /** The Track E master keys: one dominant ion per element, plus H+ (charge anchor) and the
     *  electron pool. Every OTHER catalog ion becomes a secondary species derived from these. */
    public static Set<String> masterKeys() {
        return Set.of(
            // charge anchor + redox pool
            "H+1", ELECTRON,
            // alkali / noble-metal monovalents
            "Li+1", "Na+1", "K+1", "Rb+1", "Cs+1", "Ag+1", "Au+1",
            // divalent metals
            "Be+2", "Mg+2", "Ca+2", "Sr+2", "Ba+2", "Zn+2", "Cd+2", "Hg+2", "Pb+2", "Sn+2",
            "Cu+2", "Fe+2", "Co+2", "Ni+2", "Mn+2",
            // trivalent / high-valence metals
            "Cr+3", "Al+3", "Bi+3", "Tl+3", "As+3", "Sb+3", "Ti+4", "Zr+4", "Pt+2",
            // oxyanion / oxycation metal masters (the element's dominant aqueous form)
            "MoO4-2", "WO4-2", "VO3-1", "UO2+2", "SeO4-2", "SiO3-2", "BO3-3",
            // halides
            "F-1", "Cl-1", "Br-1", "I-1",
            // non-metal oxyacid masters (dominant oxidation state)
            "SO4-2", "CO3-2", "NO3-1", "PO4-3");
    }

    /** Track E small master-ion basis: {@link #masterKeys()} ions as components. */
    public static FreeEnergyDatabase masters() {
        FreeEnergyDatabase db = new FreeEnergyDatabase();
        Set<String> mk = masterKeys();
        for (FreeEnergyDatabase.IonSpec sp : basis().basis().values()) {
            if (mk.contains(sp.key)) db.basis(sp);
        }
        if (db.basis().get(ELECTRON) == null) db.basis(ELECTRON, -1, 0.0);
        return db;
    }

    /** Track E derived secondaries: every catalog ion outside {@link #masterKeys()}. */
    public static FreeEnergyDatabase secondaries() {
        FreeEnergyDatabase db = new FreeEnergyDatabase();
        Set<String> mk = masterKeys();
        for (FreeEnergyDatabase.IonSpec sp : basis().basis().values()) {
            if (mk.contains(sp.key)) continue;
            db.species(sp.key, sp.charge, sp.dGfKj, elementsToObjects(sp.elements));
        }
        return db;
    }

    /** Full Track E model database: small master basis + all other ions as derived species
     *  (plus whatever solids the caller appends with {@code .solid(...)}). */
    public static FreeEnergyDatabase database() {
        FreeEnergyDatabase db = masters();
        for (FreeEnergyDatabase.SpeciesSpec sp : secondaries().species()) {
            db.species(sp.key, sp.charge, sp.dGfKj, elementsToObjects(sp.elements));
        }
        return db;
    }

    private static Object[] elementsToObjects(Map<String, Integer> els) {
        Object[] out = new Object[els.size() * 2];
        int i = 0;
        for (Map.Entry<String, Integer> e : els.entrySet()) { out[i++] = e.getKey(); out[i++] = e.getValue(); }
        return out;
    }

    /** Build the legacy full master-ion basis: existing 18 ions + the inorganic-chemistry expansion. */
    public static FreeEnergyDatabase basis() {
        return new FreeEnergyDatabase()
            // ---- existing ions ----
            .basis("Ag+1", +1, -77.1, "Ag", 1)
            .basis("Al+3", +3, -485.0, "Al", 1)
            .basis("Ca+2", +2, -553.6, "Ca", 1)
            .basis("Cu+2", +2, +64.8, "Cu", 1)
            .basis("Fe+2", +2, -78.9, "Fe", 1)
            .basis("Fe+3", +3, -4.6, "Fe", 1)
            .basis("H+1", +1, 0.0, "H", 1)
            .basis("K+1", +1, -282.3, "K", 1)
            .basis("Mg+2", +2, -454.8, "Mg", 1)
            .basis("Na+1", +1, -261.9, "Na", 1)
            .basis("Zn+2", +2, -147.1, "Zn", 1)
            .basis("NH4+1", +1, -79.3, "N", 1, "H", 4)
            .basis("Cl-1", -1, -131.2, "Cl", 1)
            .basis("CO3-2", -2, -527.8, "C", 1, "O", 3)
            .basis("NO3-1", -1, -111.3, "N", 1, "O", 3)
            .basis("OH-1", -1, -157.2, "O", 1, "H", 1)
            .basis("SO4-2", -2, -744.6, "S", 1, "O", 4)
            .basis("SCN-1", -1, +92.7, "S", 1, "C", 1, "N", 1)
            // ---- A. alkali / monovalent ----
            .basis("Li+1", +1, -293.3, "Li", 1)
            .basis("Rb+1", +1, -284.0, "Rb", 1)
            .basis("Cs+1", +1, -291.6, "Cs", 1)
            // ---- B. divalent metals ----
            .basis("Be+2", +2, -379.3, "Be", 1)
            .basis("Sr+2", +2, -559.5, "Sr", 1)
            .basis("Ba+2", +2, -560.8, "Ba", 1)
            .basis("Mn+2", +2, -228.1, "Mn", 1)
            .basis("Co+2", +2, -54.4, "Co", 1)
            .basis("Ni+2", +2, -45.6, "Ni", 1)
            .basis("Cu+1", +1, +50.0, "Cu", 1)
            .basis("Cd+2", +2, -77.6, "Cd", 1)
            .basis("Hg+2", +2, +164.4, "Hg", 1)
            .basis("Pb+2", +2, -24.4, "Pb", 1)
            .basis("Sn+2", +2, -27.2, "Sn", 1)
            // ---- C. trivalent metals ----
            .basis("Cr+3", +3, -215.5, "Cr", 1)         // derived
            .basis("Co+3", +3, +134.0, "Co", 1)         // derived
            .basis("Au+3", +3, +433.0, "Au", 1)         // derived
            .basis("Bi+3", +3, +82.8, "Bi", 1)
            .basis("Tl+3", +3, +209.0, "Tl", 1)         // derived
            .basis("As+3", +3, -0.4, "As", 1)
            .basis("Sb+3", +3, +80.0, "Sb", 1)          // derived
            // ---- D. high-valence cations ----
            .basis("Ti+4", +4, -295.0, "Ti", 1)         // derived
            .basis("Zr+4", +4, -480.0, "Zr", 1)         // derived
            .basis("Sn+4", +4, +5.8, "Sn", 1)           // derived
            .basis("Pb+4", +4, +280.0, "Pb", 1)         // derived
            .basis("Pt+4", +4, +280.0, "Pt", 1)         // derived
            .basis("Au+1", +1, +163.2, "Au", 1)
            .basis("Pt+2", +2, +177.0, "Pt", 1)         // derived
            .basis("Hg2+2", +2, +153.5, "Hg", 2)
            // ---- E. halide anions ----
            .basis("F-1", -1, -278.8, "F", 1)
            .basis("Br-1", -1, -104.0, "Br", 1)
            .basis("I-1", -1, -51.6, "I", 1)
            // ---- F. simple non-metal anions ----
            .basis("S-2", -2, +85.8, "S", 1)
            .basis("CN-1", -1, +172.4, "C", 1, "N", 1)
            .basis("N3-1", -1, +320.0, "N", 3)
            // ---- G. oxo-acid anions ----
            .basis("HCO3-1", -1, -586.8, "H", 1, "C", 1, "O", 3)
            .basis("NO2-1", -1, -37.2, "N", 1, "O", 2)
            .basis("HSO4-1", -1, -756.0, "H", 1, "S", 1, "O", 4)
            .basis("SO3-2", -2, -486.5, "S", 1, "O", 3)
            .basis("HSO3-1", -1, -527.7, "H", 1, "S", 1, "O", 3)
            .basis("S2O3-2", -2, -522.6, "S", 2, "O", 3)
            .basis("PO4-3", -3, -1018.8, "P", 1, "O", 4)
            .basis("HPO4-2", -2, -1089.2, "H", 1, "P", 1, "O", 4)
            .basis("H2PO4-1", -1, -1130.4, "H", 2, "P", 1, "O", 4)
            .basis("H2PO2-1", -1, -460.0, "H", 2, "P", 1, "O", 2)
            .basis("ClO-1", -1, -36.8, "Cl", 1, "O", 1)
            .basis("ClO2-1", -1, +17.2, "Cl", 1, "O", 2)
            .basis("ClO3-1", -1, -3.3, "Cl", 1, "O", 3)
            .basis("ClO4-1", -1, -8.6, "Cl", 1, "O", 4)
            .basis("BrO3-1", -1, +18.6, "Br", 1, "O", 3)
            .basis("IO3-1", -1, -126.0, "I", 1, "O", 3)
             .basis("CrO4-2", -2, -770.4, "Cr", 1, "O", 4)
            .basis("Cr2O7-2", -2, -1301.1, "Cr", 2, "O", 7)
             .basis("MnO4-1", -1, -448.2, "Mn", 1, "O", 4)
            .basis("MnO4-2", -2, -503.8, "Mn", 1, "O", 4)
            .basis("AsO4-3", -3, -648.4, "As", 1, "O", 4)
            .basis("AsO2-1", -1, -282.2, "As", 1, "O", 2)
            .basis("SeO4-2", -2, -441.4, "Se", 1, "O", 4)
            .basis("SiO3-2", -2, -938.0, "Si", 1, "O", 3)
            .basis("BO3-3", -3, -920.0, "B", 1, "O", 3)
            .basis("B4O7-2", -2, -3370.0, "B", 4, "O", 7)
            .basis("MoO4-2", -2, -836.0, "Mo", 1, "O", 4)
            .basis("WO4-2", -2, -920.0, "W", 1, "O", 4)
            .basis("VO3-1", -1, -889.0, "V", 1, "O", 3)
            // ---- H. amphoteric / coordination ions ----
             .basis("Al(OH)4-1", -1, -1304.0, "Al", 1, "O", 4, "H", 4)
             .basis("Zn(OH)4-2", -2, -864.6, "Zn", 1, "O", 4, "H", 4)
            .basis("Pb(OH)4-2", -2, -950.0, "Pb", 1, "O", 4, "H", 4)   // derived
            .basis("Sn(OH)6-2", -2, -1350.0, "Sn", 1, "O", 6, "H", 6)  // derived
            .basis("Sb(OH)6-1", -1, -1500.0, "Sb", 1, "O", 6, "H", 6)  // derived
             .basis("Cu(NH3)4+2", +2, -113.1, "Cu", 1, "N", 4, "H", 12)  // derived logbeta4
             .basis("Ag(NH3)2+1", +1, -171.4, "Ag", 1, "N", 2, "H", 6)   // derived logbeta2
             .basis("Fe(CN)6-4", -4, 755.8, "Fe", 1, "C", 6, "N", 6)    // derived logbeta6(FeII-CN)
             .basis("Fe(CN)6-3", -3, 784.4, "Fe", 1, "C", 6, "N", 6)    // derived logbeta6(FeIII-CN)
             .basis("Au(CN)2-1", -1, 285.4, "Au", 1, "C", 2, "N", 2)    // derived
             .basis("Zn(CN)4-2", -2, 439.7, "Zn", 1, "C", 4, "N", 4)    // derived
             .basis("Hg(CN)4-2", -2, 620.0, "Hg", 1, "C", 4, "N", 4)    // derived
            // ---- I. oxycations (industrial) ----
            .basis("UO2+2", +2, -952.5, "U", 1, "O", 2)
            .basis("VO+2", +2, -446.4, "V", 1, "O", 1);
    }
}

