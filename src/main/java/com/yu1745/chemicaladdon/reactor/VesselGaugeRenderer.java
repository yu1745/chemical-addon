package com.yu1745.chemicaladdon.reactor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.yu1745.chemicaladdon.ChemicalAddon;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The S02/S03 gauge needle (表盘指针): a thin needle drawn on the dial face,
 * chasing the client-synced reading (see {@link AbstractVesselGaugeBlockEntity#needleAngle}).
 * One renderer serves all four gauge forms — the full-cube wall blocks (the dial
 * texture is baked onto every face) and the thin wall-mounted panels (dial on the
 * FACING face only).
 *
 * <p>Pattern: Create's GaugeRenderer / TFMG's VoltMeterRenderer (partial model +
 * per-face pivot rotation). The per-face frame is derived from the plate's
 * blockstate rotation (BlockModelRotation applies −y about Y then −x about X,
 * see {@code BlockModelRotation}), so the needle's 12 o'clock stays glued to the
 * baked dial art on every face:
 * <ul>
 *   <li>dial plane — panel: block centre − 3/8·facing (the plate hangs 2px inside
 *       the cell, per the VoxelShapes); cube: centre + 1/2·facing (flush with the face)</li>
 *   <li>12 o'clock — +Y on horizontal faces, south on UP, north on DOWN</li>
 *   <li>sweep — ∓angle about the face normal (sign per axis) = clockwise viewed
 *       from the dial: the viewer above/below a ceiling/floor dial faces the
 *       opposite way, so the vertical faces flip the sign</li>
 * </ul>
 * The needle model is authored at the block centre pointing +Y, pivot at its base;
 * the pose stack aligns +Z → face normal, hops out to the dial plane, then sweeps.
 */
public class VesselGaugeRenderer extends SmartBlockEntityRenderer<AbstractVesselGaugeBlockEntity> {

	/** The white needle model (tinted per gauge type); pivot at its base, 12 o'clock. */
	private static final PartialModel NEEDLE =
		PartialModel.of(new ResourceLocation(ChemicalAddon.MODID, "block/gauge_needle"));

	/** Bright red while the gauge is in alarm — the world-in alarm signal on the dial itself. */
	private static final int ALARM_TINT = 0xE03A3A;

	/**
	 * Forces this class's {@code <clinit>} to run (thus registering {@link #NEEDLE}
	 * with {@code PartialModel.ALL}) <b>before</b> {@code ModelEvent.RegisterAdditional}.
	 * A {@code VesselGaugeRenderer::new} method reference alone is lazy and would
	 * leave the partial model unregistered until the first render — by which time
	 * model baking is over and the needle resolves to the missing-model cube
	 * (the "big purple-black block"). Mirrors Create's {@code AllPartialModels.init()}.
	 */
	public static void init() {
		// static field initialisation is the only side effect we need
	}

	public VesselGaugeRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(AbstractVesselGaugeBlockEntity gauge, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		BlockState state = gauge.getBlockState();
		// panels: dial on the FACING face only (the plate's back face sits against the wall);
		// full-cube wall gauges: the dial texture is baked onto all six faces
		boolean panel = state.hasProperty(BlockStateProperties.FACING);
		Direction[] faces = panel
			? new Direction[] { state.getValue(BlockStateProperties.FACING) }
			: Direction.values();
		// the plate model hangs 2px inside the block cell, so its dial plane sits 3/8
		// back from the centre; a cube's dial is flush with the block face (1/2)
		float dialOffset = gauge.dialOffset();
		float angle = gauge.getNeedleAngle(partialTicks);
		int tint = gauge.isAlarm() ? ALARM_TINT : gauge.needleTint();

		for (Direction facing : faces) {
			// The full-cube wall gauge draws a dial on all six faces, each lit by the
			// neighbour it faces (top bright, bottom dark); the panel's single face
			// just uses the block-entity light.
			int faceLight = panel
				? light
				: LevelRenderer.getLightColor(gauge.getLevel(), gauge.getBlockPos().relative(facing));
			renderNeedle(ms, buffer, state, facing, dialOffset, angle, tint, faceLight);
		}
	}

	/**
	 * Draws the needle model for one dial face — the pose shared by the in-world
	 * gauge renderer and the itemstack renderer (which parks the needle at its zero
	 * position, 0° = 12 o'clock, see {@link VesselGaugeItemRenderer}).
	 *
	 * <p>The needle model is authored in the 0..1 cell with its pivot at the block
	 * centre (0.5, 0.5, 0.5). Compose T(dial) · align · sweep · T(−pivot): hop to
	 * the dial plane, rotate the frame so +Z → face normal / +Y → 12 o'clock,
	 * sweep about that normal, then undo the pivot so the needle stays anchored at
	 * the dial centre (not flung around the block origin).
	 */
	static void renderNeedle(PoseStack ms, MultiBufferSource buffer, BlockState state, Direction facing,
		float dialOffset, float angle, int tint, int light) {
		Vector3f n = facing.step();
		Vector3f up = upOn(facing);
		Vector3f right = new Vector3f(up).cross(n, new Vector3f());
		// a viewer above/below a ceiling/floor dial faces the opposite way, so the
		// clockwise sweep flips sign on the vertical faces
		float sweep = facing.getAxis().isVertical() ? 1 : -1;

		// NOTE: createForBlock() bakes the model under FULL_DARK, and renderInto()
		// reset()s the tint after each draw, so re-tint (and re-set light) per call.
		SuperByteBuffer needle = CachedBuffers.partial(NEEDLE, state);
		needle.color((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, 255);

		ms.pushPose();
		ms.translate(0.5f, 0.5f, 0.5f);
		ms.translate(dialOffset * n.x(), dialOffset * n.y(), dialOffset * n.z());
		ms.mulPose(new Quaternionf().setFromNormalized(new Matrix3f().set(right, up, n)));
		// sweep clockwise as the reading rises (positive angle → −rotation)
		ms.mulPose(Axis.ZP.rotationDegrees(sweep * angle));
		ms.translate(-0.5f, -0.5f, -0.5f);
		needle.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
		ms.popPose();
	}

	/** The dial's 12 o'clock for each face (matches the blockstate-rotated plate art). */
	private static Vector3f upOn(Direction facing) {
		return switch (facing) {
			case UP -> new Vector3f(0, 0, 1);   // x-rotated plate: texture up = south
			case DOWN -> new Vector3f(0, 0, -1); // texture up = north
			default -> new Vector3f(0, 1, 0);
		};
	}
}
