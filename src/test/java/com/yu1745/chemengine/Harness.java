package com.yu1745.chemengine;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Shared fixture: loads the copied mod species JSONs from the test classpath once. */
public final class Harness {

    private static Engine engine;

    private Harness() {}

    public static synchronized Engine engine() {
        if (engine == null) {
            try {
                Path dir = Paths.get(Harness.class.getResource("/species").toURI());
                engine = Engine.loadDirectory(dir);
            } catch (Exception e) {
                throw new IllegalStateException("failed to load species fixtures", e);
            }
        }
        return engine;
    }
}
