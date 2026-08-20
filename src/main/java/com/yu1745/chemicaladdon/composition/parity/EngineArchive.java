package com.yu1745.chemicaladdon.composition.parity;

import java.util.List;

import com.yu1745.chemengine.kernel.ChemState;
import com.yu1745.chemengine.kernel.IPhreeqc;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/**
 * P3b 存档桥：釜的内核态权威快照（DUMP ↔ NBT）。
 *
 * <p>语义（引擎 G1c 定谳）：{@code archive()} = 先平衡再 DUMP SOLUTION_RAW
 * 全精度文本；恢复用原始 dump 文本（不重算、零漂移、池分布原样）。
 * 存档字段挂在釜 NBT 的 {@code chemengineDump} 字符串。
 *
 * <p>当前阶段：写档时机 = VesselBlockEntity.write 且 ENGINE_READINGS 开启；
 * 恢复 = read 时读回缓存字符串（供 {@link #peekDump}/后续 KINETICS 步进使用），
 * <b>不</b>回写 Mixture（Mixture 四域仍是游戏的显示/交互权威，P4 决定翻转点）。
 */
public final class EngineArchive {

	/** NBT key：DUMP SOLUTION_RAW 文本（全精度）。 */
	public static final String KEY = "chemengineDump";

	private EngineArchive() {}

	/** 有有效进料时返回 dump 文本；空釜/失败返回 null（不写档）。 */
	public static String archiveOf(List<FluidStack> fluids) {
		EngineBridge.Feed feed = EngineBridge.toFeed(fluids);
		if (feed.waterKg <= 0 || feed.totals.isEmpty()) {
			return null;
		}
		ChemState.Builder b = ChemState.builder("vessel")
				.waterKg(feed.waterKg)
				.pHCharge()
				.tempC(feed.tempC);
		boolean any = false;
		for (java.util.Map.Entry<String, Double> e : feed.totals.entrySet()) {
			if (e.getValue() > 0) {
				b.total(e.getKey(), e.getValue());
				any = true;
			}
		}
		if (!any) {
			return null;
		}
		try (IPhreeqc q = IPhreeqc.create()) {
			return q.archive(b.build());
		} catch (Exception e) {
			return null;
		}
	}

	/** 写档侧：ENGINE_READINGS 开启且有有效进料时挂 dump 字符串。 */
	public static void write(CompoundTag tag, List<FluidStack> fluids) {
		if (!ChemEngineConfig.ENGINE_READINGS) {
			return;
		}
		String dump = archiveOf(fluids);
		if (dump != null) {
			tag.putString(KEY, dump);
		}
	}

	/** 读档侧：读回 dump 缓存（存在性即"此档有内核态"标记）。 */
	public static String read(CompoundTag tag) {
		return tag.contains(KEY) ? tag.getString(KEY) : null;
	}
}
