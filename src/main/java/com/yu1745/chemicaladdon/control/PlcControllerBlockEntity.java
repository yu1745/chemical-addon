package com.yu1745.chemicaladdon.control;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** PLC controller: scans an adjacent rack, then executes one of two program modes. */
public class PlcControllerBlockEntity extends BlockEntity implements MenuProvider, IHaveGoggleInformation {
	public enum ProgramMode { INSTRUCTION, JAVASCRIPT }
	private final PlcMachine machine=new PlcMachine();
	private ProgramMode programMode=ProgramMode.INSTRUCTION;
	private String source="";
	private boolean running;
	private PlcFault fault=PlcFault.STOPPED;
	private String error="";
	@Nullable private PlcAssemblyProgram assembly;
	@Nullable private PlcJavaScriptProgram javascript;

	public PlcControllerBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntities.PLC_CONTROLLER.get(),pos,state);}

	public static void tick(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,PlcControllerBlockEntity plc){if(!level.isClientSide)plc.serverTick();}
	private void serverTick(){
		Rack rack=discoverRack();
		if(rack.controllers>1){fail(PlcFault.MULTIPLE_CONTROLLERS,"multiple controllers");return;}
		if(rack.duplicate){fail(PlcFault.DUPLICATE_CHANNEL,"duplicate channel");return;}
		if(!running){zero(rack);fault=PlcFault.STOPPED;return;}
		if((programMode==ProgramMode.INSTRUCTION&&assembly==null)||(programMode==ProgramMode.JAVASCRIPT&&javascript==null)){fail(PlcFault.NO_PROGRAM,"program is not compiled");zero(rack);return;}
		int[] input=new int[PlcMachine.CHANNELS];
		for(Endpoint e:rack.endpoints)if(e.mode==PlcIoBlockEntity.Mode.INPUT)input[e.channel]=Math.max(input[e.channel],e.module.readInput(e.face));
		machine.beginScan(input);
		try{if(programMode==ProgramMode.INSTRUCTION)assembly.scan(machine);else javascript.scan(machine);}catch(RuntimeException ex){machine.fault(PlcFault.RUNTIME_ERROR);error=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();}
		machine.finishScan();fault=machine.fault();
		for(Endpoint e:rack.endpoints)if(e.mode==PlcIoBlockEntity.Mode.OUTPUT)e.module.writeOutput(e.face,fault==PlcFault.NONE?machine.output(e.channel):0);
		setChanged();
	}

	public boolean applyProgram(ProgramMode mode,String newSource,boolean start){
		if(running){error="stop PLC before editing";return false;}
		try{PlcAssemblyProgram a=null;PlcJavaScriptProgram j=null;if(mode==ProgramMode.INSTRUCTION)a=PlcAssemblyProgram.compile(newSource);else j=PlcJavaScriptProgram.compile(newSource);programMode=mode;source=newSource;assembly=a;javascript=j;running=start;fault=start?PlcFault.NONE:PlcFault.STOPPED;error="";setChanged();sync();return true;}catch(RuntimeException ex){fault=PlcFault.COMPILE_ERROR;error=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();running=false;setChanged();sync();return false;}
	}
	public void setRunning(boolean value){if(value&&(programMode==ProgramMode.INSTRUCTION?assembly:javascript)==null){fault=PlcFault.NO_PROGRAM;return;}running=value;fault=value?PlcFault.NONE:PlcFault.STOPPED;setChanged();sync();}
	private void fail(PlcFault f,String message){fault=f;error=message;machine.fault(f);zero(discoverRack());setChanged();}
	private void zero(Rack rack){for(Endpoint e:rack.endpoints)if(e.mode==PlcIoBlockEntity.Mode.OUTPUT)e.module.writeOutput(e.face,0);}

	private Rack discoverRack(){
		Set<BlockPos> seen=new HashSet<>();ArrayDeque<BlockPos> open=new ArrayDeque<>();List<Endpoint>endpoints=new ArrayList<>();Set<Integer>used=new HashSet<>();boolean duplicate=false;int controllers=0;
		open.add(worldPosition);seen.add(worldPosition);
		while(!open.isEmpty()&&seen.size()<=256){BlockPos p=open.removeFirst();BlockEntity be=level.getBlockEntity(p);if(be instanceof PlcControllerBlockEntity)controllers++;if(be instanceof PlcIoBlockEntity io)for(Direction face:Direction.values())if(io.mode(face)!=PlcIoBlockEntity.Mode.DISABLED){int c=io.channel(face);if(!used.add(c))duplicate=true;endpoints.add(new Endpoint(io,face,c,io.mode(face)));}for(Direction d:Direction.values()){BlockPos n=p.relative(d);if(seen.contains(n))continue;BlockEntity next=level.getBlockEntity(n);if(next instanceof PlcIoBlockEntity||next instanceof PlcControllerBlockEntity){seen.add(n);open.add(n);}}}
		return new Rack(endpoints,duplicate,controllers);
	}
	private record Endpoint(PlcIoBlockEntity module,Direction face,int channel,PlcIoBlockEntity.Mode mode){}
	private record Rack(List<Endpoint>endpoints,boolean duplicate,int controllers){}

	public String source(){return source;} public ProgramMode programMode(){return programMode;} public boolean running(){return running;} public PlcFault fault(){return fault;} public String error(){return error;}
	@Override public Component getDisplayName(){return Component.translatable("block.chemicaladdon.plc_controller");}
	@Nullable @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new PlcMenu(id,inv,this);}
	private void sync(){if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
	@Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putString("source",source);tag.putString("programMode",programMode.name());tag.putBoolean("running",running);tag.putString("fault",fault.name());tag.putString("error",error);tag.putIntArray("registers",machine.registers());tag.putIntArray("timers",machine.timers());}
	@Override public void load(CompoundTag tag){super.load(tag);source=tag.getString("source");try{programMode=ProgramMode.valueOf(tag.getString("programMode"));}catch(Exception ignored){}running=tag.getBoolean("running");try{fault=PlcFault.valueOf(tag.getString("fault"));}catch(Exception ignored){}error=tag.getString("error");machine.restore(tag.getIntArray("registers"),tag.getIntArray("timers"));if(!source.isBlank()){boolean wanted=running;running=false;applyProgram(programMode,source,wanted);}}
	@Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
	@Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){tooltip.add(Component.translatable("goggles.chemicaladdon.plc",programMode.name(),running?"RUN":"STOP"));tooltip.add(Component.translatable("goggles.chemicaladdon.plc_fault",fault.name()));if(!error.isBlank())tooltip.add(Component.literal(error));return true;}
}
