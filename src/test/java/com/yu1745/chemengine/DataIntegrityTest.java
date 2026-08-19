package com.yu1745.chemengine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yu1745.chemengine.solver.SystemModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Locks the PLAN.md Track A3/A4 completion invariant:
 * no heat_kj remains, no delta_h is left as a 0 placeholder, and every delta_h
 * carries the authoritative provenance annotations (delta_h_source /
 * delta_h_derivation) written by tools/apply_phreeqc_delta.py.
 *
 * <p>Additionally: no authored equilibrium may be silently dropped by the model's
 * leaf-elimination basis reduction. A dropped entry's log_k AND delta_h are dead data
 * (never applied by the solver), yet the delta_h would still pass the provenance
 * checks above — that combination is exactly the "test green but the data is inert"
 * trap this class exists to prevent (the ammonia hydrolysis entry was such a case).
 */
class DataIntegrityTest {

    @Test void allSpeciesUseOnlyDeltaHAndNoZeroPlaceholder() throws IOException {
        Path dir = Path.of("src/test/resources/species");
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path p : paths.filter(x -> x.toString().endsWith(".json")).toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                JsonArray eqs = root.has("equilibria") ? root.getAsJsonArray("equilibria") : new JsonArray();
                for (JsonElement e : eqs) {
                    JsonObject eq = e.getAsJsonObject();
                    assertFalse(eq.has("heat_kj"), "heat_kj should be gone: " + p);
                    assertTrue(eq.has("delta_h"), "delta_h missing: " + p);
                    double deltaH = eq.get("delta_h").getAsDouble();
                    assertFalse(deltaH == 0.0,
                        "delta_h should not be a zero placeholder: " + p);
                    assertTrue(Double.isFinite(deltaH), "delta_h not finite: " + p);
                    // value-band guard (independent-review finding: provenance checks
                    // verify provenance, not magnitude — a digit/scale typo like
                    // -9.61 -> -96.1 would pass the source annotations): every authored
                    // delta_h in the shipped data is within +-400 kJ/mol and every
                    // log_k within +-150. These are EXISTENCE guards against typo
                    // classes, not physical upper bounds: legitimately strong
                    // multi-electron redox couples (e.g. MnO4- + 8H+ + 5e- = Mn2+ +
                    // 4H2O, logK ~ 128; Cl2 + 2e- = 2Cl-, dH -334 kJ/mol) must stay
                    // inside the bands — widen them (or make them redox-aware) before
                    // adding such data, never silently.
                    assertTrue(Math.abs(deltaH) <= 400.0,
                        "delta_h outside plausible band (+-400 kJ/mol): " + p);
                    assertTrue(Double.isFinite(eq.get("log_k").getAsDouble())
                            && Math.abs(eq.get("log_k").getAsDouble()) <= 150.0,
                        "log_k outside plausible band (+-150): " + p);
                    assertTrue(eq.has("delta_h_source")
                            && !eq.get("delta_h_source").getAsString().isBlank(),
                        "delta_h_source missing/blank (run tools/apply_phreeqc_delta.py --apply): " + p);
                    assertTrue(eq.has("delta_h_derivation")
                            && !eq.get("delta_h_derivation").getAsString().isBlank(),
                        "delta_h_derivation missing/blank (run tools/apply_phreeqc_delta.py --apply): " + p);
                }
            }
        }
    }

    @Test void noEquilibriumIsDroppedAsRedundant() throws IOException {
        // A dropped equilibrium is linearly dependent on the surviving basis, so its
        // log_k / delta_h are never applied — keeping it in the data is misleading
        // (the provenance checks above would bless a dead delta_h).
        SystemModel model = Harness.engine().model();
        assertTrue(model.droppedEquilibria().isEmpty(),
            "equilibria dropped as redundant (dead data, remove from the species files): "
                + model.droppedEquilibria());
    }
}
