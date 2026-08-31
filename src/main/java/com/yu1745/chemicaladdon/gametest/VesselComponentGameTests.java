package com.yu1745.chemicaladdon.gametest;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Home for vessel shell parts, gauges, ports, and in-world diagnostics. */
@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class VesselComponentGameTests {
	private static final int TICKS = 20;

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void glassPassesBlockLightLikeVanilla(GameTestHelper helper) {
		BlockPos torch = new BlockPos(1, 2, 1);
		BlockPos wall = new BlockPos(2, 2, 1);
		BlockPos behind = new BlockPos(3, 2, 1);
		helper.setBlock(torch, Blocks.TORCH.defaultBlockState());
		helper.setBlock(wall, AllBlocks.CHEMICAL_GLASS.get().defaultBlockState());
		helper.runAfterDelay(2, () -> {
			int chemical = helper.getLevel().getBrightness(LightLayer.BLOCK, behind);
			helper.setBlock(wall, Blocks.GLASS.defaultBlockState());
			helper.runAfterDelay(2, () -> {
				int vanilla = helper.getLevel().getBrightness(LightLayer.BLOCK, behind);
				helper.assertTrue(chemical == vanilla,
					"chemical glass must pass block light like vanilla glass (chemical=" + chemical + ", vanilla=" + vanilla + ")");
				helper.succeed();
			});
		});
	}
}
