package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Reaction vessel controller (U3: structure layer lives in
 * {@link VesselBlockEntity}). Holds the processing half of the vessel: the
 * multi-phase heat model, the sealed-vessel pressure model, open-top world
 * absorption and the reaction engine (auto-matching of whitelisted
 * chemical_reaction recipes with progress / intermediate completion /
 * delta-heat). Shape: hollow W x W x H shell, W,H = 3..7, open or sealed top.
 */
public class ReactorControllerBlockEntity extends VesselBlockEntity implements IHaveGoggleInformation {

	public static final int TANK_CAPACITY = 1000; // mB per interior block (1 bucket — small enough that a few buckets visibly raise the surface)
	public static final int AMBIENT_TEMP = 20;
	public static final int ITEM_SLOTS = 4;
	public static final int MAX_TEMP = 1000;
	public static final int MIN_SIZE = 3; // shell footprint W and height H both in [3, 7]
	public static final int MAX_SIZE = 7;

	private static final int HEAT_TICK = 20;
	private static final int REACTION_TICK = 10;

	/** Why the vessel is (not) reacting; shown in the goggles HUD. */
	public enum ReactorStatus {
		NOT_ASSEMBLED, REACTING, TEMPERATURE, OUTPUT_FULL, NO_RECIPE
	}

	private int tickCounter = 0;
	private float progress = 0;
	private ReactorStatus status = ReactorStatus.NOT_ASSEMBLED;
	@Nullable
	private ResourceLocation activeRecipe = null;
	/** Debug temperature pin (-1 = unpinned; when ≥0 the vessel is held at this °C regardless of the burner). */
	private int pinnedTemperature = -1;
	/**
	 * Stirring (mass-transfer) rate coefficient — the hook kept when MixDegree
	 * was deleted: a kinetic whisk will map its RPM onto 0.3–1.0. Placeholder
	 * 1.0 in U1 (plans/11 §2.1) so batch rhythm tuning lands in U5.
	 */
	private float stirringCoefficient = 1.0f;
	/** Last solve's speciation report (per-solid saturation indices) — goggles diagnostics. */
	private List<Solution.Speciation> speciation = List.of();

	public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.REACTOR_CONTROLLER.get(), pos, state, TANK_CAPACITY, ITEM_SLOTS);
	}

	// ------------------------------------------------------------ shape hooks

	@Override
	protected int minSize() {
		return MIN_SIZE;
	}

	@Override
	protected int maxSize() {
		return MAX_SIZE;
	}

	@Override
	protected int minRings() {
		return MIN_SIZE - 2; // 1 ring (3x3x3) .. 5 rings (7-tall interior)
	}

	@Override
	protected int maxRings() {
		return MAX_SIZE - 2;
	}

	@Override
	protected int capacityFor(int w, int rings) {
		return TANK_CAPACITY * (w - 2) * (w - 2) * rings; // 1 bucket per interior block
	}

	@Override
	protected int legacySizeFromCapacity(int capacityMb) {
		// legacy save: capacity was TANK_CAPACITY * height with a 1x1 interior -> n = height + 2
		return capacityMb > 0 ? (int) Math.round(Math.cbrt(capacityMb / (double) TANK_CAPACITY)) + 2 : 0;
	}

	@Override
	protected void onAssembled() {
		setStatus(ReactorStatus.REACTING);
	}

	@Override
	protected void onStructureInvalidated() {
		setStatus(ReactorStatus.NOT_ASSEMBLED);
		setProgress(0, null);
	}

	@Override
	protected void applyOpenState(boolean topOpen) {
		// update the controller block state so the open/sealed variant shows
		if (level == null) {
			return;
		}
		BlockState state = level.getBlockState(worldPosition);
		if (state.hasProperty(ReactorControllerBlock.OPEN) && state.getValue(ReactorControllerBlock.OPEN) != topOpen) {
			level.setBlock(worldPosition, state.setValue(ReactorControllerBlock.OPEN, topOpen), 3);
		}
	}

	// ------------------------------------------------------------------ tick

	@Override
	protected void vesselTick() {
		tickCounter++;
		if (tickCounter % HEAT_TICK == 0) {
			updateHeat();
		}
		if (tickCounter % REACTION_TICK == 0) {
			tickReaction();
		}
		absorbFromWorld();
		// settle multi-fluid contents into a single mixture (or degrade to pure);
		// runs after absorb/fill so coexisting species collapse same-tick
		tank.collapseIfNeeded();
	}

	/**
	 * Open-topped vessels absorb physical contents dropped/poured into the
	 * interior (the in-world side of the spill loop): item entities become
	 * buffer data (rendered floating inside), fluid source blocks become tank
	 * data — same tick, so "throwing things in" feels immediate.
	 */
	private void absorbFromWorld() {
		if (level == null || level.isClientSide || !isAssembled() || !isOpen() || getInward() == null) {
			return;
		}
		int size = getSize();
		int iw = size - 2; // interior footprint (iw x iw)
		Direction inward = getInward();
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int sStart = -((size - 1) / 2) + 1; // interior s range starts one in from the wall
		BlockPos core = worldPosition.offset(
			side.getStepX() * sStart + inward.getStepX(), 0,
			side.getStepZ() * sStart + inward.getStepZ());
		// absorb area = the interior column (from its FLOOR, which sits ringLayer
		// below the controller — the controller may be mounted on ANY ring) up
		// through the open rim and one block above it: a bucket click from inside
		// the vessel (or from below the rim) places the source on the far side of
		// the clicked block, i.e. at the rim layer or one above — those must be in
		// range or poured fluids land outside the polled area and are never absorbed
		int yBottom = getInteriorBottomRelY();
		int yTop = getRoofRelY() + 1;
		var area = new net.minecraft.world.phys.AABB(core.getX(), core.getY() + yBottom, core.getZ(),
			core.getX() + iw, core.getY() + yTop + 1, core.getZ() + iw);

		// items thrown in through the open top
		boolean absorbed = false;
		for (net.minecraft.world.entity.item.ItemEntity entity : level
			.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)) {
			ItemStack stack = entity.getItem();
			if (stack.isEmpty()) {
				entity.discard();
				continue;
			}
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(items, stack.copy(), false);
			if (remainder.getCount() < stack.getCount()) {
				entity.setItem(remainder); // partially absorbed
				absorbed = true;
			}
			if (remainder.isEmpty()) {
				entity.discard();
			}
		}

		// fluids poured in (source blocks only; flowing fluid is left to drain)
		for (int dx = 0; dx < iw; dx++) {
			for (int dz = 0; dz < iw; dz++) {
				for (int y = yBottom; y <= yTop; y++) {
					BlockPos p = core.offset(dx, y, dz);
					BlockState bs = level.getBlockState(p);
					if (bs.isAir()) {
						continue;
					}
					net.minecraft.world.level.material.FluidState fs = bs.getFluidState();
					if (fs.isEmpty() || !fs.isSource()) {
						continue;
					}
					int filled = tank.fill(new FluidStack(fs.getType(), 1000), IFluidHandler.FluidAction.EXECUTE);
					if (filled == 1000) {
						level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
						absorbed = true;
					}
				}
			}
		}

		if (absorbed) {
			level.playSound((Player) null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.2f);
		}
	}

	// ------------------------------------------------------------------ heat

	private void updateHeat() {
		// debug pin: hold the contents at a fixed temperature (the debug item sets
		// this to take control away from the Blaze Burner / ambient relaxation)
		if (pinnedTemperature >= 0) {
			for (FluidStack stack : tank.getFluids()) {
				if (Temperature.get(stack) != pinnedTemperature) {
					Temperature.set(stack, pinnedTemperature);
					setChanged();
					sync();
				}
			}
			return;
		}
		// heating from a Blaze Burner directly below the vessel's bottom layer
		// (controller sits on the first wall layer; bottom is one below, burner two)
		BlockState below = level.getBlockState(worldPosition.below(2));
		int target = switch (BlazeBurnerBlock.getHeatLevelOf(below)) {
			case KINDLED -> 500;
			case SEETHING -> 900;
			default -> AMBIENT_TEMP;
		};
		// U1/G1: the contents carry the temperature, and EVERY phase relaxes
		// toward the burner's target (or back to ambient when unheated). After
		// D18 a gas bystander phase is permanent, so the old "exactly one stack"
		// early-return left it stuck at whatever temperature it entered with;
		// vessel-level reads ({@link #getTemperature}) are amount-weighted.
		boolean changed = false;
		for (FluidStack stack : tank.getFluids()) {
			int current = Temperature.get(stack);
			int next = current + (target - current) / 10;
			if (next != current) {
				Temperature.set(stack, next);
				changed = true;
			}
		}
		if (changed) {
			setChanged();
			sync();
		}
	}

	/** Debug/dev: hold the vessel at {@code t} °C, or {@code -1} to resume normal heating. */
	public void setPinnedTemperature(int t) {
		int clamped = t < 0 ? -1 : Math.max(AMBIENT_TEMP, Math.min(MAX_TEMP, t));
		this.pinnedTemperature = clamped;
		if (clamped >= 0) {
			for (FluidStack stack : tank.getFluids()) {
				Temperature.set(stack, clamped);
			}
		}
		setChanged();
		sync();
	}

	/** The debug temperature pin, or -1 when unpinned. */
	public int getPinnedTemperature() {
		return pinnedTemperature;
	}

	// -------------------------------------------------------- reaction engine
	// (matching + completion live in ReactionLogic; this is the progress/status
	// orchestration)

	private void tickReaction() {
		if (!isAssembled()) {
			setStatus(ReactorStatus.NOT_ASSEMBLED);
			setProgress(0, null);
			return;
		}
		// emergent chemistry first (mass-action equilibria / crystallisation /
		// neutralisation / solid dissolution / evaporation derived from species
		// data), then the whitelist recipe engine. The solved snapshot's
		// speciation report feeds the goggles saturation lines (why-no-reaction
		// diagnostics: SI < 0 means "not saturated enough", not "broken").
		Solution solved = RulesEngine.apply(tank, isOpen(), items, stirringCoefficient);
		if (solved != null) {
			speciation = solved.report();
		}
		ChemicalReactionRecipe recipe = ReactionLogic.findRecipe(this);
		if (recipe == null) {
			// no fully-matching recipe: diagnose whether it is a heat problem
			setStatus(ReactionLogic.matchesIgnoringHeat(this) ? ReactorStatus.TEMPERATURE : ReactorStatus.NO_RECIPE);
			setProgress(0, null);
			return;
		}
		if (!ReactionLogic.canFitOutputs(this, recipe)) {
			setStatus(ReactorStatus.OUTPUT_FULL);
			setProgress(0, null);
			return;
		}
		setStatus(ReactorStatus.REACTING);
		float next = progress + (float) REACTION_TICK / recipe.getProcessingDuration()
			* ReactionLogic.rateCoefficient(recipe, getTemperature(), stirringCoefficient);
		if (next >= 1.0f) {
			ReactionLogic.completeRecipe(this, recipe);
			setProgress(0, recipe.getId());
			sync();
		} else {
			setProgress(next, recipe.getId());
		}
	}

	private void setStatus(ReactorStatus value) {
		if (status != value) {
			status = value;
			sync();
		}
	}

	private void setProgress(float value, @Nullable ResourceLocation recipeId) {
		boolean changed = progress != value || !java.util.Objects.equals(activeRecipe, recipeId);
		progress = value;
		activeRecipe = recipeId;
		if (changed) {
			sync();
		}
	}

	// -------------------------------------------------- temperature / pressure

	/**
	 * The vessel's temperature (°C): the amount-weighted average across ALL
	 * phases (U1/G2 — a hot liquid under a cool gas head reads as the mix, not
	 * as whichever stack happens to be entry 0); ambient when empty. Storage
	 * stays per-stack NBT so transport keeps its proven identity.
	 */
	public int getTemperature() {
		long weighted = 0;
		int total = 0;
		for (FluidStack stack : tank.getFluids()) {
			weighted += (long) Temperature.get(stack) * stack.getAmount();
			total += stack.getAmount();
		}
		return Temperature.fromWeightedSum(weighted, total);
	}

	/** Ambient absolute pressure (kPa) — the 1-atm baseline of the linear vessel model. */
	public static final int ATMOSPHERE_KPA = 101;

	/**
	 * Vessel gauge pressure in kPa (U1/G3). Linear sealed-vessel model:
	 * {@code P_abs = 1 atm × (gas volume fraction) × (T / T_ambient)} — pumping
	 * more gas in or heating the contents raises it; an open-topped (or
	 * disassembled) vessel vents to ambient so the gauge reads 0. Sealing
	 * itself stays binary in U1; material pressure ratings arrive in U11.
	 *
	 * <p>Derived on read from the already-synced tank contents / structure
	 * state (the same trust model as {@link #getFillState}) rather than
	 * persisted: a stored copy could silently drift from the contents on any
	 * missed update path, while a derivation cannot.
	 */
	public int getPressure() {
		if (!isAssembled() || isOpen()) {
			return 0; // open top / broken shell: no pressure builds
		}
		int cap = tank.getTankCapacity(0);
		if (cap <= 0) {
			return 0;
		}
		int gas = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (Miscibility.isGas(stack)) {
				gas += stack.getAmount();
			}
		}
		if (gas <= 0) {
			return 0;
		}
		double kelvin = getTemperature() + 273.15;
		double pAbs = ATMOSPHERE_KPA * gas / (double) cap * (kelvin / 293.15);
		return Math.max(0, (int) Math.round(pAbs - ATMOSPHERE_KPA));
	}

	public ReactorStatus getStatus() {
		return status;
	}

	public float getProgress() {
		return progress;
	}

	@Nullable
	public ResourceLocation getActiveRecipe() {
		return activeRecipe;
	}

	// ------------------------------------------------------------- serialization

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putFloat("progress", progress);
		if (activeRecipe != null) {
			tag.putString("activeRecipe", activeRecipe.toString());
		}
		// status drives the goggles HUD (addToGoggleTooltip reads it client-side);
		// it must be in the sync tag or the client BE keeps its default NOT_ASSEMBLED forever
		tag.putString("status", status.name());
		tag.putInt("pinnedTemperature", pinnedTemperature);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		progress = tag.getFloat("progress");
		activeRecipe = tag.contains("activeRecipe") ? ResourceLocation.tryParse(tag.getString("activeRecipe")) : null;
		// mirror of write(): restore status so the client HUD shows the real server value
		if (tag.contains("status")) {
			try {
				status = ReactorStatus.valueOf(tag.getString("status"));
			} catch (IllegalArgumentException ignored) {
				status = ReactorStatus.NOT_ASSEMBLED; // unknown enum (e.g. older/newer data pack) — safe default
			}
		} else {
			status = ReactorStatus.NOT_ASSEMBLED;
		}
		pinnedTemperature = tag.contains("pinnedTemperature") ? tag.getInt("pinnedTemperature") : -1;
	}

	// ------------------------------------------------------------- goggles HUD

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("block.chemicaladdon.reactor_controller")));

		// temperature + heat tier
		int temperature = getTemperature();
		ChatFormatting heatColor = temperature >= 800 ? ChatFormatting.RED
			: temperature >= 400 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.temperature", temperature))
			.withStyle(ChatFormatting.WHITE));
		tooltip.add(Component.literal(spacing).append(Component.translatable(heatTierKey())).withStyle(heatColor));

		// pressure (sealed builds up, open-topped vents to ambient)
		tooltip.add(isOpen()
			? Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.pressure_ambient"))
				.withStyle(ChatFormatting.GRAY)
			: Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.pressure", getPressure()))
				.withStyle(ChatFormatting.AQUA));

		// status (why it is / is not reacting)
		ChatFormatting statusColor = switch (status) {
			case REACTING -> ChatFormatting.GREEN;
			case TEMPERATURE -> ChatFormatting.GOLD;
			case OUTPUT_FULL -> ChatFormatting.RED;
			case NOT_ASSEMBLED -> ChatFormatting.RED;
			case NO_RECIPE -> ChatFormatting.GRAY;
		};
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.status"))
			.append(Component.translatable("status.chemicaladdon." + status.name().toLowerCase()))
			.withStyle(statusColor));

		// saturation lines (speciation diagnostics): which solids are at / over /
		// near their saturation. Only interesting ones are listed — a solid is
		// shown when it moved this solve or its SI is within striking distance
		// (≥ −3); anything deeper is "far from happening" and stays silent.
		if (!speciation.isEmpty()) {
			List<Component> satLines = new ArrayList<>();
			for (Solution.Speciation s : speciation) {
				boolean relevant = s.moved() != 0 || s.si() >= -3;
				if (!relevant) {
					continue;
				}
				var item = ForgeRegistries.ITEMS.getValue(s.target());
				String name = item != null && item != Items.AIR ? new ItemStack(item).getHoverName().getString()
					: s.target().getPath();
				ChatFormatting color = s.moved() > 0 || s.si() > 0.5 ? ChatFormatting.AQUA // precipitating / supersaturated
					: s.si() < -0.5 ? ChatFormatting.GOLD // approaching saturation
						: ChatFormatting.GREEN; // at equilibrium with the solid present
				satLines.add(Component.literal(spacing + " ")
					.append(Component.literal(name + "  SI " + String.format("%.1f", s.si())))
					.withStyle(color));
			}
			if (!satLines.isEmpty()) {
				tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.saturation")));
				tooltip.addAll(satLines);
			}
		}

		// contents — the vessel's contents are deliberately unnamed: it holds a clear
		// solution, and "you cannot tell what is in it" is the point (plans/03 §6).
		// Only the total is reported; a pure (non-mixture) fluid keeps its name.
		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.contents")));
		int total = tank.getTotalAmount();
		if (total == 0) {
			tooltip.add(Component.literal(spacing + " ").append(Component.literal("0 mB")).withStyle(ChatFormatting.GRAY));
		} else {
			for (FluidStack stack : tank.getFluids()) {
				if (Mixture.isMixture(stack)) {
					tooltip.add(Component.literal(spacing + " ")
						.append(Component.translatable("goggles.chemicaladdon.solution"))
						.withStyle(ChatFormatting.GRAY));
					tooltip.add(Component.literal(spacing + "  ")
						.append(Component.literal(stack.getAmount() + " mB")).withStyle(ChatFormatting.GOLD)
						.append(Component.literal(" / " + tank.getTankCapacity(0) + " mB").withStyle(ChatFormatting.DARK_GRAY)));
					if (ChemicalAddon.ASSAY_ON) {
						// dev assay: full breakdown — dissolved molecular species, then ions,
						// then the two solid domains (suspended slurry / settled sediment)
						for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveAmounts(stack).entrySet()) {
							Fluid cf = ForgeRegistries.FLUIDS.getValue(e.getKey());
							if (cf == null) {
								continue;
							}
							String name = new FluidStack(cf, e.getValue()).getDisplayName().getString();
							tooltip.add(Component.literal(spacing + "   • " + name + "  " + e.getValue() + " mB")
								.withStyle(ChatFormatting.DARK_GRAY));
						}
						for (Map.Entry<String, Integer> e : Mixture.deriveIonAmounts(stack).entrySet()) {
							tooltip.add(Component.literal(spacing + "   • " + e.getKey() + "  " + e.getValue() + " u")
								.withStyle(ChatFormatting.DARK_GRAY));
						}
						for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSuspendedAmounts(stack).entrySet()) {
							var item = ForgeRegistries.ITEMS.getValue(e.getKey());
							String name = item != null ? new ItemStack(item).getHoverName().getString()
								: e.getKey().toString();
							tooltip.add(Component.literal(spacing + "   • " + name + "  " + e.getValue()
									+ " mB " + Component.translatable("goggles.chemicaladdon.suspended").getString())
								.withStyle(ChatFormatting.GRAY));
						}
						for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSedimentAmounts(stack).entrySet()) {
							var item = ForgeRegistries.ITEMS.getValue(e.getKey());
							String name = item != null ? new ItemStack(item).getHoverName().getString()
								: e.getKey().toString();
							tooltip.add(Component.literal(spacing + "   • " + name + "  " + e.getValue()
									+ " mB " + Component.translatable("goggles.chemicaladdon.sediment").getString())
								.withStyle(ChatFormatting.GRAY));
						}
					}
				} else {
					tooltip.add(Component.literal(spacing + " ").append(stack.getDisplayName())
						.withStyle(ChatFormatting.GRAY));
					tooltip.add(Component.literal(spacing + "  ")
						.append(Component.literal(stack.getAmount() + " mB")).withStyle(ChatFormatting.GOLD)
						.append(Component.literal(" / " + tank.getTankCapacity(0) + " mB").withStyle(ChatFormatting.DARK_GRAY)));
				}
			}
		}

		// item buffer
		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.items")));
		boolean anyItem = false;
		for (int i = 0; i < items.getSlots(); i++) {
			ItemStack stack = items.getStackInSlot(i);
			if (!stack.isEmpty()) {
				anyItem = true;
				tooltip.add(Component.literal(spacing + " ")
					.append(Component.literal(stack.getHoverName().getString() + " x" + stack.getCount()))
					.withStyle(ChatFormatting.GRAY));
			}
		}
		if (!anyItem) {
			tooltip.add(Component.literal(spacing + " ").append(Component.literal("-")).withStyle(ChatFormatting.DARK_GRAY));
		}

		// reaction progress
		if (status == ReactorStatus.REACTING && activeRecipe != null) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.progress", (int) (progress * 100),
					activeRecipe.getPath()))
				.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}

	private String heatTierKey() {
		int temperature = getTemperature();
		if (temperature >= 800) {
			return "goggles.chemicaladdon.heat.superheated";
		}
		if (temperature >= 400) {
			return "goggles.chemicaladdon.heat.heated";
		}
		return "goggles.chemicaladdon.heat.none";
	}

}
