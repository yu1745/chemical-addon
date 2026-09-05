package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

class ChemicalBasisTest {
    private final ChemicalBasis basis = ChemicalBasis.loadDefault();

    @Test void mapsOnlyWholeNeutralFormulaeAndKeepsOrdinaryFormulae() {
        assertEquals("NaHyp", basis.internalFormula("NaOCl(aq)"));
        assertEquals("Na2Sul", basis.internalFormula("Na2SO3"));
        assertEquals("NH4Nitra", basis.internalFormula("NH4NO3"));
        assertEquals("HNitra", basis.internalFormula("HNO3(aq)"));
        assertEquals("AgNitra", basis.internalFormula("AgNO3(aq)"));
        assertEquals("CuSO4:5H2O", basis.internalFormula("CuSO4·5H2O"));
        assertEquals("CuSO4:5H2O", basis.internalFormula("CuSO4:5H2O"));
        assertEquals("CaCl2", basis.internalFormula("CaCl2"));
        assertEquals("CaSul", basis.internalFormula("CaSO3"));
        assertEquals("CaHyp2", basis.internalFormula("Ca(OCl)2"));
        assertEquals("CaNitra2", basis.internalFormula("Ca(NO3)2"));
        assertEquals("KMnvii", basis.internalFormula("KMnO4"));
        assertEquals("Na2Sulfide", basis.internalFormula("Na2S"));
        assertEquals("H2Sulfide", basis.internalFormula("H2S(aq)"));
    }

    @Test void rejectsUnknownOrMalformedFormulaeWithoutSubstringReplacement() {
        assertThrows(IllegalArgumentException.class, () -> basis.internalFormula("NaOClOops"));
        assertThrows(IllegalArgumentException.class, () -> basis.internalFormula("Na(OCl"));
        assertThrows(IllegalArgumentException.class, () -> basis.internalFormula("XxO"));
        assertThrows(IllegalArgumentException.class, () -> basis.internalFormula("CxHy"));
        assertThrows(IllegalArgumentException.class, () -> basis.internalFormula("Ca()"));
        assertThrows(IllegalArgumentException.class, () -> basis.internalFormula("H0O"));
        assertThrows(IllegalArgumentException.class, () -> basis.internalFormula("2"));
        assertThrows(IllegalArgumentException.class, () -> basis.internalFormula("NaCl."));
    }

    @Test void centralizesNativeComponentsAndCosmeticProjection() {
        assertEquals("Hyp", basis.phreeqcComponent("OCl"));
        assertEquals("Mnvii", basis.phreeqcComponent("MnO4"));
        assertEquals("Sulfide", basis.phreeqcComponent("S-2"));
        assertEquals("S(6)", basis.phreeqcComponent("SO4"));
        assertEquals("Ca", basis.phreeqcComponent("Ca"));
        assertEquals("OCl-1", basis.displayIon("Hyp-"));
        assertEquals("NO2-1", basis.displayIon("Nitri-"));
        assertEquals("MnO4-1", basis.displayIon("Mnvii-"));
        assertEquals("S-2", basis.displayIon("Sulfide-2"));
        assertEquals("Hyp-", basis.nativeIon("OCl-1"));
        assertEquals("Na+", basis.nativeIon("Na+1"));
        assertEquals("CO3-2", basis.phaseIon("CO3-2"));
        assertEquals("Hyp-", basis.phaseIon("OCl-1"));
        assertThrows(IllegalArgumentException.class, () -> basis.phreeqcComponent("OClThing"));
        assertThrows(IllegalArgumentException.class, () -> basis.phaseIon("CaOCl+"));
    }

    @Test void signedBatchConversionIsSeparateFromExternalFeedValidation() {
        assertEquals(Map.of("NaHyp", -1d, "NaNitra", 3d),
            basis.signedInternalFormulae(Map.of("NaOCl", -1d, "NaNO3", 3d)));
        assertEquals(Map.of("NaHyp", 2d),
            basis.signedInternalFormulae(Map.of("NaOCl", 1d, "NaHyp", 1d)));
        assertThrows(IllegalArgumentException.class, () -> basis.internalFormulae(Map.of("NaOCl", -1d)));
        assertThrows(IllegalArgumentException.class, () -> basis.signedInternalFormulae(Map.of("NaOCl", Double.POSITIVE_INFINITY)));
    }

    @Test void everyNeutralAliasPreservesRealAtomsWhenPseudoPoolsAreExpanded() {
        Curation curation = Curation.load();
        Map<String, Map<String, Double>> pseudoAtoms = new HashMap<>();
        Set<String> nativeSymbols = new HashSet<>(basis.realElements());
        for (Curation.PseudoElement pseudo : curation.pseudoElements()) {
            nativeSymbols.add(pseudo.element);
            pseudoAtoms.put(pseudo.element, pseudo.atoms);
        }
        for (Map.Entry<String, String> alias : basis.internalFormulaAliases().entrySet()) {
            Map<String, Integer> real = ChemicalBasis.Formula.parse(alias.getKey(), basis.realElements());
            Map<String, Double> expanded = expand(ChemicalBasis.Formula.parse(alias.getValue(), nativeSymbols), pseudoAtoms);
            assertEquals(toDouble(real), expanded, alias.getKey() + " -> " + alias.getValue());
        }
    }

    private static Map<String, Double> expand(Map<String, Integer> formula, Map<String, Map<String, Double>> pseudoAtoms) {
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, Integer> term : formula.entrySet()) {
            Map<String, Double> atoms = pseudoAtoms.get(term.getKey());
            if (atoms == null) out.merge(term.getKey(), term.getValue().doubleValue(), Double::sum);
            else for (Map.Entry<String, Double> atom : atoms.entrySet()) out.merge(atom.getKey(), term.getValue() * atom.getValue(), Double::sum);
        }
        return out;
    }

    private static Map<String, Double> toDouble(Map<String, Integer> atoms) {
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, Integer> atom : atoms.entrySet()) out.put(atom.getKey(), atom.getValue().doubleValue());
        return out;
    }
}
