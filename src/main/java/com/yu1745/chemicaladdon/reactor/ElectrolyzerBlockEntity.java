package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.recipe.SolutionIngredient;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.ProcessCapability;
import com.yu1745.chemicaladdon.vessel.ProcessReadings;
import com.yu1745.chemicaladdon.vessel.StructureAccess;
import com.yu1745.chemicaladdon.vessel.StructureCapabilities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Electrolyzer (施工包 F / plans/07 §2.2): the dedicated single-block cell for
 * everything driven by electricity — chlor-alkali membrane electrolysis, water
 * electrolysis, later electrowinning. It deliberately does NOT register a new
 * recipe type: electrolysis recipes are ordinary {@code chemical_reaction}
 * entries whose {@code requiredCapabilities} include
 * {@link ProcessCapability#ELECTROLYSIS} (only this cell publishes that
 * capability) plus an {@code energyFe} field — the FE gate per batch. The
 * reaction vessel never matches them (it has no electrolysis capability), so
 * recipe files stay in one pipeline.
 *
 * <p>Ports: fluid in/out through the shared multi-fluid tank on any face; FE
 * in on any face. Products (caustic liquor as dissolved ions, H₂/Cl₂ as gas
 * phases) share the tank until piped out — every product has an outlet.
 */
public class ElectrolyzerBlockEntity extends BlockEntity
	implements IHaveGoggleInformation, ProcessReadings, StructureAccess {

	public static final int TANK_CAPACITY = 4000;
	public static final int ENERGY_CAPACITY = 20000;
	public static final int ENERGY_TRANSFER = 2000;
	private static final int CELL_TICK = 10;

	/** Why the cell is (not) running. */
	public enum CellStatus {
		IDLE, NO_RECIPE, NO_POWER, RUNNING, OUTPUT_FULL
	}

	private final ReactorTank tank = new ReactorTank(TANK_CAPACITY, this::onChanged);
	private final DirtyEnergyStorage energy = new DirtyEnergyStorage(ENERGY_CAPACITY, ENERGY_TRANSFER,
		ENERGY_CAPACITY, this::onChanged);
	private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);
	private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

	private int tickCounter = 0;
	private float progress = 0;
	private CellStatus status = CellStatus.IDLE;
	@Nullable
	private ResourceLocation activeRecipe = null;

	public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.ELECTROLYZER.get(), pos, state);
	}

	private void onChanged() {
		setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata();
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	// ------------------------------------------------------------------ ticker

	public static <T extends BlockEntity> BlockEntityTicker<T> ticker() {
		return (lvl, pos, st, be) -> {
			if (be instanceof ElectrolyzerBlockEntity cell) {
				cell.tick();
			}
		};
	}

	private void tick() {
		if (level == null || level.isClientSide) {
			return;
		}
		if (++tickCounter % CELL_TICK != 0) {
			return;
		}
		tank.collapseIfNeeded();
		ChemicalReactionRecipe recipe = findRecipe();
		if (recipe == null) {
			setStatus(haveIngredientsButNoPower() ? CellStatus.NO_POWER : CellStatus.NO_RECIPE);
			progress = 0;
			activeRecipe = null;
			return;
		}
		ReactorTank completed = completedTank(recipe);
		if (completed == null) {
			setStatus(CellStatus.OUTPUT_FULL);
			progress = 0;
			activeRecipe = null;
			return;
		}
		if (energy.getEnergyStored() < recipe.getEnergyFe()) {
			setStatus(CellStatus.NO_POWER); // 断电：批停在原地，来电继续
			activeRecipe = recipe.getId();
			return;
		}
		setStatus(CellStatus.RUNNING);
		activeRecipe = recipe.getId();
		progress += (float) CELL_TICK / recipe.getProcessingDuration();
		if (progress >= 1.0f) {
			energy.extractEnergy(recipe.getEnergyFe(), false);
			tank.deserializeNBT(completed.serializeNBT());
			progress = 0;
		}
		onChanged();
	}

	/**
	 * Select the most constrained matching cell recipe (power ignored).
	 *
	 * A native aqueous state contains solvent water, so a brine charge also
	 * satisfies the generic water-electrolysis water ingredient.  Recipe-manager
	 * iteration order is data-load order and is not a chemical priority.  Prefer
	 * explicit dissolved-species constraints, then the remaining input
	 * constraints, and use the recipe id only as a deterministic tie-breaker.
	 */
	@Nullable
	private ChemicalReactionRecipe findRecipe() {
		if (level == null) {
			return null;
		}
		ChemicalReactionRecipe selected = null;
		for (ChemicalReactionRecipe recipe : level.getRecipeManager()
			.getAllRecipesFor(ReactionLogic.chemicalReactionType())) {
			if (!matchesRecipe(recipe)) {
				continue; // not a cell recipe — the vessel's business
			}
			if (selected == null || compareSpecificity(recipe, selected) > 0) {
				selected = recipe;
			}
		}
		return selected;
	}

	private static int compareSpecificity(ChemicalReactionRecipe left, ChemicalReactionRecipe right) {
		int leftConstraints = left.getSolutions().size() * 1_000
			+ left.getFluidIngredients().size() * 100 + left.getIngredients().size();
		int rightConstraints = right.getSolutions().size() * 1_000
			+ right.getFluidIngredients().size() * 100 + right.getIngredients().size();
		int byConstraints = Integer.compare(leftConstraints, rightConstraints);
		if (byConstraints != 0) {
			return byConstraints;
		}
		// Lower lexical id wins ties so reload order cannot change production.
		return right.getId().toString().compareTo(left.getId().toString());
	}

	private boolean haveIngredientsButNoPower() {
		if (level == null) {
			return false;
		}
		for (ChemicalReactionRecipe recipe : level.getRecipeManager()
			.getAllRecipesFor(ReactionLogic.chemicalReactionType())) {
			if (!matchesRecipe(recipe)) {
				continue;
			}
			if (energy.getEnergyStored() < recipe.getEnergyFe()) {
				return true;
			}
		}
		return false;
	}

	private boolean matchesRecipe(ChemicalReactionRecipe recipe) {
		if (!recipe.getRequiredCapabilities().contains(ProcessCapability.ELECTROLYSIS)
			|| !recipe.matchesStructureRequirements(this, this) || !recipe.getIngredients().isEmpty()) {
			return false;
		}
		HeatCondition heat = recipe.getRequiredHeat();
		if ((heat == HeatCondition.HEATED && getTemperature() < 400)
			|| (heat == HeatCondition.SUPERHEATED && getTemperature() < 800)) {
			return false;
		}
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			if (tank.countIngredient(fluid) < fluid.getRequiredAmount()) {
				return false;
			}
		}
		for (SolutionIngredient sol : recipe.getSolutions()) {
			if (tank.countSolution(sol.speciesId()) < sol.amount()) {
				return false;
			}
			if (sol.hasConcentrationRange()) {
				double concentration = tank.concentrationOf(sol.speciesId());
				if (concentration < sol.minConcentration() || concentration > sol.maxConcentration()) {
					return false;
				}
			}
		}
		return true;
	}

	@Nullable
	private ReactorTank completedTank(ChemicalReactionRecipe recipe) {
		ReactorTank result = new ReactorTank(TANK_CAPACITY, () -> {});
		result.deserializeNBT(tank.serializeNBT());
		int temp = getTemperature();
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			if (result.drainIngredient(fluid, fluid.getRequiredAmount(), FluidAction.EXECUTE)
				!= fluid.getRequiredAmount()) return null;
		}
		for (SolutionIngredient sol : recipe.getSolutions()) {
			if (result.drainSolution(sol.speciesId(), sol.amount(), FluidAction.EXECUTE) != sol.amount()) return null;
		}
		for (FluidStack out : recipe.getFluidResults()) {
			FluidStack copy = out.copy();
			Temperature.set(copy, temp);
			if (result.fill(copy, FluidAction.EXECUTE) != copy.getAmount()) return null;
		}
		for (SolutionIngredient out : recipe.getSolutionOutputs()) {
			FluidStack mix = createSolutionOutput(out, temp);
			if (mix.isEmpty() || result.fill(mix, FluidAction.EXECUTE) != mix.getAmount()) return null;
		}
		result.collapseIfNeeded();
		return result;
	}

	private FluidStack createSolutionOutput(SolutionIngredient out, int temperature) {
		Species species = SpeciesManager.get(out.speciesId());
		if (species == null || !species.isSolution() || !(out.targetConcentration() > 0)
			|| !Double.isFinite(out.targetConcentration())) {
			return FluidStack.EMPTY;
		}
		try {
			int waterMb = (int) Math.round(out.amount() / out.targetConcentration());
			if (waterMb <= 0) return FluidStack.EMPTY;
			int total = Math.addExact(out.amount(), waterMb);
			double formulaMol = out.amount() / (double) species.ionCount() / 1000d;
			FluidStack mix = Mixture.fromDeclaredComposition(waterMb / 1000d,
				com.yu1745.chemicaladdon.composition.parity.EngineBridge.declaredFeedForSpecies(out.speciesId(), formulaMol),
				total, temperature, java.util.List.of());
			Temperature.set(mix, temperature);
			return mix;
		} catch (RuntimeException ex) {
			com.yu1745.chemicaladdon.ChemicalAddon.LOGGER.warn("Rejected electrolyzer solution output {}: {}", out.speciesId(), ex.getMessage());
			return FluidStack.EMPTY;
		}
	}

	private void setStatus(CellStatus value) {
		if (status != value) {
			status = value;
			onChanged();
		}
	}

	// ------------------------------------------------------------------ reads

	public CellStatus getStatus() {
		return status;
	}

	public float getProgress() {
		return progress;
	}

	@Nullable
	public ResourceLocation getActiveRecipe() {
		return activeRecipe;
	}

	public ReactorTank getTank() {
		return tank;
	}

	public DirtyEnergyStorage getEnergy() {
		return energy;
	}

	@Override
	public String getProcessStatus() {
		return status.name();
	}

	@Override
	public float getProcessProgress() {
		return progress;
	}

	@Override
	public int getTemperature() {
		long weighted = 0;
		int total = 0;
		for (FluidStack stack : tank.getFluids()) {
			weighted += (long) Temperature.get(stack) * stack.getAmount();
			total += stack.getAmount();
		}
		return Temperature.fromWeightedSum(weighted, total);
	}

	@Override
	public int getPressure() {
		return 0;
	}

	@Override
	public int getPh() {
		return 7;
	}

	@Override
	public int getTurbidity() {
		return 0;
	}

	@Override
	public int getBaume() {
		return 0;
	}

	@Override
	public int getConductivity() {
		return 0;
	}

	@Override public boolean isAssembled() { return true; }
	@Override public boolean isOpen() { return false; }
	@Override public int getSize() { return 1; }
	@Override public int getHeight() { return 1; }
	@Override public int getRingLayer() { return 0; }
	@Override public Direction getInward() { return null; }
	@Override public BlockPos getStructurePos() { return worldPosition; }
	@Override public StructureCapabilities getStructureCapabilities() {
		return StructureCapabilities.of(EnumSet.of(ProcessCapability.ELECTROLYSIS), TANK_CAPACITY, 1, 1, 0);
	}

	// ------------------------------------------------------------- capability

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER) {
			return fluidCap.cast();
		}
		if (cap == ForgeCapabilities.ENERGY) {
			return energyCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCap.invalidate();
		energyCap.invalidate();
	}

	@Override
	public void reviveCaps() {
		super.reviveCaps();
		fluidCap = LazyOptional.of(() -> tank);
		energyCap = LazyOptional.of(() -> energy);
	}

	// ---------------------------------------------------------- serialization

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.put("tank", tank.serializeNBT());
		tag.putInt("energy", energy.getEnergyStored());
		tag.putFloat("progress", progress);
		tag.putString("status", status.name());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		tank.deserializeNBT(tag.getCompound("tank"));
		if (tag.contains("energy")) {
			energy.setEnergyStored(tag.getInt("energy"));
		}
		progress = tag.getFloat("progress");
		if (tag.contains("status")) {
			try {
				status = CellStatus.valueOf(tag.getString("status"));
			} catch (IllegalArgumentException ignored) {
				status = CellStatus.IDLE;
			}
		}
	}

	// ------------------------------------------------------------- goggles HUD

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("block.chemicaladdon.electrolyzer")));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.energy", energy.getEnergyStored(),
				ENERGY_CAPACITY))
			.withStyle(ChatFormatting.RED));

		ChatFormatting statusColor = switch (status) {
			case RUNNING -> ChatFormatting.GREEN;
			case NO_POWER -> ChatFormatting.RED;
			case OUTPUT_FULL -> ChatFormatting.RED;
			case IDLE, NO_RECIPE -> ChatFormatting.GRAY;
		};
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.status"))
			.append(Component.translatable("status.chemicaladdon.cell_" + status.name().toLowerCase()))
			.withStyle(statusColor));

		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.contents")));
		int total = tank.getTotalAmount();
		if (total == 0) {
			tooltip.add(Component.literal(spacing + " ").append(Component.literal("0 mB"))
				.withStyle(ChatFormatting.GRAY));
		} else {
			List<String> lines = new ArrayList<>();
			for (FluidStack stack : tank.getFluids()) {
				lines.add(stack.getDisplayName().getString() + " " + stack.getAmount() + " mB");
			}
			for (String line : lines) {
				tooltip.add(Component.literal(spacing + " ").append(Component.literal(line))
					.withStyle(ChatFormatting.AQUA));
			}
		}
		if (activeRecipe != null && progress > 0) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.progress", (int) (progress * 100),
					activeRecipe.getPath()))
				.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}

	/** The electrolyzer block. */
	public static class ElectrolyzerBlock extends Block implements EntityBlock {

		public ElectrolyzerBlock(Properties properties) {
			super(properties);
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new ElectrolyzerBlockEntity(pos, state);
		}

		@Nullable
		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
			if (level.isClientSide) {
				return null;
			}
			return ElectrolyzerBlockEntity.ticker();
		}

		@Override
		public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			if (level.isClientSide) {
				return InteractionResult.SUCCESS;
			}
			if (level.getBlockEntity(pos) instanceof ElectrolyzerBlockEntity cell) {
				player.displayClientMessage(Component.literal(String.format(
					"§7电解槽（%s，FE %d/%d，内容 %d mB）",
					cell.getStatus(), cell.getEnergy().getEnergyStored(), ENERGY_CAPACITY,
					cell.getTank().getTotalAmount())), false);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
	}
}
