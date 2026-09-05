package com.yu1745.chemengine.kernel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 一个 IPhreeqc 原生实例的 Java 会话门面。
 *
 * <p>线程模型：单个实例内部全部 synchronized（PHREEQC 实例本身非线程安全）；
 * 多线程应各自创建实例（PHREEQC 的 id 即为此设计，PhreeqcRM 同模式）。
 *
 * <p>用法：
 * <pre>{@code
 * try (IPhreeqc q = IPhreeqc.create()) {
 *     IPhreeqc.RunResult r = q.run("""
 *         SOLUTION 1
 *             pH 7
 *         SELECTED_OUTPUT 1
 *             -pH true
 *         END
 *         """);
 *     double pH = r.row(0).d("pH");
 * }
 * }</pre>
 */
public final class IPhreeqc implements AutoCloseable {

    private static final IPhreeqcLib LIB = NativeLoader.lib();
    /**
     * SELECTED_OUTPUT -high_precision improves printed rows but also changes
     * PHREEQC's instance-wide convergence tolerance. A shared session must not
     * let a previous display observation make the next material transaction
     * numerically stricter. State-changing scripts therefore begin from this
     * explicit runtime baseline while retaining high-precision output where it
     * is requested.
     */
    private static final String RUNTIME_KNOBS_INLINE = "KNOBS\n    -convergence_tolerance 1e-8\n";
    private static final String RUNTIME_KNOBS = RUNTIME_KNOBS_INLINE + "END\n";

    /** A complete, standalone solver reset command. */
    public static String runtimeKnobsBlock() { return RUNTIME_KNOBS; }

    /**
     * Solver settings for an already-open PHREEQC input sequence. Callers must
     * append a later trigger (for example KINETICS) and its terminating END.
     */
    public static String runtimeKnobsInline() { return RUNTIME_KNOBS_INLINE; }

    private int id;
    /** Formula weights are evaluated by the loaded PHREEQC database, then cached per session. */
    private final Map<String, Double> formulaWeights = new HashMap<>();
    private boolean databaseLoaded;
    private boolean closed;

    private IPhreeqc(int id) {
        this.id = id;
    }

    /** 创建实例。注意：错误/选中输出开关必须在数据库装载之后打开
     * （LoadDatabaseString → UnLoadDatabase 会清空 SelectedOutputStringOn）。 */
    public static IPhreeqc create() {
        int id = LIB.CreateIPhreeqc();
        if (id < 0) {
            throw new IPhreeqcException("CreateIPhreeqc 失败: " + id);
        }
        return new IPhreeqc(id);
    }

    /** 装载数据库（sit.dat + addendum，见 {@link Database}）。幂等。 */
    public synchronized IPhreeqc loadDatabase() {
        ensureOpen();
        if (databaseLoaded) {
            return this;
        }
		String db = Database.sitWithAddenda();
		if (LIB.LoadDatabaseString(id, db) != 0) {
            throw new IPhreeqcException("数据库装载失败:\n" + errorText());
        }
        // UnLoadDatabase 会清空这些开关，必须在装载后（重）打开
        LIB.SetErrorStringOn(id, 1);
        LIB.SetSelectedOutputStringOn(id, 1);
        LIB.SetDumpStringOn(id, 1);
        databaseLoaded = true;
        return this;
    }

    /**
     * 执行输入脚本。
     *
     * @throws IPhreeqcException whenever native PHREEQC reports one or more
     *                           errors. RunString returns the error count;
     *                           it is not an IPQ_RESULT enum.
     */
    public synchronized RunResult run(String script) {
        ensureOpen();
		loadDatabase();
        int rc = LIB.RunString(id, script);
        String err = errorText();
        List<String> lines = selectedOutputLines();
        if (rc != 0) {
            resetAfterNativeError();
            throw new IPhreeqcException("RunString 失败 (rc=" + rc + "):\n" + err);
        }
        return new RunResult(lines, "");
    }

    // ==== G1c 门面：ChemState 互译 / DUMP 存档 / 恢复 ====

    /**
     * 纯平衡计算：ChemState → SOLUTION + SELECTED_OUTPUT（pH/pe/全部总量 + 监视物种）→ 解析结果。
     *
     * @param watch 需要监视摩尔浓度的物种名（如 "Hyp-"、"Cl-"）
     */
    public synchronized RunResult equilibrate(ChemState state, String... watch) {
        StringBuilder script = new StringBuilder(state.toSolutionScript(1));
        script.append("SELECTED_OUTPUT 1\n");
        script.append("    -pH       true\n    -pe       true\n");
        script.append("    -high_precision true\n");
        script.append("    -totals   ").append(String.join("  ", state.totals().keySet())).append('\n');
        if (watch.length > 0) {
            script.append("    -molalities ").append(String.join("  ", watch)).append('\n');
        }
        script.append("END\n").append(RUNTIME_KNOBS);
        return run(script.toString());
    }

    /**
     * 全精度存档：先平衡计算再 DUMP，返回 SOLUTION_RAW 文本。
     *
     * <p>恢复有两条路（语义不同，见 {@link #runRestored}）：
     * <b>精确恢复</b>用原始 dump 文本（SOLUTION_RAW 不重算、零漂移、保留池分布）；
     * {@link ChemState#fromDump} 仅用于审视/重渲染（重入后若接反应步，价态池会坍缩——伪元素池无此问题）。
     */
    public synchronized String archive(ChemState state) {
        run(state.toSolutionScript(1) + "END\n");
        String dump = runDump(1);
        if (dump.isBlank()) {
            throw new IPhreeqcException("DUMP 未产生存档文本");
        }
        return dump;
    }

    /** 在当前会话上执行 DUMP（指定 solution 编号），返回 dump 文本。 */
    public synchronized String runDump(int... solutions) {
        ensureOpen();
        loadDatabase();
        LIB.SetDumpStringOn(id, 1);
        StringBuilder nums = new StringBuilder();
        for (int n : solutions) {
            nums.append(' ').append(n);
        }
        if (nums.isEmpty()) {
            throw new IllegalArgumentException("至少指定一个 solution 编号");
        }
        int rc = LIB.RunString(id, "DUMP\n    -solution" + nums + "\n");
        if (rc != 0) {
            resetAfterNativeError();
            throw new IPhreeqcException("DUMP 失败 (rc=" + rc + "):\n" + errorText());
        }
        return dumpText();
    }

    /**
     * 从存档继续模拟：dump 文本（{@link #archive} 的产物）+ 续算脚本。
     *
     * <p>SOLUTION_RAW 恢复不触发重算（零漂移）；续算脚本需自带 USE solution 1 +
     * 触发器（REACTION/KINETICS/EQUILIBRIUM_PHASES）+ SELECTED_OUTPUT + END。
     * 注意：仅 USE 无触发器不产生 punch 行。
     */
    public synchronized RunResult runRestored(String dumpText, String continuation) {
        return run(RUNTIME_KNOBS + dumpText + "\nEND\n" + continuation + "\nEND\n");
    }

    /**
     * Materialise a proportional amount of a {@code SOLUTION_RAW} reference
     * solution. A raw dump is deliberately kept at a fixed reference volume in
     * FluidStack NBT; a pipe drain copies that tag verbatim. PHREEQC's MIX_SOLUTION is the
     * conservation-preserving conversion from that reference state to the actual
     * amount in the receiving vessel.
     *
     * <p>The returned dump is an ordinary solution 1 dump whose inventory is
     * {@code factor} times the input. Callers normally save it with a new
     * reference amount equal to their current FluidStack amount.</p>
     */
    public synchronized String scaleRestored(String dumpText, double factor) {
        if (!(factor > 0.0) || !Double.isFinite(factor)) {
            throw new IllegalArgumentException("scale factor must be finite and positive: " + factor);
        }
        // MIX_SOLUTION only combines extensive inventory. Unlike MIX it does not
        // equilibrate redox/speciation merely because a pipe split was read.
        clearReactants();
        run(RUNTIME_KNOBS + dumpText + "\nEND\n"
                + "MIX_SOLUTION 2\n    1 " + String.format(java.util.Locale.ROOT, "%.17g", factor) + "\nEND\n");
        String scaled = runDump(2);
        // Continuations consistently address solution 1. SOLUTION_RAW has no
        // cross-object references, so normalising its declaration is safe.
        return scaled.replaceFirst("(?m)^SOLUTION_RAW\\s+2\\b", "SOLUTION_RAW 1");
    }

    /**
     * Combine complete archived solutions without equilibrating them.  This is
     * the only inventory operation used for pipe joins: {@code MIX} is
     * deliberately forbidden because it immediately re-speciates/redoxes.
     * Keys are complete {@code SOLUTION_RAW} blocks and values are their
     * extensive multipliers.
     */
    public synchronized String mixSolutions(Map<String, Double> rawFactors) {
        if (rawFactors == null || rawFactors.isEmpty()) {
            throw new IllegalArgumentException("at least one solution is required");
        }
        clearReactants();
        StringBuilder input = new StringBuilder(RUNTIME_KNOBS);
        StringBuilder mix = new StringBuilder("MIX_SOLUTION 100\n");
        int number = 1;
        for (Map.Entry<String, Double> entry : rawFactors.entrySet()) {
            String raw = entry.getKey();
            double factor = entry.getValue() == null ? Double.NaN : entry.getValue();
            if (raw == null || raw.isBlank() || !(factor > 0.0) || !Double.isFinite(factor)) {
                throw new IllegalArgumentException("raw solution and positive finite factor are required");
            }
            input.append(renameRawSolution(raw, number)).append("\nEND\n");
            mix.append("    ").append(number++).append(' ')
                    .append(String.format(java.util.Locale.ROOT, "%.17g", factor)).append('\n');
        }
        run(input.append(mix).append("END\n").toString());
        return renameRawSolution(runDump(100), 1);
    }

    /**
     * Construct a new, explicitly declared aqueous feed. Keys are neutral
     * PHREEQC REACTION formulae (for example NaCl, HCl, Ca(OH)2), values are
     * total mol. A pure-water solution is the solvent, then REACTION adds the
     * declared formula inventory; H/O and charge are consequently carried by
     * the chemical formula, never discarded or replaced by pH charge.
     */
    public synchronized String declaredSolution(double waterKg, Map<String, Double> componentMol,
            double temperatureC) {
        if (!(waterKg > 0.0) || !Double.isFinite(waterKg) || !Double.isFinite(temperatureC)) {
            throw new IllegalArgumentException("water and temperature must be finite; water must be positive");
        }
        clearReactants();
        StringBuilder script = new StringBuilder(RUNTIME_KNOBS).append("SOLUTION 1 declared\n")
                .append("    temp ").append(String.format(java.util.Locale.ROOT, "%.17g", temperatureC)).append('\n')
                .append("    water ").append(String.format(java.util.Locale.ROOT, "%.17g", waterKg)).append(" kg\nEND\n");
        boolean hasDeclaredMaterial = false;
        if (componentMol != null) {
            for (Map.Entry<String, Double> entry : componentMol.entrySet()) {
                String component = entry.getKey();
                double mol = entry.getValue() == null ? Double.NaN : entry.getValue();
                if (!isReactionFormula(component) || !(mol >= 0.0) || !Double.isFinite(mol)) {
                    throw new IllegalArgumentException("unsupported declared reaction formula: " + component);
                }
                hasDeclaredMaterial |= mol > 0.0;
            }
        }
        if (hasDeclaredMaterial) {
            script.append("USE solution 1\nREACTION 1 declared feed\n");
            for (Map.Entry<String, Double> entry : componentMol.entrySet()) {
                String component = entry.getKey();
                double mol = entry.getValue();
                if (mol > 0.0) {
                    script.append("    ").append(component).append(' ')
                            .append(String.format(java.util.Locale.ROOT, "%.17g", mol)).append('\n');
                }
            }
            script.append("    1 mol\n");
        }
        run(script.append("SAVE solution 1\nEND\n").toString());
        return renameRawSolution(runDump(1), 1);
    }

    /** Apply signed neutral-formula additions to an existing exact state. */
    public synchronized String reactRestored(String raw, Map<String, Double> formulaMol) {
        if (formulaMol == null || formulaMol.isEmpty()) throw new IllegalArgumentException("formula transaction is required");
        clearReactants();
        StringBuilder reaction = new StringBuilder(RUNTIME_KNOBS).append(raw).append("\nEND\nUSE solution 1\nREACTION 1 transaction\n");
        boolean hasMaterial = false;
        for (Map.Entry<String, Double> entry : formulaMol.entrySet()) {
            double mol = entry.getValue() == null ? Double.NaN : entry.getValue();
            if (!isReactionFormula(entry.getKey()) || !Double.isFinite(mol))
                throw new IllegalArgumentException("unsupported reaction formula: " + entry.getKey());
            if (mol != 0) {
                hasMaterial = true;
                reaction.append("    ").append(entry.getKey()).append(' ')
                    .append(String.format(java.util.Locale.ROOT, "%.17g", mol)).append('\n');
            }
        }
        if (!hasMaterial) reaction.append("    H2O 0\n");
        run(reaction.append("    1 mol\nSAVE solution 1\nEND\n").toString());
        return renameRawSolution(runDump(1), 1);
    }

    /**
     * Observe native pH, pe, water, and requested aqueous species molalities.
     * This is an isolated candidate-equilibrium observation, not a no-solve raw
     * inspection: PHREEQC needs an explicit zero-mole reaction to emit a fresh
     * selected-output row. It never DUMPs or publishes that candidate state.
     */
    public synchronized RunResult observeRestored(String raw, String... aqueousSpecies) {
		return observeRestored(raw, List.of(), aqueousSpecies);
	}

    /** Observe named total components plus aqueous species using that candidate solve. */
    public synchronized RunResult observeRestored(String raw, List<String> totalComponents, String... aqueousSpecies) {
        StringBuilder selected = new StringBuilder("SELECTED_OUTPUT 1\n -high_precision true\n -pH true\n -pe true\n -water true\n");
		if (totalComponents != null && !totalComponents.isEmpty())
			selected.append(" -totals ").append(String.join(" ", totalComponents)).append('\n');
        if (aqueousSpecies != null && aqueousSpecies.length > 0)
            selected.append(" -molalities ").append(String.join(" ", aqueousSpecies)).append('\n');
        // SELECTED_OUTPUT alone does not execute a simulation and IPhreeqc
        // retains old output rows. Run a zero-mole water reaction as an explicit
        // observation solve. It may equilibrate the in-session copy, but never
        // writes a DUMP or changes the caller's RAW state.
        clearReactants();
        return run(raw + "\nEND\nUSE solution 1\nREACTION 1 observation\n    H2O 0\n    1 mol\n"
                + selected
                // The built-in ALK function exposes the native acid/base
                // inventory independently of free H+/OH- speciation.
                + "USER_PUNCH 1\n -headings native_alk_eq_per_kg\n -start\n 10 PUNCH ALK\n -end\nEND\n"
                + RUNTIME_KNOBS);
    }

    /**
     * Returns a gram formula weight as interpreted by the loaded PHREEQC
     * database.  This deliberately uses BASIC {@code GFW} rather than a Java
     * atomic-weight table, so transactions such as water evaporation share the
     * engine's own H/O isotope and formula conventions.
     */
    public synchronized double formulaWeight(String formula) {
        if (formula == null || !formula.matches("[A-Za-z][A-Za-z0-9_:()]*"))
            throw new IllegalArgumentException("unsupported formula for native GFW: " + formula);
        Double cached = formulaWeights.get(formula);
        if (cached != null) return cached;
        clearReactants();
        RunResult result = run("SOLUTION 1 formula-weight probe\n"
                + "    water 1 kg\n"
                + "SELECTED_OUTPUT 1\n"
                + "    -high_precision true\n"
                + "USER_PUNCH 1\n"
                + "    -headings native_gfw\n"
                + "    -start\n"
                + "    10 PUNCH GFW(\"" + formula + "\")\n"
                + "    -end\nEND\n" + RUNTIME_KNOBS);
        if (result.rowCount() == 0) throw new IPhreeqcException("native GFW probe produced no row for " + formula);
        double weight = result.row(result.rowCount() - 1).d("native_gfw");
        if (!(weight > 0) || !Double.isFinite(weight))
            throw new IPhreeqcException("invalid native GFW for " + formula + ": " + weight);
        formulaWeights.put(formula, weight);
        return weight;
    }

    /** Remove every numbered reactant before a RAW transaction on the shared session. */
    public synchronized void clearReactants() {
        run("DELETE\n    -all\nEND\n");
    }

    /**
     * PHREEQC may retain partially parsed master-species/reactant state after
     * an input error. Do not reuse that native object: destroy it and create a
     * fresh session. This is deliberately an error-path cost, and prevents an
     * invalid declaration from affecting any later vessel transaction.
     */
    private void resetAfterNativeError() {
        LIB.DestroyIPhreeqc(id);
        int replacement = LIB.CreateIPhreeqc();
        if (replacement < 0) throw new IPhreeqcException("CreateIPhreeqc failed while recovering: " + replacement);
        id = replacement;
        databaseLoaded = false;
        formulaWeights.clear();
    }

    private static boolean isReactionFormula(String component) {
        return component != null && component.matches("[A-Za-z][A-Za-z0-9_:()]*");
    }

    private static String renameRawSolution(String raw, int number) {
        if (!raw.matches("(?s).*^SOLUTION_RAW\\s+\\d+\\b.*")) {
            throw new IllegalArgumentException("missing SOLUTION_RAW block");
        }
        return raw.replaceFirst("(?m)^SOLUTION_RAW\\s+\\d+\\b", "SOLUTION_RAW " + number);
    }

    /** Replace only the archived solution temperature before a continuation. */
    public static String withRestoredTemperature(String dumpText, double temperatureC) {
        if (!Double.isFinite(temperatureC)) {
            throw new IllegalArgumentException("temperature must be finite");
        }
        java.util.regex.Pattern tempLine = java.util.regex.Pattern.compile("(?m)^(\\s*-temp\\s+).*$" );
        if (!tempLine.matcher(dumpText).find()) {
            throw new IllegalArgumentException("SOLUTION_RAW has no -temp field");
        }
        String replacement = "$1" + String.format(java.util.Locale.ROOT, "%.17g", temperatureC);
        return tempLine.matcher(dumpText).replaceFirst(replacement);
    }

    private String errorText() {
        int n = LIB.GetErrorStringLineCount(id);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String line = LIB.GetErrorStringLine(id, i);
            if (line != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /** 本次 RunString 产生的 DUMP 文本（SOLUTION_RAW 块，全精度存档格式）。 */
    private String dumpText() {
        int n = LIB.GetDumpStringLineCount(id);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= n; i++) {
            String line = LIB.GetDumpStringLine(id, i);
            if (line == null) {
                continue;
            }
            if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                line = line.replaceAll("[\\r\\n]", "");
            }
            if (!line.isBlank()) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private List<String> selectedOutputLines() {
        List<String> out = new ArrayList<>();
        int count = LIB.GetSelectedOutputStringLineCount(id);
        // 行号基数按 0 基采集；越界行返回 null 自动截断，两种约定下都不丢行。
        for (int i = 0; i <= count; i++) {
            String line = LIB.GetSelectedOutputStringLine(id, i);
            if (line == null) {
                continue;
            }
            if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
                line = line.replaceAll("[\\r\\n]", "");
            }
            if (!line.isBlank()) {
                out.add(line);
            }
        }
        // SelectedOutputString 跨 RunString 累积：新一次 run 的表头会追加在旧内容之后，
        // 旧表头/数据成为残留（锅6 探针实测：重复表头被当数据行解析炸 NFE）。
        // 取最后一个表头（首列 == "sim"）之后的段，即本次 run 的输出。
        int lastHeader = -1;
        for (int i = 0; i < out.size(); i++) {
            String first = out.get(i).split("\t", -1)[0].trim();
            if (first.equals("sim")) {
                lastHeader = i;
            }
        }
        if (lastHeader > 0) {
            out = new ArrayList<>(out.subList(lastHeader, out.size()));
        }
        return out;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("IPhreeqc 实例已关闭");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            LIB.DestroyIPhreeqc(id);
        }
    }

    /** SELECTED_OUTPUT 的解析结果：首行表头（tab 分列），其余为数据行。 */
    public static final class RunResult {

        private final List<String> raw;
        private final String warnings;
        private final Map<String, Integer> columns;
        private final List<String[]> rows;

        RunResult(List<String> raw, String warnings) {
            this.raw = raw;
            this.warnings = warnings;
            this.columns = new HashMap<>();
            this.rows = new ArrayList<>();
            if (!raw.isEmpty()) {
                String[] header = split(raw.get(0));
                for (int i = 0; i < header.length; i++) {
                    columns.putIfAbsent(header[i].trim(), i);
                }
                for (int r = 1; r < raw.size(); r++) {
                    rows.add(split(raw.get(r)));
                }
            }
        }

        private static String[] split(String line) {
            return line.split("\t", -1);
        }

        public int rowCount() {
            return rows.size();
        }

        public Row row(int r) {
            return new Row(rows.get(r));
        }

        public List<String> rawLines() {
            return raw;
        }

        public String warnings() {
            return warnings;
        }

        /** 单行取值视图，按列名读取并转 double（'-' 等 PHREEQC 空值按 NaN 处理）。 */
        public final class Row {

            private final String[] vals;

            private Row(String[] vals) {
                this.vals = vals;
            }

            public String s(String column) {
                Integer idx = columns.get(column);
                if (idx == null) {
                    throw new IllegalArgumentException(
                            "SELECTED_OUTPUT 无列 " + column + "；可用列: " + columns.keySet());
                }
                return idx < vals.length ? vals[idx].trim() : "";
            }

            public double d(String column) {
                String v = s(column);
                if (v.isEmpty() || v.equals("-")) {
                    return Double.NaN;
                }
                return Double.parseDouble(v);
            }

            public double dOr(String column, double fallback) {
                double v = d(column);
                return Double.isNaN(v) ? fallback : v;
            }

            @Override
            public String toString() {
                return Arrays.toString(vals);
            }
        }
    }

    /** 数据库文本装载（{@link Database} 的包内快捷方式）。 */
    static String readResource(String path) throws IOException {
        try (InputStream in = IPhreeqc.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("classpath 资源缺失: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
