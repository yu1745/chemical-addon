package com.yu1745.chemengine.kernel.chaos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 场景6：碳酸盐大锅 Ca/Mg/Fe/Mn/Ba + C/S/Si/Cl，还原 pe=0 */
class ChaosSoup6Test extends ChaosBase {

    @Test
    @DisplayName("混沌锅6：碳酸盐（Ca Mg Fe Mn Ba × C S Si Cl，pe 0），声明 Calcite/Dolomite/Witherite/Siderite/Rhodochrosite/Quartz")
    void chaos6() {
        runSoup("Chaos6",
                """
                SOLUTION 1 chaos-carbonate
                    temp 25
                    pH   7 charge
                    pe   0
                    water 1 kg
                    Ca 10 mmol/kgw
                    Mg  6 mmol/kgw
                    Fe  2 mmol/kgw
                    Mn  3 mmol/kgw
                    Ba  2 mmol/kgw
                    Na 25 mmol/kgw
                    C  20 mmol/kgw
                    S   5 mmol/kgw
                    Si  6 mmol/kgw
                    Cl 20 mmol/kgw
                """, "",
                new String[]{"Ca", "Mg", "Fe", "Mn", "Ba", "Na", "C", "S", "Si", "Cl", "Fe(2)", "Fe(3)"},
                new String[]{"Calcite", "Dolomite", "Witherite", "Siderite", "Rhodochrosite", "Quartz"},
                new String[]{"Magnetite", "Goethite", "Talc", "Kaolinite", "Gypsum", "Barite", "Brucite", "Dolomite"});
    }
}
