package com.yu1745.chemengine.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 全家锅：氧化（Fe2+ 通 Cl2）+ 还原（Hyp 淬灭 Sul，KINETICS）
 * + 复分解沉淀（Ba + SO4 → Barite）+ 络合（Fe3+ + Cl- → FeCl+2）
 * 四类反应同一反应步联立。
 */
class GrandSoupTest {

    @Test
    @DisplayName("对照0：什么也不加，基线")
    void control0() { runSoup(false, false); }

    @Test
    @DisplayName("对照A：无 Cl₂，只有 Quench+Barite")
    void controlA_noCl2() { runSoup(false, true); }


    @Test
    @DisplayName("对照B：无 Quench，只有 Cl₂+Barite")
    void controlB_noQuench() { runSoup(true, false); }


    @Test
    @DisplayName("全家锅：氧化+还原+沉淀+络合同场联立")
    void grandSoup() { runSoup(true, true); }

    private void runSoup(boolean withCl2, boolean withQuench) {
        Curation c = Curation.load();
        String reaction = withCl2 ? """
                    REACTION 1
                        Cl2 1
                        2 mmol in 1 step
                """ : "";
        String kinetics = withQuench ? """
                    KINETICS 1
                    Quench
                        -formula Cl 1 Hyp -1 O 4 S 1 Sul -1
                        -m        1000
                        -m0       1000
                        -steps    10000 seconds
                """ : "";
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 grand soup
                        temp      25
                        pH        7 charge
                        pe        4
                        water     1 kg
                        Fe        10 mmol/kgw
                        Ba        5  mmol/kgw
                        Na        20 mmol/kgw
                        Cl        20 mmol/kgw
                        S         10 mmol/kgw
                        Hyp       8  mmol/kgw
                        Sul       5  mmol/kgw
                    END
                    """ + c.ratesBlock() + """
                    USE solution 1
                    """ + reaction + kinetics + """
                    EQUILIBRIUM_PHASES 1
                        Barite          0  0
                        Ferrihydrite(am) 0  0
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -water    true
                        -totals   Fe Ba Na Cl S Hyp Sul Fe(2) Fe(3)
                        -molalities Fe+2 Fe+3 FeCl+2 Ba+2 SO4-2 H+ Cl2 O2 H2
                        -equilibrium_phases Barite Ferrihydrite(am)
                        -pH       true
                        -pe       true
                    END
                    """);
            int last = r.rowCount() - 1;
            System.out.println("=== 全家锅输出 ===");
            System.out.println(r.rawLines().toString());
            String tag = (withCl2 ? "Cl2" : "--") + "/" + (withQuench ? "Quench" : "--");
            for (int i = 0; i < r.rowCount(); i++) {
                System.out.printf("[%s] row%d: pH=%.2f pe=%.2f Fe2tot=%.3f Fe3tot=%.3f Cl2(aq)=%.2e O2=%.3e H2=%.3e H2O=%.4f Sul=%.4f Hyp=%.4f Barite=%s Ferrihydrite=%s%n",
                        tag, i, r.row(i).d("pH"), r.row(i).d("pe"),
                        r.row(i).d("Fe(2)") * 1000, r.row(i).d("Fe(3)") * 1000,
                        r.row(i).d("m_Cl2"), r.row(i).d("m_O2") * 1000, r.row(i).d("m_H2") * 1000,
                        r.row(i).d("mass_H2O"),
                        r.row(i).d("Sul") * 1000, r.row(i).d("Hyp") * 1000, r.row(i).s("Barite"),
                        r.row(i).s("Ferrihydrite(am)"));
            }
            int last2 = r.rowCount() - 1;
            System.out.printf("[%s] totals: Fe=%s Ba=%s Na=%s Cl=%s S=%s Hyp=%s Sul=%s%n",
                    tag, r.row(last2).s("Fe"), r.row(last2).s("Ba"), r.row(last2).s("Na"),
                    r.row(last2).s("Cl"), r.row(last2).s("S"), r.row(last2).s("Hyp"), r.row(last2).s("Sul"));
        }
    }
}
