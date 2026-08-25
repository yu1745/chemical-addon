package com.yu1745.chemicaladdon.composition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * Transient snapshot the rules engine solves on: one miscible phase's ion
 * multiset + molecular species + solid domains (amounts in mB) + temperature.
 * This object is rebuilt every tick and is <b>never</b> persisted in a
 * FluidStack — it is a pure function over the mixture's {@code Ions} +
 * {@code Molecules} + {@code Suspended} + {@code Sediment} domains.
 *
 * <p>Solver v2 (plans/03 §8, PHREEQC-faithful mass-action semantics). All
 * constant-K equilibria — precipitation, complexation, (later) weak
 * electrolytes — are solved by one generic mass-action step over the global
 * {@link Equilibrium} list; curve-tabulated crystallisation is a separate
 * threshold step. Everything is compared in <b>log space</b>
 * ({@code log Q vs log_k + LOG_K_OFFSET}) so log_k values down to −10 cannot
 * underflow, and amounts move in whole integer units (the mixture's
 * charge-neutrality invariant is preserved because whole reaction sets move).
 *
 * <p>Pipeline (iterated {@value #SOLVE_PASSES}× so the steps can react to each
 * other — common-ion effects and complex masking emerge from the iteration):
 * <ol>
 *   <li>{@link #equilibrate()} — every equilibrium entry relaxes toward
 *       {@code Q = K}: minerals precipitate <b>to equilibrium</b> (a saturated
 *       mother liquor stays behind — never fully dry), supersaturated complexes
 *       form, and undersaturated solids redissolve;</li>
 *   <li>{@link #neutralise()} — H+ + OH− → H₂O (exothermic, stoichiometric);</li>
 *   <li>{@link #curveBalance()} — tabulated solubility curves: only the
 *       <b>excess</b> over saturation crystallises to {@link #sediment()}, and
 *       crystallised solids redissolve when the curve allows more.</li>
 * </ol>
 *
 * <p>Amounts are "mole-equivalents": 1 mB × a species' ion count gives that
 * many ion units, so stoichiometry is exact integer arithmetic.
 */
public final class Solution {

	/** The water species id (solvent; unit activity in every Q). */
	public static final ResourceLocation WATER = new ResourceLocation("minecraft", "water");

	private static final String H = "H+1";
	private static final String OH = "OH-1";

	/**
	 * Mineral-only log-K offset (unit convention, plans/03 §8): authored
	 * <b>mineral</b> log_k values (solubility products) are rescaled by this
	 * single knob, exactly like {@link #SOLUBILITY_SCALE} rescales solubility
	 * curves. −2 makes log_k ≤ −5 minerals leave < 1 residual unit in 1000 mB
	 * of water (visually "fully precipitated") while log_k ≥ −3 species keep a
	 * visible saturated mother liquor.
	 *
	 * <p><b>Aqueous entries (complexation, weak electrolytes) get NO offset</b>:
	 * their constants are formation/ionisation ratios already expressed in the
	 * engine's own concentration units. A Kb of 10⁻⁴·⁷⁵ offset by −2 would
	 * ionise below the integer resolution floor and read as a dead entry.
	 */
	public static final double MINERAL_LOG_OFFSET = -2.0;

	/** Outer passes letting equilibrate/neutralise/curveBalance react to each other. */
	private static final int SOLVE_PASSES = 2;

	/** Fixed-point rounds over the equilibrium list per pass (convergence guard). */
	private static final int EQUILIBRIUM_ROUNDS = 12;

	/**
	 * Crystallisation growth rate: per solve, at most this fraction of the
	 * water mass × the supersaturation drives toward the curve (affinity law,
	 * × stirring, × {@link #NUCLEATION_PENALTY} without seed crystals).
	 * Size-relative so a small pot and a big tank settle on the same clock.
	 */
	public static final double CRYSTAL_RATE_FRACTION = 0.1;

	/**
	 * Homogeneous nucleation gate: an unseeded (crystal-free) solution only
	 * self-nucleates at this supersaturation or beyond (form/c_sat − 1 ≥ 0.5)
	 * — below it the solution sits <b>metastable</b> indefinitely (the
	 * quench-cooled supersaturated state that one seed crystal collapses).
	 * Real homogeneous nucleation has exactly this threshold character (a
	 * critical supersaturation); 0.5 keeps ordinary cooling crystallisation
	 * (typically 0.3–1.7× oversaturation) working while giving a wide,
	 * playable metastable zone.
	 */
	public static final double NUCLEATION_AFFINITY = 0.5;

	/**
	 * Homogeneous nucleation penalty: the first crystal forms slowly even past
	 * the gate (multiplied into the growth rate while no seed exists).
	 */
	public static final double NUCLEATION_PENALTY = 0.05;

	private final Map<String, Long> ions = new LinkedHashMap<>(); // ion id → units
	private final Map<ResourceLocation, Long> molecular = new LinkedHashMap<>(); // water/gases/molecular solutes → mB
	private final Map<ResourceLocation, Long> suspended = new LinkedHashMap<>(); // precipitated solid species → mB (slurry)
	private final Map<ResourceLocation, Long> sediment = new LinkedHashMap<>(); // crystallised solid species → mB (settles)
	private final int temperature;
	/** Net reaction energy released this solve, in J (negative = cooling demand, e.g. latent heat of vented steam). */
	private double energyJ;
	/** The solve's feed mass in units (all four domains as constructed) — the heat-capacity basis of {@link #heatRiseC()}. */
	private final long feedUnits;
	/** Mass-transfer coefficient 0.3–1.0 (kinetic rates scale with it; 1.0 = unstirred default). */
	private double stirring = 1.0;
	private final Map<ResourceLocation, Long> netMoved = new LinkedHashMap<>(); // report accumulator (net solid units moved)
	private final Map<ResourceLocation, Boolean> rateLimited = new LinkedHashMap<>(); // report: kinetics held it back
	private final List<Speciation> report = new ArrayList<>();

	/** Solves a phase with no pre-existing solids (pure liquid contents). */
	public Solution(Map<ResourceLocation, Long> molecules, Map<String, Long> ionAmounts, int temperature) {
		this(molecules, ionAmounts, Map.of(), Map.of(), temperature);
	}

	/**
	 * Solves a phase including its solid domains: existing suspended (slurry)
	 * and sediment (settled) amounts participate — undersaturated solids
	 * redissolve. The output maps are the <b>final</b> domains (replace, don't
	 * merge, when writing back).
	 */
	public Solution(Map<ResourceLocation, Long> molecules, Map<String, Long> ionAmounts,
			Map<ResourceLocation, Long> suspendedIn, Map<ResourceLocation, Long> sedimentIn, int temperature) {
		for (Map.Entry<ResourceLocation, Long> e : molecules.entrySet()) {
			if (e.getValue() > 0) {
				this.molecular.put(e.getKey(), e.getValue());
			}
		}
		for (Map.Entry<String, Long> e : ionAmounts.entrySet()) {
			if (e.getValue() > 0) {
				this.ions.put(e.getKey(), e.getValue());
			}
		}
		for (Map.Entry<ResourceLocation, Long> e : suspendedIn.entrySet()) {
			if (e.getValue() > 0) {
				this.suspended.put(e.getKey(), e.getValue());
			}
		}
		for (Map.Entry<ResourceLocation, Long> e : sedimentIn.entrySet()) {
			if (e.getValue() > 0) {
				this.sediment.put(e.getKey(), e.getValue());
			}
		}
		this.temperature = temperature;
		long units = 0;
		for (long v : this.molecular.values()) units += v;
		for (long v : this.ions.values()) units += v;
		for (long v : this.suspended.values()) units += v;
		for (long v : this.sediment.values()) units += v;
		this.feedUnits = units;
	}

	/** Set the vessel's stirring (mass-transfer) coefficient before solving; kinetic rates scale with it. */
	public Solution stirring(double coefficient) {
		this.stirring = Math.max(0.1, Math.min(2.0, coefficient));
		return this;
	}

	public void solve() {
		for (int pass = 0; pass < SOLVE_PASSES; pass++) {
			equilibrate(pass);
			neutralise();
			curveBalance(pass);
		}
		buildReport();
		removeNonPositive(molecular);
		removeNonPositive(ions);
		removeNonPositive(suspended);
		removeNonPositive(sediment);
	}

	/** Final molecular composition (species id → mB) to write back to the mixture. */
	public Map<ResourceLocation, Long> molecular() {
		return molecular;
	}

	/** Final ion multiset (ion id → units) to write back to the mixture. */
	public Map<String, Long> ions() {
		return ions;
	}

	/** Final suspended solids (solid species id → mB) — the turbid slurry domain. */
	public Map<ResourceLocation, Long> suspended() {
		return suspended;
	}

	/** Final sediment (solid species id → mB) — the settled bottom domain. */
	public Map<ResourceLocation, Long> sediment() {
		return sediment;
	}

	/** Net reaction energy of this solve in J (U16 ledger; positive = released, negative = absorbed). */
	public double energyJ() {
		return energyJ;
	}

	/**
	 * The solve's temperature effect, °C (U16 energy ledger, plans/03 §12):
	 * {@code ΔT = Q / (feedUnits × c)} with the declared {@code 1 unit ≡ 1 g}
	 * body and water's specific heat — a fixed reaction heat warms a big
	 * vessel less, which is exactly the mass coupling the old lumped "+X °C
	 * per solve" constant could not express. The basis is the feed mass (the
	 * body that absorbed the heat as it reacted).
	 */
	public double heatRiseC() {
		return feedUnits <= 0 ? 0 : energyJ / (feedUnits * Chemistry.HEAT_CAPACITY_PER_UNIT);
	}

	/**
	 * Remove {@code mB} of water in solver units (open-vessel evaporation —
	 * the solvent vents as steam while the solutes stay, concentrating the
	 * solution). Called by the rules engine after solving; the next solve's
	 * crystallisation then sees the higher concentration. Each vented unit
	 * also carries its latent heat away ({@code energyJ -= units × 2260}),
	 * cooling the remaining body — without a heat source a boiling pot
	 * quenches itself below the boiling point; a burner's input is what keeps
	 * it boiling (the U16 self-limiting negative feedback).
	 *
	 * @return the units actually vented (clamped by the water present) — the
	 *         crystalliser condenses exactly this much back as distillate.
	 */
	/**
	 * Default dissolved-gas retention (Henry-flavoured): units of a GAS-phase
	 * molecular species kept per unit of water before the excess separates as
	 * a pure gas phase. Applies to every gas species that does not author its
	 * own {@code gasSolubility} (e.g. ammonia's is far higher — 1:700 by
	 * volume in reality — or the Solvay ammoniated brine would strip).
	 */
	public static final double GAS_SOLUBILITY_DEFAULT = 1e-3;

	/**
	 * Separate the dissolved-gas phase (generic for EVERY gas species — no
	 * per-gas special cases): each molecular species whose registered phase is
	 * GAS keeps up to {@code gasSolubility × water} units dissolved; the whole-mB
	 * excess leaves the solution. Called by the rules engine right after
	 * {@link #solve()} — the caller decides the separated gas's fate (sealed
	 * vessel: it stays as a gas stack in the tank; open vessel: it vents).
	 */
	public Map<ResourceLocation, Long> degasGases() {
		Map<ResourceLocation, Long> out = new LinkedHashMap<>();
		long water = molecular.getOrDefault(WATER, 0L);
		if (water <= 0) {
			return out;
		}
		for (Map.Entry<ResourceLocation, Long> e : molecular.entrySet()) {
			Species species = SpeciesManager.get(e.getKey());
			if (species == null || species.phase() != Species.Phase.GAS) {
				continue;
			}
			double retention = Double.isNaN(species.gasSolubility())
				? GAS_SOLUBILITY_DEFAULT : species.gasSolubility();
			long excess = e.getValue() - (long) (retention * water);
			// only whole mB leaves — sub-mB fractions stay dissolved (the
			// transport/display granularity is the mB, not the solver unit)
			excess = excess / Chemistry.QUANTA_PER_MB * Chemistry.QUANTA_PER_MB;
			if (excess > 0) {
				out.put(e.getKey(), excess);
			}
		}
		for (Map.Entry<ResourceLocation, Long> e : out.entrySet()) {
			mergeMolecular(e.getKey(), -e.getValue());
		}
		removeNonPositive(molecular);
		return out;
	}

	public long evaporateWater(long units) {
		long vented = Math.min(units, molecular.getOrDefault(WATER, 0L));
		if (vented <= 0) {
			return 0;
		}
		mergeMolecular(WATER, -vented);
		energyJ -= vented * Chemistry.VAPORISATION_J_PER_UNIT;
		return vented;
	}

	/** Per-solid diagnosis of this solve (see {@link Speciation}); empty never — always one entry per candidate. */
	public List<Speciation> report() {
		return report;
	}

	/**
	 * One diagnostic line of the speciation report: the saturation state of one
	 * solid target after solving. {@code si} is PHREEQC's saturation index
	 * ({@code log10(Q/K)}): &gt;0 supersaturated (precipitating), ≈0 at
	 * equilibrium with the solid present, &lt;0 undersaturated (any solid of
	 * this species would dissolve). {@code moved} is the net units moved
	 * <b>into</b> the solid domains this solve (negative = dissolved).
	 */
	public static final class Speciation {
		private final ResourceLocation target;
		private final double si;
		private final long moved;
		private final boolean rateLimited;

		Speciation(ResourceLocation target, double si, long moved, boolean rateLimited) {
			this.target = target;
			this.si = si;
			this.moved = moved;
			this.rateLimited = rateLimited;
		}

		/** 内核路径工厂（P7.4：IPhreeqc SI/相增量 → 化验行）。 */
	public static Speciation of(ResourceLocation target, double si, long moved, boolean rateLimited) {
			return new Speciation(target, si, moved, rateLimited);
		}

		public ResourceLocation target() {
			return target;
		}

		public double si() {
			return si;
		}

		public long moved() {
			return moved;
		}

		/** True when kinetics (not equilibrium) is holding this solid back — "be patient, or stir/seed it". */
		public boolean rateLimited() {
			return rateLimited;
		}

		@Override
		public String toString() {
			return target + "(SI " + String.format(java.util.Locale.ROOT, "%.2f", si) + ", moved " + moved
				+ (rateLimited ? ", kinetics" : "") + ")";
		}
	}

	// ------------------------------------------------------------ pipeline steps

	/**
	 * Global solubility scale (unit convention, plans/03): solubility is authored
	 * as real-world g solute / 100 g water, and the in-game threshold is
	 * {@code gPer100g / 100 × SOLUBILITY_SCALE} formula units per water mB. This
	 * declares "1 formula unit ≡ 1 g" and "1 mB water ≡ 1 g" and deliberately
	 * ignores molar mass — raise/lower this one constant to rescale every
	 * substance's crystallisation point without touching the data tables.
	 */
	public static final double SOLUBILITY_SCALE = 1.0;

	/** The "per 100 g water" divisor in real solubility tables. */
	private static final double SOLUBILITY_PER_100G = 100.0;

	/** Saturation threshold (dissolved formula units per water mB) of a crystallisable solute at {@code tempC}. */
	public static double solubilityThreshold(Species s, int tempC) {
		return s.solubilityAt(tempC) / SOLUBILITY_PER_100G * SOLUBILITY_SCALE;
	}

	/**
	 * Relax every equilibrium entry toward Q = K, most-insoluble first, until a
	 * fixed point (or the round guard — the entries interact through shared
	 * ions, and one entry moving can push another back). Neutralisation runs
	 * <b>inside</b> the rounds: a weak base feeds OH⁻ to the acid ion by ion,
	 * and the interleaving is what makes titration (HCl + NH₃·H₂O → NH₄Cl, to
	 * completion) converge in one solve instead of one OH⁻ per solve.
	 */
	private void equilibrate(int pass) {
		List<Equilibrium> entries = SpeciesManager.allEquilibria();
		for (int round = 0; round < EQUILIBRIUM_ROUNDS; round++) {
			long movedTotal = 0;
			for (Equilibrium eq : entries) {
				if (eq.rate() > 0 && (pass > 0 || round > 0)) {
					continue; // kinetic entries advance once per solve (their tick budget)
				}
				if (hasTerm(eq.right(), H) && bulkAcid()) {
					// strong-acid regime: an acid-producing entry (e.g. NH4+1 +
					// water = ammonia + H+1) would relax BACKWARD and silently
					// protonate the bulk H+ (no heat bookkeeping) before the
					// driven routes below can consume it — freeze such entries
					// while H+ is macroscopic; the equilibrium layer owns the
					// pH only at trace levels (below STRONG_ION_FRACTION).
					continue;
				}
				movedTotal += Math.abs(solveEntry(eq));
			}
			movedTotal += coupleDeficits();
			movedTotal += neutralise();
			if (movedTotal == 0) {
				break;
			}
		}
	}

	/**
	 * Macroscopic-strong-ion threshold: a strong ion counts as BULK once it
	 * exceeds {@link #STRONG_ION_FRACTION} of the feed (≈pH 4 at any scale).
	 * Below it, driven neutralisation stops forcing pairs — otherwise the
	 * driver would eat the H⁺/OH⁻ that a weak-acid/base equilibrium itself
	 * produces (e.g. NH₄⁺ hydrolysis), re-relax it, and pump
	 * {@link Chemistry#NEUTRALISATION_J_PER_PAIR} out of every round forever.
	 */
	private static final double STRONG_ION_FRACTION = 1e-4;


	/**
	 * Coupled co-move: the microscopic-reality fix for integer-floor deadlocks.
	 * In a real solution two coupled reactions happen as ONE molecular event - a
	 * mineral grabs its second OH- at the very instant hydrolysis sheds it. The
	 * sequential single-entry relaxation cannot express that: when a
	 * supersaturated mineral needs {@code c >= 2} units of a shared reactant and
	 * only {@code < c} remain, {@code maxMovable} floors to 0 and BOTH entries
	 * freeze at a false fixed point with {@code Q != K} (first seen with
	 * malachite: 82 Cu2+ stuck in solution with OH- pinned at 1). This pass
	 * lets the mineral's affinity DRAG its supplier: while the mineral is
	 * supersaturated and blocked, push any aqueous entry that produces the
	 * deficient ion one step in the producing direction, then take the
	 * mineral's own step - the generalisation of {@code driveWeakElectrolytes}'s
	 * H+/OH- pairing to arbitrary shared ions.
	 *
	 * <p>Guard rails: only supersaturated MINERALS are driven (their pull is
	 * the thermodynamic engine); H+ is never supplied here (the neutralise
	 * machinery owns it); no supplier means a genuine equilibrium floor, stop.
	 * Whole reaction sets move, so charge neutrality is preserved.
	 *
	 * @return reaction events co-moved this call (feeds the round's progress test)
	 */
	private long coupleDeficits() {
		long events = 0;
		for (Equilibrium mineral : SpeciesManager.allEquilibria()) {
			if (mineral.solid() == null) {
				continue; // driven side: minerals only
			}
			for (int guard = 0; guard < 100_000; guard++) {
				if (logQ(mineral, 0) <= effectiveLogK(mineral)) {
					break; // no longer supersaturated: the normal relaxation owns it
				}
				// deficit analysis on the mineral's aqueous reactants (right side)
				Equilibrium.Term deficit = null;
				for (Equilibrium.Term t : mineral.right()) {
					if (isAqueous(t) && amountOf(t) < t.count()) {
						deficit = t;
						break;
					}
				}
				if (deficit == null) {
					break; // not blocked: the normal relaxation owns it
				}
				if (deficit.key().equals(H)) {
					break; // H+ belongs to the neutralise machinery
				}
				// a supplier: an aqueous entry that produces the deficient ion —
				// but ONLY when the COUPLED event is net-downhill, exactly the
				// microscopic criterion (two reactions proceed together when the
				// combined event has negative ΔG). Without this guard the pass
				// would unwind complexes through the trace-ion floor: e.g. drag
				// [Zn(NH3)4]2- apart one unit at a time to feed Zn(OH)2 that is
				// only "supersaturated" because 1 leftover Zn unit is the grid's
				// smallest representable trace.
				double mineralPull = logQ(mineral, 0) - effectiveLogK(mineral);
				Equilibrium supplierFound = null;
				int supplyDir = 0;
				List<Equilibrium.Term> consumedSide = null;
				for (Equilibrium supplier : SpeciesManager.allEquilibria()) {
					if (supplier.solid() != null || supplier == mineral) {
						continue;
					}
					for (int d : SUPPLY_DIRECTIONS) {
						List<Equilibrium.Term> produced = d > 0 ? supplier.right() : supplier.left();
						List<Equilibrium.Term> consumed = d > 0 ? supplier.left() : supplier.right();
						if (!hasTerm(produced, deficit.key()) || maxMovable(consumed) <= 0) {
							continue;
						}
						if (!consumesWater(consumed)) {
							// only SOLVENT-mediated steps may be dragged: a weak
							// electrolyte ionising (water on its reactant side) is
							// the microscopic picture — the mineral tears OH- out of
							// water. Anything else (a complex dissociating to feed a
							// trace metal) is grid noise unwinding, not coupling.
							continue;
						}
						// the supplier step's own affinity in direction d (log K − log Q
						// for forward, reversed for backward)
						double supplierAffinity = d > 0
							? effectiveLogK(supplier) - logQ(supplier, 0)
							: logQ(supplier, 0) - effectiveLogK(supplier);
						if (mineralPull + supplierAffinity <= 0) {
							continue; // the coupled event is net-uphill: not microscopically real
						}
						supplierFound = supplier;
						supplyDir = d;
						consumedSide = consumed;
						break;
					}
					if (supplierFound != null) {
						break;
					}
				}
				if (supplierFound == null) {
					break; // a real floor: nothing can supply the deficit
				}
				// the mineral's step, now that the deficit is covered. The event
				// is validated at ONE unit, then (U18 grid-fineness fix) moved in
				// BULK: supply exactly what the mineral can absorb — its extent
				// as limited by every OTHER aqueous reactant — and let it take
				// its own equilibrium-bounded step (the same bisected backward
				// relax solveEntry uses). One quantum at a time would tie total
				// progress to the iteration guard, so a finer grid would
				// precipitate slower in real terms; draining the supplier's whole
				// capacity instead floods the solution far past what
				// precipitation takes back out and the round budget ends mid-
				// restore. The absorb-sized chunk keeps the co-move grid-agnostic
				// without overshoot; any small leftover is relaxed back.
				long extent = Long.MAX_VALUE;
				for (Equilibrium.Term t : mineral.right()) {
					if (isAqueous(t) && !t.key().equals(deficit.key())) {
						extent = Math.min(extent, amountOf(t) / t.count());
					}
				}
				long producedPerEvent = 1;
				for (Equilibrium.Term t : supplyDir > 0 ? supplierFound.right() : supplierFound.left()) {
					if (t.key().equals(deficit.key())) {
						producedPerEvent = t.count();
					}
				}
				long need = extent == Long.MAX_VALUE ? Long.MAX_VALUE
					: (long) Math.ceil((double) (extent * deficit.count() - amountOf(deficit)) / producedPerEvent);
				long chunk = Math.max(1, Math.min(need, maxMovable(consumedSide)));
				if (chunk > 1) {
					applyMove(supplierFound, supplyDir * chunk);
					long m = bisect(mineral, 0, maxMovable(mineral.right()), effectiveLogK(mineral), false);
					if (m > 0) {
						applyMove(mineral, -m);
						events += m;
					} else if (maxMovable(mineral.right()) > 0) {
						applyMove(mineral, -1); // sub-count tail (deficit count > 1 per event)
						events++;
					}
				} else if (maxMovable(mineral.right()) > 0) {
					applyMove(mineral, -1);
					events++;
				}
			}
		}
		return events;
	}

	/** Both relaxation directions for the supplier search (forward, backward). */
	private static final int[] SUPPLY_DIRECTIONS = { 1, -1 };

	private boolean bulkAcid() {
		return bulkIon(ions.getOrDefault(H, 0L));
	}

	private boolean bulkIon(long units) {
		return units >= Math.max(1, (long) (feedUnits * STRONG_ION_FRACTION));
	}

	/**
	 * Mass-action relax of one entry: find how many whole reaction units to move
	 * (forward = as-written, backward = reverse) so that
	 * {@code log Q ≤ log_k + LOG_K_OFFSET} without overshooting — i.e. approach
	 * the equilibrium from the allowed side and stop at the last integer step
	 * that keeps the quotient on the initial side of K (a saturated mother
	 * liquor of ≤ a few units stays behind). Monotone in the moved units, so a
	 * binary search over [0, movable] finds the boundary.
	 */
	private long solveEntry(Equilibrium eq) {
		long water = molecular.getOrDefault(WATER, 0L);
		if (water <= 0) {
			return 0; // no solvent → activities undefined; nothing is dissolved
		}
		double logKeq = effectiveLogK(eq);
		double g0 = logQ(eq, 0);
		long moved;
		if (g0 < logKeq) {
			// deficient in products as written → move forward
			long max = maxMovable(eq.left());
			moved = max > 0 ? bisect(eq, 0, max, logKeq, true) : 0;
		} else if (g0 > logKeq) {
			// excess products → move backward (reverse of as-written)
			long max = maxMovable(eq.right());
			moved = max > 0 ? -bisect(eq, 0, max, logKeq, false) : 0;
		} else {
			return 0;
		}
		if (eq.rate() > 0) {
			// affinity-law kinetics: cap this solve's advance at
			// rate(T) × |Q/K − 1| (× stirring); the drive uses the pre-move
			// quotient, so relaxation is geometric — fast far from K, asymptotic near it
			double drive = Math.abs(Math.pow(10, g0 - logKeq) - 1);
			drive = Math.min(drive, 1000); // clamp absurd supersaturations (Fe(OH)₃ territory)
			double k = eq.rate() * arrhenius(temperature) * stirring;
			long cap = Math.max(1L, Math.round(k * drive));
			long limited = moved > 0 ? Math.min(moved, cap) : Math.max(moved, -cap);
			if (Math.abs(limited) < Math.abs(moved) && eq.solid() != null) {
				rateLimited.merge(eq.solid(), true, (a, b) -> true);
			}
			moved = limited;
		}
		if (moved != 0) {
			applyMove(eq, moved);
		}
		return moved;
	}

	/** Coarse Arrhenius: rates double per 25 °C above the 25 °C reference. */
	private static double arrhenius(int temperatureC) {
		return Math.pow(2, (temperatureC - 25) / 25.0);
	}

	/**
	 * The forward reaction quotient in log space at {@code m} units moved
	 * forward (negative = backward): {@code log10(Π a(products) / Π a(reactants))}
	 * over aqueous terms only — solids and the solvent are unit activity.
	 * Monotone non-decreasing in {@code m}.
	 *
	 * <p>Integer resolution floor: a fully-consumed species sits at half a unit
	 * of concentration ({@code 0.5 / water}). Below one unit the model simply
	 * cannot see concentration differences, and the floor keeps every quotient
	 * finite (no NaN/±infinity boundary states for the bisection to trip on).
	 * A move may therefore consume a reactant down to <i>zero</i> when the
	 * quotient still allows it — e.g. very insoluble minerals precipitate the
	 * last unit (residual 0), while near-soluble ones keep a saturated trace.
	 */
	private double logQ(Equilibrium eq, long m) {
		long water = molecular.getOrDefault(WATER, 0L);
		double q = 0;
		for (Equilibrium.Term t : eq.right()) {
			if (isAqueous(t)) {
				q += t.count() * Math.log10(conc(amountOf(t) + m * t.count(), water));
			}
		}
		for (Equilibrium.Term t : eq.left()) {
			if (isAqueous(t)) {
				q -= t.count() * Math.log10(conc(amountOf(t) - m * t.count(), water));
			}
		}
		return q;
	}

	/** Concentration with the half-unit floor for exhausted species (see {@link #logQ}). */
	private static double conc(long amount, long water) {
		return (amount > 0 ? amount : 0.5) / water;
	}

	/**
	 * Largest {@code m} in [0, hi] with the quotient still on the initial side
	 * of K: forward (g(m) increasing, keep g(m) ≤ logKeq) or backward (g(−m)
	 * decreasing, keep g(−m) ≥ logKeq). Returns 0 when no move fits.
	 */
	private long bisect(Equilibrium eq, long lo, long hi, double logKeq, boolean forward) {
		if (forward ? logQ(eq, hi) <= logKeq : logQ(eq, -hi) >= logKeq) {
			return hi; // the whole range fits
		}
		while (lo < hi) {
			long mid = lo + (hi - lo + 1) / 2;
			double g = forward ? logQ(eq, mid) : logQ(eq, -mid);
			boolean ok = forward ? g <= logKeq : g >= logKeq;
			if (ok) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return lo;
	}

	/** Whole reaction units movable by consuming {@code side} (aqueous or solid amounts). */
	private long maxMovable(List<Equilibrium.Term> side) {
		long max = Long.MAX_VALUE;
		for (Equilibrium.Term t : side) {
			long avail = switch (t.phase()) {
				case ION -> ions.getOrDefault(t.key(), 0L);
				case MOLECULE -> molecular.getOrDefault(t.species(), 0L);
			case SOLID -> suspended.getOrDefault(t.species(), 0L); // mineral solids live in the slurry domain
		};
			max = Math.min(max, avail / t.count());
		}
		return max == Long.MAX_VALUE ? 0 : max;
	}

	/** Move {@code m} signed reaction units through the entry (whole sets → charge neutrality holds). */
	private void applyMove(Equilibrium eq, long m) {
		for (Equilibrium.Term t : eq.left()) {
			moveTerm(t, -m);
		}
		for (Equilibrium.Term t : eq.right()) {
			moveTerm(t, m);
		}
	}

	private void moveTerm(Equilibrium.Term t, long m) {
		if (m == 0) {
			return;
		}
		long delta = m * t.count();
		switch (t.phase()) {
			case ION -> mergeIon(t.key(), delta);
			case MOLECULE -> {
				if (!isSolvent(t)) { // solvent stays unit activity and is never consumed
					mergeMolecular(t.species(), delta);
				}
			}
			case SOLID -> {
				mergeSuspended(t.species(), delta);
				netMoved.merge(t.species(), delta, Long::sum);
			}
		}
	}

	/** True when the term participates in Q: ions and non-solvent molecules. */
	private static boolean isAqueous(Equilibrium.Term t) {
		return t.phase() != Equilibrium.TermPhase.SOLID && !isSolvent(t);
	}

	/** The solvent at unit activity — any molecule whose path is "water". */
	private static boolean isSolvent(Equilibrium.Term t) {
		return t.phase() == Equilibrium.TermPhase.MOLECULE && t.species() != null && t.species().getPath().equals("water");
	}

	/** True when the side consumes the solvent (a weak-electrolyte ionisation step). */
	private static boolean consumesWater(List<Equilibrium.Term> side) {
		for (Equilibrium.Term t : side) {
			if (isSolvent(t)) {
				return true;
			}
		}
		return false;
	}

	private long amountOf(Equilibrium.Term t) {
		return switch (t.phase()) {
			case ION -> ions.getOrDefault(t.key(), 0L);
			case MOLECULE -> molecular.getOrDefault(t.species(), 0L);
			case SOLID -> 0; // unit activity, not counted
		};
	}

	/**
	 * H+ + OH- → H₂O (exothermic), plus the weak-electrolyte pathway: a strong
	 * acid titrates a weak base to completion by eating each OH⁻ the base
	 * sheds (H+ drives the ionisation forward, one unit at a time — the
	 * equilibrium bisection alone stalls a fraction of a log-unit short at
	 * integer resolution). Stoichiometric and unconditional — the Kw-mediated
	 * driving force is effectively infinite, exactly why the direct H/OH pair
	 * is also stoichiometric here rather than an equilibrium. The reverse
	 * direction (a strong base deprotonating NH₄⁺) is deliberately NOT forced:
	 * the backward bisection already relaxes it to its real equilibrium, and a
	 * forced pass would eat the weak base's own ionisation product.
	 *
	 * @return H/OH pairs consumed (heat accounting).
	 */
	private long neutralise() {
		long pairs = neutraliseDirect();
		pairs += driveWeakElectrolytes();
		return pairs;
	}

	private long neutraliseDirect() {
		long h = ions.getOrDefault(H, 0L);
		long oh = ions.getOrDefault(OH, 0L);
		long n = Math.min(h, oh);
		// both ions must be BULK: if only one is macroscopic the minority ion is
		// equilibrium-trace (e.g. the H⁺ that NH₄⁺ hydrolysis sheds into an
		// alkaline liquor) and consuming it per round would pump heat forever
		if (n <= 0 || !bulkIon(h) || !bulkIon(oh)) {
			return 0;
		}
		mergeIon(H, -n);
		mergeIon(OH, -n);
		mergeMolecular(WATER, n);
		energyJ += n * Chemistry.NEUTRALISATION_J_PER_PAIR;
		return n;
	}

	/**
	 * For every aqueous entry with OH⁻ on one side: while the opposite strong
	 * ion (H⁺ if the entry <i>produces</i> OH⁻, OH⁻ if it consumes it) is
	 * present, drive the entry one unit toward OH⁻ and neutralise that OH⁻
	 * straight away. This is what makes HCl + NH₃·H₂O → NH₄Cl and
	 * H⁺ + [Al(OH)₄]⁻ → Al(OH)₃↓ + H₂O run to completion in one solve.
	 */
	private long driveWeakElectrolytes() {
		long pairs = 0;
		for (Equilibrium eq : SpeciesManager.allEquilibria()) {
			if (eq.solid() != null) {
				continue; // aqueous entries only (weak electrolytes / complexes)
			}
			boolean ohRight = hasTerm(eq.right(), OH);
			boolean ohLeft = hasTerm(eq.left(), OH);
			if (!ohRight && !ohLeft) {
				continue;
			}
			for (int guard = 0; guard < 100_000; guard++) {
				boolean did = false;
				if (ohRight && bulkAcid() && maxMovable(eq.left()) > 0) {
					applyMove(eq, 1); // shed one OH⁻ ...
					did = consumeHydroxideWithAcid();
				} else if (ohLeft && bulkAcid() && maxMovable(eq.right()) > 0) {
					applyMove(eq, -1); // ... or release one OH⁻ from the product side
					did = consumeHydroxideWithAcid();
				}
				if (!did) {
					break;
				}
				pairs++;
			}
		}
		return pairs;
	}

	/** H⁺ + the freshly produced OH⁻ → water (one pair, exothermic). */
	private boolean consumeHydroxideWithAcid() {
		if (ions.getOrDefault(H, 0L) > 0 && ions.getOrDefault(OH, 0L) > 0) {
			mergeIon(H, -1);
			mergeIon(OH, -1);
			mergeMolecular(WATER, 1);
			energyJ += Chemistry.NEUTRALISATION_J_PER_PAIR;
			return true;
		}
		return false;
	}

	private static boolean hasTerm(List<Equilibrium.Term> side, String ionId) {
		for (Equilibrium.Term t : side) {
			if (t.phase() == Equilibrium.TermPhase.ION && t.key().equals(ionId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Tabulated-solubility step (crystallisation) — <b>kinetic by default</b>
	 * (real crystal growth takes time; the equilibrium target stays the curve):
	 * <ul>
	 *   <li><b>Growth</b>: per solve at most
	 *       {@code CRYSTAL_RATE_FRACTION × water × (c/c_sat − 1) × stirring}
	 *       units of excess leave the solution (affinity law; geometric
	 *       approach, never overshoots). Without seed crystals the rate is
	 *       further multiplied by {@link #NUCLEATION_PENALTY} — the metastable
	 *       zone: quench-cooled solutions sit supersaturated for a long while,
	 *       one seed crashes them out.</li>
	 *   <li><b>Dissolution</b> of settled crystals is fast (fine-crystal
	 *       surface kinetics) — instant to the curve.</li>
	 *   <li><b>Evaporite dry-out</b>: with the solvent gone, dissolved curve
	 *       species crash out wholesale (boiling a pot dry yields salt).</li>
	 * </ul>
	 */
	private void curveBalance(int pass) {
		long water = molecular.getOrDefault(WATER, 0L);
		for (Species s : SpeciesManager.all()) {
			if (!s.isCrystallisable() || !s.isElectrolyte()) {
				continue;
			}
			long form = formableUnits(ions, s);
			long settled = sediment.getOrDefault(s.solute(), 0L);
			if (water <= 0) {
				// evaporite dry-out: no solvent left, everything dissolved crashes out
				if (form > 0) {
					for (Species.IonComponent c : s.ions()) {
						mergeIon(c.ion().id(), -form * c.count());
					}
					mergeSediment(s.solute(), form);
					netMoved.merge(s.solute(), form, Long::sum);
				}
				continue;
			}
			double threshold = solubilityThreshold(s, temperature);
			long cap = (long) Math.floor(threshold * water);
			if (form > cap) {
				if (pass > 0) {
					continue; // kinetic growth gets its tick budget once per solve
				}
				long excess = form - cap;
				double affinity = cap > 0 ? (double) form / cap - 1 : 1; // supersaturation drive
				if (settled <= 0 && affinity < NUCLEATION_AFFINITY) {
					continue; // metastable: unseeded and below the nucleation gate — nothing happens
				}
				double rate = CRYSTAL_RATE_FRACTION * water * affinity * stirring;
				if (settled <= 0) {
					rate *= NUCLEATION_PENALTY; // homogeneous nucleation: the first crystal forms slowly
				}
				long move = Math.min(excess, Math.max(1, Math.round(rate)));
				for (Species.IonComponent c : s.ions()) {
					mergeIon(c.ion().id(), -move * c.count());
				}
				mergeSediment(s.solute(), move);
				netMoved.merge(s.solute(), move, Long::sum);
				rateLimited.merge(s.solute(), move < excess, (a, b) -> a || b);
			} else if (settled > 0 && form < cap) {
				// undersaturated with crystals present: instant redissolution to the curve
				long move = Math.min(cap - form, settled);
				for (Species.IonComponent c : s.ions()) {
					mergeIon(c.ion().id(), move * c.count());
				}
				mergeSediment(s.solute(), -move);
				netMoved.merge(s.solute(), -move, Long::sum);
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	/** Whole formula units of {@code s} formable from the given ion amounts (no water cap). */
	public static long formableUnits(Map<String, Long> ionAmounts, Species s) {
		long form = Long.MAX_VALUE;
		for (Species.IonComponent c : s.ions()) {
			long avail = ionAmounts.getOrDefault(c.ion().id(), 0L);
			form = Math.min(form, avail / c.count());
		}
		return form == Long.MAX_VALUE ? 0 : form;
	}

	private void buildReport() {
		long water = molecular.getOrDefault(WATER, 0L);
		for (Equilibrium eq : SpeciesManager.allEquilibria()) {
			if (eq.solid() == null) {
				continue; // aqueous entries (complexation) have no mineral to report
			}
			double si = water <= 0 ? Double.NEGATIVE_INFINITY : mineralSI(eq, water);
			report.add(new Speciation(eq.solid(), si, netMoved.getOrDefault(eq.solid(), 0L),
				rateLimited.getOrDefault(eq.solid(), false)));
		}
		for (Species s : SpeciesManager.all()) {
			if (!s.isCrystallisable()) {
				continue;
			}
			double si;
			if (water <= 0) {
				si = Double.NEGATIVE_INFINITY;
			} else {
				double threshold = solubilityThreshold(s, temperature);
				double concentration = (double) formableUnits(ions, s) / water;
				si = threshold > 0 ? Math.log10(concentration / threshold) : Double.POSITIVE_INFINITY;
			}
			report.add(new Speciation(s.solute(), si, netMoved.getOrDefault(s.solute(), 0L),
				rateLimited.getOrDefault(s.solute(), false)));
		}
	}

	/** SI of a mineral entry: log10 of its aqueous constituents' product minus the effective log K. */
	private double mineralSI(Equilibrium eq, long water) {
		boolean solidOnLeft = eq.left().stream().anyMatch(t -> t.phase() == Equilibrium.TermPhase.SOLID);
		List<Equilibrium.Term> aq = solidOnLeft ? eq.right() : eq.left(); // the side opposite the solid
		boolean hasSolid = suspended.getOrDefault(eq.solid(), 0L) > 0;
		double sum = 0;
		for (Equilibrium.Term t : aq) {
			if (!isAqueous(t)) {
				continue;
			}
			long amount = amountOf(t);
			if (amount <= 0 && !hasSolid) {
				// a constituent is truly absent and no solid exists: this
				// mineral can never move — read as utterly undersaturated
				return Double.NEGATIVE_INFINITY;
			}
			// exhausted-but-precipitated reads at the half-unit floor (the
			// model's resolution limit), consistent with logQ
			sum += t.count() * Math.log10(conc(amount, water));
		}
		return sum - effectiveLogK(eq);
	}

	/** The entry's effective log K: minerals get {@link #MINERAL_LOG_OFFSET}, aqueous entries raw log_k. */
	private static double effectiveLogK(Equilibrium eq) {
		return eq.logK() + (eq.solid() != null ? MINERAL_LOG_OFFSET : 0);
	}

	private void mergeIon(String id, long delta) {
		long v = ions.getOrDefault(id, 0L) + delta;
		if (v <= 0) {
			ions.remove(id);
		} else {
			ions.put(id, v);
		}
	}

	private void mergeMolecular(ResourceLocation id, long delta) {
		long v = molecular.getOrDefault(id, 0L) + delta;
		if (v <= 0) {
			molecular.remove(id);
		} else {
			molecular.put(id, v);
		}
	}

	private void mergeSuspended(ResourceLocation id, long delta) {
		long v = suspended.getOrDefault(id, 0L) + delta;
		if (v <= 0) {
			suspended.remove(id);
		} else {
			suspended.put(id, v);
		}
	}

	private void mergeSediment(ResourceLocation id, long delta) {
		long v = sediment.getOrDefault(id, 0L) + delta;
		if (v <= 0) {
			sediment.remove(id);
		} else {
			sediment.put(id, v);
		}
	}

	private static void removeNonPositive(Map<?, Long> map) {
		map.values().removeIf(v -> v <= 0);
	}
}
