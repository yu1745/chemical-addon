package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.recipe.SolutionIngredient;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.ProcessCapability;
import com.yu1745.chemicaladdon.vessel.ProcessReadings;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.energy.EnergyStorage;
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
	implements IHaveGoggleInformation, ProcessReadings {

	public static final int TANK_CAPACITY = 4000;
	public static final int ENERGY_CAPACITY = 20000;
	public static final int ENERGY_TRANSFER = 2000;
	private static final int CELL_TICK = 10;

	/** Why the cell is (not) running. */
	public enum CellStatus {
		IDLE, NO_RECIPE, NO_POWER, RUNNING, OUTPUT_FULL
	}

	private final ReactorTank tank = new ReactorTank(TANK_CAPACITY, this::onChanged);
	private final EnergyStorage energy = new EnergyStorage(ENERGY_CAPACITY, ENERGY_TRANSFER, ENERGY_CAPACITY);
	private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);
	private final LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCap =
		LazyOptional.of(() -> energy);

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
		if (!canFitOutputs(recipe)) {
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
			complete(recipe);
			progress = 0;
		}
		setChanged();
	}

	/** The first electrolysis recipe whose inputs the cell holds (power ignored). */
	@Nullable
	private ChemicalReactionRecipe findRecipe() {
		if (level == null) {
			return null;
		}
		for (ChemicalReactionRecipe recipe : level.getRecipeManager()
			.getAllRecipesFor(ReactionLogic.chemicalReactionType())) {
			if (!recipe.getRequiredCapabilities().contains(ProcessCapability.ELECTROLYSIS)) {
				continue; // not a cell recipe — the vessel's business
			}
			boolean ok = true;
			for (FluidIngredient fluid : recipe.getFluidIngredients()) {
				if (tank.countIngredient(fluid) < fluid.getRequiredAmount()) {
					ok = false;
					break;
				}
			}
			if (ok) {
				for (SolutionIngredient sol : recipe.getSolutions()) {
					if (tank.countSolution(sol.speciesId()) < sol.amount()) {
						ok = false;
						break;
					}
				}
			}
			if (ok) {
				return recipe;
			}
		}
		return null;
	}

	private boolean haveIngredientsButNoPower() {
		if (level == null) {
			return false;
		}
		for (ChemicalReactionRecipe recipe : level.getRecipeManager()
			.getAllRecipesFor(ReactionLogic.chemicalReactionType())) {
			if (!recipe.getRequiredCapabilities().contains(ProcessCapability.ELECTROLYSIS)) {
				continue;
			}
			boolean ok = true;
			for (FluidIngredient fluid : recipe.getFluidIngredients()) {
				if (tank.countIngredient(fluid) < fluid.getRequiredAmount()) {
					ok = false;
					break;
				}
			}
			if (ok) {
				for (SolutionIngredient sol : recipe.getSolutions()) {
					if (tank.countSolution(sol.speciesId()) < sol.amount()) {
						ok = false;
						break;
					}
				}
			}
			if (ok && energy.getEnergyStored() < recipe.getEnergyFe()) {
				return true;
			}
		}
		return false;
	}

	private boolean canFitOutputs(ChemicalReactionRecipe recipe) {
		int fluidOut = 0;
		for (FluidStack out : recipe.getFluidResults()) {
			fluidOut += out.getAmount();
		}
		for (SolutionIngredient out : recipe.getSolutionOutputs()) {
			fluidOut += createSolutionOutput(out, getTemperature()).getAmount();
		}
		return fluidOut <= tank.getTankCapacity(0) - tank.getTotalAmount();
	}

	private void complete(ChemicalReactionRecipe recipe) {
		int temp = getTemperature();
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			tank.drainIngredient(fluid, fluid.getRequiredAmount(), FluidAction.EXECUTE);
		}
		for (SolutionIngredient sol : recipe.getSolutions()) {
			tank.drainSolution(sol.speciesId(), sol.amount(), FluidAction.EXECUTE);
		}
		for (FluidStack out : recipe.getFluidResults()) {
			FluidStack copy = out.copy();
			Temperature.set(copy, temp);
			tank.fill(copy, FluidAction.EXECUTE);
		}
		for (SolutionIngredient out : recipe.getSolutionOutputs()) {
			FluidStack mix = createSolutionOutput(out, temp);
			if (!mix.isEmpty()) {
				tank.fill(mix, FluidAction.EXECUTE);
			}
		}
		tank.collapseIfNeeded();
	}

	private FluidStack createSolutionOutput(SolutionIngredient out, int temperature) {
		Species species = SpeciesManager.get(out.speciesId());
		if (species == null || !species.isSolution() || Double.isNaN(out.targetConcentration())) {
			return FluidStack.EMPTY;
		}
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		species.expand(out.amount(), out.targetConcentration(), molecules, ions);
		int total = 0;
		for (int v : molecules.values()) {
			total += v;
		}
		for (int v : ions.values()) {
			total += v;
		}
		FluidStack mix = Mixture.create(molecules, ions, total);
		Temperature.set(mix, temperature);
		return mix;
	}

	private void setStatus(CellStatus value) {
		if (status != value) {
			status = value;
			setChanged();
		}
	}

	// ------------------------------------------------------------------ reads

	public CellStatus getStatus() {
		return status;
	}

	public float getProgress() {
		return progress;
	}

	public ReactorTank getTank() {
		return tank;
	}

	public EnergyStorage getEnergy() {
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
			energy.receiveEnergy(tag.getInt("energy"), false);
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
