package com.yu1745.chemengine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The integer state the engine solves on and writes back: one miscible aqueous phase's
 * ion multiset + molecular species + two solid domains, all in whole "units".
 * {@code water} lives in {@code molecules} and is the solvent (its amount is the volume V).
 */
public final class State {

    public static final String WATER = "water";

    /**
     * Internal fine grid: 1 mB of water = 10^7 quanta, so the solver resolves
     * concentrations down to 10^-7 (the weakest modelled hydrolysis). The transport /
     * display layer (FluidStack mB) stays coarse; the ratio-tag identity lives here.
     */
    public static final long QUANTA_PER_MB = 10_000_000L;

    /** Convert a transport-scale mB amount to solver quanta. */
    public static long mb(long mB) { return mB * QUANTA_PER_MB; }

    private final Map<String, Long> ions = new LinkedHashMap<>();
    private final Map<String, Long> molecules = new LinkedHashMap<>();
    private final Map<String, Long> suspended = new LinkedHashMap<>();
    private final Map<String, Long> sediment = new LinkedHashMap<>();
    private final int temperatureC;
    private double stirring = 1.0; // mass-transfer coefficient 0.3..1.0 (kinetic rates scale with it)

    public State(int temperatureC) {
        this.temperatureC = temperatureC;
    }

    public State ions(String id, long amount) { if (amount > 0) ions.put(id, amount); return this; }
    public State molecule(String id, long amount) { if (amount > 0) molecules.put(id, amount); return this; }
    public State water(long amount) { molecule(WATER, amount); return this; }
    public State suspended(String id, long amount) { if (amount > 0) suspended.put(id, amount); return this; }
    public State sediment(String id, long amount) { if (amount > 0) sediment.put(id, amount); return this; }
    public State ions(Map<String, Long> m) { m.forEach(this::ions); return this; }
    public State molecules(Map<String, Long> m) { m.forEach(this::molecule); return this; }
    public State suspended(Map<String, Long> m) { m.forEach(this::suspended); return this; }
    public State sediment(Map<String, Long> m) { m.forEach(this::sediment); return this; }

    public Map<String, Long> ions() { return new LinkedHashMap<>(ions); }
    public Map<String, Long> molecules() { return new LinkedHashMap<>(molecules); }
    public Map<String, Long> suspended() { return new LinkedHashMap<>(suspended); }
    public Map<String, Long> sediment() { return new LinkedHashMap<>(sediment); }
    public int temperatureC() { return temperatureC; }
    public double stirring() { return stirring; }
    public State stirring(double coefficient) {
        this.stirring = Math.max(0.1, Math.min(2.0, coefficient));
        return this;
    }

    /** Total mass in all four domains (the heat-capacity basis for dT = Q/(m*c)). */
    public long totalUnits() {
        long n = 0;
        for (long v : ions.values()) n += v;
        for (long v : molecules.values()) n += v;
        for (long v : suspended.values()) n += v;
        for (long v : sediment.values()) n += v;
        return n;
    }

    /** Mutable builder for test convenience (e.g. sequential process steps). */
    public static final class Builder {
        public java.util.Map<String, Long> ions = new java.util.LinkedHashMap<>();
        public java.util.Map<String, Long> molecules = new java.util.LinkedHashMap<>();

        public Builder ions(String id, long amount) { ions.merge(id, amount, Long::sum); return this; }
        public Builder molecule(String id, long amount) { molecules.merge(id, amount, Long::sum); return this; }
        public Builder water(long amount) { molecule(WATER, amount); return this; }

        public State build() {
            State s = new State(20);
            ions.forEach(s::ions);
            molecules.forEach(s::molecule);
            return s;
        }
    }

    public long waterAmount() { return molecules.getOrDefault(WATER, 0L); }

    // ------------------------------------------------ engine-side mutators (kinetic tick)

    public long ionAmount(String id) { return ions.getOrDefault(id, 0L); }
    public long moleculeAmount(String id) { return molecules.getOrDefault(id, 0L); }
    public long suspendedAmount(String id) { return suspended.getOrDefault(id, 0L); }
    public long sedimentAmount(String id) { return sediment.getOrDefault(id, 0L); }

    public void adjustIon(String id, long delta) { adjust(ions, id, delta); }
    public void adjustMolecule(String id, long delta) { adjust(molecules, id, delta); }
    public void adjustSuspended(String id, long delta) { adjust(suspended, id, delta); }
    public void adjustSediment(String id, long delta) { adjust(sediment, id, delta); }

    /** Open-vessel evaporation: remove up to {@code units} of water, return the vented amount. */
    public long evaporateWater(long units) {
        long w = molecules.getOrDefault(WATER, 0L);
        long vented = Math.min(units, w);
        if (vented > 0) {
            adjust(molecules, WATER, -vented);
        }
        return vented;
    }

    private static void adjust(Map<String, Long> map, String id, long delta) {
        long v = map.getOrDefault(id, 0L) + delta;
        if (v <= 0) map.remove(id); else map.put(id, v);
    }

    /** Net charge of the ion multiset (the mixture's hard invariant). */
    public long netCharge() {
        long q = 0;
        for (Map.Entry<String, Long> e : ions.entrySet()) q += (long) Ion.chargeOf(e.getKey()) * e.getValue();
        return q;
    }

    @Override public String toString() {
        return "State{ions=" + ions + ", molecules=" + molecules
            + ", suspended=" + suspended + ", sediment=" + sediment + "}";
    }
}
