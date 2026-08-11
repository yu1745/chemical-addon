package com.yu1745.chemicaladdon.reactor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

/**
 * Reaction vessel control panel (client). M1: displays structure state,
 * temperature and the contents of the multi-fluid tank.
 */
public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {

	public ReactorScreen(ReactorMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 176;
		this.imageHeight = 120;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = (width - imageWidth) / 2;
		int y = (height - imageHeight) / 2;
		graphics.fill(x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, 0xFF808080);
		graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF1E1E1E);

		if (minecraft == null || minecraft.level == null) {
			return;
		}
		BlockEntity be = minecraft.level.getBlockEntity(menu.getBlockPos());
		if (!(be instanceof ReactorControllerBlockEntity reactor)) {
			return;
		}

		int ty = y + 8;
		graphics.drawString(font, "Chemical Addon Reactor", x + 8, ty, 0xFFFFFF);
		ty += 12;
		graphics.drawString(font, "Structure: " + (reactor.isAssembled() ? "§aAssembled" : "§cNot assembled"), x + 8, ty, 0xFFFFFF);
		ty += 12;
		int temp = reactor.getTemperature();
		int tempColor = temp > 500 ? 0xFF5050 : temp > 200 ? 0xFFB050 : 0xFFFFFF;
		graphics.drawString(font, "Temperature: " + temp + " °C", x + 8, ty, tempColor);
		ty += 12;
		graphics.drawString(font, "Contents (" + reactor.getTank().getTotalAmount() + " mB):", x + 8, ty, 0xFFFFFF);
		ty += 11;
		for (FluidStack stack : reactor.getTank().getFluids()) {
			if (ty > y + imageHeight - 12) {
				break;
			}
			graphics.drawString(font,
				"  - " + stack.getDisplayName().getString() + ": " + stack.getAmount() + " mB",
				x + 8, ty, 0xAAAAAA);
			ty += 10;
		}
		graphics.drawString(font, "Items:", x + 8, ty, 0xFFFFFF);
		ty += 11;
		for (int i = 0; i < reactor.getItems().getSlots(); i++) {
			if (ty > y + imageHeight - 12) {
				break;
			}
			ItemStack stack = reactor.getItems().getStackInSlot(i);
			if (!stack.isEmpty()) {
				graphics.drawString(font,
					"  - " + stack.getHoverName().getString() + " x" + stack.getCount(),
					x + 8, ty, 0xAAAAAA);
				ty += 10;
			}
		}
		// reaction progress
		if (reactor.getActiveRecipe() != null) {
			graphics.drawString(font,
				"Reacting: " + reactor.getActiveRecipe().getPath() + " (" + (int) (reactor.getProgress() * 100) + "%)",
				x + 8, ty, 0x50FF50);
		} else {
			graphics.drawString(font, "Idle", x + 8, ty, 0x808080);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
