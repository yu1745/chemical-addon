package com.yu1745.chemicaladdon.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

/**
 * Creative "packed mixture" bucket: a sample container pre-filled with a known
 * solution (its ion multiset + water at the species' default concentration).
 *
 * <p>Solutions are no longer registered fluids (they are "modes", plans/03 §4), so
 * a standard Forge {@code BucketItem} cannot carry them. This item packs the
 * species' ion signature + solvent water straight into a {@link Mixture} FluidStack
 * (total 1000 mB) and stores it in the item's own NBT via a {@link FluidHandlerItemStack}
 * — so it can be emptied into a vessel like a bucket. The default instance is
 * pre-filled, so it works straight out of the creative tab / JEI.
 */
public class SolutionBucketItem extends Item {

	public static final int CAPACITY = 1000;

	private final ResourceLocation speciesId;

	public SolutionBucketItem(Properties properties, ResourceLocation speciesId) {
		super(properties);
		this.speciesId = speciesId;
	}

	/** The solution mode this bucket packs (for tooltips / JEI lookups). */
	public ResourceLocation speciesId() {
		return speciesId;
	}

	@Override
	public ItemStack getDefaultInstance() {
		ItemStack stack = new ItemStack(this);
		Species species = SpeciesManager.get(speciesId);
		if (species != null && (species.isSolution() || species.isSlurry())) {
			Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
			Map<String, Integer> ions = new LinkedHashMap<>();
			Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
			species.packBucket(CAPACITY, molecules, ions, suspended);
			int total = 0;
			for (int v : molecules.values()) {
				total += v;
			}
			for (int v : ions.values()) {
				total += v;
			}
			for (int v : suspended.values()) {
				total += v;
			}
			FluidStack mix = Mixture.create(molecules, ions, suspended, total);
			// the creative bucket carries the mode's known tint (distinct per species);
			// once poured the mixture colour is re-derived from its actual contents
			if (species.color() != 0) {
				mix.getOrCreateTag().putInt(Mixture.KEY_COLOR, 0xFF000000 | species.color());
			}
			stack.getOrCreateTag().put(FluidHandlerItemStack.FLUID_NBT_KEY, mix.writeToNBT(new CompoundTag()));
		}
		return stack;
	}

	@Override
	public ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag nbt) {
		return new FluidHandlerItemStack(stack, CAPACITY);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);
		FluidStack fluid = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
		if (fluid.isEmpty()) {
			tooltip.add(Component.translatable("goggles.chemicaladdon.bucket_empty").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			tooltip.add(Component.literal(fluid.getAmount() + " mB").withStyle(ChatFormatting.GRAY));
		}
	}
}
