package com.yu1745.chemicaladdon.composition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;


import net.minecraft.resources.ResourceLocation;

/**
 * One mass-action equilibrium entry (plans/03 §8.2), the single data shape the
 * rules engine's solver consumes — precipitation, complexation and (later)
 * weak-electrolyte ionisation are all "reaction + log_k", not separate
 * mechanisms. Modeled after PHREEQC's PHASES / SOLUTION_SPECIES organisation
 * (each entry carries its own reaction and constant; the manager aggregates
 * them into one global list).
 *
 * <p>Reaction string format (a tiny token grammar, not a formula parser):
 * <pre>
 *   "limestone(s) = Ca+2 + CO3-2"                     // mineral: as-written K = Ksp
 *   "Cu+2 + 4 chemicaladdon:ammonia = [Cu(NH3)4]+2"  // complex ion: as-written K = beta
 *   "chemicaladdon:ammonia + water = NH4+1 + OH-1"   // weak electrolyte (reserved)
 * </pre>
 * Tokens join with {@code +} (spaces around it — the joiner is literally
 * {@code " + "} so ion ids like {@code Cu+2} stay intact), sides split on {@code =}. A term is: optional
 * integer coefficient, then either an <b>ion id</b> (no colon — canonical
 * {@code Symbol±n}, e.g. {@code Cu+2}, {@code SO4-2}, {@code [Cu(NH3)4]+2}),
 * a <b>molecule species id</b> (contains {@code :}, resolved against the
 * species registry, e.g. {@code chemicaladdon:ammonia} — short names default
 * to the mod namespace), or a <b>solid phase</b> with the {@code (s)} suffix.
 * {@code water} is the solvent and is treated as unit activity (ignored in Q).
 *
 * <p>Sign convention (PHREEQC-faithful): {@code log_k} is the constant of the
 * reaction <b>as written</b>, with solids and the solvent at unit activity.
 * Mineral entries are authored in the dissolution direction —
 * {@code limestone(s) = Ca+2 + CO3-2} with log_k ≈ −8.3 means Ksp ≈ 5e−9
 * (exactly how PHREEQC's PHASES block authors them); complex entries are
 * authored as association, log_k = cumulative formation constant β. The solver
 * compares everything in log space against {@code log_k + LOG_K_OFFSET} (the
 * global dimensionless rescaling knob, see {@link Solution}).
 */
public final class Equilibrium {

	/** Where a term lives: the ion multiset, the molecular domain, or a solid phase. */
	public enum TermPhase {
		ION, MOLECULE, SOLID
	}

	public static final class Term {
		private final String key; // ion id, or species id string for MOLECULE/SOLID
		private final TermPhase phase;
		private final int count;
		@Nullable
		private final ResourceLocation species; // MOLECULE/SOLID only

		Term(String key, TermPhase phase, int count, @Nullable ResourceLocation species) {
			this.key = key;
			this.phase = phase;
			this.count = count;
			this.species = species;
		}

		/** Ion id (canonical {@code Symbol±n}) or species id string. */
		public String key() {
			return key;
		}

		public TermPhase phase() {
			return phase;
		}

		public int count() {
			return count;
		}

		/** The resolved species id for MOLECULE/SOLID terms; null for ions. */
		@Nullable
		public ResourceLocation species() {
			return species;
		}

		@Override
		public String toString() {
			return (count > 1 ? count + " " : "") + key;
		}
	}

	private final List<Term> left;
	private final List<Term> right;
	private final double logK;
	private final double deltaH; // NaN = not authored; reserved for van't Hoff (v1 unused)
	/**
	 * Optional kinetic rate constant in solver units per reaction tick at the
	 * 25 °C reference (0 = absent → the entry solves to equilibrium
	 * instantaneously, the default — most aqueous equilibria ARE
	 * millisecond-fast; PHREEQC's stance). When present, the solver moves the
	 * entry toward equilibrium at most {@code rate(T) × |Q/K − 1|} units per
	 * solve (affinity law, once per solve) with {@code rate(T) = rate ×
	 * 2^((T−25)/25)} (coarse Arrhenius) scaled by the vessel's stirring.
	 */
	private final double rateK;

	@Nullable
	private final ResourceLocation solid; // the (s) term, if this is a mineral entry
	private final boolean aqueousOnly;

	private Equilibrium(List<Term> left, List<Term> right, double logK, double deltaH, double rateK,
			@Nullable ResourceLocation solid) {
		this.left = List.copyOf(left);
		this.right = List.copyOf(right);
		this.logK = logK;
		this.deltaH = deltaH;
		this.rateK = rateK;
		this.solid = solid;
		this.aqueousOnly = solid == null;
	}

	public List<Term> left() {
		return left;
	}

	public List<Term> right() {
		return right;
	}

	public double logK() {
		return logK;
	}

	public double deltaH() {
		return deltaH;
	}

	/** The optional kinetic rate constant (units per reaction tick at 25 °C); 0 = instantaneous. */
	public double rate() {
		return rateK;
	}

	/** The solid phase of a mineral entry (precipitation target); null for aqueous entries. */
	@Nullable
	public ResourceLocation solid() {
		return solid;
	}

	/** True when both sides are fully aqueous (complexation / weak electrolyte). */
	public boolean isAqueous() {
		return aqueousOnly;
	}

	@Override
	public String toString() {
		return String.join(" + ", terms(left)) + " = " + String.join(" + ", terms(right))
			+ String.format(Locale.ROOT, " (log_k %.2g)", logK);
	}

	private static List<String> terms(List<Term> side) {
		List<String> out = new ArrayList<>();
		for (Term t : side) {
			out.add(t.toString());
		}
		return out;
	}

	/**
	 * Parse a {@code {"reaction": "...", "log_k": n, "delta_h": optional}} entry.
	 * Returns null (with a logged error) on malformed reactions — a broken
	 * equilibrium silently missing from the solver is a bug, so we fail loud
	 * and let the datapack author notice.
	 */
	@Nullable
	public static Equilibrium parse(String reaction, double logK, double deltaH) {
		return parse(reaction, logK, deltaH, 0);
	}

	/** Full form with the optional kinetic rate constant (0 = instantaneous). */
	@Nullable
	public static Equilibrium parse(String reaction, double logK, double deltaH, double rateK) {
		try {
			String[] sides = reaction.split("=");
			if (sides.length != 2) {
				throw new IllegalArgumentException("expected exactly one '='");
			}
			List<Term> l = parseSide(sides[0]);
			List<Term> r = parseSide(sides[1]);
			if (l.isEmpty() || r.isEmpty()) {
				throw new IllegalArgumentException("empty side");
			}
			ResourceLocation solid = null;
			for (Term t : l) {
				if (t.phase == TermPhase.SOLID) {
					if (solid != null) {
						throw new IllegalArgumentException("two solid terms");
					}
					solid = t.species;
				}
			}
			for (Term t : r) {
				if (t.phase == TermPhase.SOLID) {
					if (solid != null) {
						throw new IllegalArgumentException("two solid terms");
					}
					solid = t.species;
				}
			}
			return new Equilibrium(l, r, logK, deltaH, rateK, solid);
		} catch (Exception e) {
			Chemistry.LOGGER.error("Failed to parse equilibrium '{}': {}", reaction, e.getMessage());
			return null;
		}
	}

	private static List<Term> parseSide(String side) {
		List<Term> out = new ArrayList<>();
		for (String raw : side.split(" \\+ ")) { // joiner is " + " (spaced); ion ids like Cu+2 stay intact
			String token = raw.trim();
			if (token.isEmpty()) {
				continue;
			}
			int count = 1;
			// leading integer coefficient: "4 chemicaladdon:ammonia"
			int space = token.indexOf(' ');
			if (space > 0) {
				String head = token.substring(0, space);
				if (head.matches("\\d+")) {
					count = Integer.parseInt(head);
					token = token.substring(space + 1).trim();
				}
			}
			boolean solid = token.endsWith("(s)");
			if (solid) {
				token = token.substring(0, token.length() - 3).trim();
			}
			if (token.contains(":")) {
				ResourceLocation id = ResourceLocation.tryParse(token);
				if (id == null) {
					throw new IllegalArgumentException("bad species id '" + token + "'");
				}
				out.add(new Term(id.toString(), solid ? TermPhase.SOLID : TermPhase.MOLECULE, count, id));
			} else if (solid) {
				// short solid name: default to the mod namespace
				ResourceLocation id = new ResourceLocation(Chemistry.MOD_ID, token);
				out.add(new Term(id.toString(), TermPhase.SOLID, count, id));
			} else if (token.equals("water")) {
				// bare "water" = the solvent (minecraft:water, unit activity)
				out.add(new Term(Solution.WATER.toString(), TermPhase.MOLECULE, count, Solution.WATER));
			} else {
				out.add(new Term(token, TermPhase.ION, count, null));
			}
		}
		return out;
	}
}
