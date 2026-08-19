package com.yu1745.chemengine.kernel.chaos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 场景5：氧化淬灭锅 Cu/Zn/Pb + C/S/Cl + Hyp/Sul 介稳池 + Nitra/Nitri + KINETICS Quench */
class ChaosSoup5Test extends ChaosBase {

    @Test
    @DisplayName("混沌锅5：氧化淬灭（Cu Zn Pb × C S Cl + Hyp/Sul/Nitra/Nitri + Quench 动力学），声明 Malachite/Azurite/Cerussite")
    void chaos5() {
        runSoup("Chaos5",
                """
                SOLUTION 1 chaos-redox-bleach
                    temp 25
                    pH   7 charge
                    pe   8
                    water 1 kg
                    Cu   3 mmol/kgw
                    Zn   4 mmol/kgw
                    Pb   2 mmol/kgw
                    Na  25 mmol/kgw
                    C   10 mmol/kgw
                    S    6 mmol/kgw
                    Cl  20 mmol/kgw
                    Hyp  8 mmol/kgw
                    Sul  4 mmol/kgw
                    Nitra 10 mmol/kgw
                    Nitri  5 mmol/kgw
                """,
                """
                KINETICS 1
                Quench
                    -formula Cl 1 Hyp -1 O 4 S 1 Sul -1
                    -m        1000
                    -m0       1000
                    -steps    10000 seconds
                """,
                new String[]{"Cu", "Zn", "Pb", "Na", "C", "S", "Cl", "Hyp", "Sul", "Nitra", "Nitri"},
                new String[]{"Malachite", "Azurite", "Cerussite"},
                new String[]{"Brochantite", "Anglesite", "Galena", "Covellite", "Chalcocite", "Hydrocerussite", "Barite"});
    }
}
