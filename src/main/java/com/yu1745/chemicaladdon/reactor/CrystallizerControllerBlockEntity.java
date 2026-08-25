package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

/**
 * The M08 endpoint crystalliser (终点结晶器, U17 redefinition — plans/04 M08,
 * plans/12 §6): the vessel template as an <b>executor reading the same
 * physical quantity as the S04 Baumé gauge</b>. No species knowledge, no
 * magic purity judgement — one physical setpoint in °Bé:
 *
 * <ul>
 *   <li><b>below setpoint</b>: heats normally (open-topped boiling
 *       concentrates the liquor, U16 latent heat governing the pot);</li>
 *   <li><b>at/above setpoint</b>: the burner is cut ({@link #heatTarget}
 *       returns ambient) and a strong redstone endpoint event fires — the
 *       external circuit discharges the crystal slurry while the mother
 *       liquor's density never crosses the setpoint. "Only A crystallises,
 *       not B" is earned by choosing the setpoint below B's saturation;</li>
 *   <li><b>condensate recovery</b>: every unit of steam an open boiling
 *       vessel vents is condensed into an internal distillate tank and
 *       pushed to any adjacent non-vessel fluid consumer (蒸出水为产物).</li>
 * </ul>
 *
 * <p>The setpoint itself is earned in-world (校准运行 / 溶解度手册推算 / 试纸质检):
 * drop a seed crystal of the target species, watch the S04 gauge while
 * concentrating, and the °Bé at which the first cloud appears <i>is</i> the
 * number to dial in.
 */
public class CrystallizerControllerBlockEntity extends ReactorControllerBlockEntity {

	public static final int SETPOINT_STEP_BE = 2;      // °Bé per scroll unit (matches S04)
	public static final int SETPOINT_MAX_STEPS = 15;   // 0–30 °Bé
	public static final int DEFAULT_SETPOINT_STEPS = 12; // 24 °Bé
	public static final int CONDENSATE_CAPACITY = 4000;

	private ScrollValueBehaviour setpoint;
	private final FluidTank condensate = new FluidTank(CONDENSATE_CAPACITY);
	private boolean lastEndpoint = false;

	public CrystallizerControllerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.CRYSTALLIZER_CONTROLLER.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		setpoint = new ScrollValueBehaviour(
			Component.translatable("crystallizer.chemicaladdon.setpoint"), this,
			new CenteredSideValueBoxTransform());
		setpoint.between(0, SETPOINT_MAX_STEPS);
		setpoint.value = DEFAULT_SETPOINT_STEPS;
		setpoint.withFormatter(i -> (i * SETPOINT_STEP_BE) + "°Bé");
		behaviours.add(setpoint);
	}

	/** The physical endpoint setpoint in °Bé (world-in scrollable). */
	public int getSetpoint() {
		return (setpoint != null ? setpoint.getValue() : DEFAULT_SETPOINT_STEPS) * SETPOINT_STEP_BE;
	}

	/** Set the setpoint in °Bé, snapped to the step grid (test/config hook). */
	public void setSetpointBe(int be) {
		if (setpoint != null) {
			setpoint.value = net.minecraft.util.Mth.clamp(be / SETPOINT_STEP_BE, 0, SETPOINT_MAX_STEPS);
		}
	}

	/** The endpoint event: the liquor's density has reached the setpoint. */
	public boolean atEndpoint() {
		return isAssembled() && AbstractBaumeGaugeBlockEntity.baumeOf(tank) >= getSetpoint();
	}

	@Override
	protected int heatTarget() {
		// the physical setpoint gates the burner: past the endpoint the vessel
		// cools back to ambient and the supersaturated target crystallises,
		// while anything whose saturation lies above the setpoint stays dissolved
		return atEndpoint() ? AMBIENT_TEMP : super.heatTarget();
	}

	@Override
	protected void tickReaction() {
		if (!isAssembled()) {
			return; // the vessel layer already reset structure state
		}
		// 内核主循环（同反应釜，2026-08 全量切换）。已知缺口：开口蒸发/冷凝回收
		// 尚未在内核路径实现（原 U13 步骤），冷凝罐保持空——蒸发浓缩与 °Bé 终点
		// 闭环待内核侧蒸发机制（策展 interface 池）落地后恢复。
		stepKernelChemistry();
		pushCondensate();
		boolean endpoint = atEndpoint();
		if (endpoint != lastEndpoint && level != null) {
			lastEndpoint = endpoint;
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}
	}

	/**
	 * Passive distillate output: push condensate to any adjacent fluid
	 * consumer. Neighbours that belong to a vessel (shell bricks, gauges —
	 * anything master-bound or a vessel BE) are skipped: filling the
	 * crystalliser's own slurry with its own distillate would be a loop.
	 */
	private void pushCondensate() {
		if (level == null || level.isClientSide || condensate.getFluidAmount() <= 0) {
			return;
		}
		for (Direction side : Direction.values()) {
			BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(side));
			if (neighbour == null || neighbour instanceof VesselBlockEntity || neighbour instanceof IMasterBound) {
				continue;
			}
			IFluidHandler handler = neighbour.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite())
				.orElse(null);
			if (handler == null) {
				continue;
			}
			int pushed = handler.fill(new FluidStack(Fluids.WATER, condensate.getFluidAmount()), FluidAction.EXECUTE);
			if (pushed > 0) {
				condensate.drain(pushed, FluidAction.EXECUTE);
				setChanged();
				if (condensate.getFluidAmount() <= 0) {
					return;
				}
			}
		}
	}

	/** The recovered distillate (vented steam condensed), in mB — goggles display + tests. */
	public int getCondensateMb() {
		return condensate.getFluidAmount();
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putInt("condensate", condensate.getFluidAmount());
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		condensate.setFluid(tag.contains("condensate") ? new FluidStack(Fluids.WATER, tag.getInt("condensate"))
			: FluidStack.EMPTY);
	}

	@Override
	protected void addGoggleStatus(List<Component> tooltip, String spacing) {
		int baume = AbstractBaumeGaugeBlockEntity.baumeOf(tank);
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.baume", baume))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.baume_gauge_threshold", getSetpoint()))
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.crystallizer_condensate", getCondensateMb()))
			.withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.crystallizer_state"))
			.append(Component.translatable(atEndpoint()
				? "goggles.chemicaladdon.crystallizer_endpoint"
				: "goggles.chemicaladdon.crystallizer_concentrating"))
			.withStyle(atEndpoint() ? ChatFormatting.GREEN : ChatFormatting.GOLD));
	}
}
