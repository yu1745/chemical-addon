package com.yu1745.chemicaladdon.item;

import java.util.List;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.reactor.AbstractPhGaugeBlockEntity;
import com.yu1745.chemicaladdon.vessel.IMasterBound;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

/**
 * The consumable test-paper / reagent family (U17, plans/12 §2.2): one-time
 * <b>qualitative</b> probes — "what is in there", the question the continuous
 * gauges deliberately never answer. Dip (right-click a reactor controller or
 * any of its wall blocks), read the colour, lose the paper. The upgrade curve
 * runs paper → continuous gauge: litmus/phenolphthalein give way to the S16
 * pH gauge, AgNO₃/KSCN spot tests stay manual forever (specificity is
 * chemistry, not instrumentation — plans/03 §6 measurement honesty).
 *
 * <p>Usage hint is in the tooltip; the result is shown on the action bar so
 * the "paper" and its colour change read as one gesture.
 */
public class TestPaperItem extends Item {

	/** Which chemical question this paper asks. */
	public enum Kind {
		/** Litmus: acid red / neutral purple / alkali blue — the earliest pH judgement. */
		LITMUS,
		/** Phenolphthalein: pink at ≈pH 8.2 — the historical Solvay carbonisation endpoint. */
		PHENOLPHTHALEIN,
		/** Wide-range pH paper: ±1 pH quantisation. */
		WIDE_PH,
		/** AgNO₃: white turbidity = chloride present (salt refining / wash-water check). */
		SILVER_NITRATE,
		/** BaCl₂: white precipitate = sulfate present (brine sulfate removal check). */
		BARIUM_CHLORIDE,
		/** KSCN: blood red = ferric iron present (product iron contamination). */
		POTASSIUM_THIOCYANATE,
		/** Cobalt-glass flame scope: K lilac (Na yellow filtered out) / Na yellow / Ca brick red. */
		COBALT_GLASS
	}

	private final Kind kind;

	public TestPaperItem(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockEntity be = level.getBlockEntity(pos);
		ReactorControllerBlockEntity reactor = null;
		if (be instanceof ReactorControllerBlockEntity r) {
			reactor = r;
		} else if (be instanceof IMasterBound bound) {
			BlockEntity master = bound.getValidMaster();
			if (master instanceof ReactorControllerBlockEntity r) {
				reactor = r;
			}
		}
		if (reactor == null || reactor.getTank().getTotalAmount() <= 0) {
			return InteractionResult.PASS; // nothing to dip into: keep the paper
		}
		if (!level.isClientSide) {
			Player player = context.getPlayer();
			Component result = sample(kind, reactor);
			if (player != null) {
				player.displayClientMessage(result, true);
			}
			level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_STEP, SoundSource.PLAYERS, 0.8f, 1.4f);
			if (player == null || !player.getAbilities().instabuild) {
				context.getItemInHand().shrink(1); // consumed by the dip
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/**
	 * The paper's verdict as one HUD line (pure over the vessel state — the
	 * GameTests assert on {@link #verdictKey} instead of the action bar).
	 */
	public static Component sample(Kind kind, ReactorControllerBlockEntity reactor) {
		if (kind == Kind.WIDE_PH) {
			return sampleWidePh(reactor);
		}
		String key = verdictKey(kind, reactor);
		return Component.translatable(key).withStyle(colorOf(key));
	}

	/** The verdict's lang key — the pure, test-readable form of {@link #sample}. */
	public static String verdictKey(Kind kind, ReactorControllerBlockEntity reactor) {
		ReactorTank tank = reactor.getTank();
		switch (kind) {
			case LITMUS: {
				int ph = AbstractPhGaugeBlockEntity.phOf(tank);
				if (ph < 5) {
					return "paper.chemicaladdon.litmus_red";
				}
				if (ph > 8) {
					return "paper.chemicaladdon.litmus_blue";
				}
				return "paper.chemicaladdon.litmus_purple";
			}
			case PHENOLPHTHALEIN: {
				return AbstractPhGaugeBlockEntity.phOf(tank) >= 8
					? "paper.chemicaladdon.phenolphthalein_pink"
					: "paper.chemicaladdon.phenolphthalein_clear";
			}
			case WIDE_PH:
				return "paper.chemicaladdon.wide_ph"; // parameterised — see sample()
			case SILVER_NITRATE:
				return ionUnits(tank, "Cl-1") >= 1 ? "paper.chemicaladdon.agno3_positive" : "paper.chemicaladdon.agno3_negative";
			case BARIUM_CHLORIDE:
				return ionUnits(tank, "SO4-2") >= 1 ? "paper.chemicaladdon.bacl2_positive" : "paper.chemicaladdon.bacl2_negative";
			case POTASSIUM_THIOCYANATE:
				return ionUnits(tank, "Fe+3") >= 1 ? "paper.chemicaladdon.kscn_positive" : "paper.chemicaladdon.kscn_negative";
			case COBALT_GLASS: {
				// through the cobalt glass sodium's yellow is absorbed: potassium's
				// lilac becomes visible in its presence; otherwise report Na / Ca
				if (ionUnits(tank, "K+1") >= 1) {
					return "paper.chemicaladdon.flame_potassium";
				}
				if (ionUnits(tank, "Na+1") >= 1) {
					return "paper.chemicaladdon.flame_sodium";
				}
				if (ionUnits(tank, "Ca+2") >= 1) {
					return "paper.chemicaladdon.flame_calcium";
				}
				return "paper.chemicaladdon.flame_none";
			}
		}
		throw new IllegalArgumentException("unknown kind " + kind);
	}

	/** Wide-range paper carries the reading: "pH ≈ N" needs the number inline. */
	private static Component sampleWidePh(ReactorControllerBlockEntity reactor) {
		return Component.translatable("paper.chemicaladdon.wide_ph", AbstractPhGaugeBlockEntity.phOf(reactor.getTank()))
			.withStyle(ChatFormatting.AQUA);
	}

	private static ChatFormatting colorOf(String key) {
		return switch (key) {
			case "paper.chemicaladdon.litmus_red" -> ChatFormatting.RED;
			case "paper.chemicaladdon.litmus_blue" -> ChatFormatting.BLUE;
			case "paper.chemicaladdon.litmus_purple" -> ChatFormatting.DARK_PURPLE;
			case "paper.chemicaladdon.phenolphthalein_pink", "paper.chemicaladdon.flame_potassium" -> ChatFormatting.LIGHT_PURPLE;
			case "paper.chemicaladdon.flame_sodium" -> ChatFormatting.YELLOW;
			case "paper.chemicaladdon.flame_calcium" -> ChatFormatting.GOLD;
			case "paper.chemicaladdon.agno3_positive", "paper.chemicaladdon.bacl2_positive", "paper.chemicaladdon.kscn_positive" -> ChatFormatting.GREEN;
			default -> ChatFormatting.GRAY;
		};
	}

	/** Total units of one ion across the tank's aqueous phase (≥1 unit = detectable). */
	private static long ionUnits(ReactorTank tank, String ionId) {
		long total = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (Mixture.isMixture(stack)) {
				total += (long) Mixture.deriveUnitIonAmounts(stack).getOrDefault(ionId, 0);
			}
		}
		return total;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("paper.chemicaladdon.hint", stack.getItem().getDescription())
			.withStyle(ChatFormatting.DARK_GRAY));
	}
}
