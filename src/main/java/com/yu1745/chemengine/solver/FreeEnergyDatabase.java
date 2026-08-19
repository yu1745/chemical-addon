package com.yu1745.chemengine.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Free-energy-driven chemistry database for the Track B prototype.
 *
 * <p>Instead of authoring balanced chemical-formula reaction strings, every aqueous species
 * and every candidate solid is defined ONLY by its elemental composition and its standard
 * Gibbs free energy of formation ΔG_f° (kJ/mol). The master-ion basis supplies the
 * components. {@link SystemModel#fromFreeEnergy} then derives:
 *
 * <ul>
 *   <li>each species' component coefficient vector by solving element + charge balance, and</li>
 *   <li>each equilibrium constant from ΔG_f° via {@code logK = -ΔG°rxn/(RT·ln10)}.</li>
 * </ul>
 *
 * <p>Solids are candidate precipitates whose Ksp comes out of ΔG_f°; whether they actually
 * precipitate is decided by the solver's phase-assemblage from the saturation index — the
 * product is never hand-picked by a formula, it <b>emerges</b> from the thermodynamics.
 */
public final class FreeEnergyDatabase {

    /** One master ion: identity, charge, ΔG_f° (kJ/mol), elemental composition. */
    public static final class IonSpec {
        public final String key;             // e.g. "Ca+2"
        public final int charge;
        public final double dGfKj;
        public final Map<String, Integer> elements;

        public IonSpec(String key, int charge, double dGfKj, Map<String, Integer> elements) {
            this.key = key;
            this.charge = charge;
            this.dGfKj = dGfKj;
            this.elements = Collections.unmodifiableMap(new LinkedHashMap<>(elements));
        }
    }

    /** An aqueous secondary species, expressed over the master ions by element+charge balance. */
    public static final class SpeciesSpec {
        public final String key;             // e.g. "HCO3-1"
        public final int charge;
        public final double dGfKj;
        public final Map<String, Integer> elements;

        public SpeciesSpec(String key, int charge, double dGfKj, Map<String, Integer> elements) {
            this.key = key;
            this.charge = charge;
            this.dGfKj = dGfKj;
            this.elements = Collections.unmodifiableMap(new LinkedHashMap<>(elements));
        }
    }

    /** A candidate precipitate: elemental composition + ΔG_f°. Charge is zero. */
    public static final class SolidSpec {
        public final String key;
        public final double dGfKj;
        public final Map<String, Integer> elements;

        public SolidSpec(String key, double dGfKj, Map<String, Integer> elements) {
            this.key = key;
            this.dGfKj = dGfKj;
            this.elements = Collections.unmodifiableMap(new LinkedHashMap<>(elements));
        }
    }

    private final Map<String, IonSpec> basis = new LinkedHashMap<>();
    private final List<SpeciesSpec> species = new ArrayList<>();
    private final List<SolidSpec> solids = new ArrayList<>();

    public FreeEnergyDatabase basis(IonSpec ion) { basis.put(ion.key, ion); return this; }
    public FreeEnergyDatabase basis(String key, int charge, double dGfKj, Object... elementCounts) {
        basis.put(key, new IonSpec(key, charge, dGfKj, mapOf(elementCounts)));
        return this;
    }

    public FreeEnergyDatabase species(String key, int charge, double dGfKj, Object... elementCounts) {
        species.add(new SpeciesSpec(key, charge, dGfKj, mapOf(elementCounts)));
        return this;
    }

    public FreeEnergyDatabase solid(String key, double dGfKj, Object... elementCounts) {
        solids.add(new SolidSpec(key, dGfKj, mapOf(elementCounts)));
        return this;
    }

    public Map<String, IonSpec> basis() { return basis; }
    public List<SpeciesSpec> species() { return species; }
    public List<SolidSpec> solids() { return solids; }

    /** {@code "Ca", 1, "C", 1, "O", 3, ...} -> {Ca:1, C:1, O:3}. */
    private static Map<String, Integer> mapOf(Object... ec) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < ec.length; i += 2) m.put((String) ec[i], ((Number) ec[i + 1]).intValue());
        return m;
    }
}
