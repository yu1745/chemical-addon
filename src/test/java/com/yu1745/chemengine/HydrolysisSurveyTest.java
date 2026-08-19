package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.FreeEnergyDatabase;
import com.yu1745.chemengine.solver.InorganicIonCatalog;
import com.yu1745.chemengine.solver.Solver;
import com.yu1745.chemengine.solver.SystemModel;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hydrolysis Survey (审计任务): 穷举水相水解反应类型，在两个引擎上交叉验证。
 *
 * <p>被测引擎:
 * <ul>
 *   <li><b>Legacy 反应式引擎</b> — {@link Harness#engine()}，加载 src/test/resources/species/*.json，
 *       含氨水解 / 碳酸盐水解 / 氢氧化物 Ksp / 两性条目。</li>
 *   <li><b>Track E 自由能引擎</b> — {@link SystemModel#fromFreeEnergy(InorganicIonCatalog.database())}，
 *       48 主基 + 49 次级离子，所有平衡由 ΔG_f° 推导。</li>
 * </ul>
 *
 * <p>本类只写测试与观测，不改任何主代码。每个场景一个 @Test：求解 → System.out 打印实际数值 →
 * 按化学直觉断言方向。数据缺失 / 求解边界 / 常数推导正确但求解受限的用例用 {@link Disabled}
 * 标注原因（不硬删），便于报告统计 通过/失败/Disabled。
 */
class HydrolysisSurveyTest {

    private static final Engine LEGACY = Harness.engine();

    /** Track E 核心模型（无任何候选固相）。 */
    private static final SystemModel TE_CORE = SystemModel.fromFreeEnergy(InorganicIonCatalog.database());

    /** Track E 氢氧化物候选固相模型（ΔG 值与既有测试一致，见 HydroxideExpressibilityTest）。 */
    private static final SystemModel TE_HYDROXIDES = SystemModel.fromFreeEnergy(
        InorganicIonCatalog.database()
            .solid("Al(OH)3", -1120.0, "Al", 1, "O", 3, "H", 3)
            .solid("Fe(OH)3", -705.0, "Fe", 1, "O", 3, "H", 3)
            .solid("Mg(OH)2", -834.0, "Mg", 1, "O", 2, "H", 2)
            .solid("Zn(OH)2", -849.0, "Zn", 1, "O", 2, "H", 2)
            .solid("Cu(OH)2", -357.0, "Cu", 1, "O", 2, "H", 2));

    /** 与 Ksp 自洽的 Zn(OH)2 ΔG_f°（-558 kJ/mol，由 ΔG_f(Zn2+)=-147.1、ΔG_f(OH-)=-157.2、
     *  Ksp=3e-17 推出）——用于对比目录里 -849 的数据不一致。 */
    private static final SystemModel TE_ZN_KSP_CONSISTENT = SystemModel.fromFreeEnergy(
        InorganicIonCatalog.database().solid("Zn(OH)2", -558.0, "Zn", 1, "O", 2, "H", 2));

    static {
        assertTrue(TE_CORE.droppedEquilibria().isEmpty(),
            "Track E core: all secondaries expressible: " + TE_CORE.droppedEquilibria());
        assertTrue(TE_HYDROXIDES.droppedEquilibria().isEmpty(),
            "Track E hydroxides expressible: " + TE_HYDROXIDES.droppedEquilibria());
    }

    // ---------------------------------------------------------------- helpers

    /** quanta -> mB（可读性打印与断言的统一单位）。 */
    private static double mbF(long quanta) { return quanta / (double) State.QUANTA_PER_MB; }

    /** 分数 mB -> quanta（用于小阈值断言）。 */
    private static long qm(double mB) { return (long) (mB * State.QUANTA_PER_MB); }

    private static long q(Map<String, Long> map, String key) { return map.getOrDefault(key, 0L); }

    /** legacy 求解 + 打印。 */
    private static State L(int tempC, State in) {
        State out = LEGACY.solveClosed(new State(tempC).water(mb(1000)).ions(in.ions()).molecules(in.molecules())
            .suspended(in.suspended())).state;
        return out;
    }

    /** Track E 求解 + 打印。 */
    private static State T(SystemModel model, int tempC, State in) {
        State base = new State(tempC).water(mb(1000)).ions(in.ions()).molecules(in.molecules()).suspended(in.suspended());
        return Solver.solve(model, null, base, Solver.Vessel.CLOSED).state;
    }

    private static void note(String s) { System.out.println("SURVEY NOTE " + s); }

    // =====================================================================
    // A. 盐类阴离子水解（强碱弱酸盐 → 碱性）
    // =====================================================================

    @Test @DisplayName("A1 carbonateBasicHydrolysis")
    void A1_carbonateBasicHydrolysis() {
        State l = L(25, new State(25).ions("Na+1", mb(200)).ions("CO3-2", mb(100)));
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(200)).ions("CO3-2", mb(100)));
        System.out.printf("SURVEY A1 carbonateBasicHydrolysis  LEGACY: CO3=%.3fMB HCO3=%.3fMB OH=%.3fMB | TrackE: CO3=%.3fMB HCO3=%.3fMB OH=%.3fMB%n",
            mbF(q(l.ions(), "CO3-2")), mbF(q(l.ions(), "HCO3-1")), mbF(q(l.ions(), "OH-1")),
            mbF(q(t.ions(), "CO3-2")), mbF(q(t.ions(), "HCO3-1")), mbF(q(t.ions(), "OH-1")));
        // 应然: CO3-2 + H2O ⇌ HCO3- + OH- 碱性水解，约 4-6% 水解（pKb=3.67）
        assertTrue(q(l.ions(), "HCO3-1") > mb(2) && q(l.ions(), "HCO3-1") < mb(8), "legacy 碳酸盐水解量级: " + q(l.ions(), "HCO3-1"));
        assertEquals(q(l.ions(), "HCO3-1"), q(l.ions(), "OH-1"), 1_000_000L, "legacy 1:1 化学计量 OH-/HCO3");
        assertEquals(0, l.netCharge(), "legacy 电荷中性");
        assertTrue(q(t.ions(), "HCO3-1") > mb(2) && q(t.ions(), "HCO3-1") < mb(8), "TrackE 碳酸盐水解量级: " + q(t.ions(), "HCO3-1"));
        assertEquals(q(t.ions(), "HCO3-1"), q(t.ions(), "OH-1"), 1_000_000L, "TrackE 1:1 化学计量 OH-/HCO3");
        assertEquals(0, t.netCharge(), "TrackE 电荷中性");
    }

    @Test @DisplayName("A2 carbonateTemperatureDirection")
    void A2_carbonateTemperatureDirection() {
        State l0 = L(0, new State(0).ions("Na+1", mb(200)).ions("CO3-2", mb(100)));
        State l25 = L(25, new State(25).ions("Na+1", mb(200)).ions("CO3-2", mb(100)));
        State l50 = L(50, new State(50).ions("Na+1", mb(200)).ions("CO3-2", mb(100)));
        State t0 = T(TE_CORE, 0, new State(0).ions("Na+1", mb(200)).ions("CO3-2", mb(100)));
        State t50 = T(TE_CORE, 50, new State(50).ions("Na+1", mb(200)).ions("CO3-2", mb(100)));
        System.out.printf("SURVEY A2 carbonateTemperature  LEGACY OH @0/25/50 = %.3f / %.3f / %.3f MB | TrackE OH @0/@50 = %.3f / %.3f MB%n",
            mbF(q(l0.ions(), "OH-1")), mbF(q(l25.ions(), "OH-1")), mbF(q(l50.ions(), "OH-1")),
            mbF(q(t0.ions(), "OH-1")), mbF(q(t50.ions(), "OH-1")));
        // 应然: 水解吸热（ΔH=+41.0 kJ/mol）→ 加热促进水解 → OH- 随温度单调上升
        assertTrue(q(l50.ions(), "OH-1") > q(l25.ions(), "OH-1") + mb(1), "legacy 加热促进碳酸盐水解 (OH50>OH25)");
        assertTrue(q(l25.ions(), "OH-1") > q(l0.ions(), "OH-1") + mb(1), "legacy 25C>0C 水解");
        // Track E 无焓数据（deltaH NaN）→ 温度无关（按构造相同），此断言锁定该局限
        assertEquals(q(t0.ions(), "OH-1"), q(t50.ions(), "OH-1"), 0L,
            "TrackE 温度盲区：无 Van't Hoff 数据，0C 与 50C 输出必须相同（数据局限，非错误）");
    }

    @Test @DisplayName("A3 bicarbonateSecondHydrolysisToCO2")
    void A3_bicarbonateSecondHydrolysisToCO2() {
        State l25 = L(25, new State(25).ions("Na+1", mb(100)).ions("HCO3-1", mb(100)));
        State l50 = L(50, new State(50).ions("Na+1", mb(100)).ions("HCO3-1", mb(100)));
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(100)).ions("HCO3-1", mb(100)));
        System.out.printf("SURVEY A3 HCO3 second step  LEGACY CO2(aq)@25/50=%.4f/%.4fMB CO3=%.3fMB OH=%.4fMB | TrackE: CO2 key absent, CO3=%.6fMB H+=%.6fMB%n",
            mbF(q(l25.molecules(), "chemicaladdon:carbon_dioxide")), mbF(q(l50.molecules(), "chemicaladdon:carbon_dioxide")),
            mbF(q(l25.ions(), "CO3-2")), mbF(q(l25.ions(), "OH-1")),
            mbF(q(t.ions(), "CO3-2")), mbF(q(t.ions(), "H+1")));
        // 应然: HCO3- ⇌ CO2(aq) + OH-（第二步水解，pKb=7.65），吸热 → 加热促进
        assertTrue(q(l25.molecules(), "chemicaladdon:carbon_dioxide") > mb(1),
            "legacy HCO3 第二步水解生成 CO2(aq): " + q(l25.molecules(), "chemicaladdon:carbon_dioxide"));
        assertTrue(q(l50.molecules(), "chemicaladdon:carbon_dioxide") > q(l25.molecules(), "chemicaladdon:carbon_dioxide"),
            "legacy 加热促进 HCO3→CO2（ΔH=+46.8 kJ/mol）");
        // Track E: 目录无 CO2(aq) 物种（只有 CO3-2 主离子）→ 该步不可表达；HCO3- 只能做 Ka2 酸性电离（产物 CO3+H+）
        assertNull(TE_CORE.speciesIndexOf("chemicaladdon:carbon_dioxide"), "TrackE 无 CO2(aq) 物种（数据缺口，非错误）");
        assertTrue(q(t.ions(), "HCO3-1") > mb(95), "TrackE HCO3- 几乎不电离（无 CO2 接收端）: " + q(t.ions(), "HCO3-1"));
    }

    @Test @DisplayName("A4 sulfiteHydrolysis")
    void A4_sulfiteHydrolysis() {
        State l25 = L(25, new State(25).ions("Na+1", mb(200)).ions("SO3-2", mb(100)));
        State l50 = L(50, new State(50).ions("Na+1", mb(200)).ions("SO3-2", mb(100)));
        System.out.printf("SURVEY A4 sulfiteHydrolysis  LEGACY HSO3@25/50=%.4f/%.4fMB OH@25=%.4fMB | TrackE: 见 A4_disabled_trackE%n",
            mbF(q(l25.ions(), "HSO3-1")), mbF(q(l50.ions(), "HSO3-1")), mbF(q(l25.ions(), "OH-1")));
        // 应然: SO3-2 + H2O ⇌ HSO3- + OH-（pKb=6.82 弱碱），吸热 → 加热促进
        assertTrue(q(l25.ions(), "HSO3-1") > qm(0.05) && q(l25.ions(), "HSO3-1") < qm(0.5),
            "legacy 亚硫酸盐水解量级: " + q(l25.ions(), "HSO3-1"));
        assertEquals(q(l25.ions(), "HSO3-1"), q(l25.ions(), "OH-1"), 1_000_000L, "1:1 化学计量");
        assertTrue(q(l50.ions(), "HSO3-1") > q(l25.ions(), "HSO3-1"), "legacy 加热促进亚硫酸盐水解");
    }

    @Test @DisplayName("A4_trackE sulfite (disabled: redox dominates)")
    @Disabled("Track E 下 SO3-2 输入触发氧化还原歧化（实测 SO4-2 52MB + S2O3-2 19MB + HSO4-1 10MB + e- 50MB），"
        + "酸式水解 HSO3- 仅 33 量子(<1e-4 MB)——水解被 redox 路径淹没，不是有效的水解测试场景（数据缺 HSO3- 酸碱性弱、S 系第二酸解离常数由 ΔG 推导过弱）"
        + "；legacy 侧正常（A4 通过）。")
    void A4_trackE_sulfiteHydrolysis() {
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(200)).ions("SO3-2", mb(100)));
        System.out.printf("SURVEY A4_trackE(disabled) SO3: HSO3=%.6fMB SO4=%.2fMB S2O3=%.2fMB H+=%.2fMB e-=%.2fMB%n",
            mbF(q(t.ions(), "HSO3-1")), mbF(q(t.ions(), "SO4-2")), mbF(q(t.ions(), "S2O3-2")),
            mbF(q(t.ions(), "H+1")), mbF(q(t.ions(), "e-")));
        assertTrue(q(t.ions(), "HSO3-1") > mb(1), "若此处真发生水解，HSO3- 应 >1MB（实际远小于，说明未发生）");
    }

    @Test @DisplayName("A5 phosphateHydrolysis")
    void A5_phosphateHydrolysis() {
        State l = L(25, new State(25).ions("Na+1", mb(300)).ions("PO4-3", mb(100)));
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(300)).ions("PO4-3", mb(100)));
        System.out.printf("SURVEY A5 phosphateHydrolysis  LEGACY: PO4=%.2fMB HPO4=%.2fMB H2PO4=%.4fMB OH=%.2fMB | TrackE: PO4=%.2fMB HPO4=%.2fMB OH=%.2fMB%n",
            mbF(q(l.ions(), "PO4-3")), mbF(q(l.ions(), "HPO4-2")), mbF(q(l.ions(), "H2PO4-1")), mbF(q(l.ions(), "OH-1")),
            mbF(q(t.ions(), "PO4-3")), mbF(q(t.ions(), "HPO4-2")), mbF(q(t.ions(), "OH-1")));
        // 应然: PO4-3 + H2O ⇌ HPO4-2 + OH-（Kb=10^-1.65 强碱）约 37% 水解；HPO4-2 再水解弱（Kb=10^-6.8）
        assertTrue(q(l.ions(), "HPO4-2") > mb(25) && q(l.ions(), "HPO4-2") < mb(45), "legacy 磷酸盐水解量级: " + q(l.ions(), "HPO4-2"));
        assertEquals(q(l.ions(), "HPO4-2"), q(l.ions(), "OH-1"), 500_000L, "1:1 化学计量");
        assertEquals(0, l.netCharge());
        assertTrue(q(t.ions(), "HPO4-2") > mb(25) && q(t.ions(), "HPO4-2") < mb(45), "TrackE 磷酸盐水解量级: " + q(t.ions(), "HPO4-2"));
        assertEquals(0, t.netCharge());
        // HPO4-2 的弱水解与 H2PO4- 的弱电离
        State l2 = L(25, new State(25).ions("Na+1", mb(200)).ions("HPO4-2", mb(100)));
        State t2 = T(TE_CORE, 25, new State(25).ions("Na+1", mb(200)).ions("HPO4-2", mb(100)));
        System.out.printf("SURVEY A5b HPO4 输入  LEGACY H2PO4=%.4fMB PO4=%.4fMB OH=%.4fMB | TrackE H2PO4=%.4fMB PO4=%.4fMB OH=%.4fMB%n",
            mbF(q(l2.ions(), "H2PO4-1")), mbF(q(l2.ions(), "PO4-3")), mbF(q(l2.ions(), "OH-1")),
            mbF(q(t2.ions(), "H2PO4-1")), mbF(q(t2.ions(), "PO4-3")), mbF(q(t2.ions(), "OH-1")));
        assertTrue(q(l2.ions(), "OH-1") > qm(0.01) && q(l2.ions(), "OH-1") < mb(1), "HPO4-2 弱水解: " + q(l2.ions(), "OH-1"));
        assertTrue(q(t2.ions(), "H2PO4-1") > qm(0.1), "TrackE HPO4 双性: 微弱酸碱并存: " + q(t2.ions(), "H2PO4-1"));
    }

    @Test @DisplayName("A6 sulfideHydrolysis (missing data)")
    void A6_sulfideHydrolysis_missingData() {
        State l = L(25, new State(25).ions("Na+1", mb(200)).ions("S-2", mb(100)));
        System.out.printf("SURVEY A6 sulfideHydrolysis  LEGACY: S-2=%.2fMB OH=%.6fMB（S-2 非 legacy 物种 → 惰性携带） | TrackE: 见 disabled%n",
            mbF(q(l.ions(), "S-2")), mbF(q(l.ions(), "OH-1")));
        // 应然: S-2 + H2O ⇌ HS- + OH-（强碱）。legacy 无 S-2/HS- 条目 → 惰性：断言"不水解"并记录数据缺口
        assertEquals(mb(100), q(l.ions(), "S-2"), "legacy 无 S-2 数据 → S-2 原样携带（数据缺口）");
        assertEquals(0, l.netCharge());
    }

    @Test @DisplayName("A6_trackE sulfide (disabled: no HS- species)")
    @Disabled("Track E 目录有 S-2 次级但无 HS-1（氢硫酸根）；S-2 只能按 redox 表达（S-2 = SO4-2 + 8H+ + 8e- - 4H2O, "
        + "logK=20.69）。实输入 Na2S：S-2 歧化/氧化为 S2O3-2 48MB + e- 385MB + H+ 289MB（强酸性），"
        + "无任何 HS-/OH- 水解产物；纯还原剂输入走 redox 边界（known_limitations §10）。水解测试无对象物种。")
    void A6_trackE_sulfideHydrolysis() {
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(200)).ions("S-2", mb(100)));
        System.out.printf("SURVEY A6_trackE(disabled) S-2: H+=%.1fMB e-=%.1fMB S2O3=%.1fMB S-2=%.2fMB HS-=%s%n",
            mbF(q(t.ions(), "H+1")), mbF(q(t.ions(), "e-")), mbF(q(t.ions(), "S2O3-2")),
            mbF(q(t.ions(), "S-2")), t.ions().get("HS-1"));
        assertTrue(q(t.ions(), "OH-1") > mb(1), "若真发生碱式水解，OH- 应 >1MB（实际≈0，说明走 redox 而非水解）");
    }

    @Test @DisplayName("A7 cyanideHydrolysis (missing data)")
    void A7_cyanideHydrolysis_missingData() {
        State l = L(25, new State(25).ions("K+1", mb(100)).ions("CN-1", mb(100)));
        System.out.printf("SURVEY A7 cyanideHydrolysis  LEGACY: CN-1=%.2fMB OH=%.6fMB（非 legacy 物种 → 惰性） | TrackE: 见 disabled%n",
            mbF(q(l.ions(), "CN-1")), mbF(q(l.ions(), "OH-1")));
        assertEquals(mb(100), q(l.ions(), "CN-1"), "legacy 无 CN- 数据 → 原样携带（数据缺口）");
        assertEquals(0, l.netCharge());
    }

    @Test @DisplayName("A7_trackE cyanide (disabled: no HCN species)")
    @Disabled("Track E 目录有 CN-1 次级但无 HCN(aq)（氢氰酸）；CN- 只能 redox 表达（CN- = CO3-2 + NO3-1 + 12H+ + 10e- - 3H2O, "
        + "logK=107.1）。实输入 KCN：CN- 分解为 CO3-2 94MB + N3-1 33MB + H+ 594MB（强酸性），"
        + "无 HCN/OH- 水解产物。水解测试无对象物种（数据缺口：HCN 缺失）。")
    void A7_trackE_cyanideHydrolysis() {
        State t = T(TE_CORE, 25, new State(25).ions("K+1", mb(100)).ions("CN-1", mb(100)));
        System.out.printf("SURVEY A7_trackE(disabled) CN-: H+=%.1fMB CO3=%.1fMB N3=%.1fMB HCO3=%.1fMB%n",
            mbF(q(t.ions(), "H+1")), mbF(q(t.ions(), "CO3-2")), mbF(q(t.ions(), "N3-1")), mbF(q(t.ions(), "HCO3-1")));
        assertTrue(q(t.ions(), "OH-1") > mb(1), "若真发生碱式水解，OH- 应 >1MB（实际≈0）");
    }

    @Test @DisplayName("A8 fluorideHydrolysis (missing HF)")
    void A8_fluorideHydrolysis_missingHF() {
        State l = L(25, new State(25).ions("Na+1", mb(100)).ions("F-1", mb(100)));
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(100)).ions("F-1", mb(100)));
        System.out.printf("SURVEY A8 fluorideHydrolysis  LEGACY: F=%.2fMB OH=%.6fMB（非物种→惰性） | TrackE: F=%.2fMB OH=%.6fMB（主离子，无 HF 物种）%n",
            mbF(q(l.ions(), "F-1")), mbF(q(l.ions(), "OH-1")), mbF(q(t.ions(), "F-1")), mbF(q(t.ions(), "OH-1")));
        // 应然: F- + H2O ⇌ HF + OH-（pKb=10.8 弱碱）。两引擎均无 HF(aq) → 无法表达；F- 保持原样（数据缺口，断言当前行为）
        assertEquals(mb(100), q(l.ions(), "F-1"), "legacy 无 HF 数据 → F- 惰性");
        assertEquals(mb(100), q(t.ions(), "F-1"), "TrackE F- 是主离子、无 HF 物种 → 不转化");
        assertNull(TE_CORE.speciesIndexOf("HF"), "TrackE 无 HF(aq) 物种");
        assertEquals(0, t.netCharge());
    }

    @Test @DisplayName("A9 hypohaliteHydrolysis")
    void A9_hypohaliteHydrolysis() {
        // legacy: ClO2-/ClO3-/ClO4- 非物种 → 惰性（ClO- 崩溃见 disabled 用例）
        for (String ion : new String[]{"ClO2-1", "ClO3-1", "ClO4-1"}) {
            State l = L(25, new State(25).ions("Na+1", mb(100)).ions(ion, mb(100)));
            assertEquals(mb(100), q(l.ions(), ion), "legacy " + ion + " 无条目 → 惰性");
            assertEquals(0, l.netCharge());
        }
        System.out.println("SURVEY A9a legacy ClO2-/ClO3-/ClO4- 惰性通过（无数据条目）");
        // TrackE: 歧化（非水解，但考察氧卤酸根行为）
        State c1 = T(TE_CORE, 25, new State(25).ions("Na+1", mb(100)).ions("ClO-1", mb(100)));
        State c3 = T(TE_CORE, 25, new State(25).ions("Na+1", mb(100)).ions("ClO3-1", mb(100)));
        State c4 = T(TE_CORE, 25, new State(25).ions("Na+1", mb(100)).ions("ClO4-1", mb(100)));
        System.out.printf("SURVEY A9b TrackE 歧化 ClO-: Cl-=%.1fMB ClO4-%.1fMB OH=%.6fMB | ClO3-: Cl-%.1fMB ClO4-%.1fMB | ClO4- 不变=%.1fMB%n",
            mbF(q(c1.ions(), "Cl-1")), mbF(q(c1.ions(), "ClO4-1")), mbF(q(c1.ions(), "OH-1")),
            mbF(q(c3.ions(), "Cl-1")), mbF(q(c3.ions(), "ClO4-1")), mbF(q(c4.ions(), "ClO4-1")));
        // 应然: ClO- 歧化 4ClO- → 3Cl- + ClO4-（符合热力学但实际动力学产物是 ClO3-）；OH-≈0 说明无 HClO 水解
        assertEquals(mb(75), q(c1.ions(), "Cl-1"), 1_000_000L, "ClO- 歧化 3/4 → Cl-");
        assertEquals(mb(25), q(c1.ions(), "ClO4-1"), 1_000_000L, "ClO- 歧化 1/4 → ClO4-");
        assertTrue(q(c1.ions(), "OH-1") < mb(1), "无 HClO 水解产物（OH-≈0，数据缺口 HClO 缺失）");
        assertTrue(q(c4.ions(), "ClO4-1") > mb(98), "ClO4- 最稳定氧化态，基本不反应");
        assertEquals(0, c3.netCharge(), mb(2), "ClO3- 歧化净电荷（亚量级噪声容忍）");
    }

    @Test @DisplayName("A9_legacy hypochlorite (disabled: crashes)")
    @Disabled("legacy 引擎输入 ClO-1（NaClO）崩溃：'negative component remainder for Cl-1'——ClO-1 由氯歧化条目"
        + "（Cl2+2OH-=Cl-+ClO-+H2O）推导，其组分向量含 Cl-1 负系数，直接输入使 t[Cl-1]<0 触发整数投影崩溃"
        + "（边界，非水解失败）；legacy 也无 HClO 水解条目。配平 Cl- 后 ClO- 仍不水解（实测 ClO- 原样 100MB，"
        + "OH- 仅 0.002MB），故 legacy 的 ClO- 水解既崩溃又无数据。")
    void A9_legacy_hypochloriteHydrolysis() {
        State l = L(25, new State(25).ions("Na+1", mb(100)).ions("ClO-1", mb(100)));
        assertTrue(q(l.ions(), "OH-1") > mb(1), "若 legacy 有 HClO 水解，OH- 应 >1MB");
    }

    @Test @DisplayName("A10 nitriteHydrolysis (missing data)")
    void A10_nitriteHydrolysis_missingData() {
        State l = L(25, new State(25).ions("Na+1", mb(100)).ions("NO2-1", mb(100)));
        System.out.printf("SURVEY A10 nitriteHydrolysis  LEGACY: NO2=%.2fMB OH=%.6fMB（非物种→惰性） | TrackE: 见 disabled%n",
            mbF(q(l.ions(), "NO2-1")), mbF(q(l.ions(), "OH-1")));
        assertEquals(mb(100), q(l.ions(), "NO2-1"), "legacy 无 NO2- 数据 → 惰性");
        assertEquals(0, l.netCharge());
    }

    @Test @DisplayName("A10_trackE nitrite (disabled: no HNO2 species)")
    @Disabled("Track E 目录有 NO2-1 次级但无 HNO2(aq)（亚硝酸）。实输入 NaNO2：NO2- 氧化还原再分配为 "
        + "NO3- 54MB + N3-1 10MB + OH- 16MB + e- 4MB，无 HNO2 水解产物——非碱式水解路径，无法断言水解常数。")
    void A10_trackE_nitriteHydrolysis() {
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(100)).ions("NO2-1", mb(100)));
        System.out.printf("SURVEY A10_trackE(disabled) NO2-: NO3=%.1fMB N3=%.1fMB OH=%.1fMB e-=%.1fMB%n",
            mbF(q(t.ions(), "NO3-1")), mbF(q(t.ions(), "N3-1")), mbF(q(t.ions(), "OH-1")), mbF(q(t.ions(), "e-")));
        assertTrue(q(t.ions(), "OH-1") < mb(1), "若按 pKb(NO2-)=10.85 发生碱式水解，应与 NO2- 同量级 OH-（实际被 redox 淹没）");
    }

    @Test @DisplayName("A11 arsenateHydrolysis (missing HAsO4)")
    void A11_arsenateHydrolysis_missingData() {
        State l = L(25, new State(25).ions("Na+1", mb(300)).ions("AsO4-3", mb(100)));
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(300)).ions("AsO4-3", mb(100)));
        System.out.printf("SURVEY A11 arsenate  LEGACY: AsO4=%.2fMB OH=%.6fMB（惰性） | TrackE: AsO4=%.2fMB OH=%.6fMB（无 HAsO4-2 物种）%n",
            mbF(q(l.ions(), "AsO4-3")), mbF(q(l.ions(), "OH-1")), mbF(q(t.ions(), "AsO4-3")), mbF(q(t.ions(), "OH-1")));
        // 应然: AsO4-3 + H2O ⇌ HAsO4-2 + OH-（pKb 约 2.5 强碱式水解）。两引擎均无 HAsO4-2 → 无法表达
        assertEquals(mb(100), q(l.ions(), "AsO4-3"), "legacy 无 As 数据 → 惰性");
        assertTrue(q(t.ions(), "AsO4-3") > mb(99), "TrackE 无 HAsO4-2 物种 → 砷酸根不转化（数据缺口）");
        assertTrue(q(t.ions(), "OH-1") < mb(1), "TrackE 未见砷酸根碱式水解（缺共轭酸）");
        assertEquals(0, t.netCharge(), 10_000L, "亚量级净电荷噪声");
    }

    @Test @DisplayName("A12 boraxHydrolysis (missing H3BO3)")
    void A12_boraxHydrolysis_missingData() {
        State l = L(25, new State(25).ions("Na+1", mb(200)).ions("B4O7-2", mb(100)));
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(200)).ions("B4O7-2", mb(100)));
        System.out.printf("SURVEY A12 borax  LEGACY: B4O7=%.2fMB OH=%.6fMB（惰性） | TrackE: B4O7=%.2fMB BO3=%.6fMB H+=%.3fMB OH=%.3fMB%n",
            mbF(q(l.ions(), "B4O7-2")), mbF(q(l.ions(), "OH-1")), mbF(q(t.ions(), "B4O7-2")),
            mbF(q(t.ions(), "BO3-3")), mbF(q(t.ions(), "H+1")), mbF(q(t.ions(), "OH-1")));
        // 应然: 硼砂水解出偏硼酸（弱酸）：B4O7-2 + 7H2O ⇌ 4H3BO3 + 2OH-（碱性）。两引擎均无 H3BO3/B(OH)3 物种
        assertEquals(mb(100), q(l.ions(), "B4O7-2"), "legacy 无硼数据 → 惰性");
        assertTrue(q(t.ions(), "B4O7-2") > mb(95), "TrackE 无 H3BO3 物种 → 硼酸根基本不转化（数据缺口）");
        assertTrue(q(t.ions(), "OH-1") < mb(5), "TrackE 未见硼砂碱式水解（H3BO3 缺失）；H+/OH- 的 1.2MB 是电荷平衡伪影");
    }

    @Test @DisplayName("A13 silicateHydrolysis (missing H2SiO3)")
    void A13_silicateHydrolysis_missingData() {
        State l = L(25, new State(25).ions("Na+1", mb(200)).ions("SiO3-2", mb(100)));
        State t = T(TE_CORE, 25, new State(25).ions("Na+1", mb(200)).ions("SiO3-2", mb(100)));
        System.out.printf("SURVEY A13 silicate  LEGACY: SiO3=%.2fMB OH=%.6fMB（惰性） | TrackE: SiO3=%.2fMB OH=%s H+=%s%n",
            mbF(q(l.ions(), "SiO3-2")), mbF(q(l.ions(), "OH-1")), mbF(q(t.ions(), "SiO3-2")),
            t.ions().get("OH-1"), t.ions().get("H+1"));
        // 应然: SiO3-2 + 2H2O ⇌ H2SiO3 + 2OH-（硅酸极弱酸，强碱式水解）。两引擎均无 H2SiO3/H4SiO4
        assertEquals(mb(100), q(l.ions(), "SiO3-2"), "legacy 无硅数据 → 惰性");
        assertEquals(mb(100), q(t.ions(), "SiO3-2"), "TrackE SiO3-2 是主离子、无 H2SiO3 物种 → 完全不转化（数据缺口）");
        assertEquals(0, t.netCharge());
    }

    @Test @DisplayName("A14 ammoniumAcidHydrolysis")
    void A14_ammoniumAcidHydrolysis() {
        State l0 = L(0, new State(0).ions("NH4+1", mb(100)).ions("Cl-1", mb(100)));
        State l50 = L(50, new State(50).ions("NH4+1", mb(100)).ions("Cl-1", mb(100)));
        State a0 = L(0, new State(0).molecule("chemicaladdon:ammonia", mb(100)));
        State a50 = L(50, new State(50).molecule("chemicaladdon:ammonia", mb(100)));
        System.out.printf("SURVEY A14 ammonium  NH4Cl: NH3@0/50=%.4f/%.4fMB H+@0/50=%.4f/%.4fMB | NH3(aq): NH4@0/50=%.3f/%.3fMB OH@0/50=%.3f/%.3fMB%n",
            mbF(q(l0.molecules(), "chemicaladdon:ammonia")), mbF(q(l50.molecules(), "chemicaladdon:ammonia")),
            mbF(q(l0.ions(), "H+1")), mbF(q(l50.ions(), "H+1")),
            mbF(q(a0.ions(), "NH4+1")), mbF(q(a50.ions(), "NH4+1")),
            mbF(q(a0.ions(), "OH-1")), mbF(q(a50.ions(), "OH-1")));
        // 应然: NH4+ + H2O ⇌ NH3 + H+（酸性，Ka=10^-9.25），吸热（+52.2）→ 加热促进 NH3/H+；
        // NH3 + H2O ⇌ NH4+ + OH-（碱性），净吸热（+3.69）→ 加热促进 NH4+/OH-（F1 修复验证点）
        assertTrue(q(l50.molecules(), "chemicaladdon:ammonia") > q(l0.molecules(), "chemicaladdon:ammonia") + qm(0.005),
            "铵根酸式水解吸热：加热产生更多 NH3");
        assertTrue(q(l50.ions(), "H+1") > q(l0.ions(), "H+1"), "加热产生更多 H+");
        assertTrue(q(a50.ions(), "NH4+1") > q(a0.ions(), "NH4+1"), "氨水解吸热：加热促进 NH4+（温度方向修复验证）");
        assertTrue(q(a0.ions(), "NH4+1") > qm(0.5), "0C 氨水解已发生 " + q(a0.ions(), "NH4+1") / (double) State.QUANTA_PER_MB);
    }

    @Test @DisplayName("A14_trackE ammonium (disabled: no NH3(aq) species)")
    @Disabled("Track E 目录无 NH3(aq) 物种（只有 NH4+1 次级：NH4+1 = NO3-1 + 10H+1 + 8e-, logK=119.03），"
        + "NH4+ 的酸式水解（→NH3+H+）无对象物种、只能整体 redox 表达。实输入 NH4Cl（100MB）：收敛但不水解，"
        + "而是氧化还原再分配为 NO3-1 57MB + N3-1 14MB + H+ 743MB + e- 571MB（氮守恒：57+3×14=99MB ✓），"
        + "N3- 叠氮在溶液中无化学意义；paired NH4+NO3 输入同理（H+ 100MB + N3- 50MB）。"
        + "NH4+/NH3 酸常数只能靠 A15 的常数推导验证（E°=0.88V ✓）。已知边界（known_limitations §10）。")
    void A14_trackE_ammoniumAcidHydrolysis() {
        State t = T(TE_CORE, 25, new State(25).ions("NH4+1", mb(100)).ions("Cl-1", mb(100)));
        System.out.printf("SURVEY A14_trackE(disabled) NH4+: H+=%.1fMB NO3=%.1fMB N3=%.1fMB e-=%.1fMB（redox 再分配，非水解）%n",
            mbF(q(t.ions(), "H+1")), mbF(q(t.ions(), "NO3-1")), mbF(q(t.ions(), "N3-1")), mbF(q(t.ions(), "e-")));
        assertTrue(q(t.molecules(), "chemicaladdon:ammonia") > mb(1), "若发生水解，应有 NH3(aq) 分子（缺物种，实际≈0）");
    }

    @Test @DisplayName("A15 ammoniumTrackE constantDerivation")
    void A15_ammoniumTrackE_constantDerivation() {
        // 常数推导层验证（known_limitations §10 推荐路径）：从 model.secondaries() 读出 NH4+ 的形成常数
        double logK = Double.NaN;
        double e0 = Double.NaN;
        for (SystemModel.Secondary sc : TE_CORE.secondaries()) {
            if (sc.key.equals("NH4+1")) {
                logK = sc.logKEff;
                // NH4+ = NO3-1 + 10H+1 + 8e- → n=8 电子，E° = logK·0.05916/n
                e0 = logK * 0.05916 / 8.0;
                System.out.printf("SURVEY A15 TrackE NH4+ logK=%.3f  E°(NO3/NH4)=%.3f V  coeff=%s%n",
                    logK, e0, java.util.Arrays.toString(sc.coeff));
            }
        }
        assertTrue(logK > 118.5 && logK < 119.5, "NH4+ 形成常数 logK≈119.03: " + logK);
        assertTrue(e0 > 0.87 && e0 < 0.89, "E°(NO3-/NH4+)=0.88V（NBS 值）: " + e0);
        // 并验证 TrackE 的温度盲区：OH- 次级无 ΔH → pKw 恒 14
        for (SystemModel.Secondary sc : TE_CORE.secondaries()) {
            if (sc.key.equals("OH-1")) {
                assertEquals(sc.logKEffAt(0), sc.logKEffAt(50), 1e-9, "TrackE 无焓数据，pKw 温度无关（局限）");
            }
        }
    }

    // =====================================================================
    // B. 金属阳离子水解（Lewis 酸；目录缺中间羟基配合物时的断言降级为"能与 OH- 结合成沉淀/配合物"）
    // =====================================================================

    @Test @DisplayName("B1 aluminiumAmphotericHydrolysis")
    void B1_aluminiumAmphotericHydrolysis() {
        // 化学计量碱 → Al(OH)3 沉淀；过量碱 → [Al(OH)4]- 溶解（两性）
        State lSt = L(20, new State(20).ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("OH-1", mb(300)));
        State lEx = L(20, new State(20).ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(700)).ions("OH-1", mb(700)));
        State tSt = T(TE_HYDROXIDES, 25, new State(25).ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("OH-1", mb(300)));
        State tEx = T(TE_HYDROXIDES, 25, new State(25).ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(700)).ions("OH-1", mb(700)));
        System.out.printf("SURVEY B1 Al  LEGACY 化学计量: Al(OH)3=%.1fMB 铝酸根=%.2fMB | 过量: Al(OH)3=%.1fMB 铝酸根=%.1fMB%n"
            + "SURVEY B1 Al  TrackE  化学计量: Al(OH)3=%.1fMB 铝酸根=%.2fMB | 过量: Al(OH)3=%.1fMB 铝酸根=%.1fMB%n",
            mbF(q(lSt.suspended(), "chemicaladdon:aluminium_hydroxide")), mbF(q(lSt.ions(), "[Al(OH)4]-1")),
            mbF(q(lEx.suspended(), "chemicaladdon:aluminium_hydroxide")), mbF(q(lEx.ions(), "[Al(OH)4]-1")),
            mbF(q(tSt.suspended(), "Al(OH)3")), mbF(q(tSt.ions(), "Al(OH)4-1")),
            mbF(q(tEx.suspended(), "Al(OH)3")), mbF(q(tEx.ions(), "Al(OH)4-1")));
        // 应然: Al3+ + 3OH- → Al(OH)3↓；pH>10.5 时 [Al(OH)4]- 胜出
        assertTrue(q(lSt.suspended(), "chemicaladdon:aluminium_hydroxide") > mb(90), "legacy 化学计量碱沉淀 Al(OH)3");
        assertTrue(q(lEx.ions(), "[Al(OH)4]-1") > mb(30), "legacy 过量碱生成铝酸根");
        assertTrue(q(tSt.suspended(), "Al(OH)3") > mb(90), "TrackE 化学计量碱沉淀 Al(OH)3");
        assertTrue(q(tEx.ions(), "Al(OH)4-1") > mb(90) && q(tEx.suspended(), "Al(OH)3") == 0,
            "TrackE 过量碱全溶为铝酸根");
        assertEquals(0, lSt.netCharge());
        assertEquals(0, tEx.netCharge());
        // Al3+ 单独（无外加碱）：legacy 靠 Ksp+Kw 自发弱酸化（H+ 2.1MB、Al(OH)3 0.7MB）；TrackE 温和酸化（H+ 0.024MB）
        State lA = L(25, new State(25).ions("Al+3", mb(100)).ions("Cl-1", mb(300)));
        State tA = T(TE_HYDROXIDES, 25, new State(25).ions("Al+3", mb(100)).ions("Cl-1", mb(300)));
        System.out.printf("SURVEY B1c Al3+ 单独  LEGACY H+=%.3fMB Al(OH)3=%.3fMB | TrackE H+=%.4fMB Al(OH)4-=%.4fMB%n",
            mbF(q(lA.ions(), "H+1")), mbF(q(lA.suspended(), "chemicaladdon:aluminium_hydroxide")),
            mbF(q(tA.ions(), "H+1")), mbF(q(tA.ions(), "Al(OH)4-1")));
        assertTrue(q(lA.ions(), "H+1") > qm(0.5), "legacy Al3+ 自发酸化（Ksp 驱动）");
        assertTrue(q(tA.ions(), "H+1") < mb(1), "TrackE Al3+ 温和酸化（无 AlOH2+ 中间体，靠 Ksp/铝酸根平衡）");
        assertTrue(q(lA.ions(), "H+1") < mb(10), "legacy Al3+ 酸化量级合理（<10MB）");
    }

    @Test @DisplayName("B2 zincHydrolysis")
    void B2_zincHydrolysis() {
        // legacy: Zn2+ + 2OH- → Zn(OH)2(s)（Ksp 10^-16.4）；过量 → [Zn(OH)4]-2
        State lSt = L(20, new State(20).ions("Zn+2", mb(100)).ions("SO4-2", mb(100))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)));
        State lEx = L(20, new State(20).ions("Zn+2", mb(100)).ions("SO4-2", mb(100))
            .ions("Na+1", mb(900)).ions("OH-1", mb(900)));
        // TrackE（目录 Zn(OH)2 ΔG=-849，Ksp 推导 ≈10^-68，见 B2b disabled）；Ksp 自洽值 -558 下沉淀正常
        State tEx = T(TE_HYDROXIDES, 25, new State(25).ions("Zn+2", mb(100)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(400)).ions("OH-1", mb(400)));
        State tSt = T(TE_ZN_KSP_CONSISTENT, 25, new State(25).ions("Zn+2", mb(100)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)));
        System.out.printf("SURVEY B2 Zn  LEGACY 化学计量: Zn(OH)2=%.1fMB | 过量9:1: Zn(OH)4-2=%.4fMB | TrackE(-558) 化学计量: Zn(OH)2=%.1fMB | 过量4:1: Zn(OH)4-2=%.1fMB%n",
            mbF(q(lSt.suspended(), "chemicaladdon:zinc_hydroxide")), mbF(q(lEx.ions(), "[Zn(OH)4]-2")),
            mbF(q(tSt.suspended(), "Zn(OH)2")), mbF(q(tEx.ions(), "Zn(OH)4-2")));
        assertTrue(q(lSt.suspended(), "chemicaladdon:zinc_hydroxide") > mb(90), "legacy 化学计量碱沉淀 Zn(OH)2");
        assertTrue(q(lEx.ions(), "[Zn(OH)4]-2") > qm(0.05), "legacy 过量碱生成少量锌酸根");
        assertTrue(q(tSt.suspended(), "Zn(OH)2") > mb(90), "TrackE(Ksp自洽-558) 化学计量碱沉淀 Zn(OH)2");
        assertTrue(q(tEx.ions(), "Zn(OH)4-2") > mb(90), "TrackE 过量碱全成锌酸根 [Zn(OH)4]-2");
        note("B2 legacy 锌酸盐溶解量级可疑：9:1 过量碱（pH≈14）下 [Zn(OH)4]-2 仅 0.11MB，真实化学同条件约 20MB 级"
            + "（Zn(OH)2+2OH- ⇌ Zn(OH)4-2, K≈10^-1.5）；TrackE 则相反偏强（4:1 即 100MB）。legacy 锌两性偏弱、TrackE 偏强。");
        // Zn2+ 单独：legacy 极弱酸化（H+ 0.005MB）
        State lA = L(25, new State(25).ions("Zn+2", mb(100)).ions("SO4-2", mb(100)));
        System.out.printf("SURVEY B2c Zn2+ 单独 LEGACY H+=%.4fMB%n", mbF(q(lA.ions(), "H+1")));
        assertTrue(q(lA.ions(), "H+1") < mb(1), "legacy Zn2+ 单独几乎不酸化（弱 Lewis 酸）");
    }

    @Test @DisplayName("B2_trackE zinc catalog solid (disabled: data inconsistency)")
    @Disabled("目录 ΔG_f(Zn(OH)2)=-849 kJ/mol 与自身 ΔG_f(Zn2+)=-147.1、ΔG_f(OH-)=-157.2 不自洽："
        + "推出 Ksp≈10^-68（真实 10^-16.9，差 50 个数量级；酸式溶解 logK=-39.9 极端不溶）。"
        + "实测化学计量 Zn2+100+OH-200：Zn(OH)4-2 占 50MB、无 Zn(OH)2 沉淀（相装配跳过极端 logK 酸式氢氧化物，"
        + "疑为 Newton 发散）；Ksp 自洽值 -558 下立刻正确沉淀（见 B2 主用例）。数据 bug，待修。")
    void B2_trackE_zincCatalogSolid() {
        State t = T(TE_HYDROXIDES, 25, new State(25).ions("Zn+2", mb(100)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)));
        System.out.printf("SURVEY B2_trackE(disabled) 目录-849: Zn(OH)2 沉淀=%sMB Zn(OH)4-2=%.1fMB Zn2+=%.1fMB%n",
            t.suspended().get("Zn(OH)2"), mbF(q(t.ions(), "Zn(OH)4-2")), mbF(q(t.ions(), "Zn+2")));
        assertTrue(q(t.suspended(), "Zn(OH)2") > mb(90), "目录 -849 下应沉淀 Zn(OH)2（实际不沉淀 → 数据不一致）");
    }

    @Test @DisplayName("B3 leadHydrolysis")
    void B3_leadHydrolysis() {
        // legacy: Pb2+ 非物种 → 惰性
        State l = L(25, new State(25).ions("Pb+2", mb(100)).ions("Cl-1", mb(200)));
        // TrackE: 过量 OH → [Pb(OH)4]-2（Pb(II) 两性配合物，非 redox）；目录无 Pb(OH)2 固相
        State tEx = T(TE_CORE, 25, new State(25).ions("Pb+2", mb(100)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(400)).ions("OH-1", mb(400)));
        State tSt = T(TE_CORE, 25, new State(25).ions("Pb+2", mb(100)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)));
        System.out.printf("SURVEY B3 Pb  LEGACY: Pb2+=%.2fMB（惰性，无数据） | TrackE 过量: Pb(OH)4-2=%.1fMB Pb2+=%.6fMB | 化学计量: Pb(OH)4-2=%.1fMB Pb2+=%.1fMB H+=%.1fMB%n",
            mbF(q(l.ions(), "Pb+2")), mbF(q(tEx.ions(), "Pb(OH)4-2")), mbF(q(tEx.ions(), "Pb+2")),
            mbF(q(tSt.ions(), "Pb(OH)4-2")), mbF(q(tSt.ions(), "Pb+2")), mbF(q(tSt.ions(), "H+1")));
        assertEquals(mb(100), q(l.ions(), "Pb+2"), "legacy 无 Pb 数据 → 惰性");
        assertTrue(q(tEx.ions(), "Pb(OH)4-2") > mb(90), "TrackE 过量碱：Pb2+ 全部形成两性 [Pb(OH)4]-2");
        assertNull(TE_CORE.speciesIndexOf("Pb(OH)2"), "目录无 Pb(OH)2 固相（数据缺口：2:1 比例下应沉淀）");
        // 化学计量（2OH/Pb）：实测 70% 生成 [Pb(OH)4]-2 + H+ 80MB，无沉淀——真实化学应为 Pb(OH)2 沉淀（缺数据）
        assertEquals(0, tSt.netCharge(), mb(2), "化学计量 Pb 输入净电荷（亚量级噪声容忍）");
        note("B3 化学计量 OH 下无 Pb(OH)2 固相 → 引擎以 [Pb(OH)4]-2 + H+ 表达（无沉淀）；真实化学应沉淀 Pb(OH)2，数据缺口。");
    }

    @Test @DisplayName("B4 tinHydrolysis (disabled: Sn(IV) oxidation)")
    @Disabled("Track E 目录只有 Sn(OH)6-2（Sn(IV)）与 Sn+2（Sn(II) 主离子），无 Sn(II) 羟基中间体（SnOH+）。"
        + "Sn2+ 遇碱转化为 Sn(OH)6-2 是氧化（+2→+4，释放 e- 134MB），不是水解。"
        + "真实 Sn2+ 水解（Sn2+ + H2O ⇌ SnOH+ + H+，pKa≈2 强酸）缺中间体无法表达。legacy 无 Sn 数据（惰性）。")
    void B4_tinHydrolysis() {
        State t = T(TE_CORE, 25, new State(25).ions("Sn+2", mb(100)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(400)).ions("OH-1", mb(400)));
        System.out.printf("SURVEY B4_trackE(disabled) Sn2+ + OH: Sn(OH)6-2=%.1fMB Sn2+=%.1fMB e-=%.1fMB H+=%.2fMB（氧化非水解）%n",
            mbF(q(t.ions(), "Sn(OH)6-2")), mbF(q(t.ions(), "Sn+2")), mbF(q(t.ions(), "e-")), mbF(q(t.ions(), "H+1")));
        assertTrue(q(t.ions(), "OH-1") > mb(1), "若为水解应有 OH- 消耗与 H+ 等当量产生（实际走氧化路径）");
    }

    @Test @DisplayName("B5 antimonyHydrolysis (disabled: Sb(V) oxidation)")
    @Disabled("Track E 目录只有 Sb(OH)6-1（Sb(V)）与 Sb+3（Sb(III) 主离子），无 Sb(III) 羟基中间体（SbO+）。"
        + "Sb3+ 遇碱转化为 Sb(OH)6-1 是氧化（+3→+5，释放 e- 200MB）、H+ 600MB 强酸化——非水解。"
        + "真实 Sb3+ 水解（Sb3+ + H2O ⇌ SbO+ + 2H+，pKa≈1.4 极强酸）缺中间体无法表达。legacy 无 Sb 数据（惰性）。")
    void B5_antimonyHydrolysis() {
        State t = T(TE_CORE, 25, new State(25).ions("Sb+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(400)).ions("OH-1", mb(400)));
        System.out.printf("SURVEY B5_trackE(disabled) Sb3+ + OH: Sb(OH)6-1=%.1fMB e-=%.1fMB H+=%.1fMB（氧化非水解）%n",
            mbF(q(t.ions(), "Sb(OH)6-1")), mbF(q(t.ions(), "e-")), mbF(q(t.ions(), "H+1")));
        assertTrue(q(t.ions(), "Sb+3") < mb(1), "Sb3+ 全部氧化为 Sb(V)（非水解）");
    }

    @Test @DisplayName("B6 ferricHydrolysis")
    void B6_ferricHydrolysis() {
        // Fe3+ + 3OH- → Fe(OH)3（Ksp 10^-38.6/ΔG -705 极不溶）
        State lSt = L(20, new State(20).ions("Fe+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("OH-1", mb(300)));
        State tSt = T(TE_HYDROXIDES, 25, new State(25).ions("Fe+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("OH-1", mb(300)));
        System.out.printf("SURVEY B6 Fe  LEGACY 化学计量: Fe(OH)3=%.1fMB | TrackE: Fe(OH)3=%.1fMB%n",
            mbF(q(lSt.suspended(), "chemicaladdon:iron_hydroxide")), mbF(q(tSt.suspended(), "Fe(OH)3")));
        assertTrue(q(lSt.suspended(), "chemicaladdon:iron_hydroxide") > mb(90), "legacy Fe(OH)3 沉淀");
        assertTrue(q(tSt.suspended(), "Fe(OH)3") > mb(90), "TrackE Fe(OH)3 沉淀");
        // Fe3+ 单独：legacy 强酸化（Ksp 驱动，Fe3+ + 3H2O → Fe(OH)3 + 3H+，H+ 130MB）；TrackE 纯氧化剂边界（见 disabled）
        State lA = L(25, new State(25).ions("Fe+3", mb(100)).ions("Cl-1", mb(300)));
        System.out.printf("SURVEY B6c Fe3+ 单独 LEGACY H+=%.1fMB Fe(OH)3=%.1fMB（强酸） | TrackE: 纯氧化剂边界见 disabled%n",
            mbF(q(lA.ions(), "H+1")), mbF(q(lA.suspended(), "chemicaladdon:iron_hydroxide")));
        assertTrue(q(lA.ions(), "H+1") > mb(50), "legacy Fe3+ 单独强酸化（真实化学：FeCl3 溶液强酸性）");
        assertEquals(0, lSt.netCharge());
        assertEquals(0, tSt.netCharge());
    }

    @Test @DisplayName("B6_trackE ferric alone (disabled: pure oxidant boundary)")
    @Disabled("Track E 下纯 Fe3+ 输入（无还原剂）是纯氧化剂边界：Fe3+ = Fe2+ - e- 使 t[e-]=-300MB，"
        + "连续 Newton 在 n[e-]=0 边界无内点解，抛 'negative component remainder for 47'"
        + "（known_limitations §10）。与 Fe(OH)3 沉淀配套输入（Fe3++OH-）可收敛（B6 主用例）。"
        + "缺 FeOH2+ 中间体：无法表达 Fe3+ + H2O ⇌ FeOH2+ + H+ 的逐级水解，legacy 靠 Ksp 一跳到位。")
    void B6_trackE_ferricAlone() {
        State t = T(TE_HYDROXIDES, 25, new State(25).ions("Fe+3", mb(100)).ions("Cl-1", mb(300)));
        assertTrue(q(t.ions(), "H+1") > mb(1), "纯 Fe3+ 应酸化（实际边界崩溃，见说明）");
    }

    @Test @DisplayName("B7 cupricHydrolysis")
    void B7_cupricHydrolysis() {
        State lSt = L(20, new State(20).ions("Cu+2", mb(100)).ions("SO4-2", mb(100))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)));
        State tSt = T(TE_HYDROXIDES, 25, new State(25).ions("Cu+2", mb(100)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)));
        System.out.printf("SURVEY B7 Cu  LEGACY 化学计量: Cu(OH)2=%.1fMB | TrackE: Cu(OH)2=%.1fMB%n",
            mbF(q(lSt.suspended(), "chemicaladdon:copper_hydroxide")), mbF(q(tSt.suspended(), "Cu(OH)2")));
        assertTrue(q(lSt.suspended(), "chemicaladdon:copper_hydroxide") > mb(90), "legacy Cu(OH)2 沉淀");
        assertTrue(q(tSt.suspended(), "Cu(OH)2") > mb(90), "TrackE Cu(OH)2 沉淀");
        // Cu2+ 单独：legacy 弱酸化（H+ 0.13MB）；TrackE 完全不水解（Cu2+ 主离子、无 CuOH+ 中间体）
        State lA = L(25, new State(25).ions("Cu+2", mb(100)).ions("SO4-2", mb(100)));
        State tA = T(TE_CORE, 25, new State(25).ions("Cu+2", mb(100)).ions("Cl-1", mb(200)));
        System.out.printf("SURVEY B7c Cu2+ 单独 LEGACY H+=%.4fMB | TrackE H+=%s（CuOH+ 中间体缺失，不酸化）%n",
            mbF(q(lA.ions(), "H+1")), tA.ions().get("H+1"));
        assertTrue(q(lA.ions(), "H+1") > qm(0.01) && q(lA.ions(), "H+1") < mb(1), "legacy Cu2+ 弱酸化（真实 pH≈4）");
        assertEquals(0, q(tA.ions(), "H+1"), "TrackE 无 CuOH+ → Cu2+ 不酸化（数据缺口）");
    }

    @Test @DisplayName("B8 magnesiumHydrolysis")
    void B8_magnesiumHydrolysis() {
        State lSt = L(20, new State(20).ions("Mg+2", mb(100)).ions("SO4-2", mb(100))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)));
        State lEx = L(20, new State(20).ions("Mg+2", mb(100)).ions("SO4-2", mb(100))
            .ions("Na+1", mb(400)).ions("OH-1", mb(400)));
        System.out.printf("SURVEY B8 Mg  LEGACY 化学计量: Mg(OH)2=%.1fMB | 过量: Mg(OH)2=%.1fMB OH残留=%.1fMB | TrackE: 见 disabled%n",
            mbF(q(lSt.suspended(), "chemicaladdon:magnesium_hydroxide")), mbF(q(lEx.suspended(), "chemicaladdon:magnesium_hydroxide")),
            mbF(q(lEx.ions(), "OH-1")));
        // 应然: Mg2+ + 2OH- → Mg(OH)2（Ksp 10^-11.2，中溶解度——化学计量比下接近完全沉淀）
        assertTrue(q(lSt.suspended(), "chemicaladdon:magnesium_hydroxide") > mb(90), "legacy Mg(OH)2 化学计量沉淀");
        assertTrue(q(lEx.suspended(), "chemicaladdon:magnesium_hydroxide") > mb(95) && q(lEx.ions(), "OH-1") > mb(150),
            "legacy 过量 OH：沉淀 100MB + OH 残留 200MB");
        assertEquals(0, lSt.netCharge());
    }

    @Test @DisplayName("B8_trackE magnesium (disabled: phase assembly fails)")
    @Disabled("Track E 下 Mg(OH)2（ΔG_f=-834 kJ/mol，推导 Ksp≈10^-11.4 与真实 5.6e-12 一致）在"
        + "Mg2+100+OH-200 输入下不沉淀：实测 Mg2+ 与 OH- 全部留在溶液（SI 高达 +20 却无固相生成）。"
        + "同为酸式溶解形式的 Al/Fe/Cu(OH)2（logK +9~+15）能沉淀，Mg(OH)2（logK +16.7）被相装配跳过，"
        + "疑为高 logK 酸式氢氧化物的 joint-Newton 发散（全局 2^5 子集搜索跳过含 Mg 子集）。legacy Ksp 直写可沉淀（B8 主用例）。"
        + "缺陷类别：相装配条件化问题，非数据缺失。")
    void B8_trackE_magnesiumHydrolysis() {
        State t = T(TE_HYDROXIDES, 25, new State(25).ions("Mg+2", mb(100)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)));
        System.out.printf("SURVEY B8_trackE(disabled) Mg2+=%.1fMB OH-=%.1fMB Mg(OH)2=%s（Ksp 正确却不沉淀）%n",
            mbF(q(t.ions(), "Mg+2")), mbF(q(t.ions(), "OH-1")), t.suspended().get("Mg(OH)2"));
        assertTrue(q(t.suspended(), "Mg(OH)2") > mb(1), "Mg(OH)2 应沉淀（实际不沉淀 → 相装配缺陷）");
    }

    @Test @DisplayName("B9 missingIntermediateHydroxoSpecies")
    void B9_missingIntermediateHydroxoSpecies() {
        // 记录两引擎都缺失的逐级羟基中间体（报告的组装依据）
        String[] missing = {"FeOH+2", "Fe(OH)2+1", "AlOH+2", "Al(OH)2+1", "CuOH+1", "ZnOH+1",
            "MgOH+1", "PbOH+1", "SnOH+1", "SbO+1", "HCN", "HF", "HNO2", "H3BO3", "H2SiO3", "HAsO4-2"};
        int absent = 0;
        for (String sp : missing) {
            boolean inLegacy = LEGACY.model().speciesIndexOf(sp) != null;
            boolean inTrackE = TE_CORE.speciesIndexOf(sp) != null;
            if (!inLegacy && !inTrackE) absent++;
            if (inLegacy || inTrackE) System.out.println("SURVEY B9 意外存在 " + sp + "? legacy=" + inLegacy + " trackE=" + inTrackE);
        }
        System.out.println("SURVEY B9 两引擎均缺的逐级羟基/共轭酸中间体 " + missing.length + " 个中 " + absent + " 个确认缺失"
            + "（逐级酸式水解与弱酸盐碱式水解因此不可表达）");
        assertEquals(missing.length, absent, "所有列举的中间体在两引擎中都应缺失（数据缺口记录）");
    }

    // =====================================================================
    // C. 双水解（两盐相遇互相促进）
    // =====================================================================

    @Test @DisplayName("C1 aluminiumCarbonateDoubleHydrolysis")
    void C1_aluminiumCarbonateDoubleHydrolysis() {
        State l = L(20, new State(20).ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("CO3-2", mb(150)));
        State lB = L(20, new State(20).ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("HCO3-1", mb(300)));
        State t = T(TE_HYDROXIDES, 25, new State(25).ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("CO3-2", mb(150)));
        System.out.printf("SURVEY C1 Al+CO3  LEGACY: Al(OH)3=%.1fMB CO2(aq)=%.1fMB HCO3=%.1fMB | Al+HCO3: Al(OH)3=%.1fMB CO2=%.1fMB | TrackE: Al(OH)3=%.1fMB Al3+残留=%.1fMB HCO3=%.1fMB（无CO2物种）%n",
            mbF(q(l.suspended(), "chemicaladdon:aluminium_hydroxide")), mbF(q(l.molecules(), "chemicaladdon:carbon_dioxide")),
            mbF(q(l.ions(), "HCO3-1")), mbF(q(lB.suspended(), "chemicaladdon:aluminium_hydroxide")),
            mbF(q(lB.molecules(), "chemicaladdon:carbon_dioxide")),
            mbF(q(t.suspended(), "Al(OH)3")), mbF(q(t.ions(), "Al+3")), mbF(q(t.ions(), "HCO3-1")));
        // 应然: 2Al3+ + 3CO3-2 + 3H2O → 2Al(OH)3↓ + 3CO2↑（双水解互相促进）
        assertTrue(q(l.suspended(), "chemicaladdon:aluminium_hydroxide") > mb(90), "legacy Al+CO3 → Al(OH)3 沉淀");
        assertTrue(q(l.molecules(), "chemicaladdon:carbon_dioxide") > mb(100), "legacy Al+CO3 → CO2 释放: " + q(l.molecules(), "chemicaladdon:carbon_dioxide"));
        assertTrue(q(lB.suspended(), "chemicaladdon:aluminium_hydroxide") > mb(90), "legacy Al+HCO3 → Al(OH)3 沉淀");
        assertTrue(q(lB.molecules(), "chemicaladdon:carbon_dioxide") > mb(200), "legacy Al+HCO3 → CO2 释放");
        assertTrue(q(t.suspended(), "Al(OH)3") > mb(30), "TrackE Al+CO3 沉淀 Al(OH)3（无 CO2 物种，只能部分表达）");
        assertEquals(0, l.netCharge());
        assertEquals(0, t.netCharge(), mb(2));
    }

    @Test @DisplayName("C2 ferricCarbonateDoubleHydrolysis")
    void C2_ferricCarbonateDoubleHydrolysis() {
        State l = L(20, new State(20).ions("Fe+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("CO3-2", mb(150)));
        State lB = L(20, new State(20).ions("Fe+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("HCO3-1", mb(300)));
        State t = T(TE_HYDROXIDES, 25, new State(25).ions("Fe+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("CO3-2", mb(150)));
        System.out.printf("SURVEY C2 Fe+CO3  LEGACY: Fe(OH)3=%.1fMB CO2=%.1fMB | Fe+HCO3: Fe(OH)3=%.1fMB CO2=%.1fMB | TrackE: Fe(OH)3=%.1fMB Fe3+=%.1fMB H+=%.1fMB%n",
            mbF(q(l.suspended(), "chemicaladdon:iron_hydroxide")), mbF(q(l.molecules(), "chemicaladdon:carbon_dioxide")),
            mbF(q(lB.suspended(), "chemicaladdon:iron_hydroxide")), mbF(q(lB.molecules(), "chemicaladdon:carbon_dioxide")),
            mbF(q(t.suspended(), "Fe(OH)3")), mbF(q(t.ions(), "Fe+3")), mbF(q(t.ions(), "H+1")));
        // 应然: 2Fe3+ + 3CO3-2 + 3H2O → 2Fe(OH)3↓ + 3CO2↑
        assertTrue(q(l.suspended(), "chemicaladdon:iron_hydroxide") > mb(90), "legacy Fe+CO3 → Fe(OH)3 沉淀");
        assertTrue(q(l.molecules(), "chemicaladdon:carbon_dioxide") > mb(100), "legacy Fe+CO3 → CO2 释放");
        assertTrue(q(lB.suspended(), "chemicaladdon:iron_hydroxide") > mb(90), "legacy Fe+HCO3 → Fe(OH)3 沉淀");
        assertTrue(q(t.suspended(), "Fe(OH)3") > mb(30), "TrackE Fe+CO3 沉淀 Fe(OH)3");
        assertEquals(0, l.netCharge());
    }

    @Test @DisplayName("C3 zincSulfideDoubleHydrolysis (disabled: no data)")
    @Disabled("Zn2+ + S-2 → ZnS 双水解：legacy 无 S-2/ZnS 条目（S-2 惰性）；TrackE 无 ZnS 固相且 S-2 无 HS-，"
        + "走 redox 歧化（H+ 289MB/e- 385MB/S2O3-2 48MB）非水解。两引擎均无法表达 ZnS 沉淀。")
    void C3_zincSulfideDoubleHydrolysis() {
        State l = L(25, new State(25).ions("Zn+2", mb(100)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(200)).ions("S-2", mb(100)));
        assertTrue(q(l.suspended(), "chemicaladdon:zinc_sulfide") > mb(1), "legacy 应沉淀 ZnS（实际无数据，未发生）");
    }

    @Test @DisplayName("C4 ammoniumCarbonateMutualHydrolysis")
    void C4_ammoniumCarbonateMutualHydrolysis() {
        // 弱酸弱碱盐互水解：(NH4)2CO3 两翼同时水解；NH4+ 弱酸 + CO3-2 强碱 → 净碱性偏弱
        State l = L(20, new State(20).ions("NH4+1", mb(200)).ions("CO3-2", mb(100)));
        System.out.printf("SURVEY C4 (NH4)2CO3  LEGACY: NH3=%.1fMB NH4=%.1fMB HCO3=%.1fMB CO3=%.1fMB CO2=%.2fMB OH=%.4fMB%n",
            mbF(q(l.molecules(), "chemicaladdon:ammonia")), mbF(q(l.ions(), "NH4+1")),
            mbF(q(l.ions(), "HCO3-1")), mbF(q(l.ions(), "CO3-2")), mbF(q(l.molecules(), "chemicaladdon:carbon_dioxide")),
            mbF(q(l.ions(), "OH-1")));
        // 应然: NH4+ + CO3-2 互促水解：NH4→NH3+H+ 与 CO3→HCO3+OH 并存；因 Kb(CO3)>>Ka(NH4)，平衡略偏碱性
        assertTrue(q(l.molecules(), "chemicaladdon:ammonia") > mb(50), "NH4+ 水解出 NH3: " + q(l.molecules(), "chemicaladdon:ammonia"));
        assertTrue(q(l.ions(), "HCO3-1") > mb(50), "CO3-2 水解出 HCO3: " + q(l.ions(), "HCO3-1"));
        assertTrue(q(l.ions(), "CO3-2") < mb(20), "碳酸根大部分被质子化: " + q(l.ions(), "CO3-2"));
        assertTrue(q(l.ions(), "OH-1") < mb(1), "互促水解弱碱性（OH 被 NH4+/CO2 中和）");
        assertEquals(0, l.netCharge());
    }

    // =====================================================================
    // D. 温度方向（核心验证点）
    // =====================================================================

    @Test @DisplayName("D1 ammoniaHydrolysisTemperatureDirection")
    void D1_ammoniaHydrolysisTemperatureDirection() {
        State a0 = L(0, new State(0).molecule("chemicaladdon:ammonia", mb(10)));
        State a50 = L(50, new State(50).molecule("chemicaladdon:ammonia", mb(10)));
        State h0 = L(0, new State(0).ions("NH4+1", mb(10)).ions("Cl-1", mb(10)));
        State h50 = L(50, new State(50).ions("NH4+1", mb(10)).ions("Cl-1", mb(10)));
        System.out.printf("SURVEY D1 氨  NH3(aq)稀释: NH4@0/50=%.4f/%.4fMB（加热→NH4+↑） | NH4Cl稀释: H+@0/50=%.4f/%.4fMB（加热→H+↑）%n",
            mbF(q(a0.ions(), "NH4+1")), mbF(q(a50.ions(), "NH4+1")), mbF(q(h0.ions(), "H+1")), mbF(q(h50.ions(), "H+1")));
        // 应然: 氨水解净吸热（+3.69 kJ/mol）→ 加热促进 NH4+；铵根酸解吸热（+52.2）→ 加热促进 H+
        assertTrue(q(a50.ions(), "NH4+1") > q(a0.ions(), "NH4+1"), "氨水解吸热：加热促进 NH4+");
        assertTrue(q(h50.ions(), "H+1") > q(h0.ions(), "H+1"), "铵根酸解吸热：加热促进 H+");
    }

    @Test @DisplayName("D2 diluteElectrolytePhDirection")
    void D2_diluteElectrolytePhDirection() {
        State c0 = L(0, new State(0).ions("Na+1", mb(20)).ions("CO3-2", mb(10)));
        State c50 = L(50, new State(50).ions("Na+1", mb(20)).ions("CO3-2", mb(10)));
        State n0 = L(0, new State(0).ions("NH4+1", mb(10)).ions("Cl-1", mb(10)));
        State n50 = L(50, new State(50).ions("NH4+1", mb(10)).ions("Cl-1", mb(10)));
        System.out.printf("SURVEY D2 稀释电解质  Na2CO3: OH@0/50=%.3f/%.3fMB（加热碱性↑） | NH4Cl: H+@0/50=%.4f/%.4fMB（加热酸性↑）%n",
            mbF(q(c0.ions(), "OH-1")), mbF(q(c50.ions(), "OH-1")), mbF(q(n0.ions(), "H+1")), mbF(q(n50.ions(), "H+1")));
        // 纯水自电离对会被 suppressAutoionisation 抹掉（见 D4），故用含微量电解质体系断言 pH 移动方向
        assertTrue(q(c50.ions(), "OH-1") > q(c0.ions(), "OH-1") * 2, "碳酸盐体系加热 pH 上升（水解吸热）");
        assertTrue(q(n50.ions(), "H+1") > q(n0.ions(), "H+1") * 2, "铵盐体系加热 pH 下降（酸解吸热）");
    }

    @Test @DisplayName("D3 kwTemperatureConstants")
    void D3_kwTemperatureConstants() {
        SystemModel legacyModel = LEGACY.model();
        double pKw0 = Double.NaN, pKw25 = Double.NaN, pKw50 = Double.NaN;
        double hco3_0 = Double.NaN, hco3_50 = Double.NaN;
        for (SystemModel.Secondary sc : legacyModel.secondaries()) {
            if (sc.key.equals("OH-1")) {
                pKw0 = -sc.logKEffAt(0);
                pKw25 = -sc.logKEffAt(25);
                pKw50 = -sc.logKEffAt(50);
            }
            if (sc.key.equals("HCO3-1")) {
                hco3_0 = sc.logKEffAt(0);
                hco3_50 = sc.logKEffAt(50);
            }
        }
        System.out.printf("SURVEY D3 legacy 常数层  pKw @0/25/50 = %.2f / %.2f / %.2f | logK(HCO3-形成) @0/50 = %.3f / %.3f%n",
            pKw0, pKw25, pKw50, hco3_0, hco3_50);
        // 应然（真实 pHREEQC/实验）：pKw 14.9@0°C、14.0@25°C、13.3@50°C（Kw_diss 吸热 55.91 kJ/mol）
        assertTrue(pKw0 > 14.75 && pKw0 < 15.05, "pKw(0C)≈14.9: " + pKw0);
        assertEquals(14.0, pKw25, 0.05, "pKw(25C)=14");
        assertTrue(pKw50 > 13.1 && pKw50 < 13.45, "pKw(50C)≈13.3: " + pKw50);
        // 碳酸第二酸常数随温度（10.57@0 → 10.13@50），加热使 CO3-2 碱性更强
        assertTrue(hco3_0 > hco3_50 + 0.3, "Ka2 温度依赖：logK(HCO3) 0C>50C: " + hco3_0 + " vs " + hco3_50);
        // TrackE：无焓 → pKw 恒定 14（局限记录）
        for (SystemModel.Secondary sc : TE_CORE.secondaries()) {
            if (sc.key.equals("OH-1")) {
                assertEquals(-14.0, sc.logKEffAt(0), 1e-9, "TrackE pKw(0C) 恒定（无焓数据）");
                assertEquals(-14.0, sc.logKEffAt(50), 1e-9, "TrackE pKw(50C) 恒定（无焓数据）");
            }
        }
    }

    @Test @DisplayName("D4 pureWaterAutoionisationSuppressed")
    void D4_pureWaterAutoionisationSuppressed() {
        // 纯水自电离对在两引擎中都被 suppressAutoionisation 抹掉（物种层输出 0 离子）
        State l = L(25, new State(25));
        State tT = T(TE_CORE, 25, new State(25));
        System.out.printf("SURVEY D4 纯水 autoionisation 抑制 legacy ions=%s TrackE ions=%s（不可直接断言纯水 pH）%n", l.ions(), tT.ions());
        assertTrue(l.ions().isEmpty(), "legacy 纯水无自电离离子（抑制机制，已知行为）");
        assertTrue(tT.ions().isEmpty(), "TrackE 纯水无自电离离子（抑制机制）");
        // pH 方向改用 D1/D2/D3 的稀释电解质/常数层断言
        note("纯水 pH 数值不可直接断言（输出被抑制）；温度方向用含微量电解质断言（D2）与模型常数层断言（D3）。");
    }

    // =====================================================================
    // E. 引擎覆盖范围外（列出但不算错误）
    // =====================================================================

    @Test @DisplayName("E1 organicHydrolysisOutOfScope")
    void E1_organicHydrolysisOutOfScope() {
        // 酯/酰胺水解：两引擎无任何有机物物种（只含水、无机离子与无机分子）
        String[] organics = {"ethyl_acetate", "acetamide", "ester", "amide", "CH3COOH", "C2H5OH"};
        int absent = 0;
        for (String o : organics) {
            if (LEGACY.database().get(o) == null) absent++;
        }
        System.out.println("SURVEY E1 有机酯/酰胺水解 超出范围：" + absenceNote(organics, absent));
        assertEquals(organics.length, absent, "有机物酯/酰胺全部不存在于引擎（超出范围，非错误）");
    }

    @Test @DisplayName("E2 halideHydrolysisOutOfScope")
    void E2_halideHydrolysisOutOfScope() {
        // PCl5/SiCl4 气固水解：不在任何数据源中（无 P/Si 氯化物物种，也无对应水解平衡）
        String[] halides = {"PCl5", "SiCl4", "phosphorus_pentachloride", "silicon_tetrachloride"};
        int absent = 0;
        for (String h : halides) {
            if (LEGACY.database().get(h) == null && TE_CORE.speciesIndexOf(h) == null
                && TE_CORE.speciesIndexOf(h.replace("PCl5", "P+5").replace("SiCl4", "Si+4")) == null) absent++;
        }
        System.out.println("SURVEY E2 卤化物(PCl5/SiCl4)气固水解 超出范围：" + absenceNote(halides, absent));
        assertEquals(halides.length, absent, "PCl5/SiCl4 不在数据目录（超出范围）");
    }

    @Test @DisplayName("E3 calciumCarbideHydrolysis (D3 net reaction)")
    void E3_calciumCarbideHydrolysis() {
        // CaC2 + 2H2O → Ca(OH)2(s) + C2H2：legacy 通过 D3 纯计量净反应表达（无平衡常数）
        Electrolysis hyd = Electrolysis.parse(
            "chemicaladdon:calcium_carbide(s) + 2 water = chemicaladdon:slaked_lime(s) + chemicaladdon:acetylene");
        State advanced = hyd.advance(new State(20).water(mb(1000)).suspended("chemicaladdon:calcium_carbide", mb(50)), mb(50));
        Solver.Result r = LEGACY.solveOpen(advanced);
        System.out.printf("SURVEY E3 CaC2 水解(D3) advanced: 电石残留=%.1fMB 熟石灰=%.1fMB 乙炔=%.1fMB | solveOpen: 熟石灰=%.1fMB 乙炔残留=%.1fMB 逸出=%s%n",
            mbF(advanced.suspendedAmount("chemicaladdon:calcium_carbide")), mbF(advanced.suspendedAmount("chemicaladdon:slaked_lime")),
            mbF(advanced.moleculeAmount("chemicaladdon:acetylene")),
            mbF(r.state.suspendedAmount("chemicaladdon:slaked_lime")), mbF(r.state.moleculeAmount("chemicaladdon:acetylene")), r.gasVented);
        assertEquals(0, advanced.suspendedAmount("chemicaladdon:calcium_carbide"), "电石消耗完");
        assertEquals(mb(50), advanced.suspendedAmount("chemicaladdon:slaked_lime"), "生成 Ca(OH)2 50MB");
        assertEquals(mb(50), advanced.moleculeAmount("chemicaladdon:acetylene"), "生成 C2H2 50MB");
        assertTrue(r.state.suspendedAmount("chemicaladdon:slaked_lime") > mb(40), "solveOpen 后熟石灰大部分保留");
        // TrackE：CaC2 不在无机离子目录（无碳化物离子）→ 无法表达，超出范围
        assertNull(TE_CORE.speciesIndexOf("C2-2"), "TrackE 无机目录无碳化物离子（超出范围）");
    }

    @Test @DisplayName("E4 oxideDirectHydrolysisOutOfScope")
    void E4_oxideDirectHydrolysisOutOfScope() {
        // Na2O/K2O 直接水解：两引擎目录均无这些氧化物（legacy 只有 CaO 并可经 D3 净反应消化）
        State adv = Electrolysis.parse("chemicaladdon:quicklime(s) + water = chemicaladdon:slaked_lime(s)")
            .advance(new State(20).water(mb(1000)).suspended("chemicaladdon:quicklime", mb(50)), mb(50));
        System.out.printf("SURVEY E4 氧化物水解  Na2O/K2O 不在目录（超出范围）；legacy CaO 的 D3 消化可用：熟石灰=%.1fMB%n",
            mbF(adv.suspendedAmount("chemicaladdon:slaked_lime")));
        assertEquals(mb(50), adv.suspendedAmount("chemicaladdon:slaked_lime"), "CaO + H2O → Ca(OH)2（D3 净反应）");
        assertNull(LEGACY.database().get("sodium_oxide"), "Na2O 不在 legacy 数据");
        assertNull(LEGACY.database().get("potassium_oxide"), "K2O 不在 legacy 数据");
    }

    private static String absenceNote(String[] items, int absent) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) sb.append(s).append(", ");
        return absent + "/" + items.length + " 个确认不存在（" + sb + "）";
    }
}