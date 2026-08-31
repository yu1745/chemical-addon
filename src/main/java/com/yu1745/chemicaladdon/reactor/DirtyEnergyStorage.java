package com.yu1745.chemicaladdon.reactor;

import net.minecraftforge.energy.EnergyStorage;

/** Energy storage whose real mutations participate in BE persistence/sync. */
public class DirtyEnergyStorage extends EnergyStorage {

	private final Runnable onChanged;

	public DirtyEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable onChanged) {
		super(capacity, maxReceive, maxExtract);
		this.onChanged = onChanged;
	}

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		int received = super.receiveEnergy(maxReceive, simulate);
		if (!simulate && received > 0) {
			onChanged.run();
		}
		return received;
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		int extracted = super.extractEnergy(maxExtract, simulate);
		if (!simulate && extracted > 0) {
			onChanged.run();
		}
		return extracted;
	}

	/** Restore an absolute saved value; unlike receiveEnergy this never accumulates packets. */
	public void setEnergyStored(int value) {
		int clamped = Math.max(0, Math.min(capacity, value));
		if (energy != clamped) {
			energy = clamped;
			onChanged.run();
		}
	}
}
