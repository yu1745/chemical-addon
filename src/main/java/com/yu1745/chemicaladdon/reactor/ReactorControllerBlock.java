package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity.AssembleIssue;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity.AssembleResult;

import net.minecraftforge.items.ItemHandlerHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Controller of the reaction vessel multiblock (M1 template: 3x3x3 hollow
 * brick shell, controller embedded in a wall). Right-click while un-assembled
 * attempts structure validation with a diagnostic failure message (which face
 * / which brick is missing); once assembled it opens the item panel.
 */
public class ReactorControllerBlock extends Block implements EntityBlock {

	/** true = the vessel is open-topped (interior visible), false = sealed top. */
	public static final BooleanProperty OPEN = BooleanProperty.create("open");

	public ReactorControllerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(OPEN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(OPEN);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReactorControllerBlockEntity(pos, state);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		// re-placing the controller re-forms the structure automatically
		if (oldState.getBlock() == state.getBlock() || moved || level.isClientSide) {
			return;
		}
		if (level.getBlockEntity(pos) instanceof ReactorControllerBlockEntity controller && !controller.isAssembled()) {
			controller.tryAssemble();
		}
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		// breaking the controller itself spills the contents at its own position
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof ReactorControllerBlockEntity controller) {
				controller.invalidateStructure(pos);
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		// tick on both sides: the server runs the reaction/spill engine, the client
		// chases the fluid-surface animation (renderedLevel). The BE tick() branches on side.
		return (lvl, pos, st, be) -> {
			if (be instanceof ReactorControllerBlockEntity controller) {
				controller.tick();
			}
		};
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof ReactorControllerBlockEntity controller) {
			if (controller.isAssembled()) {
				ItemStack held = player.getItemInHand(hand);

				// fluid buckets/containers: pour into or fill from the vessel
				// (Create Basin behaviour, wired to the vessel's multi-fluid tank)
				if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, held, controller)) {
					return InteractionResult.SUCCESS;
				}
				if (FluidHelper.tryFillItemFromBE(level, player, hand, held, controller)) {
					return InteractionResult.SUCCESS;
				}
				if (GenericItemEmptying.canItemBeEmptied(level, held) || GenericItemFilling.canItemBeFilled(level, held)) {
					return InteractionResult.SUCCESS; // fluid container handled above
				}

				// held item, not sneaking: toss it into the vessel (in-world "throw"
				// replacement — the closed shell cannot catch item entities)
				if (!held.isEmpty() && !player.isShiftKeyDown()) {
					ItemStack remainder = ItemHandlerHelper.insertItemStacked(controller.getItems(), held.copy(), false);
					int inserted = held.getCount() - remainder.getCount();
					if (inserted > 0) {
						held.shrink(inserted);
						player.setItemInHand(hand, held);
						level.playSound((Player) null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.2f);
						return InteractionResult.SUCCESS;
					}
					return InteractionResult.SUCCESS; // buffer full: swallow the click
				}

				if (held.isEmpty()) {
					// empty hand: take the vessel's items back (Basin behaviour)
					boolean success = false;
					for (int i = 0; i < controller.getItems().getSlots(); i++) {
						ItemStack stack = controller.getItems().getStackInSlot(i);
						if (!stack.isEmpty()) {
							player.getInventory().placeItemBackInInventory(stack);
							controller.getItems().setStackInSlot(i, ItemStack.EMPTY);
							success = true;
						}
					}
					if (success) {
						level.playSound((Player) null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.2f);
					}
					return InteractionResult.SUCCESS;
				}

				// sneaking with an item in hand: report the diagnostic status
				ReactorControllerBlockEntity.ReactorStatus status = controller.getStatus();
				ChatFormatting color = switch (status) {
					case REACTING -> ChatFormatting.GREEN;
					case TEMPERATURE -> ChatFormatting.GOLD;
					case OUTPUT_FULL, NOT_ASSEMBLED -> ChatFormatting.RED;
					case NO_RECIPE -> ChatFormatting.GRAY;
				};
				player.displayClientMessage(Component.translatable("goggles.chemicaladdon.status")
					.append(Component.translatable("status.chemicaladdon." + status.name().toLowerCase()))
					.withStyle(color), false);
			} else {
				AssembleResult result = controller.tryAssemble();
				if (result.ok()) {
					player.displayClientMessage(Component.translatable("assemble.chemicaladdon.ok"), false);
					// celebratory feedback in the world
					level.playSound((Player) null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0f, 1.2f);
					if (level instanceof ServerLevel serverLevel) {
						for (int i = 0; i < 12; i++) {
							serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
								pos.getX() + 0.5 + level.random.nextGaussian() * 1.6,
								pos.getY() + 0.5 + level.random.nextGaussian() * 1.2,
								pos.getZ() + 0.5 + level.random.nextGaussian() * 1.6, 1, 0, 0, 0, 0);
						}
					}
				} else {
					player.displayClientMessage(buildFailureMessage(result), false);
					level.playSound((Player) null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 0.7f);
				}
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** "§c结构不完整（北侧）：壁层缺少化工砖 @ (x,y,z)" */
	private Component buildFailureMessage(AssembleResult result) {
		AssembleIssue issue = result.issue() != null ? result.issue() : AssembleIssue.TOO_SHORT;
		String issueKey = "assemble.chemicaladdon." + issue.name().toLowerCase();
		Direction face = result.face() != null ? result.face() : Direction.NORTH;
		String faceKey = "assemble.chemicaladdon." + face.name().toLowerCase() + "_side";
		Component issueText = Component.translatable(issueKey);
		if (result.issuePos() != null) {
			BlockPos p = result.issuePos();
			issueText = Component.translatable(issueKey)
				.copy()
				.append(Component.literal(" @ (" + p.getX() + "," + p.getY() + "," + p.getZ() + ")"));
		}
		return Component.translatable("assemble.chemicaladdon.fail", Component.translatable(faceKey), issueText);
	}
}
