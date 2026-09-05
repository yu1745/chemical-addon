package com.yu1745.chemengine.kernel;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Collections;

/**
 * The sole game-boundary vocabulary for PHREEQC's metastable pseudo pools.
 * Pseudo elements remain private kernel implementation details: callers submit
 * neutral real formulae and receive native component/species names only here.
 */
public final class ChemicalBasis {
    private static final String RESOURCE = "/curation/chemical-basis.json";
    private final Map<String, String> internalFormulae;
    private final Map<String, String> components;
    private final Map<String, String> displayIons;
    private final Map<String, String> phaseIons;
    private final Set<String> knownElements;
    private final Set<String> internalAliases;

    private ChemicalBasis(Raw raw) {
        if (raw == null || raw.internalFormulae == null || raw.components == null || raw.displayIons == null || raw.phaseIons == null || raw.elements == null)
            throw new IllegalArgumentException("chemical basis catalogue is incomplete");
        Map<String, String> aliases = immutable(raw.internalFormulae, "formula");
        components = immutable(raw.components, "component");
        displayIons = immutable(raw.displayIons, "display ion");
        phaseIons = immutable(raw.phaseIons, "phase ion");
        knownElements = Set.copyOf(raw.elements);
        Map<String, String> compiled = new TreeMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String canonical = Formula.canonical(entry.getKey(), knownElements);
            if (compiled.put(canonical, entry.getValue()) != null) throw new IllegalArgumentException("duplicate canonical formula: " + entry.getKey());
        }
        internalFormulae = frozen(compiled);
        internalAliases = Set.copyOf(aliases.values());
    }

    public static ChemicalBasis loadDefault() {
        try (InputStream in = ChemicalBasis.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing chemical basis: " + RESOURCE);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new ChemicalBasis(new Gson().fromJson(reader, Raw.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
    }

    /**
     * Validate a neutral real formula and translate only a whole, registered
     * formula. No substring substitution is permitted: {@code NaOCl} maps,
     * while a coincidental textual occurrence of {@code OCl} never does.
     */
    public String internalFormula(String neutralFormula) {
        if (internalAliases.contains(neutralFormula)) return neutralFormula; // boundary conversion is idempotent
        String canonical = Formula.canonical(neutralFormula, knownElements);
        return internalFormulae.getOrDefault(canonical, Formula.nativeFormula(neutralFormula, knownElements));
    }

    /** Declared-neutral aliases for curation validation and boundary diagnostics. */
    public Map<String, String> internalFormulaAliases() { return internalFormulae; }
    /** Real elements recognized in external formulae. */
    public Set<String> realElements() { return knownElements; }

    /** Normalize a non-negative external feed batch atomically; equal aliases coalesce. */
    public Map<String, Double> internalFormulae(Map<String, Double> declaredFormulae) {
        return mapFormulae(declaredFormulae, false);
    }

    /** Normalize a signed reaction delta. External feed factories still use {@link #internalFormulae(Map)}. */
    public Map<String, Double> signedInternalFormulae(Map<String, Double> declaredFormulae) {
        return mapFormulae(declaredFormulae, true);
    }
    private Map<String, Double> mapFormulae(Map<String, Double> declaredFormulae, boolean signed) {
        if (declaredFormulae == null) throw new IllegalArgumentException("declared formulae are required");
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : declaredFormulae.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !Double.isFinite(entry.getValue()) || (!signed && entry.getValue() < 0))
                throw new IllegalArgumentException("invalid declared amount: " + entry.getKey());
            String formula = internalFormula(entry.getKey());
            double total = out.getOrDefault(formula, 0d) + entry.getValue();
            if (!Double.isFinite(total)) throw new IllegalArgumentException("declared amount overflow: " + formula);
            out.put(formula, total);
        }
        return frozen(out);
    }

    /** Strict native component name for a declared ion/group; unknown groups are rejected. */
    public String phreeqcComponent(String ionOrGroup) {
        String mapped = components.get(ionOrGroup);
        if (mapped != null) return mapped;
        if (ionOrGroup != null && ionOrGroup.matches("[A-Z][a-z]?" ) && knownElements.contains(ionOrGroup)) return ionOrGroup;
        throw new IllegalArgumentException("unsupported chemical component: " + ionOrGroup);
    }

    /** Native PHREEQC aqueous species → cosmetic game ion id. Read-only presentation mapping. */
    public Map<String, String> displayIons() { return displayIons; }
    public String displayIon(String nativeSpecies) {
        String mapped = displayIons.get(nativeSpecies);
        if (mapped == null) throw new IllegalArgumentException("unsupported native display species: " + nativeSpecies);
        return mapped;
    }
    /** Reverse a display-ion identifier to its PHREEQC species name, rejecting unknown labels. */
    public String nativeIon(String displayIon) {
        if (displayIon == null) throw new IllegalArgumentException("display ion is null");
        for (Map.Entry<String, String> entry : displayIons.entrySet()) if (entry.getValue().equals(displayIon)) return entry.getKey();
        String phase = phaseIons.get(displayIon);
        if (phase != null) return phase;
        // Native names use + / - for unit charges while game IDs conventionally spell +1 / -1.
        if (displayIon.matches(".+[+-]1")) {
            String normalized = displayIon.substring(0, displayIon.length() - 1);
            for (String nativeName : displayIons.keySet()) if (nativeName.equals(normalized)) return nativeName;
        }
        throw new IllegalArgumentException("unsupported display ion: " + displayIon);
    }

    /** Strict game-equilibrium RHS token → native PHREEQC ion, independent of cosmetic display coverage. */
    public String phaseIon(String gameIon) {
        String mapped = phaseIons.get(gameIon);
        if (mapped == null) throw new IllegalArgumentException("unsupported phase ion: " + gameIon);
        return mapped;
    }

    private static Map<String, String> immutable(Map<String, String> values, String kind) {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank())
                throw new IllegalArgumentException("invalid " + kind + " mapping");
            if (out.put(entry.getKey(), entry.getValue()) != null) throw new IllegalArgumentException("duplicate " + kind + " mapping: " + entry.getKey());
        }
        return frozen(out);
    }
    private static <T> Map<String, T> frozen(Map<String, T> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static final class Raw { Map<String, String> internalFormulae; Map<String, String> components; Map<String, String> displayIons; Map<String, String> phaseIons; Set<String> elements; }

    /** Formula parser supporting nested parentheses and hydrate dots; phase suffixes are ignored. */
    static final class Formula {
        static String canonical(String authored, Set<String> elements) {
            // Keep sorting here even if a future immutable-map implementation
            // changes its iteration contract.
            Map<String, Integer> atoms = new TreeMap<>(parse(authored, elements));
            // Formula-equivalent ordering is deliberately canonical so NaCl(aq)
            // and NaCl identify the same boundary declaration.
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, Integer> atom : atoms.entrySet()) out.append(atom.getKey()).append(atom.getValue() == 1 ? "" : atom.getValue());
            return out.toString();
        }
        static String normalized(String authored) {
            if (authored == null) throw new IllegalArgumentException("formula is null");
            return authored.replace("(aq)", "").replace("(s)", "").replace("(l)", "").replace("(g)", "")
                .replace(" slurry", "").replace(" ", "").replace('·', ':');
        }
        static Map<String, Integer> parse(String authored, Set<String> elements) {
            if (authored == null) throw new IllegalArgumentException("formula is null");
            String formula = normalized(authored);
            if (formula.isEmpty()) throw new IllegalArgumentException("unsupported chemical formula: " + authored);
            Map<String, Integer> total = new TreeMap<>();
            for (String hydrate : formula.split("[:.]", -1)) {
                if (hydrate.isEmpty()) throw new IllegalArgumentException("malformed hydrate: " + authored);
                int p = 0; while (p < hydrate.length() && Character.isDigit(hydrate.charAt(p))) p++;
                int multiplier = p == 0 ? 1 : positiveCount(hydrate.substring(0, p), authored);
                Parser parser = new Parser(hydrate.substring(p), elements);
                add(total, parser.group(false), multiplier);
                if (!parser.done()) throw new IllegalArgumentException("malformed formula: " + authored);
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(total));
        }
        static String nativeFormula(String authored, Set<String> elements) {
            parse(authored, elements); // validate first
            return normalized(authored).replace('.', ':');
        }
        private static int positiveCount(String digits, String authored) {
            try {
                int count = Integer.parseInt(digits);
                if (count <= 0) throw new IllegalArgumentException("nonpositive formula count: " + authored);
                return count;
            } catch (NumberFormatException e) { throw new IllegalArgumentException("invalid formula count: " + authored, e); }
        }
        private static void add(Map<String, Integer> target, Map<String, Integer> part, int multiplier) {
            for (Map.Entry<String, Integer> e : part.entrySet()) target.merge(e.getKey(), Math.multiplyExact(e.getValue(), multiplier), Math::addExact);
        }
        private static final class Parser {
            private final String text; private final Set<String> elements; private int pos;
            Parser(String text, Set<String> elements) { this.text = text; this.elements = elements; }
            boolean done() { return pos == text.length(); }
            Map<String, Integer> group(boolean closing) {
                Map<String, Integer> out = new TreeMap<>(); boolean any = false;
                while (pos < text.length() && text.charAt(pos) != ')') {
                    Map<String, Integer> unit;
                    if (text.charAt(pos) == '(') { pos++; unit = group(true); }
                    else if (Character.isUpperCase(text.charAt(pos))) {
                        String element = elements.stream().filter(candidate -> text.startsWith(candidate, pos))
                            .max(java.util.Comparator.comparingInt(String::length)).orElse(null);
                        if (element == null) throw new IllegalArgumentException("unknown element in formula at: " + text.substring(pos));
                        pos += element.length();
                        unit = Map.of(element, 1);
                    } else throw new IllegalArgumentException("malformed formula token: " + text);
                    int start = pos; while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
                    add(out, unit, start == pos ? 1 : positiveCount(text.substring(start, pos), text)); any = true;
                }
                if (!any) throw new IllegalArgumentException("empty formula group: " + text);
                if (closing) { if (pos >= text.length()) throw new IllegalArgumentException("unclosed formula group: " + text); pos++; }
                return out;
            }
        }
    }
}
