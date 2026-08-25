package com.yu1745.chemicaladdon.composition.parity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.yu1745.chemicaladdon.composition.Equilibrium;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;

import net.minecraft.resources.ResourceLocation;

/**
 * 固相桥（P7）：物种 JSON 的 equilibria（Ksp，旧引擎同源数据）→ PHREEQC
 * inline PHASES + EQUILIBRIUM_PHASES。
 *
 * <p>规则（PhaseProbe 实验验证）：
 * <ul>
 *   <li>相名 {@code mod_<path>}（mod 前缀，不与 sit.dat 1848 相撞）；</li>
 *   <li>方程 LHS = 物种 formula（元素配平账本），RHS = equilibria 右侧离子 token
 *       翻译成 sit.dat 命名（单价 ±1 去尾数：OH-1→OH-、Ag+1→Ag+；多价原样
 *       Ca+2/SO4-2）；</li>
 *   <li>EQUILIBRIUM_PHASES 目标 SI 0、初始量 = 当前悬浮 part（mol = part×1e-3）：
 *       过饱和自动析出（初始 0 也析）、欠饱和回溶到饱和——「自发的归规则引擎」；</li>
 *   <li>相摩尔经 USER_PUNCH {@code EQUI("mod_x")} punch（绝对 mol，非 mol/kgw；
 *       {@code -equilibrium_phases} 标识符在本版 PHREEQC 不产列，PUNCH 多值必须分行）；</li>
 *   <li>曲线物种（rock_salt/copper_sulfate，溶解度表）与伪池固相（Sul 等，
 *       元素账不平，结构性不可能）不参与——结晶曲线/伪池语义不变。</li>
 * </ul>
 */
public final class PhaseBridge {

	/** species id → 相定义（方程 + 稳定顺序）。 */
	private static final Map<ResourceLocation, PhaseDef> BY_SPECIES = build();

	/** phase 名 → species id（punch 列名反查）。 */
	private static final Map<String, ResourceLocation> BY_PHASE = invert(BY_SPECIES);

	/** 一个可析出/可回溶的固相（来自某个 SOLID 物种的首条矿物 equilibria）。 */
	public record PhaseDef(ResourceLocation species, String phaseName, String equation, double logK,
			java.util.List<String> elements) {}

	private record Def(String equation, double logK) {}

	static boolean has(ResourceLocation species) {
		return BY_SPECIES.containsKey(species);
	}

	/** 物种 id → 相定义（无相 = null）。 */
	static PhaseDef def(ResourceLocation species) {
		return BY_SPECIES.get(species);
	}

	/** part → mol（1 mB = 1e-3 mol FU；与离子同标度；与 WriteBack.PARTS_PER_MOL 互逆）。 */
	static final double MOL_PER_PART = EngineBridge.UNITS_PER_MOL / 1.0e10; // 1e-3

	/** 相牵扯的元素 token（punch 列补全用：矿物 RHS 离子 → 元素/伪池，H/O 不出）。 */
	private static java.util.List<String> elementsOf(Equilibrium eq) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (Equilibrium.Term t : eq.right()) {
			if (t.phase() != Equilibrium.TermPhase.ION) {
				continue;
			}
			String sym = t.key().replaceAll("[+-]\\d+$", "");
			String pool = EngineBridge.PSEUDO_POOLS.get(sym);
			if (pool != null) {
				if (!out.contains(pool)) {
					out.add(pool);
				}
				continue;
			}
			java.util.Map<String, Integer> group = EngineBridge.GROUPS.get(sym);
			String el = group != null ? group.keySet().iterator().next() : sym;
			if (!"H".equals(el) && !"O".equals(el) && EngineBridge.ATOMIC.containsKey(el) && !out.contains(el)) {
				out.add(el);
			}
		}
		return out;
	}

	/** 全部相（稳定顺序；公开给化验行）。 */
	public static Collection<PhaseDef> all() {
		return BY_SPECIES.values();
	}

	/** punch 列名 → species id。 */
	static ResourceLocation speciesOf(String phaseName) {
		return BY_PHASE.get(phaseName);
	}

	/** 相名表（punch/USER_PUNCH 生成与解析共用）。 */
	static Collection<ResourceLocation> speciesList() {
		return BY_SPECIES.keySet();
	}

	/** PHASES 块（inline 定义，sit.dat 缺什么补什么）。 */
	static String phasesBlock() {
		if (BY_SPECIES.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("PHASES\n");
		for (PhaseDef d : BY_SPECIES.values()) {
			sb.append(d.phaseName()).append('\n');
			sb.append("    ").append(d.equation()).append('\n');
			sb.append(String.format("    log_k    %.4g%n", d.logK()));
		}
		return sb.toString();
	}

	/** EQUILIBRIUM_PHASES 块（目标 SI 0；initial = phaseName → 当前悬浮 mol，缺省 0）。 */
	static String equilibriumPhasesBlock(Map<String, Double> initial) {
		if (BY_SPECIES.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("EQUILIBRIUM_PHASES 1\n");
		for (PhaseDef d : BY_SPECIES.values()) {
			double mol = initial.getOrDefault(d.phaseName(), 0.0);
			sb.append("    ").append(d.phaseName()).append(" 0 ").append(String.format("%.8g", mol)).append('\n');
		}
		return sb.toString();
	}

	/** USER_PUNCH 块（每个相两行：EQUI 终量 + SI 饱和指数；多值 PUNCH 分行是实验铁律）。 */
	static String userPunchBlock() {
		if (BY_SPECIES.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder("USER_PUNCH 1\n    -headings");
		for (PhaseDef d : BY_SPECIES.values()) {
			sb.append(' ').append(d.phaseName()).append(" si_").append(d.phaseName());
		}
		sb.append("\n    -start\n");
		int line = 10;
		for (PhaseDef d : BY_SPECIES.values()) {
			sb.append("    ").append(line++).append(" PUNCH EQUI(\"")
					.append(d.phaseName()).append("\")\n");
			sb.append("    ").append(line++).append(" PUNCH SI(\"")
					.append(d.phaseName()).append("\")\n");
		}
		return sb + "    -end\n";
	}

	private static Map<ResourceLocation, PhaseDef> build() {
		Map<ResourceLocation, PhaseDef> out = new LinkedHashMap<>();
		for (Species sp : SpeciesManager.all()) {
			if (sp.phase() != Species.Phase.SOLID) {
				continue;
			}
			for (Equilibrium eq : sp.equilibria()) {
				if (eq.solid() == null) {
					continue; // 水相条目（络合等）不是相定义
				}
				String rhs = translateRhs(eq);
				if (rhs == null) {
					break; // 有无法翻译的 token → 该物种整体不参与（宁缺毋错）
				}
				String phaseName = "mod_" + sp.id().getPath();
				out.put(sp.id(), new PhaseDef(sp.id(), phaseName,
						sp.formula() + " = " + rhs, eq.logK(), elementsOf(eq)));
				break; // 首条矿物条目即相定义
			}
		}
		return Map.copyOf(out);
	}

	private static Map<String, ResourceLocation> invert(Map<ResourceLocation, PhaseDef> bySpecies) {
		Map<String, ResourceLocation> out = new LinkedHashMap<>();
		for (PhaseDef d : bySpecies.values()) {
			out.put(d.phaseName(), d.species());
		}
		return Map.copyOf(out);
	}

	/**
	 * equilibria 右侧 → sit.dat 命名：mod 存储 token 恒为 {@code Symbol±n}，
	 * sit.dat 单价无尾数（Cl-1→Cl-）、多价保留（Ca+2）。未知 token（络合物
	 * 括号形态等）返回 null——矿物 RHS 只应是简单离子。
	 */
	private static String translateRhs(Equilibrium eq) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Equilibrium.Term t : eq.right()) {
			if (t.phase() != Equilibrium.TermPhase.ION) {
				return null; // 分子 token 不该出现在矿物 RHS
			}
			String key = t.key();
			if (!key.matches("[A-Z][a-zA-Z0-9\\[\\]\\(]\\S*[+-]\\d+")) {
				return null; // 非规范离子 token（含括号/复杂形态）
			}
			if (!first) {
				sb.append(" + ");
			}
			first = false;
			if (t.count() > 1) {
				sb.append(t.count()).append(' ');
			}
			sb.append(toSit(key));
		}
		return first ? null : sb.toString();
	}

	/** {@code Symbol±n} → sit.dat 命名：|n|=1 去尾数，否则原样。 */
	private static String toSit(String token) {
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("^(.*?)([+-])(\\d+)$").matcher(token);
		if (!m.matches() || !"1".equals(m.group(3))) {
			return token;
		}
		return m.group(1) + m.group(2);
	}

	private PhaseBridge() {}
}
