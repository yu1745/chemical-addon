package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Settling basin (M2 → 施工包 C): pool-shaped instance of the vessel template —
 * an open, wide, shallow gravity thickener. Geometry is now real (3×3 … 15×15
 * footprint, 1–4 ring depth): <b>area sets the clarification flux</b> (the
 * maximum sustained clear-liquid withdrawal), <b>depth sets the sludge bed
 * capacity</b> (the buffer between de-sludging rounds).
 *
 * <p>Process model (plans/05 §3, every {@link #SETTLE_INTERVAL} ticks):
 * <ol>
 *   <li><b>churn</b> — a surface draw beyond the standing supernatant
 *       ({@code clearCreditMb}) is an overdraw: the violence kicks the settled
 *       bed back into suspension (turbidity rises — S17 sees it);</li>
	 *   <li><b>gravity settling</b> — up to {@code area × FLUX_MB_PER_BLOCK_STEP}
	 *       mB of slurry is clarified, moving that slice's proportional share of
	 *       suspended solids into Sediment (the sludge bed),
 *       bounded by the bed capacity; a full bed stalls settling (nothing can
 *       leave suspension — withdraw the underflow);</li>
 *   <li>the freed volume grows the clear supernatant credit.</li>
 * </ol>
 *
 * <p>Two ports (plans/05 §2 进料/溢流/底泥分工), by pipe face: a horizontal side
 * face is the <b>overflow</b> (skims clear supernatant; an overdrawn pull
 * entrains suspended solids — 夹带), the bottom face is the <b>underflow</b>
 * (thickened sludge at ~50% solids, reslurried so the filter press downstream
 * splits it again). The basin deliberately does not output dry cake
 * (plans/05 §1) — the item slot is vestigial.
 */
public class SettlingBasinBlockEntity extends VesselBlockEntity implements IHaveGoggleInformation {

	/** ticks per gravity-settling step */
	public static final int SETTLE_INTERVAL = 10;
	/** clarification flux: mB of slurry clarified per interior block per step */
	public static final int FLUX_MB_PER_BLOCK_STEP = 200;
	/** sludge bed capacity: mB of settled solids per interior block per ring of depth
	 *  (half the liquid volume — the compression zone at the bottom; plans/05 §3) */
	public static final int SLUDGE_MB_PER_BLOCK_RING = 500;
	/** underflow thickening target: solids volume fraction of the pumped sludge */
	public static final double UNDERFLOW_SOLIDS_FRACTION = 0.5;
	/** mB of bed resuspended per mB of overdrawn surface lift (a disturbed bed
	 *  gives up twice the overdrawn volume — flocs break apart) */
	public static final double RESUSPEND_RATE = 2.0;

	private int tickCounter = 0;
	/** standing clear supernatant (mB) — the clean-withdrawal budget an overflow may skim */
	private int clearCreditMb = 0;
	/** overdraw recorded since the last settle step (mB) — churned back up next step */
	private int overdrawMb = 0;
	/** slurry volume which has not yet passed through the clarification flux */
	private int unclarifiedMb = 0;
	/** last observed tank volume, used to add newly filled slurry to the work queue */
	private int observedTankMb = 0;

	private final LazyOptional<IFluidHandler> overflowCap;
	private final LazyOptional<IFluidHandler> underflowCap;

	public SettlingBasinBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.SETTLING_BASIN.get(), pos, state, 1000, 1);
		IFluidHandler overflow = new IFluidHandler() {
			@Override
			public int getTanks() {
				return 1;
			}

			@Override
			public FluidStack getFluidInTank(int tank) {
				return overflowDrain(1000, FluidAction.SIMULATE);
			}

			@Override
			public int getTankCapacity(int tank) {
				return SettlingBasinBlockEntity.this.tank.getTankCapacity(0);
			}

			@Override
			public boolean isFluidValid(int tank, FluidStack stack) {
				return true;
			}

			@Override
			public int fill(FluidStack resource, FluidAction action) {
				return 0; // overflow is outlet-only; filling uses the controller's side-less interaction
			}

			@Override
			public FluidStack drain(FluidStack resource, FluidAction action) {
				if (resource.isEmpty()) {
					return FluidStack.EMPTY;
				}
				FluidStack available = overflowDrain(resource.getAmount(), FluidAction.SIMULATE);
				if (available.isEmpty() || !available.isFluidEqual(resource)) {
					return FluidStack.EMPTY;
				}
				return overflowDrain(resource.getAmount(), action);
			}

			@Override
			public FluidStack drain(int maxDrain, FluidAction action) {
				return overflowDrain(maxDrain, action);
			}
		};
		IFluidHandler underflow = new IFluidHandler() {
			@Override
			public int getTanks() {
				return 1;
			}

			@Override
			public FluidStack getFluidInTank(int tank) {
				return SettlingBasinBlockEntity.this.tank.drainThickenedUnderflow(1000,
					UNDERFLOW_SOLIDS_FRACTION, FluidAction.SIMULATE);
			}

			@Override
			public int getTankCapacity(int tank) {
				return SettlingBasinBlockEntity.this.tank.getTankCapacity(0);
			}

			@Override
			public boolean isFluidValid(int tank, FluidStack stack) {
				return true;
			}

			@Override
			public int fill(FluidStack resource, FluidAction action) {
				return 0; // underflow is outlet-only
			}

			@Override
			public FluidStack drain(FluidStack resource, FluidAction action) {
				if (resource.isEmpty()) {
					return FluidStack.EMPTY;
				}
				FluidStack available = SettlingBasinBlockEntity.this.tank.drainThickenedUnderflow(
					resource.getAmount(), UNDERFLOW_SOLIDS_FRACTION, FluidAction.SIMULATE);
				if (available.isEmpty() || !available.isFluidEqual(resource)) {
					return FluidStack.EMPTY;
				}
				return SettlingBasinBlockEntity.this.tank.drainThickenedUnderflow(resource.getAmount(),
					UNDERFLOW_SOLIDS_FRACTION, action);
			}

			@Override
			public FluidStack drain(int maxDrain, FluidAction action) {
				return SettlingBasinBlockEntity.this.tank.drainThickenedUnderflow(maxDrain,
					UNDERFLOW_SOLIDS_FRACTION, action);
			}
		};
		this.overflowCap = LazyOptional.of(() -> overflow);
		this.underflowCap = LazyOptional.of(() -> underflow);
	}

	// ------------------------------------------------------------ shape hooks

	@Override
	protected int minSize() {
		return 3;
	}

	@Override
	protected int maxSize() {
		return 15; // plans/05 §2: 底面 3×3 ~ 15×15
	}

	@Override
	protected int minRings() {
		return 1;
	}

	@Override
	protected int maxRings() {
		return 4; // plans/05 §2: 深 1 ~ 4
	}

	@Override
	protected RoofMode roofMode() {
		return RoofMode.FORBIDDEN; // roofless pool: the layer above the rim is not part of the shape
	}

	@Override
	protected int capacityFor(int w, int rings) {
		return (w - 2) * (w - 2) * rings * 1000; // 1 bucket per interior block (same rule as the reactor)
	}

	@Override
	protected void onAssembled() {
		// no process state to set (the pool has no status machine)
	}

	@Override
	protected void onStructureInvalidated() {
		clearCreditMb = 0;
		overdrawMb = 0;
		unclarifiedMb = 0;
		observedTankMb = tank.getTotalAmount();
	}

	// ------------------------------------------------------------------ ports

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER) {
			if (!isAssembled()) {
				return LazyOptional.empty();
			}
			if (side == null) {
				return super.getCapability(cap, null); // hand/container input keeps the generic vessel path
			}
			if (side == Direction.UP) {
				return LazyOptional.empty(); // the open rim never accepts a pipe
			}
			// bottom face = thickened underflow; side (or side-less) = surface overflow
			return (side == Direction.DOWN ? underflowCap : overflowCap).cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		overflowCap.invalidate();
		underflowCap.invalidate();
	}

	/**
	 * The surface overflow: skims the clear supernatant (liquid only, the settled
	 * bed's pore liquor protected — same floor as the decant spout). A draw larger
	 * than the standing supernatant punches through into the slurry zone: the pull
	 * entrains suspended solids (夹带) and the violence is recorded as churn.
	 */
	FluidStack overflowDrain(int maxDrain, FluidAction action) {
		if (maxDrain <= 0) {
			return FluidStack.EMPTY;
		}
		if (maxDrain <= clearCreditMb) {
			FluidStack out = tank.decantClear(maxDrain, action);
			if (action.execute() && !out.isEmpty()) {
				clearCreditMb -= out.getAmount();
			}
			return out;
		}
		// overdrawn: the lift pulls tank-average slurry (liquid + suspended; the
		// bed stays) and kicks the bed — resuspended at the next settle step
		FluidStack out = tank.drainSlurryZone(maxDrain, action);
		if (action.execute() && !out.isEmpty()) {
			overdrawMb += Math.max(0, out.getAmount() - clearCreditMb);
			clearCreditMb = 0;
		}
		return out;
	}

	// ------------------------------------------------------------------ tick

	@Override
	protected void vesselTick() {
		if (!isAssembled()) {
			return;
		}
		if (++tickCounter % SETTLE_INTERVAL != 0) {
			return;
		}
		long upmb = Chemistry.UNIT_PER_MB;
		int currentTankMb = tank.getTotalAmount();
		int volumeDelta = currentTankMb - observedTankMb;
		if (volumeDelta > 0) {
			unclarifiedMb = Math.min(currentTankMb, unclarifiedMb + volumeDelta);
		} else if (volumeDelta < 0) {
			unclarifiedMb = Math.min(unclarifiedMb, currentTankMb);
		}
		observedTankMb = currentTankMb;
		// 1) churn: the overdraw recorded since the last step kicks the bed back up
		boolean changed = overdrawMb > 0;
		if (overdrawMb > 0) {
			long resuspended = tank.resuspendSediment(Math.round(overdrawMb * RESUSPEND_RATE) * upmb,
				FluidAction.EXECUTE);
			unclarifiedMb = Math.min(currentTankMb,
				Math.max(unclarifiedMb, (int) Math.ceil(resuspended / (double) upmb)));
			overdrawMb = 0;
		}
		// 2) Process an area-scaled volume of slurry. Move only the proportional
		// suspended-solid share of that volume; flux is not a solid-volume rate.
		int slurryBatchMb = Math.min(unclarifiedMb, clarificationPerStepMb());
		long bedCap = (long) interiorArea() * getHeight() * SLUDGE_MB_PER_BLOCK_RING * upmb;
		long room = Math.max(0, bedCap - tank.sedimentUnits());
		long suspendedBefore = tank.suspendedUnits();
		long requestedMove = slurryBatchMb <= 0 || suspendedBefore <= 0 ? 0
			: slurryBatchMb >= unclarifiedMb ? suspendedBefore
				: Math.max(1, Math.round(suspendedBefore * (double) slurryBatchMb / unclarifiedMb));
		long moved = tank.settleSuspended(Math.min(requestedMove, room), FluidAction.EXECUTE);
		int processedMb = requestedMove <= 0 ? slurryBatchMb
			: (int) Math.floor(slurryBatchMb * (double) moved / requestedMove);
		unclarifiedMb = Math.max(0, unclarifiedMb - processedMb);
		changed |= moved > 0;
		int oldClearCredit = clearCreditMb;
		int drawableClearLiquid = tank.clearLiquidAvailable();
		// 3) Once no suspended solid remains, all physically drawable liquor above
		// the bed is supernatant.  The old model added the volume of settled solids
		// to this credit, making e.g. 91 mB of settled solid look like only 91 mB
		// of clear liquor even though the whole liquid layer had clarified.
		if (tank.suspendedUnits() == 0) {
			clearCreditMb = drawableClearLiquid;
		} else {
			clearCreditMb = Math.min(drawableClearLiquid, clearCreditMb + processedMb);
		}
		changed |= clearCreditMb != oldClearCredit;
		if (changed) sync();
	}

	/** Interior footprint in blocks ((W-2)² — the settling area). */
	public int interiorArea() {
		return size >= 3 ? (size - 2) * (size - 2) : 0;
	}

	/** mB of suspended solids still in suspension (test/diagnostic view). */
	public int suspendedMb() {
		return (int) (tank.suspendedUnits() / Chemistry.UNIT_PER_MB);
	}

	/** mB of settled solids in the sludge bed (test/diagnostic view). */
	public int sedimentMb() {
		return (int) (tank.sedimentUnits() / Chemistry.UNIT_PER_MB);
	}

	/** Standing clear supernatant the overflow may skim (mB). */
	public int getClearCreditMb() {
		return clearCreditMb;
	}

	public int clarificationPerStepMb() {
		return interiorArea() * FLUX_MB_PER_BLOCK_STEP;
	}

	public int sludgeCapacityMb() {
		return interiorArea() * getHeight() * SLUDGE_MB_PER_BLOCK_RING;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String space = " ";
		tooltip.add(Component.literal(space).append(Component.translatable("block.chemicaladdon.settling_basin")));
		if (!isAssembled()) {
			tooltip.add(Component.literal(space).append(Component.translatable("goggles.chemicaladdon.basin_not_assembled"))
				.withStyle(ChatFormatting.RED));
			return true;
		}
		tooltip.add(Component.literal(space).append(Component.translatable("goggles.chemicaladdon.basin_volume",
			tank.getTotalAmount(), tank.getTankCapacity(0))).withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.literal(space).append(Component.translatable("goggles.chemicaladdon.basin_clarification",
			clarificationPerStepMb())).withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(space).append(Component.translatable("goggles.chemicaladdon.basin_clear_credit",
			clearCreditMb)).withStyle(clearCreditMb > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		tooltip.add(Component.literal(space).append(Component.translatable("goggles.chemicaladdon.basin_suspended",
			suspendedMb())).withStyle(suspendedMb() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		tooltip.add(Component.literal(space).append(Component.translatable("goggles.chemicaladdon.basin_sludge",
			sedimentMb(), sludgeCapacityMb())).withStyle(
				sedimentMb() >= sludgeCapacityMb() && sludgeCapacityMb() > 0 ? ChatFormatting.RED : ChatFormatting.GOLD));
		if (ChemicalAddon.ASSAY_ON) {
			Map<ResourceLocation, Integer> sediment = new LinkedHashMap<>();
			for (FluidStack stack : tank.getFluids()) {
				for (Map.Entry<ResourceLocation, Integer> entry : Mixture.deriveSedimentAmounts(stack).entrySet()) {
					sediment.merge(entry.getKey(), entry.getValue(), Integer::sum);
				}
			}
			if (!sediment.isEmpty()) {
				tooltip.add(Component.literal(space + " ")
					.append(Component.translatable("goggles.chemicaladdon.basin_sludge_contents"))
					.withStyle(ChatFormatting.DARK_GRAY));
				for (Map.Entry<ResourceLocation, Integer> entry : sediment.entrySet()) {
					var item = ForgeRegistries.ITEMS.getValue(entry.getKey());
					String name = item != null && item != Items.AIR
						? new ItemStack(item).getHoverName().getString() : entry.getKey().toString();
					tooltip.add(Component.literal(space + "   • " + name + "  " + entry.getValue() + " mB")
						.withStyle(ChatFormatting.DARK_GRAY));
				}
			}
		}
		if (overdrawMb > 0) {
			tooltip.add(Component.literal(space).append(Component.translatable("goggles.chemicaladdon.basin_overdraw",
				overdrawMb)).withStyle(ChatFormatting.RED));
		}
		tooltip.add(Component.literal(space).append(Component.translatable("goggles.chemicaladdon.basin_ports"))
			.withStyle(ChatFormatting.DARK_GRAY));
		return true;
	}

	// ---------------------------------------------------------- serialization

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putInt("clearCredit", clearCreditMb);
		tag.putInt("overdraw", overdrawMb);
		tag.putInt("unclarified", unclarifiedMb);
		tag.putInt("observedTank", observedTankMb);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		clearCreditMb = Math.max(0, tag.getInt("clearCredit"));
		overdrawMb = Math.max(0, tag.getInt("overdraw"));
		unclarifiedMb = Math.max(0, tag.getInt("unclarified"));
		observedTankMb = Math.max(0, tag.getInt("observedTank"));
	}

	/** Controller block of the settling basin. */
	public static class SettlingBasinBlock extends Block implements EntityBlock {

		public SettlingBasinBlock(Properties properties) {
			super(properties);
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new SettlingBasinBlockEntity(pos, state);
		}

		@Nullable
		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			if (level.isClientSide) {
				return null;
			}
			return (lvl, pos, st, be) -> {
				if (be instanceof SettlingBasinBlockEntity basin) {
					basin.tick();
				}
			};
		}

		@Override
		public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			if (level.isClientSide) {
				return InteractionResult.SUCCESS;
			}
			if (level.getBlockEntity(pos) instanceof SettlingBasinBlockEntity basin) {
				if (!basin.isAssembled()) {
					boolean ok = basin.tryAssemble().ok();
					player.displayClientMessage(Component.literal(ok
						? "§a沉淀池成型！"
						: "§c结构不完整：需要化工砖池底 + 一圈以上池壁（3×3 ~ 15×15，深 1~4），控制器嵌在壁中"),
						false);
				} else {
					ItemStack held = player.getItemInHand(hand);
					// Match Create Basin / reactor-controller container interaction. The
					// side-less capability deliberately resolves to the surface port.
					if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, held, basin)
						|| FluidHelper.tryFillItemFromBE(level, player, hand, held, basin)) {
						return InteractionResult.SUCCESS;
					}
					if (GenericItemEmptying.canItemBeEmptied(level, held)
						|| GenericItemFilling.canItemBeFilled(level, held)) {
						return InteractionResult.SUCCESS;
					}
					player.displayClientMessage(Component.literal(String.format(
						"§7沉淀池 %d×%d×%d（澄清 %d mB/步，底泥 %d/%d mB，清液层 %d mB）—— 侧面溢流取清液，底下排泥",
						basin.getSize(), basin.getSize(), basin.getHeight(),
						basin.interiorArea() * FLUX_MB_PER_BLOCK_STEP,
						basin.sedimentMb(), basin.interiorArea() * basin.getHeight() * SLUDGE_MB_PER_BLOCK_RING,
						basin.getClearCreditMb())), false);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
	}
}
