package com.yu1745.chemengine.kernel.chaos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 场景2：重金属酸性锅 Zn/Pb/Ni/Co/Mn + S(6)/C/N(5)/Cl */
class ChaosSoup2Test extends ChaosBase {

    @Test
    @DisplayName("混沌锅2：重金属（Zn Pb Ni Co Mn × S C N Cl），声明 Cerussite/Anglesite/Rhodochrosite")
    void chaos2() {
        runSoup("Chaos2",
                """
                SOLUTION 1 chaos-heavy-metal
                    temp 25
                    pH   7 charge
                    pe   2
                    water 1 kg
                    Zn  6 mmol/kgw
                    Pb  4 mmol/kgw
                    Ni  3 mmol/kgw
                    Co  2 mmol/kgw
                    Mn  5 mmol/kgw
                    Na 20 mmol/kgw
                    S   12 mmol/kgw
                    C    4 mmol/kgw
                    N   10 mmol/kgw
                    Cl 20 mmol/kgw
                """, "",
                new String[]{"Zn", "Pb", "Ni", "Co", "Mn", "Na", "S", "C", "N", "Cl"},
                new String[]{"Cerussite", "Anglesite", "Rhodochrosite"},
                new String[]{"Gypsum", "Hydrocerussite", "Galena", "Siderite", "Barite", "Litharge", "Massicot"});
    }
}
