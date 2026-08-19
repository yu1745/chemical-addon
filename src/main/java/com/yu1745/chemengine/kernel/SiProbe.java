package com.yu1745.chemengine.kernel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 饱和指数（SI）审计器：策展完备性的烟雾报警器。
 *
 * <p>质量账闭合检测不出<b>缺失的相</b>（Fe 沉不沉，九本元素账照样平——一锅汤实验教训：
 * EQUILIBRIUM_PHASES 白名单外的相 SI=5.33 引擎也只能干看着）。本工具对候选相集计算 SI，
 * 报告"过饱和且不在相组装名单"的相 = 场景缺策展的候选。
 *
 * <p>用法：
 * <pre>{@code
 * // 关心域扫描（推荐：给本场景相关的矿物家族）
 * List<SiProbe.Finding> risks = SiProbe.scan(q, 1.0,
 *     List.of("Ferrihydrite(am)", "Goethite", "Siderite"), "Calcite" /* 已声明 *\/);
 * // 全库扫描（Database.phases() 提取，代价是几百列 punch）
 * List<SiProbe.Finding> all = SiProbe.scanAll(q, 1.0, "Calcite");
 * }</pre>
 *
 * <p>注意：SI&gt;0 只是"热力学允许"，不等于"现实中会沉"（奥斯特瓦德阶段规则、动力学
 * 冻结——Hematite 对新鲜沉淀 SI 常 &gt;10 但 25°C 下地质时间才成相）。读表时优先看
 * <b>无定形/介稳变体</b>是否过饱和，它们才是"昨天就该沉"的真实候选；结晶完美变体的高
 * SI 是热力学地狱的路标，不是行动清单。
 *
 * <p>实现注意：scan 重算一次初始解（SOLUTION 不可 USE——USE 无触发器不 punch）；
 * `-saturation_indices` 需要具体相名，不支持 all（实测 "all" 被当字面相名静默出空列）。
 */
public final class SiProbe {

    /** 单个发现：相名、饱和指数、简单注记。 */
    public record Finding(String phase, double si, String note) {}

    private SiProbe() {}

    /**
     * 对当前会话最后定义的 solution 重算并扫描候选相。
     *
     * @param q 会话（需已用 {@code SOLUTION 1} 定义过溶液——scan 会重算它）
     * @param threshold 报警阈值（常用 1.0 = 过饱和 10 倍）
     * @param candidatePhases 要计算 SI 的相名清单（sit.dat + addendum 中存在的相）
     * @param declaredPhases 场景已声明（EQUILIBRIUM_PHASES）的相名——它们达到 SI=0 是本分
     */
    public static List<Finding> scan(IPhreeqc q, double threshold,
                                     List<String> candidatePhases, String... declaredPhases) {
        if (candidatePhases == null || candidatePhases.isEmpty()) {
            throw new IllegalArgumentException("候选相清单为空");
        }
        // 纯 SELECTED_OUTPUT 不触发计算（无 punch 行），且 -saturation_indices 在 i_soln
        // 状态会静默丢行（PA/P6 探针实验）：用 1 µmol 惰性 REACTION 触发器取 react 状态
        StringBuilder so = new StringBuilder("USE solution 1\nREACTION 1\n    Na 1\n    1 umol in 1 step\n");
        so.append("SELECTED_OUTPUT 1\n");
        so.append("    -saturation_indices");
        for (String p : candidatePhases) {
            so.append(' ').append(p);
        }
        so.append("\nEND\n");
        IPhreeqc.RunResult r = q.run(so.toString());
        if (r.rowCount() < 1) {
            throw new IPhreeqcException("SI 扫描无输出行（会话未定义过 solution？）");
        }

        List<Finding> out = new ArrayList<>();
        IPhreeqc.RunResult.Row row = r.row(r.rowCount() - 1);
        for (String p : candidatePhases) {
            double si = row.dOr("si_" + p, Double.NEGATIVE_INFINITY);
            if (si > threshold && !isDeclared(p, declaredPhases)) {
                out.add(new Finding(p, si, classify(p)));
            }
        }
        out.sort((a, b) -> Double.compare(b.si(), a.si()));
        return out;
    }

    /** 全库扫描：候选相 = sit.dat + 策展 addendum 的全部 PHASES 段相名。 */
    public static List<Finding> scanAll(IPhreeqc q, double threshold, String... declaredPhases) {
        return scan(q, threshold, Database.phases(), declaredPhases);
    }

    private static boolean isDeclared(String phase, String... declared) {
        for (String d : declared) {
            if (d.equalsIgnoreCase(phase)) {
                return true;
            }
        }
        return false;
    }

    /** 简单分类注记：无定形/介稳变体 = 现实候选；结晶完美变体 = 热力学路标。 */
    private static String classify(String phase) {
        String p = phase.toLowerCase();
        if (p.contains("(am)") || p.contains("amorph") || p.contains("active")) {
            return "无定形/活性变体：过饱和 = 新鲜沉淀候选（优先处理）";
        }
        if (p.contains("(cr)") || p.contains("(s)")) {
            return "结晶变体：高 SI 多为奥斯特瓦德终点（地质时间），谨慎解读";
        }
        return "";
    }

    /** （备用）从数据库文本提取 PHASES 段相名，见 {@link Database#phases()}。 */
    static Set<String> phaseNamesFrom(String dbText) {
        Set<String> names = new LinkedHashSet<>();
        boolean inPhases = false;
        for (String rawLine : dbText.split("\n", -1)) {
            String line = rawLine.replaceFirst("\\r$", "");
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (!Character.isWhitespace(line.charAt(0))) {
                String keyword = line.trim().split("\\s+")[0];
                inPhases = keyword.equals("PHASES");
                continue;
            }
            if (inPhases) {
                String head = line.trim().split("\\s+")[0];
                // 相方程行以缩进开头且含 '='；相名行缩进但无 '='（名称可能带空格，取全行）
                String trimmed = line.trim();
                if (!trimmed.contains("=") && !trimmed.startsWith("log_k")
                        && !trimmed.startsWith("delta_h") && !trimmed.startsWith("-")
                        && !trimmed.startsWith("no_check") && !trimmed.startsWith("analytic")
                        && !head.equals("log_k") && !trimmed.matches("\\d+.*")) {
                    names.add(trimmed.split("\\s+#")[0].trim());
                }
            }
        }
        return names;
    }
}
