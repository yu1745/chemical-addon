package com.yu1745.chemengine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A registry of {@link Species} plus the derived equilibrium list. Data is fed by the
 * caller — the library itself ships no data, so the mod keeps its JSON as the single
 * source of truth. For standalone tests, the mod's species JSON files are copied into
 * the test classpath.
 */
public final class SpeciesDatabase {

    private final Map<String, Species> registry = new LinkedHashMap<>();

    public SpeciesDatabase() {}

    public SpeciesDatabase register(Species species) {
        registry.put(species.id(), species);
        return this;
    }

    public Species get(String id) { return registry.get(id); }
    public Collection<Species> all() { return registry.values(); }

    /** Load every *.json file in a directory (ids = file name without extension). */
    public SpeciesDatabase loadDirectory(Path dir) throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String id = p.getFileName().toString().replaceFirst("\\.json$", "");
                loadOne(id, Files.newInputStream(p));
            }
        }
        return this;
    }

    /** Load every *.json resource under a classpath prefix (e.g. "/species"). */
    public SpeciesDatabase loadClasspath(String prefix) throws IOException {
        var stream = SpeciesDatabase.class.getResourceAsStream(prefix + "/");
        if (stream != null) {
            // not enumerable as a directory; fall through to the jar listing is overkill —
            // tests use loadDirectory. This branch exists for the mod integration path.
            stream.close();
        }
        throw new UnsupportedOperationException("classpath directory listing not supported; use loadDirectory");
    }

    private void loadOne(String id, InputStream in) {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject o = new Gson().fromJson(r, JsonObject.class);
            Species s = Species.parse(id, o);
            if (s != null) registry.put(id, s);
        } catch (IOException e) {
            System.err.println("[chemengine] failed to read species " + id + ": " + e);
        }
    }

    /** All equilibria across every species, plus the implicit water autoionisation entry. */
    public List<Equilibrium> allEquilibria() {
        List<Equilibrium> out = new ArrayList<>();
        for (Species s : registry.values()) out.addAll(s.equilibria());
        // H2O = H+1 + OH-1, Kw = 1e-14 (solvent on the left is unit activity).
        // Kw carries its own Van't Hoff enthalpy: -55.91 kJ/mol as written in the
        // FORMATION direction (H+ + OH- -> H2O); the leaf elimination of OH- (-H+)
        // then flips it to the dissociation enthalpy +55.91 for the secondary's
        // effective component-space mass action (SystemModel's exprDeltaH algebra),
        // so Kw_diss(T) rises with temperature as in reality (pKw 14.9 @0C, 13.3 @50C).
        // Phreeqc.dat "H2O = OH- + H+" carries +55.9066 kJ/mol — the same value the
        // tools/apply_phreeqc_delta.py "+ autoion" Hess conversions use. Without this
        // entry the net NH3 hydrolysis scaled as -52 kJ/mol (exothermic, inverted)
        // instead of the real +3.6 kJ/mol (endothermic) — see PLAN.md Track F1.
        // Note: surviving products-side hydrolysis entries (soda_ash) bake the same
        // +55.91 into their delta_h; that is the TRUE written-reaction enthalpy and is
        // NOT double-counted (the expression algebra folds the Kw contribution out).
        out.add(Equilibrium.parse("H+1 + OH-1 = water", 14.0, -55.91, 0.0));
        return out;
    }
}
