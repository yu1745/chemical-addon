package com.yu1745.chemicaladdon.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.fluid.IonColors;
import com.yu1745.chemicaladdon.fluid.SolidColors;
import com.yu1745.chemicaladdon.composition.parity.KernelSolutionState;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The mixed solid: what whole-lump extraction yields when a vessel's solid
 * domain holds more than one species (plans/03 §12, U15).
 *
 * <p>Domain-per-species bookkeeping is a ledger, not a separation — physically
 * the crystals at the bottom are one muddled mass, and handing the player pure
 * items for it would erase the entire purification game. So:
 * <ul>
 *   <li><b>strict single-species = pure</b> — one species in the domain
 *       extracts as that species' plain item;</li>
 *   <li><b>any second species = this item</b> — a mixed salt residue whose NBT
 *       carries the composition as GCD-reduced ratio parts (the same identity
 *       trick as the mixture fluid's tag: equal tags stack, and the
 *       deterministic engine guarantees the same feed yields the same
 *       residue);</li>
 *   <li><b>dissolving it is the assay</b> — dropping it back into water
 *       expands the NBT composition exactly into the ion domain, where test
 *       papers (U17) can detect each species.</li>
 * </ul>
 *
 * <p>U16.5 adds the <b>mother-liquor phase</b>: extraction is mechanically
 * imperfect — the wet cake drags along a declared pore fraction of the liquor
 * it formed in (entrainment). The entrained ions live in a second NBT map
 * ({@code Liquor}: water + ion ids + {@code s:}-prefixed molecular solutes,
 * jointly GCD-reduced with the solids so the solids↔liquor ratio is part of
 * the stack identity). A single-species cake whose entrained liquor still
 * holds ions is therefore <b>not</b> pure — washing is what earns the pure
 * item, and the machine never judges concentration, only composition
 * (water-only liquor is invisible, exactly like the mixture itself).
 *
 * <p>Player-visible information obeys the measurement-honesty principle
 * (plans/03 §6): unified name and a blended colour (both physically
 * observable). The composition percentages are engine-internal knowledge and
 * only show in dev assay mode ({@link Chemistry#ASSAY}).
 */
public class MixedResidueItem extends Item {

	/** NBT key: composition ratio parts (solid species id → GCD-reduced part). */
	public static final String TAG_SOLIDS = "Solids";

	/** NBT key: entrained mother-liquor ratio parts (water/ions/solutes, U16.5). */
	public static final String TAG_LIQUOR = "Liquor";

	/** Liquor-map key of water (solvent — never collides with an ion id). */
	public static final String LIQUOR_WATER = "water";

	/** Liquor-map key prefix for molecular solutes (rest are bare ion ids). */
	public static final String LIQUOR_SOLUTE_PREFIX = "s:";
	/** Exact raw mother-liquor payload; never reconstructed from the cosmetic liquor map. */
	public static final String TAG_ENGINE_LIQUOR_RAW = "EngineLiquorRaw";
	public static final String TAG_ENGINE_LIQUOR_MB = "EngineLiquorMb";
	/** Exact solid ledger for wet-cake reinjection; cosmetic Solids is only a ratio view. */
	public static final String TAG_ENGINE_SOLIDS = "EngineSolids";

	public MixedResidueItem(Properties properties) {
		super(properties);
	}

	/**
	 * Build one residue stack (1000 mB of mixed solids) from the domain's unit
	 * amounts. The parts are the GCD-reduced composition, so identical
	 * compositions produce byte-identical tags and stack together.
	 */
	public static ItemStack of(Map<ResourceLocation, Long> unitAmounts) {
		return of(unitAmounts, Map.of(), 0);
	}

	/**
	 * Build one wet-cake residue stack: solids plus the entrained mother
	 * liquor (ions and molecular solutes in solver units, and the entrained
	 * water in units). Everything is jointly GCD-reduced into ratio parts —
	 * the same feed (and the same wash) yields byte-identical tags.
	 */
	public static ItemStack of(Map<ResourceLocation, Long> unitAmounts, Map<String, Long> liquorUnits, long waterUnits) {
		ItemStack stack = new ItemStack(com.yu1745.chemicaladdon.registry.AllItems.MIXED_RESIDUE.get());
		// joint GCD over solids + liquor entries: the solids↔liquor ratio is part
		// of the stack identity (an unwashed and a washed cake never stack)
		long g = 0;
		for (long v : unitAmounts.values()) {
			g = gcd(g, v);
		}
		for (long v : liquorUnits.values()) {
			g = gcd(g, v);
		}
		g = gcd(g, waterUnits);
		CompoundTag solids = new CompoundTag();
		for (Map.Entry<ResourceLocation, Long> e : unitAmounts.entrySet()) {
			long part = g > 1 ? e.getValue() / g : e.getValue();
			if (part > 0) {
				solids.putInt(e.getKey().toString(), (int) Math.min(part, Integer.MAX_VALUE));
			}
		}
		if (!solids.isEmpty()) {
			stack.getOrCreateTag().put(TAG_SOLIDS, solids);
		}
		if (waterUnits > 0 || !liquorUnits.isEmpty()) {
			CompoundTag liquor = new CompoundTag();
			if (waterUnits > 0) {
				liquor.putInt(LIQUOR_WATER, (int) Math.min(g > 1 ? waterUnits / g : waterUnits, Integer.MAX_VALUE));
			}
			for (Map.Entry<String, Long> e : liquorUnits.entrySet()) {
				long part = g > 1 ? e.getValue() / g : e.getValue();
				if (part > 0) {
					liquor.putInt(e.getKey(), (int) Math.min(part, Integer.MAX_VALUE));
				}
			}
			if (!liquor.isEmpty()) {
				stack.getOrCreateTag().put(TAG_LIQUOR, liquor);
			}
		}
		return stack;
	}

	/** Attach a proportional engine-owned mother-liquor batch to a wet cake. */
	public static ItemStack withEngineLiquor(ItemStack stack, KernelSolutionState liquor) {
		if (stack.isEmpty() || liquor == null) throw new IllegalArgumentException("cake and liquor are required");
		CompoundTag tag = stack.getOrCreateTag();
		tag.putString(TAG_ENGINE_LIQUOR_RAW, liquor.raw());
		tag.putInt(TAG_ENGINE_LIQUOR_MB, liquor.referenceMb());
		return stack;
	}

	@Nullable
	public static KernelSolutionState engineLiquor(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(TAG_ENGINE_LIQUOR_RAW) || !tag.contains(TAG_ENGINE_LIQUOR_MB, 99)) return null;
		try { return new KernelSolutionState(tag.getString(TAG_ENGINE_LIQUOR_RAW), tag.getInt(TAG_ENGINE_LIQUOR_MB)); }
		catch (IllegalArgumentException ignored) { return null; }
	}

	/** Attach exact extracted solid mol inventory to a wet cake. */
	public static ItemStack withEngineSolids(ItemStack stack, List<KernelSolutionState.SolidPhase> solids) {
		if (stack.isEmpty() || solids == null) throw new IllegalArgumentException("cake and solids are required");
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (KernelSolutionState.SolidPhase solid : solids) {
			CompoundTag tag = new CompoundTag();
			tag.putString("Species", solid.speciesId()); tag.putDouble("Mol", solid.mol());
			tag.putString("Location", solid.location().name()); list.add(tag);
		}
		stack.getOrCreateTag().put(TAG_ENGINE_SOLIDS, list);
		return stack;
	}

	/** Exact solid inventory; malformed payload is rejected rather than guessed from ratio parts. */
	public static List<KernelSolutionState.SolidPhase> engineSolids(ItemStack stack) {
		CompoundTag root = stack.getTag(); if (root == null) return List.of();
		List<KernelSolutionState.SolidPhase> out = new java.util.ArrayList<>();
		net.minecraft.nbt.ListTag encoded = root.getList(TAG_ENGINE_SOLIDS, net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < encoded.size(); i++) try {
			CompoundTag tag = encoded.getCompound(i);
			out.add(new KernelSolutionState.SolidPhase(tag.getString("Species"), tag.getDouble("Mol"),
					KernelSolutionState.SolidLocation.valueOf(tag.getString("Location"))));
		} catch (RuntimeException bad) { return List.of(); }
		return List.copyOf(out);
	}

	/** The composition ratio parts (species id → part); empty when no NBT. */
	public static Map<ResourceLocation, Integer> parts(ItemStack stack) {
		Map<ResourceLocation, Integer> parts = new LinkedHashMap<>();
		CompoundTag c = liquorContainer(stack, TAG_SOLIDS);
		for (String key : c.getAllKeys()) {
			ResourceLocation id = ResourceLocation.tryParse(key);
			if (id != null && c.getInt(key) > 0) {
				parts.put(id, c.getInt(key));
			}
		}
		return parts;
	}

	/** The entrained mother-liquor ratio parts ({@link #LIQUOR_WATER}/ion id/{@code s:}solute → part). */
	public static Map<String, Integer> liquorParts(ItemStack stack) {
		Map<String, Integer> parts = new LinkedHashMap<>();
		CompoundTag c = liquorContainer(stack, TAG_LIQUOR);
		for (String key : c.getAllKeys()) {
			if (c.getInt(key) > 0) {
				parts.put(key, c.getInt(key));
			}
		}
		return parts;
	}

	private static CompoundTag liquorContainer(ItemStack stack, String key) {
		if (stack.isEmpty() || !(stack.getItem() instanceof MixedResidueItem)) {
			return new CompoundTag();
		}
		CompoundTag tag = stack.getTag();
		return tag != null ? tag.getCompound(key) : new CompoundTag();
	}

	/** Weight-blended RGB of solids + entrained ions (ItemColor tint; grey when bare). */
	public static int colorOf(ItemStack stack) {
		Map<ResourceLocation, Integer> parts = parts(stack);
		Map<String, Integer> liquor = liquorParts(stack);
		long r = 0, g = 0, b = 0, total = 0;
		for (Map.Entry<ResourceLocation, Integer> e : parts.entrySet()) {
			int c = SolidColors.of(e.getKey());
			r += (long) ((c >> 16) & 0xFF) * e.getValue();
			g += (long) ((c >> 8) & 0xFF) * e.getValue();
			b += (long) (c & 0xFF) * e.getValue();
			total += e.getValue();
		}
		for (Map.Entry<String, Integer> e : liquor.entrySet()) {
			if (e.getKey().equals(LIQUOR_WATER) || e.getKey().startsWith(LIQUOR_SOLUTE_PREFIX)) {
				continue; // solvent and colourless solutes: no tint contribution
			}
			int c = IonColors.of(e.getKey());
			r += (long) ((c >> 16) & 0xFF) * e.getValue();
			g += (long) ((c >> 8) & 0xFF) * e.getValue();
			b += (long) (c & 0xFF) * e.getValue();
			total += e.getValue();
		}
		if (total <= 0) {
			return 0x9A9A94; // bare stack fallback: the baked neutral grey
		}
		return 0xFF000000 | ((int) (r / total) << 16) | ((int) (g / total) << 8) | (int) (b / total);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);
		if (!Chemistry.ASSAY) {
			return; // measurement honesty: composition is dev-assay knowledge only
		}
		Map<ResourceLocation, Integer> parts = parts(stack);
		Map<String, Integer> liquor = liquorParts(stack);
		long total = 0;
		for (int v : parts.values()) {
			total += v;
		}
		for (int v : liquor.values()) {
			total += v;
		}
		if (total <= 0) {
			return;
		}
		for (Map.Entry<ResourceLocation, Integer> e : parts.entrySet()) {
			appendPct(tooltip, e.getValue(), total, e.getKey().toString());
		}
		for (Map.Entry<String, Integer> e : liquor.entrySet()) {
			appendPct(tooltip, e.getValue(), total,
				e.getKey().equals(LIQUOR_WATER) ? "water (liquor)" : "liquor " + e.getKey());
		}
	}

	private static void appendPct(List<Component> tooltip, int part, long total, String label) {
		int pct = (int) Math.round(part * 100.0 / total);
		if (pct > 0) {
			tooltip.add(Component.literal("  " + pct + "% ")
				.append(Component.literal(label))
				.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static long gcd(long a, long b) {
		return b == 0 ? a : gcd(b, a % b);
	}
}
