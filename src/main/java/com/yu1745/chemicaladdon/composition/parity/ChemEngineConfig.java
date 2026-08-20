package com.yu1745.chemicaladdon.composition.parity;

/**
 * 内核整合的阶段开关（系统属性，默认保守）。
 *
 * <ul>
 *   <li>{@code chemengine.parity}=1 —— P2 双跑观察（日志对照，默认关）</li>
 *   <li>{@code chemengine.readings}=engine —— P3 pH 表计读数源切内核
 *       （legacy = 原 Analyte.ph 路径，默认）</li>
 * </ul>
 *
 * <p>系统属性而非 config 文件：这些是迁移期开关，切稳后删除；不值得进
 * player-visible 配置面。
 */
public final class ChemEngineConfig {

	private ChemEngineConfig() {}

	/** 双跑观察（P2）。 */
	public static final boolean PARITY_OBSERVE = Boolean.getBoolean("chemengine.parity");

	/** pH 读数走内核（P3）：legacy | engine。 */
	public static final boolean ENGINE_READINGS =
			"engine".equalsIgnoreCase(System.getProperty("chemengine.readings", "legacy"));

	/** KINETICS 主循环接线（P5）：开=每 REACTION_TICK 内核步进+写回（与 RulesEngine 双跑）。 */
	public static final boolean ENGINE_KINETICS =
			"on".equalsIgnoreCase(System.getProperty("chemengine.kinetics", "off"));
}
