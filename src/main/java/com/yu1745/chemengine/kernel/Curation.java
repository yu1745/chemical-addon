package com.yu1745.chemengine.kernel;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 策展表（G2）：{@code resources/curation/chemistry.json} 的加载器与 PHREEQC 文本生成器。
 *
 * <p>策展表是"手工内容的边界"——介稳伪元素注册（哪些慢反应被冻结成独立池）+
 * 动力学白名单（哪些反应允许跨池发生 + 化学计量 + k 值）。与 sit.dat 同性质的数据，
 * 非逐场景脚本；k 值是游戏节奏旋钮。
 *
 * <p>生成三块文本：
 * <ul>
 *   <li>{@link #addendumText()}：数据库 addendum（SOLUTION_MASTER_SPECIES + SOLUTION_SPECIES），
 *       由 {@link Database} 拼接在 sit.dat 之后；</li>
 *   <li>{@link #ratesBlock()}：RATES 块（全部白名单反应的 BASIC 速率程序）；</li>
 *   <li>{@link #kineticsBlock(double...)}：KINETICS 块（-formula 化学计量 + -steps 时间步）。</li>
 * </ul>
 *
 * <p>语法铁律（实验验证，见 PLAN.md「G1b 补充实验」）：
 * 物种名不得含真实元素 token（HypOCl- 会按 Hyp-O-Cl 解析，向每个组成元素的
 * 物料账本缴税）；被定义物种 = 等号右侧首 token；-formula 只用元素/伪元素 token。
 */
public final class Curation {

    private static final String RESOURCE = "/curation/chemistry.json";
    private static final Gson GSON = new Gson();

    private final List<PseudoElement> pseudoElements;
    private final List<Phase> phases;
    private final List<Reaction> reactions;

    private Curation(List<PseudoElement> pseudoElements, List<Phase> phases, List<Reaction> reactions) {
        this.pseudoElements = List.copyOf(pseudoElements);
        this.phases = phases == null ? List.of() : List.copyOf(phases);
        this.reactions = List.copyOf(reactions);
        validate();
    }

    /** 从 classpath 装载默认策展表。 */
    public static Curation load() {
        try (InputStream in = Curation.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("classpath 资源缺失: " + RESOURCE);
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Raw raw = GSON.fromJson(r, Raw.class);
                return new Curation(raw.pseudoElements, raw.phases, raw.reactions);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取 " + RESOURCE + " 失败", e);
        }
    }

    public List<PseudoElement> pseudoElements() {
        return pseudoElements;
    }

    /** 策展平衡相（sit.dat 缺失的固体/气体，如 Pyrolusite）。 */
    public List<Phase> phases() {
        return phases;
    }

    public Phase phase(String name) {
        return phases.stream().filter(x -> x.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("策展表无相: " + name));
    }

    public List<Reaction> reactions() {
        return reactions;
    }

    public Reaction reaction(String name) {
        return reactions.stream().filter(x -> x.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("策展表无反应: " + name));
    }

    // ==== 文本生成 ====

    /** 数据库 addendum（伪元素 master + 物种方程 + 策展平衡相）。keyword 段归拢：每类一个块。 */
    public String addendumText() {
        StringBuilder masters = new StringBuilder();
        StringBuilder species = new StringBuilder();
        for (PseudoElement pe : pseudoElements) {
            masters.append(String.format("%-15s %-15s %-15s %-8s %.4f%n",
                    pe.element, pe.master, "0", pe.element, pe.molarMass));
            if (pe.species.isEmpty()) {
                throw new IllegalStateException("伪元素 " + pe.element + " 无物种");
            }
            boolean hasIdentity = false;
            for (Species s : pe.species) {
                if (s.equation.startsWith(pe.master + " =")) {
                    hasIdentity = true;
                }
                species.append(s.equation).append('\n');
                species.append(String.format("    log_k    %s%n", num(s.logK)));
                if (s.note != null && !s.note.isBlank()) {
                    species.append("    # ").append(s.note).append('\n');
                }
            }
            if (!hasIdentity) {
                // master 必须是某方程的被定义物种（右侧首 token）
                throw new IllegalStateException(
                        "伪元素 " + pe.element + " 缺 master 恒等式: " + pe.master + " = " + pe.master);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ==== chemistry-addon addendum: metastable pseudo-elements (curated) ====\n");
        sb.append("SOLUTION_MASTER_SPECIES\n").append(masters);
        sb.append("SOLUTION_SPECIES\n").append(species);
        if (!phases.isEmpty()) {
            sb.append("# ==== curated equilibrium phases (missing from sit.dat) ====\n");
            sb.append("PHASES\n");
            for (Phase ph : phases) {
                sb.append(ph.name).append('\n');
                sb.append("    ").append(ph.equation).append('\n');
                sb.append(String.format("    log_k    %s%n", num(ph.logK)));
                if (ph.deltaH != null && !ph.deltaH.isBlank()) {
                    sb.append("    delta_h ").append(ph.deltaH).append('\n');
                }
                if (ph.note != null && !ph.note.isBlank()) {
                    sb.append("    # ").append(ph.note).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** RATES 块（全部白名单反应）。 */
    public String ratesBlock() {
        StringBuilder sb = new StringBuilder("RATES\n");
        for (Reaction rx : reactions) {
            sb.append(rx.name).append('\n');
            sb.append("    -start\n");
            sb.append("    10  r = ").append(rx.rateExpression).append('\n');
            sb.append("    20  SAVE r * TIME\n");
            sb.append("    -end\n");
        }
        return sb.toString();
    }

    /** KINETICS 块（全部白名单反应 + 给定时间步，单位秒）。 */
    public String kineticsBlock(double... stepsSeconds) {
        return kineticsBlock(null, null, stepsSeconds);
    }

    /** KINETICS 块（可覆盖反应参数：游戏侧供压/供流量等）。 */
    public String kineticsBlock(Map<String, double[]> parmOverrides, double... stepsSeconds) {
        return kineticsBlock(null, parmOverrides, stepsSeconds);
    }

    /**
     * KINETICS 块（反应包含过滤 + 参数覆盖）。
     *
     * @param include 仅这些反应进入 KINETICS；null = 全部 <b>bulk</b> 反应
     *                （interface 反应必须显式 opt-in——它们建模与外部储库的交换，
     *                默认在场等于接了一个虚拟大气/虚拟母液，会污染场景）；
     *                游戏侧选择本容器活跃的过程
     * @param parmOverrides 反应名 → 参数数组（覆盖 JSON 默认 parms；未覆盖的用默认，
     *                      JSON 未定义 parms 的反应不输出 -parms 行）
     */
    public String kineticsBlock(java.util.Set<String> include,
                                 Map<String, double[]> parmOverrides, double... stepsSeconds) {
        if (stepsSeconds.length == 0) {
            throw new IllegalArgumentException("至少一个时间步");
        }
        StringBuilder steps = new StringBuilder();
        for (double s : stepsSeconds) {
            steps.append(' ').append(num(s));
        }
        StringBuilder sb = new StringBuilder("KINETICS 1\n");
        for (Reaction rx : reactions) {
            if (include == null && rx.kindEnum() == Kind.INTERFACE) {
                continue;  // 界面反应默认不发射（防虚拟大气污染）
            }
            if (include != null && !include.contains(rx.name)) {
                continue;
            }
            sb.append(rx.name).append('\n');
            sb.append("    -formula");
            // TreeMap 保证生成文本稳定（测试可断言）
            for (Map.Entry<String, Double> e : new TreeMap<>(rx.formula).entrySet()) {
                sb.append(' ').append(e.getKey()).append(' ').append(num(e.getValue()));
            }
            sb.append('\n');
            double cap = rx.capacity > 0 ? rx.capacity : 1000.0;
            sb.append("    -m        ").append(num(cap)).append('\n');
            sb.append("    -m0       ").append(num(cap)).append('\n');
            double[] parms = rx.parms;
            if (parmOverrides != null && parmOverrides.containsKey(rx.name)) {
                parms = parmOverrides.get(rx.name);
            }
            if (parms != null && parms.length > 0) {
                sb.append("    -parms   ");
                for (double p : parms) {
                    sb.append(' ').append(num(p));
                }
                sb.append('\n');
            }
        }
        sb.append("    -steps   ").append(steps.toString().trim()).append(" seconds\n");
        return sb.toString();
    }

    private static String num(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        String s = String.format("%.6g", v);
        if (s.indexOf('.') >= 0 && s.indexOf('E') < 0) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    private void validate() {
        Map<String, PseudoElement> byElement = new LinkedHashMap<>();
        for (PseudoElement pe : pseudoElements) {
            if (pe.element == null || pe.element.isBlank()) {
                throw new IllegalStateException("伪元素名为空");
            }
            if (byElement.put(pe.element, pe) != null) {
                throw new IllegalStateException("伪元素重复: " + pe.element);
            }
            if (pe.master == null || !pe.master.contains(pe.element)) {
                throw new IllegalStateException(
                        "master 物种名必须含元素 token: " + pe.element + " / " + pe.master);
            }
            if (pe.molarMass <= 0) {
                throw new IllegalStateException("摩尔质量必须 > 0: " + pe.element);
            }
        }
        Map<String, Reaction> byName = new LinkedHashMap<>();
        for (Reaction rx : reactions) {
            if (rx.name == null || rx.name.isBlank()) {
                throw new IllegalStateException("反应名为空");
            }
            if (byName.put(rx.name, rx) != null) {
                throw new IllegalStateException("反应重复: " + rx.name);
            }
            if (rx.formula == null || rx.formula.isEmpty()) {
                throw new IllegalStateException("反应无化学计量: " + rx.name);
            }
            if (rx.rateExpression == null || rx.rateExpression.isBlank()) {
                throw new IllegalStateException("反应无速率表达式: " + rx.name);
            }
            int parmRefs = countParmRefs(rx.rateExpression);
            int parmsLen = rx.parms == null ? 0 : rx.parms.length;
            if (parmRefs > parmsLen) {
                throw new IllegalStateException("速率表达式引用 PARM(" + parmRefs + ") 但 parms 只有 "
                        + parmsLen + " 个（反应 " + rx.name + "）");
            }
            for (String token : rx.formula.keySet()) {
                if (token.contains("(") || token.contains(")")) {
                    throw new IllegalStateException(
                            "-formula 不得用价态 token（毁池）: " + rx.name + " / " + token);
                }
            }
        }
        for (Phase ph : phases) {
            if (ph.name == null || ph.name.isBlank()) {
                throw new IllegalStateException("相名为空");
            }
            if (ph.equation == null || !ph.equation.contains("=")) {
                throw new IllegalStateException("相方程缺等号: " + ph.name);
            }
        }
    }

    /** 计算速率表达式里最大的 PARM(n) 索引（校验 parms 覆盖）。 */
    private static int countParmRefs(String expr) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("PARM\\((\\d+)\\)").matcher(expr);
        int max = 0;
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max;
    }

    // ==== JSON 模型（gson 反序列化目标；字段名与 chemistry.json 对应） ====

    private static final class Raw {
        List<PseudoElement> pseudoElements;
        List<Phase> phases;
        List<Reaction> reactions;
    }

    /** 策展平衡相（sit.dat 缺失，常数取自其他官方库/文献）。 */
    public static final class Phase {
        public String name;
        public String equation;
        public double logK;
        public String deltaH;
        public String note;
    }

    /** 介稳伪元素：独立守恒池 + 池内酸碱形态。 */
    public static final class PseudoElement {
        public String element;
        public String label;
        public double molarMass;
        public String master;
        public List<Species> species;
    }

    public static final class Species {
        public String equation;
        public double logK;
        public String note;
    }

    /** 反应类型：bulk=液内（默认全量发射时包含）；interface=储库交换（须显式 opt-in，否则虚拟大气污染场景）。 */
    public enum Kind { BULK, INTERFACE }

    /** 动力学白名单反应：元素级化学计量 + BASIC 速率表达式 + 容量上限。 */
    public static final class Reaction {
        public String name;
        public String note;
        public Map<String, Double> formula = Collections.emptyMap();
        public String rateExpression;
        public double capacity = 1000.0;
        public double[] parms;
        public List<String> parmDoc;
        public String kind;

        public Kind kindEnum() {
            return "interface".equalsIgnoreCase(kind) ? Kind.INTERFACE : Kind.BULK;
        }

        public Map<String, Double> formulaView() {
            return Collections.unmodifiableMap(formula);
        }
    }
}
