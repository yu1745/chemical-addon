package com.yu1745.chemengine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.yu1745.chemengine.solver.Solver;
import com.yu1745.chemengine.solver.SystemModel;

/**
 * Public facade. Load a species database (the same JSON the mod ships), build the
 * compiled {@link SystemModel} once, then solve {@link State}s cheaply and repeatedly.
 *
 * <pre>
 *   Engine engine = Engine.load(Path.of("species"));
 *   Solver.Result r = engine.solve(openOrClosed, state);
 * </pre>
 */
public final class Engine {

    private final SpeciesDatabase db;
    private final SystemModel model;

    private Engine(SpeciesDatabase db) {
        this.db = db;
        this.model = new SystemModel(db);
    }

    public static Engine loadDirectory(Path dir) throws IOException {
        SpeciesDatabase db = new SpeciesDatabase().loadDirectory(dir);
        return new Engine(db);
    }

    public static Engine from(SpeciesDatabase db) {
        return new Engine(db);
    }

    public SystemModel model() { return model; }
    public SpeciesDatabase database() { return db; }

    public Solver.Result solveClosed(State state) {
        return Solver.solve(model, db, state, Solver.Vessel.CLOSED);
    }

    public Solver.Result solveOpen(State state) {
        return Solver.solve(model, db, state, Solver.Vessel.OPEN);
    }

    /**
     * Drive an electrolysis cell: advance the forced net reaction by {@code units}, then
     * re-equilibrate in an open vessel so produced gases (H2/Cl2/O2) evolve. The returned
     * result's {@code gasVented} records the gases that left the cell.
     */
    public Solver.Result electrolyze(State state, Electrolysis cell, long units) {
        return solveOpen(cell.advance(state, units));
    }

    /**
     * Generic forced net-reaction step (open vessel): advance {@code cell} by {@code units}
     * and re-equilibrate, letting product gases evolve. Electrolysis (D1b) is the canonical
     * case; high-temperature combustion/calcination net steps (D3) are the same mechanism —
     * the reaction is driven externally, so only stoichiometry is needed (no fabricated
     * equilibrium constant).
     */
    public Solver.Result react(State state, Electrolysis cell, long units) {
        return solveOpen(cell.advance(state, units));
    }
}
