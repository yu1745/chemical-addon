package com.yu1745.chemicaladdon.item;

import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Creative-only debug tool: right-click a reaction vessel (the controller or any
 * bound shell brick) to pin its temperature to the next preset in the ladder, so
 * the Blaze Burner / ambient relaxation in {@code updateHeat} no longer moves it.
 * Sneak-right-click unpins and resumes normal heating.
 *
 * <p>The temperature is pinned server-side on the vessel ({@code pinnedTemperature});
 * the feedback is an action-bar message. Modelled on vanilla's {@code DebugStickItem}.
 */
public class TemperatureDebugItem extends Item {

	/** Preset temperature ladder (°C): ambient, "hot", KINDLED/HEATED, SEETHING/SUPERHEATED. */
	private static final int[] LADDER = { 20, 100, 500, 900 };

	public TemperatureDebugItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockEntity be = level.getBlockEntity(pos);
		ReactorControllerBlockEntity reactor = resolveReactor(be);
		if (reactor == null) {
			return InteractionResult.PASS;
		}
		Player player = context.getPlayer();
		if (!level.isClientSide && player != null) {
			if (player.isShiftKeyDown()) {
				reactor.setPinnedTemperature(-1);
				player.displayClientMessage(Component.literal("反应釜温度已解锁（恢复正常加热）"), true);
			} else {
				int current = reactor.getPinnedTemperature() >= 0
					? reactor.getPinnedTemperature()
					: reactor.getTemperature();
				int next = nextUp(current);
				reactor.setPinnedTemperature(next);
				boolean empty = reactor.getTank().getFluids().isEmpty();
				player.displayClientMessage(Component.literal(
					"反应釜温度 → " + next + "°C" + (empty ? "（空釜，加入流体后生效）" : "")), true);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Smallest ladder value strictly above {@code current}, wrapping to the lowest. */
	private static int nextUp(int current) {
		for (int t : LADDER) {
			if (t > current) {
				return t;
			}
		}
		return LADDER[0];
	}

	/** The reactor the player clicked: the controller itself, or a shell block's master. */
	private static ReactorControllerBlockEntity resolveReactor(BlockEntity be) {
		if (be instanceof ReactorControllerBlockEntity reactor) {
			return reactor;
		}
		if (be instanceof IMasterBound bound) {
			BlockEntity master = bound.getValidMaster();
			if (master instanceof ReactorControllerBlockEntity reactor) {
				return reactor;
			}
		}
		return null;
	}
}
