package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.vessel.ProcessReadings;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

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
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Tower controller (施工包 E): the second-complexity vessel topology — a
 * segmented countercurrent column. Geometry reuses the vessel shell (3×3 or
 * 5×5 footprint, up to 16 rings, sealed); the interior is cached as DISCRETE
 * STAGES (plans/04 §2): a ring layer counts as one stage only when it holds a
 * packing block ({@link AllBlocks#TOWER_PACKING}) — an empty shell's height
 * buys nothing (空塔加高无收益).
 *
 * <p>Ports carry fixed height semantics by face (plans/04 §2 端口高度):
 * <ul>
 *   <li><b>UP = spray inlet</b> (顶部喷淋): liquid in only — gases are rejected;</li>
 *   <li><b>sides = gas port</b> (底部气口): gas in/out only — liquids are
 *       rejected, which is the testable counterflow diagnostic (反接端口失败可诊断);</li>
 *   <li><b>DOWN = bottoms outlet</b> (底部采出): drains the densest liquid.</li>
 * </ul>
 *
 * <p>Absorption model (plans/04 §4, every 10 ticks): gas → liquid interphase
 * mass transfer at {@code STAGE_MB_PER_STEP × effectiveStages} per step,
 * bounded by the spray liquid present. No chemistry engine runs here — the
 * absorbed species land in the aqueous mixture as molecules; the kernel owns
 * the chemistry wherever a reactor consumes the column effluent.
 *
 * <p>Flooding (液泛, first cut): a sustained gas feed above the cross-section
 * threshold ({@code FLOOD_LIMIT_MB_PER_STEP}) floods the column — absorption
 * stalls with a FLOODED diagnostic and recovers when the feed drops (可测可恢复).
 */
public class TowerControllerBlockEntity extends VesselBlockEntity
	implements IHaveGoggleInformation, ProcessReadings {

	/** gas→liquid transfer per effective stage per 10-tick step (mB). */
	public static final int STAGE_MB_PER_STEP = 50;
	/** flooding threshold: 3×3 → 400 mB/step, 5×5 → 1200 mB/step of gas feed. */
	public static final int FLOOD_LIMIT_3X3 = 400;
	public static final int FLOOD_LIMIT_5X5 = 1200;
	private static final int STEP_TICK = 10;

	/** Why the column is (not) absorbing; goggles HUD / status port. */
	public enum TowerStatus {
		NOT_ASSEMBLED, NO_STAGES, IDLE, ABSORBING, FLOODED
	}

	private int tickCounter = 0;
	private TowerStatus status = TowerStatus.NOT_ASSEMBLED;
	/** effective stages (packing layers) — recomputed on demand, never per tick. */
	private int stages = -1;
	private boolean stagesDirty = true;
	/** gas fed through the gas port since the last step (flooding watch). */
	private int gasFedThisStep = 0;
	private boolean flooded = false;

	private final LazyOptional<IFluidHandler> sprayPort = LazyOptional.of(() -> new IFluidHandler() {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return TowerControllerBlockEntity.this.tank.getFluidInTank(0);
		}

		@Override
		public int getTankCapacity(int tank) {
			return TowerControllerBlockEntity.this.tank.getTankCapacity(0);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return !Miscibility.isGas(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (Miscibility.isGas(resource)) {
				return 0; // the spray inlet feeds LIQUID; gas goes through the side port
			}
			return TowerControllerBlockEntity.this.tank.fill(resource, action);
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY; // inlet only
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY; // inlet only
		}
	});

	private final LazyOptional<IFluidHandler> gasPort = LazyOptional.of(() -> new IFluidHandler() {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			for (FluidStack stack : TowerControllerBlockEntity.this.tank.getFluids()) {
				if (Miscibility.isGas(stack)) {
					return stack;
				}
			}
			return FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			return TowerControllerBlockEntity.this.tank.getTankCapacity(0);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return Miscibility.isGas(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (!Miscibility.isGas(resource)) {
				return 0; // the gas port carries GAS; spray liquid goes in the top
			}
			int filled = TowerControllerBlockEntity.this.tank.fill(resource, action);
			if (action.execute() && filled > 0) {
				gasFedThisStep += filled;
			}
			return filled;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (!Miscibility.isGas(resource)) {
				return FluidStack.EMPTY;
			}
			return TowerControllerBlockEntity.this.tank.drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			// tail gas leaves gas-first (unabsorbed species exit the top of the bed)
			return TowerControllerBlockEntity.this.tank.drainLightest(maxDrain, action);
		}
	});

	private final LazyOptional<IFluidHandler> bottomsPort = LazyOptional.of(() -> new IFluidHandler() {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return TowerControllerBlockEntity.this.tank.getFluidInTank(0);
		}

		@Override
		public int getTankCapacity(int tank) {
			return TowerControllerBlockEntity.this.tank.getTankCapacity(0);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return false; // bottoms is an outlet only
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return 0;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (Miscibility.isGas(resource)) {
				return FluidStack.EMPTY;
			}
			return TowerControllerBlockEntity.this.tank.drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			// bottoms: the densest liquid leaves first (gases last)
			return TowerControllerBlockEntity.this.tank.drain(maxDrain, action);
		}
	});

	public TowerControllerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.TOWER_CONTROLLER.get(), pos, state, 1000, 0);
	}

	// ------------------------------------------------------------ shape hooks

	@Override
	protected int minSize() {
		return 3;
	}

	@Override
	protected int maxSize() {
		return 5; // plans/04 §2: 底面 3×3 或 5×5
	}

	@Override
	protected int minRings() {
		return 2;
	}

	@Override
	protected int maxRings() {
		return 16; // plans/04 §2: 高 4~16
	}

	@Override
	protected RoofMode roofMode() {
		return RoofMode.OPTIONAL; // a column is sealed in operation; open top = disassembling state
	}

	@Override
	protected int capacityFor(int w, int rings) {
		return 1000 * (w - 2) * (w - 2) * rings; // holdup: 1 bucket per interior block
	}

	@Override
	protected void onAssembled() {
		stagesDirty = true;
		setStatus(TowerStatus.NO_STAGES);
	}

	@Override
	protected void onStructureInvalidated() {
		setStatus(TowerStatus.NOT_ASSEMBLED);
	}

	// ------------------------------------------------------------------ stages

	/** Mark the cached stage count stale (the packing block calls this on place/remove). */
	public void markStagesDirty() {
		stagesDirty = true;
	}

	/** Effective stages: interior ring layers holding at least one packing block. */
	public int getStages() {
		if (!isAssembled()) {
			return 0;
		}
		if (!stagesDirty && stages >= 0) {
			return stages;
		}
		int count = 0;
		int w = getSize();
		Direction inward = getInward();
		if (level == null || inward == null) {
			return stages = 0;
		}
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int sStart = -((w - 1) / 2) + 1;
		for (int y = getInteriorBottomRelY(); y <= getRoofRelY() - 1; y++) {
			boolean packed = false;
			for (int s = 0; s < w - 2 && !packed; s++) {
				for (int d = 0; d < w - 2 && !packed; d++) {
					BlockPos p = worldPosition.offset(
						side.getStepX() * (sStart + s) + inward.getStepX() * (1 + d), y,
						side.getStepZ() * (sStart + s) + inward.getStepZ() * (1 + d));
					if (level.getBlockState(p).is(AllBlocks.TOWER_PACKING.get())) {
						packed = true;
					}
				}
			}
			if (packed) {
				count++;
			}
		}
		stages = count;
		stagesDirty = false;
		return count;
	}

	// ------------------------------------------------------------------ tick

	@Override
	protected void vesselTick() {
		if (++tickCounter % STEP_TICK != 0) {
			return;
		}
		// flooding watch: a sustained over-section gas feed floods the column
		int limit = getSize() >= 5 ? FLOOD_LIMIT_5X5 : FLOOD_LIMIT_3X3;
		boolean overfed = gasFedThisStep > limit;
		gasFedThisStep = 0;
		flooded = overfed;

		if (!isAssembled()) {
			setStatus(TowerStatus.NOT_ASSEMBLED);
			return;
		}
		if (getStages() <= 0) {
			setStatus(TowerStatus.NO_STAGES); // an empty shell absorbs nothing
			return;
		}
		if (flooded) {
			setStatus(TowerStatus.FLOODED); // 液泛：传质停摆，降负荷恢复
			return;
		}
		int gasTotal = gasTotalMb();
		int liquidTotal = liquidTotalMb();
		if (gasTotal <= 0 || liquidTotal <= 0) {
			setStatus(TowerStatus.IDLE); // dry column passes gas; no spray = no absorption
			return;
		}
		setStatus(TowerStatus.ABSORBING);
		absorbStep(Math.min(STAGE_MB_PER_STEP * getStages(), gasTotal));
	}

	/**
	 * One mass-transfer step: move {@code transferMb} of gas into the liquid as
	 * dissolved molecules. The liquid phase (molecules + ions + solid domains)
	 * and the remaining gas phase are rebuilt as SEPARATE stacks — merging the
	 * gas into the liquid wholesale would dissolve everything in one step and
	 * defeat the stage rate limit.
	 */
	private void absorbStep(int transferMb) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> sediment = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> gasUnits = new LinkedHashMap<>(); // mB per gas species
		long weightedTemp = 0;
		int totalMb = 0;
		int liquidMb = 0;
		for (FluidStack stack : tank.getFluids()) {
			totalMb += stack.getAmount();
			weightedTemp += (long) Temperature.get(stack) * stack.getAmount();
			if (Miscibility.isGas(stack)) {
				ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
				if (id != null) {
					gasUnits.merge(id, stack.getAmount(), Integer::sum);
				}
				continue; // gas stays gas unless transferred this step
			}
			liquidMb += stack.getAmount();
			if (Mixture.isMixture(stack)) {
				mergeInto(molecules, Mixture.deriveUnitAmounts(stack));
				mergeIonsInto(ions, Mixture.deriveUnitIonAmounts(stack));
				mergeInto(suspended, Mixture.deriveUnitSuspendedAmounts(stack));
				mergeInto(sediment, Mixture.deriveUnitSedimentAmounts(stack));
			} else {
				ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
				if (id != null) {
					mergeInto(molecules, Map.of(id, (int) ((long) stack.getAmount() * Chemistry.UNIT_PER_MB)));
				}
			}
		}
		if (totalMb <= 0 || gasUnits.isEmpty()) {
			return;
		}
		long gasTotalMb = 0;
		for (int v : gasUnits.values()) {
			gasTotalMb += v;
		}
		// proportional largest-remainder split of the transfer over the gas species
		Map<ResourceLocation, Integer> gasTake = new LinkedHashMap<>();
		long assigned = 0;
		ResourceLocation largest = null;
		for (Map.Entry<ResourceLocation, Integer> e : gasUnits.entrySet()) {
			long share = (long) transferMb * e.getValue() / gasTotalMb;
			gasTake.put(e.getKey(), (int) share);
			assigned += share;
			if (largest == null || share > gasTake.get(largest)) {
				largest = e.getKey();
			}
		}
		if (largest != null) {
			gasTake.merge(largest, (int) Math.min(transferMb - assigned, gasUnits.get(largest) - gasTake.get(largest)),
				Integer::sum);
		}
		// the absorbed share joins the liquid as dissolved molecules
		for (Map.Entry<ResourceLocation, Integer> e : gasTake.entrySet()) {
			if (e.getValue() > 0) {
				molecules.merge(e.getKey(), (int) ((long) e.getValue() * Chemistry.UNIT_PER_MB), Integer::sum);
			}
		}
		int temperature = Temperature.fromWeightedSum(weightedTemp, totalMb);
		List<FluidStack> rebuilt = new ArrayList<>();
		// liquid phase (mixture or pure) — gases never merge into it implicitly
		int liquidNow = liquidMb + transferMb;
		if (molecules.size() == 1 && ions.isEmpty() && suspended.isEmpty() && sediment.isEmpty()) {
			Fluid pure = ForgeRegistries.FLUIDS.getValue(molecules.keySet().iterator().next());
			if (pure != null && pure != net.minecraft.world.level.material.Fluids.EMPTY) {
				FluidStack stack = new FluidStack(pure, liquidNow);
				Temperature.set(stack, temperature);
				rebuilt.add(stack);
			}
		} else if (!molecules.isEmpty() || !ions.isEmpty()) {
			FluidStack mix = Mixture.create(molecules, ions, suspended, sediment, liquidNow);
			Temperature.set(mix, temperature);
			rebuilt.add(mix);
		}
		// remaining gas stays pure gas
		for (Map.Entry<ResourceLocation, Integer> e : gasUnits.entrySet()) {
			int remaining = e.getValue() - gasTake.getOrDefault(e.getKey(), 0);
			if (remaining <= 0) {
				continue;
			}
			Fluid gas = ForgeRegistries.FLUIDS.getValue(e.getKey());
			if (gas != null && gas != net.minecraft.world.level.material.Fluids.EMPTY) {
				FluidStack stack = new FluidStack(gas, remaining);
				Temperature.set(stack, temperature);
				rebuilt.add(stack);
			}
		}
		tank.setFluids(rebuilt);
	}
	private static void mergeInto(Map<ResourceLocation, Integer> into, Map<ResourceLocation, Integer> from) {
		for (Map.Entry<ResourceLocation, Integer> e : from.entrySet()) {
			into.merge(e.getKey(), e.getValue(), Integer::sum);
		}
	}

	private static void mergeIonsInto(Map<String, Integer> into, Map<String, Integer> from) {
		for (Map.Entry<String, Integer> e : from.entrySet()) {
			into.merge(e.getKey(), e.getValue(), Integer::sum);
		}
	}

	private int gasTotalMb() {
		int total = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (Miscibility.isGas(stack)) {
				total += stack.getAmount();
			}
		}
		return total;
	}

	private int liquidTotalMb() {
		int total = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (!Miscibility.isGas(stack)) {
				total += stack.getAmount();
			}
		}
		return total;
	}

	private void setStatus(TowerStatus value) {
		if (status != value) {
			status = value;
			sync();
		}
	}

	// ------------------------------------------------------------------ reads

	public TowerStatus getStatus() {
		return status;
	}

	/** Gas-phase volume in the column (mB; test/diagnostic view). */
	public int gasMb() {
		return gasTotalMb();
	}

	/** Liquid-phase volume in the column (mB; test/diagnostic view). */
	public int liquidMb() {
		return liquidTotalMb();
	}

	public boolean isFlooded() {
		return flooded;
	}

	@Override
	public String getProcessStatus() {
		return status.name();
	}

	@Override
	public float getProcessProgress() {
		return 0;
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
			if (side == Direction.UP) {
				return sprayPort.cast(); // spray inlet: liquid only, never drains
			}
			if (side == Direction.DOWN) {
				return bottomsPort.cast(); // bottoms: densest liquid first
			}
			return gasPort.cast(); // sides: gas only (counterflow diagnostic)
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		sprayPort.invalidate();
		gasPort.invalidate();
		bottomsPort.invalidate();
	}

	// ---------------------------------------------------------- serialization

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putString("status", status.name());
		tag.putInt("stages", stages);
		tag.putBoolean("stagesDirty", true);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		if (tag.contains("status")) {
			try {
				status = TowerStatus.valueOf(tag.getString("status"));
			} catch (IllegalArgumentException ignored) {
				status = TowerStatus.NOT_ASSEMBLED;
			}
		}
		stages = tag.contains("stages") ? tag.getInt("stages") : -1;
		stagesDirty = true; // always rescan once after load — the world is the truth
	}

	// ------------------------------------------------------------- goggles HUD

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("block.chemicaladdon.tower_controller")));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.tower_stages", getStages()))
			.withStyle(ChatFormatting.AQUA));

		ChatFormatting statusColor = switch (status) {
			case ABSORBING -> ChatFormatting.GREEN;
			case FLOODED -> ChatFormatting.RED;
			case NO_STAGES -> ChatFormatting.GOLD;
			case NOT_ASSEMBLED -> ChatFormatting.RED;
			case IDLE -> ChatFormatting.GRAY;
		};
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.status"))
			.append(Component.translatable("status.chemicaladdon.tower_" + status.name().toLowerCase()))
			.withStyle(statusColor));

		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.contents")));
		tooltip.add(Component.literal(spacing + " ")
			.append(Component.literal(liquidTotalMb() + " mB " + Component
				.translatable("goggles.chemicaladdon.tower_liquid").getString()))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(spacing + " ")
			.append(Component.literal(gasTotalMb() + " mB " + Component
				.translatable("goggles.chemicaladdon.tower_gas").getString()))
			.withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Controller block of the tower. */
	public static class TowerControllerBlock extends Block implements EntityBlock {

		public TowerControllerBlock(Properties properties) {
			super(properties);
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new TowerControllerBlockEntity(pos, state);
		}

		@Nullable
		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
			if (level.isClientSide) {
				return null;
			}
			return (lvl, pos, st, be) -> {
				if (be instanceof TowerControllerBlockEntity tower) {
					tower.tick();
				}
			};
		}

		@Override
		public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			if (level.isClientSide) {
				return InteractionResult.SUCCESS;
			}
			if (level.getBlockEntity(pos) instanceof TowerControllerBlockEntity tower) {
				if (!tower.isAssembled()) {
					boolean ok = tower.tryAssemble().ok();
					player.displayClientMessage(Component.literal(ok
						? "§a吸收塔成型！"
						: "§c结构不完整：需要化工砖密闭壳（3×3 或 5×5，高 4~16），控制器嵌在壁中；内部填塔填料计级"),
						false);
				} else {
					player.displayClientMessage(Component.literal(String.format(
						"§7吸收塔（%s，有效段 %d，液 %d mB / 气 %d mB）—— 顶喷淋进液、侧口进出气、底采出",
						tower.getStatus(), tower.getStages(), tower.liquidMb(), tower.gasMb())), false);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
	}

	/** Column packing block: fills a tower interior ring layer to count as one stage. */
	public static class TowerPackingBlock extends Block {

		public TowerPackingBlock(Properties properties) {
			super(properties);
		}

		@Override
		public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
			super.onPlace(state, level, pos, oldState, isMoving);
			notifyTower(level, pos);
		}

		@Override
		public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
			notifyTower(level, pos);
			super.onRemove(state, level, pos, newState, isMoving);
		}

		/** A packing placement/removal may change the column's stage count — poke the tower. */
		private static void notifyTower(Level level, BlockPos pos) {
			if (level.isClientSide) {
				return;
			}
			for (int dx = -8; dx <= 8; dx++) {
				for (int dy = -16; dy <= 16; dy++) {
					for (int dz = -8; dz <= 8; dz++) {
						if (level.getBlockEntity(pos.offset(dx, dy, dz))
							instanceof TowerControllerBlockEntity tower) {
							tower.markStagesDirty();
						}
					}
				}
			}
		}
	}
}
