package com.yu1745.chemicaladdon.control;

import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Six independently configurable redstone faces of a PLC rack I/O module. */
public class PlcIoBlockEntity extends BlockEntity implements IHaveGoggleInformation {
	public enum Mode { DISABLED, INPUT, OUTPUT }
	private final Mode[] modes = new Mode[6];
	private final int[] channels = new int[6];
	private final int[] outputImage = new int[6];

	public PlcIoBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.PLC_IO.get(), pos, state);
		java.util.Arrays.fill(modes, Mode.DISABLED);
	}

	public Mode mode(Direction face) { return modes[face.ordinal()]; }
	public int channel(Direction face) { return channels[face.ordinal()]; }
	public void cycleMode(Direction face) {
		int i=face.ordinal(); modes[i]=Mode.values()[(modes[i].ordinal()+1)%Mode.values().length]; changedAndNotify();
	}
	public void nextChannel(Direction face) { int i=face.ordinal(); channels[i]=(channels[i]+1)%PlcMachine.CHANNELS; changedAndNotify(); }
	public int readInput(Direction face) {
		if (level == null || mode(face) != Mode.INPUT) return 0;
		BlockPos neighbour = worldPosition.relative(face);
		return ControlSignal.clamp(level.getSignal(neighbour, face));
	}
	public void writeOutput(Direction face, int value) {
		int i=face.ordinal(); int next=mode(face)==Mode.OUTPUT?ControlSignal.clamp(value):0;
		if(outputImage[i]!=next){outputImage[i]=next; if(level!=null)level.updateNeighborsAt(worldPosition,getBlockState().getBlock()); changedAndNotify();}
	}
	public int output(Direction face) { return mode(face)==Mode.OUTPUT?outputImage[face.ordinal()]:0; }

	private void changedAndNotify(){setChanged(); if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
	@Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);for(Direction d:Direction.values()){tag.putByte("mode"+d.ordinal(),(byte)mode(d).ordinal());tag.putInt("channel"+d.ordinal(),channel(d));}}
	@Override public void load(CompoundTag tag){super.load(tag);for(Direction d:Direction.values()){int i=d.ordinal();modes[i]=Mode.values()[Math.max(0,Math.min(2,tag.getByte("mode"+i)))];channels[i]=Math.floorMod(tag.getInt("channel"+i),PlcMachine.CHANNELS);}}
	@Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
	@Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking){
		tooltip.add(Component.translatable("goggles.chemicaladdon.plc_io"));
		for(Direction d:Direction.values())if(mode(d)!=Mode.DISABLED)tooltip.add(Component.literal(d.getName()+": "+mode(d)+" C"+channel(d)));
		return true;
	}
}
