package com.yu1745.chemengine;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A chemical species definition (parsed from the same datapack JSON the mod ships).
 * Only the fields the engine consumes are kept: phase, dissociation ions,
 * equilibria and gas solubility. Species ids are plain strings ("chemicaladdon:ammonia",
 * short names default to the mod namespace).
 */
public final class Species {

    public enum Phase { GAS, LIQUID, SOLID }

    /** One ion in a dissociation: symbol, charge and multiplicity. */
    public static final class IonComponent {
        private final String symbol;
        private final int charge;
        private final int count;

        public IonComponent(String symbol, int charge, int count) {
            this.symbol = symbol;
            this.charge = charge;
            this.count = count;
        }

        public String ionId() { return new Ion(symbol, charge).id(); }
        public int charge() { return charge; }
        public int count() { return count; }
    }

    private final String id;
    private final Phase phase;
    private final List<IonComponent> ions;
    private final List<Equilibrium> equilibria;
    private final double gasSolubility;   // NaN = use the engine default
    private final List<SolubilityPoint> solubility; // g solute / 100 g water vs tempC
    private final String solute;          // solid species this curve crystallises into (null if none)
    private final int solventRatio;
    private final double molarMass;       // g/mol, NaN if unknown

    private Species(String id, Phase phase, List<IonComponent> ions,
                    List<Equilibrium> equilibria, double gasSolubility,
                    List<SolubilityPoint> solubility, String solute, int solventRatio,
                    double molarMass) {
        this.id = id;
        this.phase = phase;
        this.ions = List.copyOf(ions);
        this.equilibria = List.copyOf(equilibria);
        this.gasSolubility = gasSolubility;
        this.solubility = List.copyOf(solubility);
        this.solute = solute;
        this.solventRatio = solventRatio;
        this.molarMass = molarMass;
    }

    /** One point of a solubility curve (g solute per 100 g water at tempC). */
    public static final class SolubilityPoint {
        public final int tempC;
        public final double gPer100g;

        public SolubilityPoint(int tempC, double gPer100g) {
            this.tempC = tempC;
            this.gPer100g = gPer100g;
        }
    }

    public String id() { return id; }
    public Phase phase() { return phase; }
    public List<IonComponent> ions() { return ions; }
    public List<Equilibrium> equilibria() { return equilibria; }
    public double gasSolubility() { return gasSolubility; }
    public boolean isGas() { return phase == Phase.GAS; }
    public List<SolubilityPoint> solubility() { return solubility; }
    public String solute() { return solute; }
    public int solventRatio() { return solventRatio; }
    public double molarMass() { return molarMass; }
    public boolean isCrystallisable() { return solute != null && !solubility.isEmpty(); }

    /** Linear interpolation of the solubility curve at tempC (clamped to the table ends). */
    public double solubilityAt(int tempC) {
        if (solubility.size() == 1) return solubility.get(0).gPer100g;
        SolubilityPoint lo = solubility.get(0), hi = solubility.get(solubility.size() - 1);
        for (SolubilityPoint p : solubility) {
            if (p.tempC <= tempC) lo = p;
            if (p.tempC >= tempC) { hi = p; break; }
        }
        if (lo == hi) return lo.gPer100g;
        double f = (double) (tempC - lo.tempC) / (hi.tempC - lo.tempC);
        return lo.gPer100g + f * (hi.gPer100g - lo.gPer100g);
    }

    /** Parse one species JSON. Returns null (after logging) for structurally broken files. */
    public static Species parse(String id, JsonObject o) {
        Phase phase;
        try {
            phase = Phase.valueOf(getString(o, "phase", "LIQUID").toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("[chemengine] species " + id + ": bad phase");
            return null;
        }
        List<IonComponent> ions = new ArrayList<>();
        if (o.has("ions")) {
            for (JsonElement e : o.getAsJsonArray("ions")) {
                JsonObject c = e.getAsJsonObject();
                ions.add(new IonComponent(c.get("ion").getAsString(), c.get("charge").getAsInt(),
                    getInt(c, "count", 1)));
            }
        }
        double molarMass = getDouble(o, "molarMass", Double.NaN);
        List<Equilibrium> eqs = new ArrayList<>();
        if (o.has("equilibria")) {
            for (JsonElement e : o.getAsJsonArray("equilibria")) {
                JsonObject c = e.getAsJsonObject();
                double logK = c.get("log_k").getAsDouble();
                double deltaH = getDouble(c, "delta_h", Double.NaN);
                double heatKJ = getDouble(c, "heat_kj", Double.NaN);
                double rate = getDouble(c, "rate", 0.0);
                eqs.add(Equilibrium.parse(c.get("reaction").getAsString(), logK, deltaH, heatKJ, molarMass, rate));
            }
        }
        double gasSolubility = getDouble(o, "gasSolubility", Double.NaN);
        List<SolubilityPoint> solubility = new ArrayList<>();
        if (o.has("solubility")) {
            for (JsonElement e : o.getAsJsonArray("solubility")) {
                JsonObject c = e.getAsJsonObject();
                solubility.add(new SolubilityPoint(getInt(c, "tempC", 0), getDouble(c, "gPer100g", 0)));
            }
        }
        String solute = null;
        if (o.has("solute")) solute = o.get("solute").getAsString();
        int solventRatio = getInt(o, "solventRatio", 10);
        return new Species(id, phase, ions, eqs, gasSolubility, solubility, solute, solventRatio, molarMass);
    }

    private static String getString(JsonObject o, String key, String def) {
        return o.has(key) ? o.get(key).getAsString() : def;
    }

    private static int getInt(JsonObject o, String key, int def) {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }

    private static double getDouble(JsonObject o, String key, double def) {
        return o.has(key) ? o.get(key).getAsDouble() : def;
    }

    @Override public String toString() { return id; }
}
