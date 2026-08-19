package com.yu1745.chemengine.kernel.chaos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 场景3：铁铝风化锅 Fe(2)/Fe(3)/Al/Mn + Si/C/S/Cl，氧化 pe=10 */
class ChaosSoup3Test extends ChaosBase {

    @Test
    @DisplayName("混沌锅3：铁铝风化（Fe2 Fe3 Al Mn × Si C S Cl，pe 10），声明 Goethite/Gibbsite/Quartz/Siderite")
    void chaos3() {
        runSoup("Chaos3",
                """
                SOLUTION 1 chaos-weathering
                    temp 25
                    pH   7 charge
                    pe   10
                    water 1 kg
                    Fe(2) 6 mmol/kgw
                    Fe(3) 4 mmol/kgw
                    Al    5 mmol/kgw
                    Mn    2 mmol/kgw
                    Na   20 mmol/kgw
                    Si    8 mmol/kgw
                    C    10 mmol/kgw
                    S     4 mmol/kgw
                    Cl   20 mmol/kgw
                """, "",
                new String[]{"Fe", "Al", "Mn", "Na", "Si", "C", "S", "Cl", "Fe(2)", "Fe(3)"},
                new String[]{"Goethite", "Gibbsite", "Quartz", "Siderite"},
                new String[]{"Magnetite", "Kaolinite", "Pyrite", "Troilite", "Rhodochrosite", "Dolomite", "Talc"});
    }
}
