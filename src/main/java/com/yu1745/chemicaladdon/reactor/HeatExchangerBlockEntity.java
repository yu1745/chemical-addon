package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

/**
 * Heat exchanger (施工包 F2 / plans/07 §2.3): two streams exchange ENERGY and
 * never composition. The hot side lives on the north/south faces, the cold
 * side on the east/west faces — one block, two independent tanks, no mixing.
 *
 * <p>Model (every {@link #STEP_TICK} ticks): both streams relax toward their
 * amount-weighted equilibrium temperature at efficiency {@link #EFFICIENCY}
 * (0.8 — a countercurrent exchanger's approach); the joules the cold stream
 * gains are the <b>recovered heat</b> accumulator. Energy is conserved across
 * the pair ({@code c = 4.18 J/mB·°C}, the U16 ledger's water-scale constant)
 * minus a small ambient loss when a side runs empty. The bottleneck readout is
 * the live ΔT between the streams: a big ΔT with flow means the exchanger is
 * undersized for the stream.
 */
public class HeatExchangerBlockEntity extends BlockEntity implements IHaveGoggleInformation {

	public static final int TANK_CAPACITY = 4000;
	/** J per mB per °C (the U16 energy ledger's water-scale heat capacity). */
	public static final double SPECIFIC_HEAT = 4.18;
	/** fraction of the remaining temperature gap closed per step (approach). */
	public static final double EFFICIENCY = 0.8;
	private static final int STEP_TICK = 10;

	private final ReactorTank hotTank = new ReactorTank(TANK_CAPACITY, this::onChanged);
	private final ReactorTank coldTank = new ReactorTank(TANK_CAPACITY, this::onChanged);
	private final LazyOptional<IFluidHandler> hotCap = LazyOptional.of(() -> hotTank);
	private final LazyOptional<IFluidHandler> coldCap = LazyOptional.of(() -> coldTank);

	private int tickCounter = 0;
	/** cumulative joules handed to the cold stream (the recovered-heat meter). */
	private double recoveredJ = 0;

	public HeatExchangerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.HEAT_EXCHANGER.get(), pos, state);
	}

	private void onChanged() {
		setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	// ------------------------------------------------------------------ ticker

	private void tick() {
		if (level == null || level.isClientSide) {
			return;
		}
		if (++tickCounter % STEP_TICK != 0) {
			return;
		}
		hotTank.collapseIfNeeded();
		coldTank.collapseIfNeeded();
		int hotMb = hotTank.getTotalAmount();
		int coldMb = coldTank.getTotalAmount();
		if (hotMb <= 0 || coldMb <= 0) {
			return; // one empty side: nothing to exchange (no free ambient heat)
		}
		int hotTemp = temperatureOf(hotTank);
		int coldTemp = temperatureOf(coldTank);
		if (hotTemp <= coldTemp) {
			return;
		}
		// amount-weighted equilibrium (the two streams exchange energy only)
		double equilibrium = ((double) hotTemp * hotMb + (double) coldTemp * coldMb) / (hotMb + coldMb);
		double hotNext = hotTemp + (equilibrium - hotTemp) * EFFICIENCY;
		double coldNext = coldTemp + (equilibrium - coldTemp) * EFFICIENCY;
		// recovered = what the cold stream gained (mB × °C × c)
		recoveredJ += (coldNext - coldTemp) * coldMb * SPECIFIC_HEAT;
		apply(hotTank, hotNext);
		apply(coldTank, coldNext);
		setChanged();
	}

	private static int temperatureOf(ReactorTank tank) {
		long weighted = 0;
		int total = 0;
		for (FluidStack stack : tank.getFluids()) {
			weighted += (long) com.yu1745.chemicaladdon.fluid.Temperature.get(stack) * stack.getAmount();
			total += stack.getAmount();
		}
		return com.yu1745.chemicaladdon.fluid.Temperature.fromWeightedSum(weighted, total);
	}

	private static void apply(ReactorTank tank, double next) {
		int target = (int) Math.round(next);
		for (FluidStack stack : tank.getFluids()) {
			com.yu1745.chemicaladdon.fluid.Temperature.set(stack, target);
		}
	}

	// ------------------------------------------------------------------ reads

	public ReactorTank getHotTank() {
		return hotTank;
	}

	public ReactorTank getColdTank() {
		return coldTank;
	}

	/** Cumulative recovered heat (J) — the optimization meter. */
	public double getRecoveredJ() {
		return recoveredJ;
	}

	/** Live ΔT between the streams — the sizing/bottleneck readout. */
	public int getDeltaT() {
		return temperatureOf(hotTank) - temperatureOf(coldTank);
	}

	// ------------------------------------------------------------- capability

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER && side != null) {
			switch (side) {
				case NORTH, SOUTH -> {
					return hotCap.cast();
				}
				case EAST, WEST -> {
					return coldCap.cast();
				}
				default -> {
				}
			}
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		hotCap.invalidate();
		coldCap.invalidate();
	}

	// ---------------------------------------------------------- serialization

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.put("hot", hotTank.serializeNBT());
		tag.put("cold", coldTank.serializeNBT());
		tag.putDouble("recoveredJ", recoveredJ);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		hotTank.deserializeNBT(tag.getCompound("hot"));
		coldTank.deserializeNBT(tag.getCompound("cold"));
		recoveredJ = tag.contains("recoveredJ") ? tag.getDouble("recoveredJ") : 0;
	}

	// ------------------------------------------------------------- goggles HUD

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("block.chemicaladdon.heat_exchanger")));
		tooltip.add(Component.literal(spacing + " ")
			.append(Component.translatable("goggles.chemicaladdon.hx_hot", temperatureOf(hotTank),
				hotTank.getTotalAmount()))
			.withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.literal(spacing + " ")
			.append(Component.translatable("goggles.chemicaladdon.hx_cold", temperatureOf(coldTank),
				coldTank.getTotalAmount()))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(spacing + " ")
			.append(Component.translatable("goggles.chemicaladdon.hx_recovered", (int) recoveredJ))
			.withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.literal(spacing + " ")
			.append(Component.translatable("goggles.chemicaladdon.hx_delta", getDeltaT()))
			.withStyle(getDeltaT() > 50 ? ChatFormatting.RED : ChatFormatting.GRAY));
		return true;
	}

	/** The heat exchanger block. */
	public static class HeatExchangerBlock extends Block implements EntityBlock {

		public HeatExchangerBlock(Properties properties) {
			super(properties);
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new HeatExchangerBlockEntity(pos, state);
		}

		@Nullable
		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
			if (level.isClientSide) {
				return null;
			}
			return (lvl, pos, st, be) -> {
				if (be instanceof HeatExchangerBlockEntity hx) {
					hx.tick();
				}
			};
		}

		@Override
		public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			if (level.isClientSide) {
				return InteractionResult.SUCCESS;
			}
			if (level.getBlockEntity(pos) instanceof HeatExchangerBlockEntity hx) {
				player.displayClientMessage(Component.literal(String.format(
					"§7换热器（热侧 %d°C / 冷侧 %d°C，ΔT %d，累计回收 %d J）—— 南北面接热流、东西面接冷流",
					hx.getHotTank().getTotalAmount() > 0 ? temperatureOf(hx.getHotTank()) : 0,
					hx.getColdTank().getTotalAmount() > 0 ? temperatureOf(hx.getColdTank()) : 0,
					hx.getDeltaT(), (int) hx.getRecoveredJ())), false);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
	}
}
