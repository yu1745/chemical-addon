package com.yu1745.chemengine.kernel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 化学容器的进料描述（G1c 门面的输入侧）：水量 + 元素/伪元素总量 + pH/pe 策略。
 *
 * <p>单位约定：总量为 <b>mol</b>（每份容器，非 molal）；渲染为 SOLUTION 时除以
 * {@link #kgw} 换算成 mol/kgw。pH 两种策略：定值或电荷平衡涌现（漂白液等真实体系
 * 必须用 charge——阴离子失衡由 pH 吸收）。
 *
 * <p>介稳物种用伪元素（Hyp/Sul，见 {@link Database}）录入；价态池写法（S(4) 等）
 * 仅适合"初始解一步出结果"的场景——任何反应步都会把价态池坍缩到统一 pe。
 *
 * <pre>{@code
 * ChemState s = ChemState.builder("漂白液")
 *     .waterKg(1.0)
 *     .pHCharge()
 *     .pe(4)
 *     .total("Na", 0.150)
 *     .total("Cl", 0.100)
 *     .total("Hyp", 0.050)
 *     .build();
 * }</pre>
 */
public final class ChemState {

    private final String description;
    private final double kgw;
    private final Double ph;         // null = charge balance
    private final double pe;
    private final double tempC;
    private final Map<String, Double> totals;

    private ChemState(Builder b) {
        this.description = b.description == null ? "" : b.description;
        this.kgw = b.kgw;
        this.ph = b.ph;
        this.pe = b.pe;
        this.tempC = b.tempC;
        this.totals = Collections.unmodifiableMap(new LinkedHashMap<>(b.totals));
    }

    public static Builder builder(String description) {
        return new Builder(description);
    }

    public String description() {
        return description;
    }

    /** 水量（kg）。 */
    public double kgw() {
        return kgw;
    }

    /** 元素/伪元素 → mol（容器总量）。 */
    public Map<String, Double> totals() {
        return totals;
    }

    /** 求解后的 pH（若来自 dump 解析）；进料侧为策略（定值/charge）。 */
    public Double ph() {
        return ph;
    }

    public double pe() {
        return pe;
    }

    public double tempC() {
        return tempC;
    }

    /** 渲染为 SOLUTION 块（含 END 前的全部行，不含 SELECTED_OUTPUT）。 */
    String toSolutionScript(int number) {
        StringBuilder sb = new StringBuilder("SOLUTION ").append(number);
        if (!description.isBlank()) {
            sb.append(' ').append(description);
        }
        sb.append('\n');
        sb.append("    temp      ").append(fmt(tempC)).append('\n');
        sb.append("    pH        ").append(ph == null ? "7 charge" : fmt(ph)).append('\n');
        sb.append("    pe        ").append(fmt(pe)).append('\n');
        sb.append("    water     ").append(fmt(kgw)).append(" kg\n");
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            sb.append("    ").append(pad(e.getKey())).append(' ')
                    .append(fmt(e.getValue() / kgw)).append(" mol/kgw\n");
        }
        return sb.toString();
    }

    private static String pad(String key) {
        return (key + "               ").substring(0, Math.min(11, Math.max(key.length(), 9)));
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.format("%.15g", v);
    }

    /**
     * 解析 SOLUTION_RAW（{@link IPhreeqc#archive} 的产物）为 ChemState。
     *
     * <p>注意：这是<b>审视/重渲染</b>用途。精确恢复（零漂移、保留池分布）应把原始
     * dump 文本交给 {@link IPhreeqc#runRestored}；本方法重渲染的 SOLUTION 若接反应步，
     * dump 里的价态池键（如 Cl(-1)）会触发池坍缩（伪元素键无此问题）。
     */
    public static ChemState fromDump(String dumpText) {
        Builder b = builder("restored");
        boolean inTotals = false;
        for (String rawLine : dumpText.split("\n", -1)) {
            String line = rawLine.trim();
            if (line.startsWith("SOLUTION_RAW")) {
                String d = line.replaceFirst("^SOLUTION_RAW\\s+\\d+\\s*", "").trim();
                if (!d.isEmpty()) {
                    b.description = d;
                }
                continue;
            }
            if (line.startsWith("-totals")) {
                inTotals = true;
                continue;
            }
            if (line.startsWith("-")) {
                inTotals = false;
            }
            if (inTotals && !line.isEmpty()) {
                String[] parts = line.split("\\s+");
                if (parts.length == 2) {
                    b.total(parts[0], Double.parseDouble(parts[1]));
                }
                continue;
            }
            if (line.startsWith("-pH ")) {
                b.pH(Double.parseDouble(line.substring(4).trim()));
            } else if (line.startsWith("-pe ")) {
                b.pe = Double.parseDouble(line.substring(4).trim());
            } else if (line.startsWith("-temp ")) {
                b.tempC = Double.parseDouble(line.substring(6).trim());
            } else if (line.startsWith("-mass_water ")) {
                b.kgw = Double.parseDouble(line.substring(12).trim());
            }
        }
        return b.build();
    }

    /** 构建器。 */
    public static final class Builder {

        private String description;
        private double kgw = 1.0;
        private Double ph = null;
        private double pe = 4.0;
        private double tempC = 25.0;
        private final Map<String, Double> totals = new LinkedHashMap<>();

        private Builder(String description) {
            this.description = description;
        }

        public Builder waterKg(double kg) {
            if (kg <= 0) {
                throw new IllegalArgumentException("水量必须 > 0: " + kg);
            }
            this.kgw = kg;
            return this;
        }

        /** pH 定值（强缓冲/已知体系）。 */
        public Builder pH(double value) {
            this.ph = value;
            return this;
        }

        /** pH 由电荷平衡涌现（默认；真实体系推荐）。 */
        public Builder pHCharge() {
            this.ph = null;
            return this;
        }

        public Builder pe(double value) {
            this.pe = value;
            return this;
        }

        public Builder tempC(double t) {
            this.tempC = t;
            return this;
        }

        /** 元素/伪元素总量（mol，容器总量）。重复键覆盖。 */
        public Builder total(String element, double mol) {
            if (element == null || element.isBlank()) {
                throw new IllegalArgumentException("元素名为空");
            }
            if (mol < 0) {
                throw new IllegalArgumentException("总量必须 >= 0: " + element + " " + mol);
            }
            totals.put(element, mol);
            return this;
        }

        public ChemState build() {
            return new ChemState(this);
        }
    }
}
