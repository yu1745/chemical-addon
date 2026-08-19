package com.yu1745.chemengine.kernel.chaos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 场景1：硬水结垢锅 Ca/Mg/Ba/Fe/Cu + C/S/F/Cl */
class ChaosSoup1Test extends ChaosBase {

    @Test
    @DisplayName("混沌锅1：硬水结垢（Ca Mg Ba Fe Cu × C S F Cl），声明 Calcite/Barite/Fluorite/Witherite/Dolomite")
    void chaos1() {
        runSoup("Chaos1",
                """
                SOLUTION 1 chaos-hardwater
                    temp 25
                    pH   7 charge
                    pe   4
                    water 1 kg
                    Ca  8 mmol/kgw
                    Mg  5 mmol/kgw
                    Ba  3 mmol/kgw
                    Fe  3 mmol/kgw
                    Cu  2 mmol/kgw
                    Na 30 mmol/kgw
                    C  10 mmol/kgw
                    S   6 mmol/kgw
                    F   4 mmol/kgw
                    Cl 20 mmol/kgw
                """, "",
                new String[]{"Ca", "Mg", "Ba", "Fe", "Cu", "Na", "C", "S", "F", "Cl"},
                new String[]{"Calcite", "Barite", "Fluorite", "Witherite", "Dolomite"},
                new String[]{"Gypsum", "Celestite", "Brucite", "Malachite", "Azurite", "Goethite", "Siderite", "Quartz", "Strontianite"});
    }
}
