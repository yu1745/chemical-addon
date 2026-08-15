package com.yu1745.chemicaladdon.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.fluid.SolidColors;

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
 * <p>Player-visible information obeys the measurement-honesty principle
 * (plans/03 §6): unified name and a blended colour (both physically
 * observable). The composition percentages are engine-internal knowledge and
 * only show in dev assay mode ({@link Chemistry#ASSAY}).
 */
public class MixedResidueItem extends Item {

	/** NBT key: composition ratio parts (solid species id → GCD-reduced part). */
	public static final String TAG_SOLIDS = "Solids";

	public MixedResidueItem(Properties properties) {
		super(properties);
	}

	/**
	 * Build one residue stack (1000 mB of mixed solids) from the domain's unit
	 * amounts. The parts are the GCD-reduced composition, so identical
	 * compositions produce byte-identical tags and stack together.
	 */
	public static ItemStack of(Map<ResourceLocation, Long> unitAmounts) {
		ItemStack stack = new ItemStack(com.yu1745.chemicaladdon.registry.AllItems.MIXED_RESIDUE.get());
		Map<ResourceLocation, Integer> parts = reduce(unitAmounts);
		CompoundTag c = new CompoundTag();
		for (Map.Entry<ResourceLocation, Integer> e : parts.entrySet()) {
			if (e.getValue() > 0) {
				c.putInt(e.getKey().toString(), e.getValue());
			}
		}
		stack.getOrCreateTag().put(TAG_SOLIDS, c);
		return stack;
	}

	/** The composition ratio parts (species id → part); empty when no NBT. */
	public static Map<ResourceLocation, Integer> parts(ItemStack stack) {
		Map<ResourceLocation, Integer> parts = new LinkedHashMap<>();
		if (stack.isEmpty() || !(stack.getItem() instanceof MixedResidueItem)) {
			return parts;
		}
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return parts;
		}
		CompoundTag c = tag.getCompound(TAG_SOLIDS);
		for (String key : c.getAllKeys()) {
			ResourceLocation id = ResourceLocation.tryParse(key);
			if (id != null && c.getInt(key) > 0) {
				parts.put(id, c.getInt(key));
			}
		}
		return parts;
	}

	/** Weight-blended RGB of the composition (ItemColor tint; grey when bare). */
	public static int colorOf(ItemStack stack) {
		Map<ResourceLocation, Integer> parts = parts(stack);
		long r = 0, g = 0, b = 0, total = 0;
		for (Map.Entry<ResourceLocation, Integer> e : parts.entrySet()) {
			int c = SolidColors.of(e.getKey());
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
		long total = 0;
		for (int v : parts.values()) {
			total += v;
		}
		if (total <= 0) {
			return;
		}
		for (Map.Entry<ResourceLocation, Integer> e : parts.entrySet()) {
			int pct = (int) Math.round(e.getValue() * 100.0 / total);
			if (pct > 0) {
				tooltip.add(Component.literal("  " + pct + "% ")
					.append(Component.literal(e.getKey().toString()))
					.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}

	/** GCD-reduce a unit map into canonical smallest ratio parts (mixture-style identity). */
	private static Map<ResourceLocation, Integer> reduce(Map<ResourceLocation, Long> amounts) {
		long g = 0;
		for (long v : amounts.values()) {
			g = gcd(g, v);
		}
		Map<ResourceLocation, Integer> parts = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : amounts.entrySet()) {
			long v = g > 1 ? e.getValue() / g : e.getValue();
			if (v > 0 && v <= Integer.MAX_VALUE) {
				parts.put(e.getKey(), (int) v);
			}
		}
		return parts;
	}

	private static long gcd(long a, long b) {
		return b == 0 ? a : gcd(b, a % b);
	}
}
