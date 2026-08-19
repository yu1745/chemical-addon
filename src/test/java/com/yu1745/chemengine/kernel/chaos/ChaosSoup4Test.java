package com.yu1745.chemengine.kernel.chaos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 场景4：磷氟锅 Ca/Mg/Ba/Al + P/F/S/B/Cl（无碳） */
class ChaosSoup4Test extends ChaosBase {

    @Test
    @DisplayName("混沌锅4：磷氟（Ca Mg Ba Al × P F S B Cl，无碳），声明 Hydroxyapatite/Fluorite/Barite/Gibbsite")
    void chaos4() {
        runSoup("Chaos4",
                """
                SOLUTION 1 chaos-phosphate
                    temp 25
                    pH   7 charge
                    pe   4
                    water 1 kg
                    Ca 12 mmol/kgw
                    Mg  4 mmol/kgw
                    Ba  2 mmol/kgw
                    Al  3 mmol/kgw
                    Na 25 mmol/kgw
                    P   8 mmol/kgw
                    F   6 mmol/kgw
                    S   4 mmol/kgw
                    B   5 mmol/kgw
                    Cl 20 mmol/kgw
                """, "",
                new String[]{"Ca", "Mg", "Ba", "Al", "Na", "P", "F", "S", "B", "Cl"},
                new String[]{"Hydroxyapatite", "Fluorite", "Barite", "Gibbsite"},
                new String[]{"Brucite", "Witherite", "Gypsum", "Celestite", "Quartz", "Portlandite"});
    }
}
