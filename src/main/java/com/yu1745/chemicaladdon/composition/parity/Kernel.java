package com.yu1745.chemicaladdon.composition.parity;

import com.yu1745.chemengine.kernel.IPhreeqc;

/**
 * 共享内核会话：sit.dat（460 KB）的装载解析是内核调用里最贵的部分，
 * 每 JVM 只做一次。主循环（tick 步进/存档/读数）全部复用本会话——
 * MC 服务端 tick 单线程，且 IPhreeqc 实例方法内部 synchronized，
 * 共享安全；实例不主动销毁（原生库本就驻留 JVM 生命周期）。
 *
 * <p>不复用会话的后果（2026-08 切换初版实测）：每反应拍 create+装库+求解+销毁，
 * GameTest 从 ~1 分钟劣化到 5–10 分钟。
 */
final class Kernel {

	private static volatile IPhreeqc session;

	private Kernel() {}

	static synchronized IPhreeqc get() {
		if (session == null) {
			session = IPhreeqc.create();
		}
		return session;
	}
}
