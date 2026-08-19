package com.yu1745.chemengine.industrial;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.Equilibrium;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Track C 蓝图完整性防线（防"类存在但内容空转"）：
 * <ul>
 *   <li>每个原料类的 NAME / PLUGIN_IDS / STEPS 契约齐全、STEPS 非空；</li>
 *   <li>每条步骤的引擎反应式（非 null 时）必须能被 {@link Equilibrium#parse} 解析
 *       ——语法错误立即暴露，而不是等到未来转数据时才炸；</li>
 *   <li>插件全部物种 id（除 thermal_oil 等非化学项）必须被至少一个原料类覆盖。</li>
 * </ul>
 */
class IndustrialProcessBlueprintTest {

    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> cls, String name) throws ReflectiveOperationException {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(null);
    }

    @Test void everyMaterialClassHonoursTheBlueprintContract() throws Exception {
        for (Class<?> cls : IndustrialProcesses.ALL) {
            String name = field(cls, "NAME");
            assertFalse(name == null || name.isBlank(), cls.getSimpleName() + ": NAME missing");
            List<String> ids = field(cls, "PLUGIN_IDS");
            List<ProcessStep> steps = field(cls, "STEPS");
            assertFalse(steps.isEmpty(), cls.getSimpleName() + " (" + name + "): STEPS empty");
            for (ProcessStep step : steps) {
                assertFalse(step.note() == null || step.note().isBlank(),
                    cls.getSimpleName() + " (" + name + "): step with empty note");
                if (step.reaction() != null) {
                    // syntax gate: must parse as a reaction (equilibrium entries via
                    // Equilibrium.parse restrict to one solid per side; forced net-reaction
                    // steps may have several solids per side, so use the unrestricted parser)
                    Equilibrium.parseReactionSides(step.reaction());
                }
            }
            assertTrue(ids != null, cls.getSimpleName() + ": PLUGIN_IDS missing");
        }
    }

    @Test void everyPluginSpeciesIsCoveredBySomeProcessClass() throws IOException, ReflectiveOperationException {
        Set<String> covered = new HashSet<>();
        for (Class<?> cls : IndustrialProcesses.ALL) {
            List<String> ids = field(cls, "PLUGIN_IDS");
            if (ids != null) covered.addAll(ids);
        }
        List<String> uncovered = new ArrayList<>();
        Path dir = Path.of("src/test/resources/species");
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path p : paths.filter(x -> x.toString().endsWith(".json")).toList()) {
                String id = p.getFileName().toString().replaceFirst("\\.json$", "");
                if (IndustrialProcesses.NOT_REQUIRED.contains(id)) continue;
                if (!covered.contains(id)) uncovered.add(id);
            }
        }
        assertTrue(uncovered.isEmpty(),
            "species without a process blueprint (add a class + register it in IndustrialProcesses, "
                + "or justify in NOT_REQUIRED): " + uncovered);
    }
}
