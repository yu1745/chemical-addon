package com.yu1745.chemicaladdon.control;

import java.util.Arrays;

/** Server-authoritative PLC process image shared by assembly and JavaScript. */
public final class PlcMachine {
	public static final int CHANNELS = 64;
	public static final int REGISTERS = 32;
	private final int[] inputs = new int[CHANNELS];
	private final int[] outputs = new int[CHANNELS];
	private final int[] registers = new int[REGISTERS];
	private final int[] timers = new int[REGISTERS];
	private final boolean[] previous = new boolean[CHANNELS];
	private PlcFault fault = PlcFault.NONE;

	public void beginScan(int[] image) {
		Arrays.fill(outputs, 0); // fail-safe output image: unwritten outputs are off
		for (int i = 0; i < CHANNELS; i++) inputs[i] = ControlSignal.clamp(i < image.length ? image[i] : 0);
		fault = PlcFault.NONE;
	}

	public void finishScan() {
		for (int i = 0; i < CHANNELS; i++) previous[i] = inputs[i] > 0;
	}

	public int input(int channel) { checkChannel(channel); return inputs[channel]; }
	public int output(int channel) { checkChannel(channel); return outputs[channel]; }
	public void output(int channel, int value) { checkChannel(channel); outputs[channel] = ControlSignal.clamp(value); }
	public int register(int index) { checkRegister(index); return registers[index]; }
	public void register(int index, int value) { checkRegister(index); registers[index] = value; }
	public boolean rising(int channel) { checkChannel(channel); return inputs[channel] > 0 && !previous[channel]; }

	/** TON timer, measured in PLC scans. */
	public boolean timer(int index, boolean enabled, int preset) {
		checkRegister(index);
		if (!enabled) timers[index] = 0;
		else if (timers[index] < Math.max(1, preset)) timers[index]++;
		return timers[index] >= Math.max(1, preset);
	}

	/** Retriggerable pulse, measured in PLC scans. */
	public boolean pulse(int index, boolean trigger, int duration) {
		checkRegister(index);
		if (trigger) timers[index] = Math.max(1, duration);
		boolean active = timers[index] > 0;
		if (active) timers[index]--;
		return active;
	}

	public int[] outputs() { return outputs.clone(); }
	public int[] registers() { return registers.clone(); }
	public int[] timers() { return timers.clone(); }
	public void restore(int[] savedRegisters, int[] savedTimers) {
		System.arraycopy(savedRegisters, 0, registers, 0, Math.min(savedRegisters.length, REGISTERS));
		System.arraycopy(savedTimers, 0, timers, 0, Math.min(savedTimers.length, REGISTERS));
	}
	public PlcFault fault() { return fault; }
	public void fault(PlcFault fault) { this.fault = fault; Arrays.fill(outputs, 0); }

	private static void checkChannel(int i) { if (i < 0 || i >= CHANNELS) throw new IllegalArgumentException("channel " + i); }
	private static void checkRegister(int i) { if (i < 0 || i >= REGISTERS) throw new IllegalArgumentException("register " + i); }
}
