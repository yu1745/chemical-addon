package com.yu1745.chemicaladdon.parity;

import org.junit.jupiter.api.Test;

import com.yu1745.chemengine.kernel.IPhreeqc;

/**
 * 探针（非回归测试）：验证固相桥的 PHREEQC 脚本语法假设——
 * ① inline PHASES（mod_ 前缀，不与 sit.dat 相撞）；
 * ② EQUILIBRIUM_PHASES 目标 SI 0：过饱和自动析出（初始 0 mol 也会析）、
 *    欠饱和回溶（初始 = 当前悬浮量）——<b>必须挂在首个模拟里</b>，
 *    否则 punch 表头不含相列（表头由首次 punch 的模拟决定）；
 * ③ USER_PUNCH + EQUI("phase") punch 相摩尔（绝对量，非 mol/kgw；
 * ④ mod 方程 token 翻译（OH-1→OH- 等单价电荷）后 sit.dat 物种可解析。
 */
class PhaseProbe {

	@Test
	void limestonePrecipitatesFromCaCarbonate() {
		String script = """
				PHASES
				mod_limestone
				    CaCO3 = Ca+2 + CO3-2
				    log_k    -8.3
				mod_gypsum
				    CaSO4 = Ca+2 + SO4-2
				    log_k    -4.6
				SOLUTION 1 tick
				    temp      25
				    pH        7 charge
				    water     1 kg
				    Ca   0.3 mol/kgw
				    C    0.3 mol/kgw
				    Na   0.3 mol/kgw
				    Cl   0.3 mol/kgw
				EQUILIBRIUM_PHASES 1
				    mod_limestone 0 0
				    mod_gypsum 0 0
				SELECTED_OUTPUT 1
				    -state  true
				    -pH     true
				    -totals  Ca C Na Cl
				USER_PUNCH 1
				    -headings mod_limestone mod_gypsum
				    -start
				    10 PUNCH EQUI("mod_limestone")
				    20 PUNCH EQUI("mod_gypsum")
				    -end
				END
				USE solution 1
				USE equilibrium_phases 1
				END
				""";
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.run(script);
			System.out.println("[dbg] rows=" + r.rowCount() + " warn=[" + r.warnings() + "]");
			int last = r.rowCount() - 1;
			System.out.println("[phase] rows=" + r.rowCount() + " pH=" + r.row(last).d("pH")
					+ " mod_limestone=" + r.row(last).dOr("mod_limestone", -1)
					+ " mod_gypsum=" + r.row(last).dOr("mod_gypsum", -1)
					+ " Ca=" + r.row(last).d("Ca") + " C=" + r.row(last).d("C"));
			org.junit.jupiter.api.Assertions.assertTrue(r.row(last).dOr("mod_limestone", -1) > 0.29,
					"石灰石应析出 ~0.3 mol");
		}
	}

	@Test
	void gypsumDissolvesToKsp() {
		String script = """
				PHASES
				mod_gypsum
				    CaSO4 = Ca+2 + SO4-2
				    log_k    -4.6
				SOLUTION 1 tick
				    temp      25
				    pH        7 charge
				    water     1 kg
				EQUILIBRIUM_PHASES 1
				    mod_gypsum 0 0.2
				SELECTED_OUTPUT 1
				    -state  true
				    -pH     true
				    -totals  Ca S
				USER_PUNCH 1
				    -headings mod_gypsum
				    -start
				    10 PUNCH EQUI("mod_gypsum")
				    -end
				END
				USE solution 1
				USE equilibrium_phases 1
				END
				""";
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.run(script);
			System.out.println("[dbg] rows=" + r.rowCount() + " warn=[" + r.warnings() + "]");
			int last = r.rowCount() - 1;
			double left = r.row(last).dOr("mod_gypsum", -1);
			double ca = r.row(last).d("Ca");
			System.out.println("[phase] gypsum left=" + left + " mol, aqueous Ca=" + ca
					+ " mol/kgw (Ksp=1e-4.6 → 饱和 ≈ " + Math.pow(10, -2.3) + ")");
			// 0.2 mol 悬浮石膏在 1 kg 水里：溶解到饱和（络合+活度让总量 Ca ~0.0156 高于自由离子预期）
			org.junit.jupiter.api.Assertions.assertTrue(left > 0.17 && left < 0.2, "石膏应溶解到 Ksp");
			org.junit.jupiter.api.Assertions.assertTrue(ca > 0.004 && ca < 0.02, "水相应饱和");
		}
	}

	@Test
	void slakedLimeNeutralisesAcidToGypsum() {
		// 石灰乳 + 硫酸：Ca(OH)2 相溶解 → 中和 → Ca+SO4 过饱和 → 石膏析出
		String script = """
				PHASES
				mod_slaked_lime
				    Ca(OH)2 = Ca+2 + 2 OH-
				    log_k    -5.2
				mod_gypsum
				    CaSO4 = Ca+2 + SO4-2
				    log_k    -4.6
				SOLUTION 1 tick
				    temp      25
				    pH        7 charge
				    water     1 kg
				    S    0.2 mol/kgw
				EQUILIBRIUM_PHASES 1
				    mod_slaked_lime 0 0.2
				    mod_gypsum 0 0
				SELECTED_OUTPUT 1
				    -state  true
				    -pH     true
				    -totals  Ca S
				USER_PUNCH 1
				    -headings mod_slaked_lime mod_gypsum
				    -start
				    10 PUNCH EQUI("mod_slaked_lime")
				    20 PUNCH EQUI("mod_gypsum")
				    -end
				END
				USE solution 1
				USE equilibrium_phases 1
				END
				""";
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.run(script);
			System.out.println("[dbg] rows=" + r.rowCount() + " warn=[" + r.warnings() + "]");
			int last = r.rowCount() - 1;
			System.out.println("[phase] pH=" + r.row(last).d("pH")
					+ " gypsum=" + r.row(last).dOr("mod_gypsum", -1)
					+ " lime=" + r.row(last).dOr("mod_slaked_lime", -1));
			double ph = r.row(last).d("pH");
			double gyp = r.row(last).dOr("mod_gypsum", -1);
			org.junit.jupiter.api.Assertions.assertTrue(gyp > 0.1, "0.2 mol S 应几乎全部析石膏（等当量石灰恰好中和）");
			org.junit.jupiter.api.Assertions.assertTrue(ph > 7.0, "石灰消耗硫酸、石膏大量析出（精确终点由真实 Ksp 决定，此处只验机制）");
		}
	}
}
