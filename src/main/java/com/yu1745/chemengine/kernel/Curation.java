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
            if (rx.conditionExpression == null || rx.conditionExpression.isBlank()) {
                sb.append("    10  r = ").append(rx.rateExpression).append('\n');
            } else {
                sb.append("    10  IF (").append(rx.conditionExpression).append(") THEN r = ")
                        .append(rx.rateExpression).append(" ELSE r = 0\n");
            }
            // JSON 的 r 是 mol/kgw/s；PHREEQC 的 SAVE 必须返回本次体系的 mol。
            // TOT("water") 的单位是 kg water（PHREEQC BASIC reference）。
            sb.append("    20  SAVE r * TIME * TOT(\"water\")\n");
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
            if (!Double.isFinite(pe.molarMass) || pe.molarMass <= 0) {
                throw new IllegalStateException("摩尔质量必须 > 0: " + pe.element);
            }
        }
        for (PseudoElement pe : pseudoElements) {
            if (pe.atoms == null || pe.atoms.isEmpty()) {
                throw new IllegalStateException("伪元素缺真实原子组成: " + pe.element);
            }
            for (Map.Entry<String, Double> atom : pe.atoms.entrySet()) {
                if (atom.getKey() == null || !atom.getKey().matches("[A-Z][a-z]?")
                        || atom.getValue() == null || !Double.isFinite(atom.getValue()) || atom.getValue() <= 0) {
                    throw new IllegalStateException("伪元素原子组成非法: " + pe.element + " / " + atom);
                }
                if (byElement.containsKey(atom.getKey())) {
                    throw new IllegalStateException("伪元素组成不得引用伪元素: " + pe.element + " / " + atom.getKey());
                }
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
            if (rx.conditionExpression != null && rx.conditionExpression.isBlank()) {
                throw new IllegalStateException("反应工况条件不能为空白: " + rx.name);
            }
            int parmRefs = Math.max(countParmRefs(rx.rateExpression), countParmRefs(rx.conditionExpression));
            int parmsLen = rx.parms == null ? 0 : rx.parms.length;
            if (parmRefs > parmsLen) {
                throw new IllegalStateException("速率表达式引用 PARM(" + parmRefs + ") 但 parms 只有 "
                        + parmsLen + " 个（反应 " + rx.name + "）");
            }
            for (Map.Entry<String, Double> term : rx.formula.entrySet()) {
                String token = term.getKey();
                if (token == null || term.getValue() == null || !Double.isFinite(term.getValue()) || term.getValue() == 0) {
                    throw new IllegalStateException("-formula 系数非法: " + rx.name + " / " + term);
                }
                if (token.contains("(") || token.contains(")")) {
                    throw new IllegalStateException(
                            "-formula 不得用价态 token（毁池）: " + rx.name + " / " + token);
                }
                if (!byElement.containsKey(token) && !token.matches("[A-Z][a-z]?")) {
                    throw new IllegalStateException("-formula 只允许真实元素或登记伪元素: "
                            + rx.name + " / " + token);
                }
            }
            if (rx.reservoirAtoms == null) rx.reservoirAtoms = Collections.emptyMap();
            for (Map.Entry<String, Double> atom : rx.reservoirAtoms.entrySet()) {
                if (atom.getKey() == null || !atom.getKey().matches("[A-Z][a-z]?")
                        || atom.getValue() == null || !Double.isFinite(atom.getValue())) {
                    throw new IllegalStateException("外部储库原子流非法: " + rx.name + " / " + atom);
                }
            }
            ExpandedFormulaAudit audit = expandedFormulaAudit(rx, byElement);
            if (rx.kindEnum() == Kind.BULK && !audit.isAtomConserving()) {
                throw new IllegalStateException("bulk 反应展开后不守恒: " + rx.name + " / "
                        + audit.atomDelta);
            }
            if (rx.kindEnum() == Kind.INTERFACE && !sameAtoms(audit.atomDelta, rx.reservoirAtoms)) {
                throw new IllegalStateException("interface 反应的外部储库原子流不匹配: " + rx.name
                        + " / formula=" + audit.atomDelta + " / reservoir=" + rx.reservoirAtoms);
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

    /**
     * 将 {@code -formula} 的伪元素 token 展开为真实原子源项。
     *
     * <p>这是元素守恒审计，<strong>不是</strong>离子反应式。KINETICS 的 -formula
     * 仅接受元素源项，不能以伪 master 的离子电荷对 H/O/Cl 等元素 token 再做电荷配平。
     */
    public ExpandedFormulaAudit expandedFormulaAudit(Reaction reaction) {
        Map<String, PseudoElement> byElement = new LinkedHashMap<>();
        for (PseudoElement pe : pseudoElements) byElement.put(pe.element, pe);
        return expandedFormulaAudit(reaction, byElement);
    }

    private static ExpandedFormulaAudit expandedFormulaAudit(Reaction reaction,
                                                              Map<String, PseudoElement> pseudoByElement) {
        Map<String, Double> delta = new TreeMap<>();
        for (Map.Entry<String, Double> term : reaction.formula.entrySet()) {
            PseudoElement pseudo = pseudoByElement.get(term.getKey());
            if (pseudo == null) {
                delta.merge(term.getKey(), term.getValue(), Double::sum);
            } else {
                for (Map.Entry<String, Double> atom : pseudo.atoms.entrySet()) {
                    delta.merge(atom.getKey(), term.getValue() * atom.getValue(), Double::sum);
                }
            }
        }
        delta.entrySet().removeIf(e -> Math.abs(e.getValue()) < 1e-12);
        return new ExpandedFormulaAudit(Collections.unmodifiableMap(delta));
    }

    private static boolean sameAtoms(Map<String, Double> left, Map<String, Double> right) {
        if (right == null) return left.isEmpty();
        Map<String, Double> normalized = new TreeMap<>(right);
        normalized.entrySet().removeIf(e -> Math.abs(e.getValue()) < 1e-12);
        if (!left.keySet().equals(normalized.keySet())) return false;
        for (Map.Entry<String, Double> entry : left.entrySet()) {
            if (Math.abs(entry.getValue() - normalized.get(entry.getKey())) >= 1e-12) {
                return false;
            }
        }
        return true;
    }

    /** 计算速率表达式里最大的 PARM(n) 索引（校验 parms 覆盖）。 */
    private static int countParmRefs(String expr) {
        if (expr == null) return 0;
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
        /** 该内部池每 mol 所代表的真实元素原子数；仅用于守恒审计。 */
        public Map<String, Double> atoms = Collections.emptyMap();
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
        /** interface 反应由外部气/液储库注入本体系的真实原子量；bulk 必须为空。 */
        public Map<String, Double> reservoirAtoms = Collections.emptyMap();
        public String rateExpression;
        /** Optional PHREEQC BASIC boolean condition; false means a zero rate without changing stoichiometry. */
        public String conditionExpression;
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

    /** 真实原子源项审计结果；空 map 表示元素守恒。 */
    public record ExpandedFormulaAudit(Map<String, Double> atomDelta) {
        public boolean isAtomConserving() {
            return atomDelta.isEmpty();
        }
    }
}
