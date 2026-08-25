package com.yu1745.chemicaladdon.parity;

import org.junit.jupiter.api.Test;

import com.yu1745.chemengine.kernel.Curation;
import com.yu1745.chemengine.kernel.IPhreeqc;

/** 内核单拍耗时探针（无 MC 依赖，性能回归定位用）。 */
class KernelPerfProbe {

	@Test
	void runTiming() {
		Curation curation = Curation.load();
		// 典型主循环脚本：水 + Na/Hyp 漂白液，bulk 全量 + KINETICS 0.5s
		String script = "SOLUTION 1 tick\n"
				+ "    temp      25.0\n"
				+ "    pH        7 charge\n"
				+ "    water     0.98 kg\n"
				+ "    Na  0.00869817983906478 mol/kgw\n"
				+ "    Hyp 0.0038884061726024437 mol/kgw\n"
				+ "SELECTED_OUTPUT 1\n"
				+ "    -state          true\n"
				+ "    -time           true\n"
				+ "    -high_precision true\n"
				+ "    -totals   Na  Hyp  Sul  Nitra  Nitri  Cl  S\n"
				+ "    -pH       true\n"
				+ "    -pe       true\n"
				+ "END\n"
				+ curation.ratesBlock()
				+ "USE solution 1\n"
				+ curation.kineticsBlock(null, null, 0.5) + "\n"
				+ "END\n";

		long t0 = System.nanoTime();
		IPhreeqc q1 = IPhreeqc.create();
		IPhreeqc.RunResult r1 = q1.run(script);
		long first = (System.nanoTime() - t0) / 1_000_000;

		// 共享会话复跑（TickDriver 现路径）
		IPhreeqc q2 = IPhreeqc.create();
		q2.run(script); // 预热
		int n = 20;
		long t1 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			q2.run(script);
		}
		long per = (System.nanoTime() - t1) / 1_000_000 / n;
		System.out.println("[perf] first(create+装库+跑)=" + first + "ms, shared per-run=" + per
				+ "ms, rows=" + r1.rowCount());
		// 性能哨兵（宽松上界，防共享会话被退回每步重建实例的老路）
		org.junit.jupiter.api.Assertions.assertTrue(per < 500,
				"共享会话单拍应远低于 500ms（实测 " + per + "ms）——若超标，检查是否回退成了每步 create+装库");
		q1.close();
		q2.close();
	}
}
