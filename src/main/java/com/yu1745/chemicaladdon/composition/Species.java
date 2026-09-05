package com.yu1745.chemicaladdon.composition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/**
 * A chemical species definition, loaded from datapack JSON
 * ({@code data/<mod>/chemistry/species/*.json}).
 *
 * <p>Modeled after Tinkers' JSON modifier architecture: definitions are
 * data-driven, ids are ResourceLocations, and mixtures are expressed as
 * base species + components with concentration caps (composition system).
 *
 * <p>This class also carries the thermodynamic/ionic data the emergent
 * rules engine (plans/03 §8) consumes: electrolyte dissociation ({@link #ions}),
 * constant-K equilibrium entries ({@link #equilibria} — precipitation,
 * complexation; plans/03 §8.2), crystallisation curve
 * ({@link #solubility}/{@link #solute}), phase
 * transitions ({@link #phaseTransitions}) and miscibility grouping
 * ({@link #miscibilityGroup}). Ions are NOT registered species — they are
 * simulation-internal identities carried by {@link Ion}.
 */
public final class Species {

	public enum Phase {
		GAS, LIQUID, SOLID
	}

	/** One ion in a dissociation: the ion plus its multiplicity in the species. */
	public static final class IonComponent {
		private final Ion ion;
		private final int count;

		public IonComponent(Ion ion, int count) {
			this.ion = ion;
			this.count = count;
		}

		public Ion ion() {
			return ion;
		}

		public int count() {
			return count;
		}
	}

	/** One suspended solid in a slurry: the solid species plus its multiplicity. */
	public static final class SuspendedComponent {
		private final ResourceLocation species;
		private final int count;

		public SuspendedComponent(ResourceLocation species, int count) {
			this.species = species;
			this.count = count;
		}

		public ResourceLocation species() {
			return species;
		}

		public int count() {
			return count;
		}
	}

	/** One point on a solubility curve (grams solute per 100 g water at tempC). */
	public static final class SolubilityPoint {
		private final int tempC;
		private final double gPer100g;

		public SolubilityPoint(int tempC, double gPer100g) {
			this.tempC = tempC;
			this.gPer100g = gPer100g;
		}

		public int tempC() {
			return tempC;
		}

		public double gPer100g() {
			return gPer100g;
		}
	}

	/** A phase transition: above {@code tempC} the species becomes {@code to}. */
	public static final class PhaseTransition {
		private final int tempC;
		private final ResourceLocation to;

		public PhaseTransition(int tempC, ResourceLocation to) {
			this.tempC = tempC;
			this.to = to;
		}

		public int tempC() {
			return tempC;
		}

		public ResourceLocation to() {
			return to;
		}
	}

	public static final class Component {
		private final ResourceLocation species;
		private final float maxConcentration;

		public Component(ResourceLocation species, float maxConcentration) {
			this.species = species;
			this.maxConcentration = maxConcentration;
		}

		public ResourceLocation species() {
			return species;
		}

		public float maxConcentration() {
			return maxConcentration;
		}
	}

	private final ResourceLocation id;
	private final String formula;
	/** Explicit neutral PHREEQC REACTION formula; null means normalize {@link #formula}. */
	private final String engineFormula;
	/** Optional native sit.dat phase name; avoids inventing a duplicate K. */
	private final String enginePhase;
	private final Phase phase;
	private final int boilingPointC;
	private final int meltingPointC;
	private final List<Component> components;
	private final Set<String> dangers;
	private final List<IonComponent> ions;
	private final List<SuspendedComponent> suspended; // solids suspended in water (slurry)
	private final List<Equilibrium> equilibria; // constant-K entries (plans/03 §8.2)
	private final List<SolubilityPoint> solubility;
	private final ResourceLocation solute; // solid that crystallises out (nullable)
	private final int solventRatio; // water parts per formula unit (0 = not a solution)
	private final int color; // RGB tint for the creative bucket (0 = derive from components)
	private final String miscibilityGroup; // nullable
	private final double gasSolubility; // dissolved units retained per water unit before degassing (NaN = default)
	private final List<PhaseTransition> phaseTransitions;

	private Species(ResourceLocation id, String formula, String engineFormula, String enginePhase, Phase phase, int boilingPointC, int meltingPointC,
		List<Component> components, Set<String> dangers, List<IonComponent> ions, List<SuspendedComponent> suspended,
		List<Equilibrium> equilibria, List<SolubilityPoint> solubility, ResourceLocation solute, int solventRatio,
		int color, String miscibilityGroup, double gasSolubility, List<PhaseTransition> phaseTransitions) {
		this.id = id;
		this.formula = formula;
		this.engineFormula = engineFormula;
		this.enginePhase = enginePhase;
		this.phase = phase;
		this.boilingPointC = boilingPointC;
		this.meltingPointC = meltingPointC;
		this.components = components;
		this.dangers = dangers;
		this.ions = ions;
		this.suspended = suspended;
		this.equilibria = equilibria;
		this.solubility = solubility;
		this.solute = solute;
		this.solventRatio = solventRatio;
		this.color = color;
		this.miscibilityGroup = miscibilityGroup;
		this.gasSolubility = gasSolubility;
		this.phaseTransitions = phaseTransitions;
	}

	/** Parses a species JSON; returns null (with a logged error) on failure. */
	@Nullable
	public static Species parse(ResourceLocation id, JsonElement json) {
		try {
			JsonObject o = json.getAsJsonObject();
			String formula = getString(o, "formula", id.getPath());
			String engineFormula = getStringOrNull(o, "engineFormula");
			String enginePhase = getStringOrNull(o, "enginePhase");
			Phase phase = Phase.valueOf(getString(o, "phase", "LIQUID").toUpperCase(Locale.ROOT));
			int bp = getInt(o, "boilingPointC", 0);
			int mp = getInt(o, "meltingPointC", 0);

			List<Component> components = new ArrayList<>();
			if (o.has("components")) {
				for (JsonElement e : o.getAsJsonArray("components")) {
					JsonObject c = e.getAsJsonObject();
					ResourceLocation sid = ResourceLocation.tryParse(getString(c, "species", ""));
					if (sid == null) {
						continue;
					}
					components.add(new Component(sid, getFloat(c, "maxConcentration", 1.0f)));
				}
			}

			Set<String> dangers = new LinkedHashSet<>();
			if (o.has("dangers")) {
				for (JsonElement e : o.getAsJsonArray("dangers")) {
					dangers.add(e.getAsString());
				}
			}

			// electrolyte dissociation: [ { "ion": "Ca", "charge": 2, "count": 1 }, ... ]
			List<IonComponent> ions = new ArrayList<>();
			if (o.has("ions")) {
				for (JsonElement e : o.getAsJsonArray("ions")) {
					JsonObject c = e.getAsJsonObject();
					String symbol = getString(c, "ion", "");
					if (symbol.isEmpty()) {
						continue;
					}
					int charge = getInt(c, "charge", 0);
					int count = getInt(c, "count", 1);
					ions.add(new IonComponent(new Ion(symbol, charge), count));
				}
			}

			// suspended solids (a slurry): [ { "species": "chemicaladdon:slaked_lime", "count": 1 }, ... ]
			List<SuspendedComponent> suspended = new ArrayList<>();
			if (o.has("suspended")) {
				for (JsonElement e : o.getAsJsonArray("suspended")) {
					JsonObject c = e.getAsJsonObject();
					ResourceLocation sid = ResourceLocation.tryParse(getString(c, "species", ""));
					if (sid == null) {
						continue;
					}
					suspended.add(new SuspendedComponent(sid, getInt(c, "count", 1)));
				}
			}

			// constant-K equilibria (plans/03 §8.2):
			// [ { "reaction": "limestone(s) = Ca+2 + CO3-2", "log_k": -8.3, "delta_h": optional, "rate": optional kinetic } ]
			List<Equilibrium> equilibria = new ArrayList<>();
			if (o.has("equilibria")) {
				for (JsonElement e : o.getAsJsonArray("equilibria")) {
					JsonObject c = e.getAsJsonObject();
					Equilibrium eq = Equilibrium.parse(getString(c, "reaction", ""),
						getDouble(c, "log_k", 0), getDouble(c, "delta_h", Double.NaN),
						getDouble(c, "rate", 0));
					if (eq != null) {
						equilibria.add(eq);
					}
				}
			}

			List<SolubilityPoint> solubility = new ArrayList<>();
			if (o.has("solubility")) {
				for (JsonElement e : o.getAsJsonArray("solubility")) {
					JsonObject c = e.getAsJsonObject();
					solubility.add(new SolubilityPoint(getInt(c, "tempC", 0), getDouble(c, "gPer100g", 0)));
				}
			}

			ResourceLocation solute = null;
			if (o.has("solute")) {
				solute = ResourceLocation.tryParse(getString(o, "solute", ""));
			}

			int solventRatio = getInt(o, "solventRatio", 0);
			int color = 0;
			if (o.has("color")) {
				String cs = o.get("color").getAsString().replace("#", "");
				try {
					color = (int) Long.parseLong(cs, 16);
				} catch (NumberFormatException ignored) {
					// leave colour 0 (derive from components)
				}
			}
			String miscibilityGroup = getStringOrNull(o, "miscibilityGroup");
			// dissolved-gas retention (Henry semantics): units kept per water unit
			// before the degas step separates the excess as a pure gas phase
			double gasSolubility = getDouble(o, "gasSolubility", Double.NaN);

			List<PhaseTransition> transitions = new ArrayList<>();
			if (o.has("phaseTransition")) {
				for (JsonElement e : o.getAsJsonArray("phaseTransition")) {
					JsonObject c = e.getAsJsonObject();
					ResourceLocation to = ResourceLocation.tryParse(getString(c, "to", ""));
					if (to == null) {
						continue;
					}
					transitions.add(new PhaseTransition(getInt(c, "tempC", 0), to));
				}
			}

			return new Species(id, formula, engineFormula, enginePhase, phase, bp, mp, List.copyOf(components), Set.copyOf(dangers),
				List.copyOf(ions), List.copyOf(suspended), List.copyOf(equilibria), List.copyOf(solubility), solute,
				solventRatio, color, miscibilityGroup, gasSolubility, List.copyOf(transitions));
		} catch (Exception e) {
			Chemistry.LOGGER.error("Failed to parse species {}: {}", id, e.getMessage());
			return null;
		}
	}

	public ResourceLocation id() {
		return id;
	}

	public String formula() {
		return formula;
	}

	/** Neutral public feed formula, or null when this species has no engine model.
	 * KernelSolutionState translates it to any private PHREEQC pseudo pool. */
	@Nullable
	public String engineFormula() {
		return engineFormula != null ? engineFormula : normalizeEngineFormula(formula);
	}
	@Nullable
	public String enginePhase() { return enginePhase; }

	/** Remove display phase labels and translate a hydrate dot to PHREEQC's colon syntax. */
	@Nullable
	public static String normalizeEngineFormula(String displayFormula) {
		if (displayFormula == null) return null;
		String normalized = displayFormula.trim()
				.replaceFirst("\\s+(?:slurry)$", "")
				.replaceFirst("\\((?:aq|s|l|g)\\)$", "")
				.replace('·', ':');
		return normalized.matches("[A-Za-z][A-Za-z0-9_:()]*") ? normalized : null;
	}

	public Phase phase() {
		return phase;
	}

	public int boilingPointC() {
		return boilingPointC;
	}

	public int meltingPointC() {
		return meltingPointC;
	}

	public List<Component> components() {
		return components;
	}

	public Set<String> dangers() {
		return dangers;
	}

	public List<IonComponent> ions() {
		return ions;
	}

	/** True if this species dissociates into ions in aqueous solution. */
	public boolean isElectrolyte() {
		return !ions.isEmpty();
	}

	/**
	 * The constant-K equilibrium entries authored on this species (plans/03 §8.2).
	 * The entry's carrier file is only organisation — the solver consumes the
	 * manager-aggregated list (see {@link SpeciesManager#allEquilibria()}).
	 */
	public List<Equilibrium> equilibria() {
		return equilibria;
	}

	public List<SolubilityPoint> solubility() {
		return solubility;
	}

	/** The solid species that crystallises out of this solution (nullable). */
	@Nullable
	public ResourceLocation solute() {
		return solute;
	}

	/**
	 * True when this solution species can crystallise on cooling: it names a solid
	 * solute and carries a solubility curve. Whether it <i>actually</i> crystallises
	 * is decided at runtime by the rules engine from the vessel's continuous
	 * concentration (formula units / water) against {@link #solubilityAt(int)}.
	 */
	public boolean isCrystallisable() {
		return solute != null && !solubility.isEmpty();
	}

	/** Default water parts per formula unit (the "known ratio" a creative bucket packs); 0 = none. */
	public int solventRatio() {
		return solventRatio;
	}

	/** RGB tint for this mode's creative bucket; 0 = derive from the packed components. */
	public int color() {
		return color;
	}

	/**
	 * True when this species is a solution mode: a liquid-phase electrolyte.
	 * Concentration is a runtime ratio of the vessel contents (ion units / water
	 * units), not an identity — so no fixed solvent ratio is required. Precipitates
	 * (solid, equilibria-bearing) and gases are excluded by the phase check.
	 */
	public boolean isSolution() {
		return phase == Phase.LIQUID && isElectrolyte();
	}

	/** The solids suspended in this slurry (empty when this is not a slurry). */
	public List<SuspendedComponent> suspendedSolids() {
		return suspended;
	}

	/**
	 * True when this species is a slurry: a liquid with suspended solids (plans/03
	 * §12 — "mixture + 悬浮固相"). Mutually exclusive with {@link #isSolution()}:
	 * a slurry's solid is already present as a solid, not dissolved as ions.
	 */
	public boolean isSlurry() {
		return phase == Phase.LIQUID && !suspended.isEmpty();
	}

	/** Mole-equivalents of one formula unit's ions (Σ stoichiometric counts). */
	public int ionCount() {
		int n = 0;
		for (IonComponent c : ions) {
			n += c.count();
		}
		return n;
	}

	/** Default concentration (ion mB / water mB) implied by {@link #solventRatio()}; 0 if none. */
	public double defaultConcentration() {
		return solventRatio > 0 ? (double) ionCount() / solventRatio : 0;
	}

	/** Whole formula units formable from the given ion amounts (no water cap). */
	public long formulaUnits(Map<String, Integer> ionAmounts) {
		long fu = Long.MAX_VALUE;
		for (IonComponent c : ions) {
			long avail = ionAmounts.getOrDefault(c.ion().id(), 0);
			fu = Math.min(fu, avail / c.count());
		}
		return fu;
	}

	/**
	 * mB of the solute ions (mole-equivalents) formable from the given ion
	 * amounts — whole formula units only, so the multiset stays charge-neutral.
	 */
	public int equivalentIonMb(Map<String, Integer> ionAmounts) {
		long fu = formulaUnits(ionAmounts);
		if (fu <= 0 || fu == Long.MAX_VALUE) {
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE, fu * ionCount());
	}

	/**
	 * Expand {@code ionAmount} mB of solute into its ions (whole formula units,
	 * charge-neutral) plus solvent water at {@code concentration} (ion mB / water
	 * mB). Used to pack a solution pattern (recipe output / creative bucket).
	 */
	public void expand(int ionAmount, double concentration, Map<ResourceLocation, Integer> molecules,
		Map<String, Integer> ions) {
		int ic = ionCount();
		if (ic <= 0 || concentration <= 0) {
			return;
		}
		int formulaUnits = (int) Math.max(0, Math.round((double) ionAmount / ic));
		int actualIon = formulaUnits * ic;
		for (IonComponent c : this.ions) {
			ions.merge(c.ion().id(), formulaUnits * c.count(), Integer::sum);
		}
		int water = (int) Math.round(actualIon / concentration);
		if (water > 0) {
			molecules.merge(Solution.WATER, water, Integer::sum);
		}
	}

	/**
	 * Pack {@code totalMb} mB of this species at its default ratio into
	 * {@code molecules} (water) + {@code ions} + {@code suspended}: a <b>solution</b>
	 * packs its solute ions + water (whole formula units, charge-neutral); a
	 * <b>slurry</b> packs its suspended solids + water. The integer remainder goes
	 * to water so the total is exact. Used by the creative "packed mixture" bucket.
	 */
	public void packBucket(int totalMb, Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspendedOut) {
		if (isSlurry()) {
			packSlurryBucket(totalMb, molecules, suspendedOut);
			return;
		}
		int ic = ionCount();
		double c = defaultConcentration();
		if (ic <= 0 || c <= 0 || totalMb <= 0) {
			return;
		}
		int ionMb = (int) Math.round(totalMb * c / (1.0 + c));
		int formulaUnits = (int) Math.round((double) ionMb / ic);
		int actualIon = formulaUnits * ic;
		for (IonComponent comp : this.ions) {
			ions.merge(comp.ion().id(), formulaUnits * comp.count(), Integer::sum);
		}
		int water = totalMb - actualIon;
		if (water > 0) {
			molecules.merge(Solution.WATER, water, Integer::sum);
		}
	}

	/** Pack a slurry: suspended solids + solvent water at the default ratio. */
	private void packSlurryBucket(int totalMb, Map<ResourceLocation, Integer> molecules,
		Map<ResourceLocation, Integer> suspendedOut) {
		int solidCount = 0;
		for (SuspendedComponent c : suspended) {
			solidCount += c.count();
		}
		int denom = solidCount + solventRatio;
		if (solidCount <= 0 || denom <= 0 || totalMb <= 0) {
			return;
		}
		int solidMb = (int) Math.round((double) totalMb * solidCount / denom);
		int assigned = 0;
		for (int i = 0; i < suspended.size(); i++) {
			SuspendedComponent c = suspended.get(i);
			int share = (i == suspended.size() - 1)
				? solidMb - assigned
				: (int) Math.round((double) solidMb * c.count() / solidCount);
			if (share > 0) {
				suspendedOut.merge(c.species(), share, Integer::sum);
			}
			assigned += share;
		}
		int water = totalMb - solidMb;
		if (water > 0) {
			molecules.merge(Solution.WATER, water, Integer::sum);
		}
	}

	@Nullable
	public String miscibilityGroup() {
		return miscibilityGroup;
	}

	/** Dissolved units retained per water unit before degassing ({@link #GAS_SOLUBILITY_DEFAULT} when unauthored). */
	public double gasSolubility() {
		return gasSolubility;
	}

	public List<PhaseTransition> phaseTransitions() {
		return phaseTransitions;
	}

	/** True if this species is a mixture (has components). */
	public boolean isCompound() {
		return !components.isEmpty();
	}

	/**
	 * Interpolated solubility (g solute / 100 g water) at {@code tempC}.
	 * Returns 0 when no curve is defined (nothing is ever supersaturated).
	 */
	public double solubilityAt(int tempC) {
		if (solubility.isEmpty()) {
			return 0;
		}
		if (solubility.size() == 1) {
			return solubility.get(0).gPer100g();
		}
		List<SolubilityPoint> pts = solubility;
		if (tempC <= pts.get(0).tempC()) {
			return pts.get(0).gPer100g();
		}
		SolubilityPoint last = pts.get(pts.size() - 1);
		if (tempC >= last.tempC()) {
			return last.gPer100g();
		}
		for (int i = 1; i < pts.size(); i++) {
			SolubilityPoint lo = pts.get(i - 1);
			SolubilityPoint hi = pts.get(i);
			if (tempC <= hi.tempC()) {
				double f = (double) (tempC - lo.tempC()) / (hi.tempC() - lo.tempC());
				return lo.gPer100g() + f * (hi.gPer100g() - lo.gPer100g());
			}
		}
		return last.gPer100g();
	}

	private static String getString(JsonObject o, String key, String def) {
		return o.has(key) ? o.get(key).getAsString() : def;
	}

	@Nullable
	private static String getStringOrNull(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsString() : null;
	}

	private static int getInt(JsonObject o, String key, int def) {
		return o.has(key) ? o.get(key).getAsInt() : def;
	}

	private static float getFloat(JsonObject o, String key, float def) {
		return o.has(key) ? o.get(key).getAsFloat() : def;
	}

	private static double getDouble(JsonObject o, String key, double def) {
		return o.has(key) ? o.get(key).getAsDouble() : def;
	}
}
