package com.yu1745.chemicaladdon.reactor;

import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Renders the vessel interior: the fluid surface (a semi-transparent "pot of
 * soup" — liquids layered upward from the floor, gases hanging from the top)
 * and the item buffer floating ON the surface, half-submerged with a gentle
 * bobbing. Handles any n x n x n shell size (interior (n-2)^3). Fluid rendering
 * reuses Create's catnip FluidRenderHelper, animated by a client-side LerpedFloat
 * chasing the synced fill state.
 */
public class ReactorControllerRenderer extends SmartBlockEntityRenderer<ReactorControllerBlockEntity> {

	/** Global speed multiplier for item drift / bob / roll (lower = slower, lazier motion). */
	private static final float MOTION_SPEED = 0.2f;

	public ReactorControllerRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(ReactorControllerBlockEntity reactor, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		super.renderSafe(reactor, partialTicks, ms, buffer, light, overlay);

		Direction inward = reactor.getInward();
		if (inward == null || !reactor.isAssembled()) {
			return; // not assembled (or client hasn't received the structure yet)
		}
		int size = reactor.getSize();
		int height = reactor.getHeight();

		// interior footprint in controller-local coordinates (the (n-2)^2 hollow core)
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int half = (size - 1) / 2;
		int sMin = -half + 1;
		int sMax = -half + size - 2;
		Vec3 c1 = new Vec3(side.getStepX() * sMin + inward.getStepX(), 0,
			side.getStepZ() * sMin + inward.getStepZ());
		Vec3 c2 = new Vec3(side.getStepX() * sMax + inward.getStepX() * (size - 2), 0,
			side.getStepZ() * sMax + inward.getStepZ() * (size - 2));
		float x1 = (float) Math.min(c1.x, c2.x);
		float x2 = (float) Math.max(c1.x, c2.x) + 1;
		float z1 = (float) Math.min(c1.z, c2.z);
		float z2 = (float) Math.max(c1.z, c2.z) + 1;
		float cx = (x1 + x2) / 2f;
		float cz = (z1 + z2) / 2f;

		// light is sampled at the vessel interior (the controller block sits in
		// the wall and is nearly unlit — contents must use the light of the spot
		// they actually occupy: open vessels get full skylight, sealed ones go dark)
		BlockPos centerPos = reactor.getBlockPos().offset((int) cx, height / 2, (int) cz);
		light = LightTexture.pack(reactor.getLevel().getBrightness(LightLayer.BLOCK, centerPos),
			reactor.getLevel().getBrightness(LightLayer.SKY, centerPos));

		float renderTime = AnimationTickHolder.getRenderTime(reactor.getLevel());
		// motion (drift / bob / roll / pitch rock) runs on a scaled clock so the whole
		// animation can be sped up or slowed down from one constant (MOTION_SPEED)
		float t = renderTime * MOTION_SPEED;

		// --- fluid-surface height: compute WITHOUT drawing yet. Items must render
		// before the fluid so their submerged parts show through the translucent
		// fluid (the fluid writes depth; rendering it first would depth-cull every
		// item fragment behind its front faces). ---
		float levelHeight = reactor.getRenderedLevel(partialTicks) * height;
		float liquidSurface = liquidSurfaceHeight(reactor, levelHeight);

		// --- item pass: float on the fluid surface, half-submerged, bobbing ---
		int itemCount = 0;
		for (int i = 0; i < reactor.getItems().getSlots(); i++) {
			if (!reactor.getItems().getStackInSlot(i).isEmpty()) {
				itemCount++;
			}
		}

		// surface the items float on (below any gas layer); empty vessel -> near the floor
		float surfaceY = Math.max(liquidSurface, 0.125f);
		boolean floating = reactor.getFillState() > 0;

		if (itemCount > 0) {
			// Free-drift positions roam the WHOLE interior footprint (two sines per
			// axis, large amplitude) — not a small pocket. A lightweight pairwise
			// repulsion (O(n^2), n <= 4) then separates any items that drift too
			// close, so they share the vessel without overlapping. Everything is a
			// pure function of time -> no per-item state, deterministic per frame.
			float interiorR = Math.max(0.1f, (size - 2) * 0.5f - 0.35f);
			float[] posX = new float[itemCount];
			float[] posZ = new float[itemCount];
			float[] velX = new float[itemCount];
			float[] velZ = new float[itemCount];

			int idx = 0;
			for (int slot = 0; slot < reactor.getItems().getSlots(); slot++) {
				if (reactor.getItems().getStackInSlot(slot).isEmpty()) {
					continue;
				}
				if (floating) {
					// roam the full footprint; the two periods are incommensurate so the
					// path never retraces (an organic Lissajous wander)
					float ax = t / 9.1f + slot * 2.3f;
					float az = t / 11.7f + slot * 3.7f;
					posX[idx] = Mth.sin(ax) * interiorR;
					posZ[idx] = Mth.cos(az) * interiorR;
					// analytic velocity (derivative of position) -> heading of motion
					velX[idx] = Mth.cos(ax) / 9.1f * interiorR;
					velZ[idx] = -Mth.sin(az) / 11.7f * interiorR;
				} else {
					// dry vessel: rest at a fixed ring anchor near the floor, no motion
					float a = itemCount == 1 ? 0 : idx * (float) (2.0 * Math.PI / itemCount);
					float ar = itemCount == 1 ? 0 : interiorR;
					posX[idx] = (float) Math.cos(a) * ar;
					posZ[idx] = (float) Math.sin(a) * ar;
				}
				idx++;
			}

			// pairwise repulsion: nudge apart any pair closer than minDist (a few
			// relaxation passes), then clamp back inside the footprint.
			float minDist = 0.55f;
			for (int iter = 0; iter < 3 && itemCount > 1; iter++) {
				for (int i = 0; i < itemCount; i++) {
					for (int j = i + 1; j < itemCount; j++) {
						float ddx = posX[i] - posX[j];
						float ddz = posZ[i] - posZ[j];
						float d = (float) Math.sqrt(ddx * ddx + ddz * ddz);
						float overlap = minDist - d;
						if (overlap > 0 && d > 1e-4f) {
							float nx = ddx / d * overlap * 0.5f;
							float nz = ddz / d * overlap * 0.5f;
							posX[i] += nx; posZ[i] += nz;
							posX[j] -= nx; posZ[j] -= nz;
						}
					}
				}
			}
			for (int i = 0; i < itemCount; i++) {
				posX[i] = Math.max(-interiorR, Math.min(interiorR, posX[i]));
				posZ[i] = Math.max(-interiorR, Math.min(interiorR, posZ[i]));
			}

			RandomSource rng = RandomSource.create(reactor.getBlockPos().hashCode());
			ms.pushPose();
			ms.translate(cx, surfaceY - 0.1f, cz);

			idx = 0;
			for (int slot = 0; slot < reactor.getItems().getSlots(); slot++) {
				ItemStack stack = reactor.getItems().getStackInSlot(slot);
				if (stack.isEmpty()) {
					continue;
				}
				float px = posX[idx], pz = posZ[idx];

				// vertical bob: sum of two incommensurate sines (gentle, never metronomic)
				float bob = 0;
				if (floating) {
					bob = (Mth.sin(t / 12f + slot) + 0.5f * Mth.sin(t / 7.3f + slot * 1.7f)) * 1 / 32f;
				}

				ms.pushPose();
				ms.translate(px, bob, pz);

				// orientation: face the direction of travel (heading), barrel-roll about
				// the side axis so the item tumbles as it drifts, plus a gentle pitch
				// rock. Nothing is locked — items turn and roll with their motion.
				float yawDeg;
				float rollDeg;
				float pitchDeg;
				if (floating) {
					float speed = (float) Math.sqrt(velX[idx] * velX[idx] + velZ[idx] * velZ[idx]);
					yawDeg = speed > 1e-3f
						? (float) Math.toDegrees(Math.atan2(velZ[idx], velX[idx]))
						: slot * 47 + 35; // near-stationary: stable fallback heading
					rollDeg = (t * 1.5f + slot * 60f) % 360f;            // slow continuous barrel roll
					pitchDeg = 65 + 6 * Mth.sin(t / 8.3f + slot * 2.1f); // lay flat + gentle rock
				} else {
					yawDeg = slot * 47 + 35;
					rollDeg = 0;
					pitchDeg = 65;
				}
				ms.mulPose(Axis.YP.rotationDegrees(yawDeg));
				ms.mulPose(Axis.ZP.rotationDegrees(rollDeg));
				ms.mulPose(Axis.XP.rotationDegrees(pitchDeg));

				// a small heap per stack (visible even at low counts); tight scatter
				// keeps the pile at this item's position, clear of the others.
				int copies = 1 + stack.getCount() / 4;
				for (int i = 0; i < copies; i++) {
					ms.pushPose();
					Vec3 scatter = VecHelper.offsetRandomly(Vec3.ZERO, rng, 1 / 24f);
					ms.translate(scatter.x, scatter.y, scatter.z);
					Minecraft.getInstance().getItemRenderer()
						.renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, ms, buffer,
							Minecraft.getInstance().level, 0);
					ms.popPose();
				}
				ms.popPose();
				idx++;
			}
			ms.popPose();
		}

		// --- fluid pass: drawn AFTER the items. Because the fluid renders in the
		// translucent pass (which writes depth) and is flushed after the item
		// geometry, the submerged item fragments — already in the framebuffer —
		// show through the translucent fluid instead of being depth-culled. ---
		if (levelHeight > 1 / 1024f) {
			renderFluid(reactor, x1, z1, x2, z2, levelHeight, ms, buffer, light);
		}
	}

	/**
	 * Height of the top of the non-gas (liquid) region — the surface items float on
	 * (gases hang above it). Computed without drawing so the item pass can run
	 * before the fluid pass (items must render first to show through the fluid).
	 */
	private float liquidSurfaceHeight(ReactorControllerBlockEntity reactor, float levelHeight) {
		if (levelHeight <= 1 / 1024f) {
			return 0;
		}
		List<FluidStack> fluids = reactor.getTank().getFluids();
		int total = reactor.getTank().getTotalAmount();
		if (fluids.isEmpty() || total <= 0) {
			return 0;
		}
		int liquidAmount = 0;
		for (FluidStack f : fluids) {
			if (!isLighterThanAir(f)) {
				liquidAmount += f.getAmount();
			}
		}
		return levelHeight * liquidAmount / total;
	}

	/**
	 * Renders the layered fluid body from the interior floor up to {@code levelHeight}
	 * (local y). Non-gas fluids build the bottom layers, gases hang from the top of
	 * the level (the lighter-than-air layer of the "soup"). Returns the top of the
	 * liquid (non-gas) region — the surface items float on.
	 */
	private float renderFluid(ReactorControllerBlockEntity reactor, float x1, float z1, float x2, float z2,
		float levelHeight, PoseStack ms, MultiBufferSource buffer, int light) {
		List<FluidStack> fluids = reactor.getTank().getFluids();
		int total = reactor.getTank().getTotalAmount();
		if (fluids.isEmpty() || total <= 0) {
			return 0;
		}

		// inset 1/32 from the shell walls
		float ix1 = x1 + 1 / 32f;
		float ix2 = x2 - 1 / 32f;
		float iz1 = z1 + 1 / 32f;
		float iz2 = z2 - 1 / 32f;

		int liquidAmount = 0;
		for (FluidStack f : fluids) {
			if (!isLighterThanAir(f)) {
				liquidAmount += f.getAmount();
			}
		}
		float liquidHeight = levelHeight * liquidAmount / total;

		// liquids: bottom-up
		float y = 0;
		for (FluidStack f : fluids) {
			if (isLighterThanAir(f)) {
				continue;
			}
			if (Mixture.isMixture(f) && Mixture.getMixDegree(f) < 1.0f) {
				// not yet homogenised: render the component colours as a patchwork
				// across the XZ surface (a particle grid), so the un-mixed state is
				// visible looking down at the fluid; once MixDegree reaches 1 this
				// falls through to the single blended box below
				float h = levelHeight * f.getAmount() / total;
				renderMixtureSurface(f, ix1, ix2, iz1, iz2, y, y + h, ms, buffer, light, reactor.getBlockPos());
				y += h;
			} else {
				float h = levelHeight * f.getAmount() / total;
				renderBox(f, ix1, y, iz1, ix2, y + h, iz2, ms, buffer, light);
				y += h;
			}
		}
		// gases: hang from the level top downward
		float gasTop = levelHeight;
		for (FluidStack f : fluids) {
			if (!isLighterThanAir(f)) {
				continue;
			}
			float h = levelHeight * f.getAmount() / total;
			renderBox(f, ix1, gasTop - h, iz1, ix2, gasTop, iz2, ms, buffer, light);
			gasTop -= h;
		}
		return liquidHeight;
	}

	private void renderBox(FluidStack fluid, float x1, float y1, float z1, float x2, float y2, float z2,
		PoseStack ms, MultiBufferSource buffer, int light) {
		// no bottom face (the vessel floor is brick), no gas flipping (we layer gases on top ourselves)
		ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, x1, y1, z1, x2, y2, z2, buffer, ms, light, false, false);
	}

	/**
	 * Renders a not-yet-homogenised mixture as a particle grid across the XZ
	 * surface: each cell is a full-height column coloured by one component
	 * (chosen by amount-weighted deterministic scatter). The grid is coarse when
	 * MixDegree is low (big colour patches = visibly un-mixed) and grows fine as
	 * it approaches 1 (fine speckle that reads as the blended colour). This is
	 * the surface-particle model: the colours live in the XZ plane (the fluid
	 * top), not stacked vertically, so the patchwork is visible looking down into
	 * the vessel.
	 */
	private void renderMixtureSurface(FluidStack mixture, float x1, float x2, float z1, float z2,
		float yBottom, float yTop, PoseStack ms, MultiBufferSource buffer, int light, BlockPos vesselPos) {
		Map<ResourceLocation, Integer> comps = Mixture.deriveAmounts(mixture);
		int total = mixture.getAmount();
		if (total <= 0 || comps.isEmpty()) {
			return;
		}
		float md = Mixture.getMixDegree(mixture);
		int grid = Math.min(8, 2 + Math.round(md * 6f)); // 2 (4 cells) -> 8 (64 cells)
		float cellW = (x2 - x1) / grid;
		float cellD = (z2 - z1) / grid;
		int seed = vesselPos.hashCode();
		for (int i = 0; i < grid; i++) {
			for (int j = 0; j < grid; j++) {
				int h = seed ^ (i * 73856093) ^ (j * 19349663);
				Fluid cf = pickComponentFluid(comps, total, h);
				if (cf == null || cf == Fluids.EMPTY) {
					continue;
				}
				float cx1 = x1 + i * cellW;
				float cz1 = z1 + j * cellD;
				renderBox(new FluidStack(cf, 1000), cx1, yBottom, cz1, cx1 + cellW, yTop, cz1 + cellD,
					ms, buffer, light);
			}
		}
	}

	/** Picks a component fluid by amount-weighted deterministic scatter. */
	private static Fluid pickComponentFluid(Map<ResourceLocation, Integer> comps, int total, int hash) {
		int r = (hash & 0x7FFFFFFF) % total;
		int acc = 0;
		ResourceLocation picked = null;
		for (Map.Entry<ResourceLocation, Integer> e : comps.entrySet()) {
			acc += e.getValue();
			picked = e.getKey();
			if (r < acc) {
				break;
			}
		}
		return picked != null ? ForgeRegistries.FLUIDS.getValue(picked) : null;
	}

	private static boolean isLighterThanAir(FluidStack fluid) {
		return fluid.getFluid().getFluidType().isLighterThanAir();
	}

	@Override
	public boolean shouldRenderOffScreen(ReactorControllerBlockEntity reactor) {
		// the interior (fluid + items) can extend up to 7 blocks from the controller
		// block, beyond the default per-block render culling
		return true;
	}
}
