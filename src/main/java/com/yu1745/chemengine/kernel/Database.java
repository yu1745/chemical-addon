package com.yu1745.chemengine.kernel;

import java.io.IOException;

/**
 * PHREEQC 数据库文本：sit.dat（ThermoChimie 12a，官方 phreeqc3 仓库）+ chemistry-addon 补丁。
 *
 * <p>sit.dat 是官方全部数据库中唯一同时具备 Cl2(aq)（{@code Cl2 = -2 e- + 2 Cl-}）与
 * 完整 S(+4) 体系的库；HOCl/OCl- 官方各库皆无，自补。
 *
 * <p><b>addendum 的组成</b>：
 * <ol>
 *   <li>策展表伪元素（{@link Curation}，{@code resources/curation/chemistry.json}）——
 *       介稳氧化态的<b>推荐路径</b>（AMM.DAT 的 Amm 先例）。伪元素与真实元素无共享 master、
 *       无 e- 联系，穿过任意批式反应步不塌缩；跨池耦合由脚本层 KINETICS 白名单声明
 *       （{@link Curation#ratesBlock()} / {@link Curation#kineticsBlock(double...)}）。</li>
 *   <li>Cl(+1) 价态物种（{@link #CL1_REFERENCE}）——<b>仅平衡参考</b>：供 {@code Cl}
 *       元素总量输入在高 pe 下的平衡分配。全平衡热力学下 HOCl 会氧化水
 *       （E°&gt;1.23 V，真实漂白液纯靠动力学介稳存在），介稳场景一律走 Hyp 伪元素。</li>
 * </ol>
 *
 * <p><b>价态池铁律</b>（实验定论，PLAN.md「G1b 补充实验」）：价态池输入（如 Cl(1)）只在
 * 初始解计算有效——任何批式反应步都会把同元素全部价态池坍缩到统一 pe
 * （输出标记 "Adjusted to redox equilibrium"）。
 */
public final class Database {

    private static volatile String cached;

    private Database() {}

    public static String sitWithAddenda() {
        String text = cached;
        if (text == null) {
            synchronized (Database.class) {
                if (cached == null) {
                    try {
                        String base = IPhreeqc.readResource("/db/sit.dat");
                        cached = base + '\n'
                                + Curation.load().addendumText() + '\n'
                                + CL1_REFERENCE;
                    } catch (IOException e) {
                        throw new IllegalStateException("读取 /db/sit.dat 失败", e);
                    }
                }
                text = cached;
            }
        }
        return text;
    }

    /**
     * Cl(+1) 平衡物种（文献常数：E°(HOCl/Cl⁻)=1.482 V、pKa(HOCl)=7.53）。
     *
     * <p>书写约定（与 sit.dat 一致，见 parse_eq 的 token 交换逻辑）：
     * 被定义物种 = 等号右侧首 token；log_k 按书写方向计。
     */
    static final String CL1_REFERENCE = """
            # ==== Cl(+1) equilibrium species (reference only; metastable scenarios use Hyp) ====
            SOLUTION_MASTER_SPECIES
            Cl(+1)         HOCl           0              Cl      52.46
            SOLUTION_SPECIES
            Cl- + H2O = HOCl + H+ + 2 e-
                    log_k    -50.13       # as written; reduction is +50.13 (E = 1.482 V)
            HOCl = OCl- + H+
                    log_k    -7.53        # as written; OCl- + H+ -> HOCl is +7.53 (pKa)
            # ==== end addendum ====
            """;

    /**
     * 提取全部 PHASES 段相名（sit.dat + 策展 addendum），供 {@link SiProbe#scanAll}。
     *
     * <p>解析规则：PHASES 段内，缩进行中不含 {@code =} 且非参数行（log_k/delta_h/-analytic
     * 等）的行 = 相名（相名可含空格，如 "Ferrihydrite(am)"）。粗解析仅供审计候选集，
     * 个别异常名最多造成一个空 SI 列，无致命风险。
     */
    public static java.util.List<String> phases() {
        return java.util.List.copyOf(SiProbe.phaseNamesFrom(sitWithAddenda()));
    }
}
