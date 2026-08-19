package com.yu1745.chemengine.kernel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final int id;
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
     * @throws IPhreeqcException rc &gt; 0（ERROR/FATAL）时抛出，携带原生错误文本；
     *                           rc == 2（WARNING）不抛，警告在 {@link RunResult#warnings}
     */
    public synchronized RunResult run(String script) {
        ensureOpen();
        loadDatabase();
        int rc = LIB.RunString(id, script);
        String err = errorText();
        List<String> lines = selectedOutputLines();
        if (rc == 1 || rc == 3) {
            throw new IPhreeqcException("RunString 失败 (rc=" + rc + "):\n" + err);
        }
        return new RunResult(lines, rc == 2 ? err : "");
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
        script.append("END\n");
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
        return run(dumpText + "\nEND\n" + continuation + "\nEND\n");
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
