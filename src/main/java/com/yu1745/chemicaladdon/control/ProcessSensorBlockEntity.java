package com.yu1745.chemicaladdon.control;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.reactor.GasDistributorBlockEntity;
import com.yu1745.chemicaladdon.reactor.MeteringInletBlockEntity;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

/** Shared implementation for the flow and settled-solid bed transmitters. */
public class ProcessSensorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
	private ScrollValueBehaviour minimum,maximum;
	private int reading,signal,previousCounter;
	public ProcessSensorBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntities.PROCESS_SENSOR.get(),pos,state);}
	private boolean flow(){return getBlockState().is(AllBlocks.FLOW_METER.get());}
	private int step(){return flow()?10:100;}
	@Override public void addBehaviours(List<BlockEntityBehaviour> list){minimum=make("gauge.chemicaladdon.minimum",slot(.28));maximum=make("gauge.chemicaladdon.maximum",slot(.72));minimum.value=0;maximum.value=flow()?100:160;list.add(minimum);list.add(maximum);}
	private CenteredSideValueBoxTransform slot(double y){return new CenteredSideValueBoxTransform((s,d)->true){@Override public Vec3 getLocalOffset(LevelAccessor level,BlockPos pos,BlockState state){Direction side=getSide();return new Vec3(.5+side.getStepX()*.51,y,.5+side.getStepZ()*.51);}};}
	private ScrollValueBehaviour make(String key,CenteredSideValueBoxTransform slot){ScrollValueBehaviour b=new ScrollValueBehaviour(Component.translatable(key),this,slot){@Override public ValueSettingsBoard createBoard(net.minecraft.world.entity.player.Player p,net.minecraft.world.phys.BlockHitResult h){return new ValueSettingsBoard(label,1001,100,ImmutableList.of(Component.literal("mB")),new ValueSettingsFormatter(v->Component.literal(v.value()*step()+" mB")));}};b.between(0,1001);b.withFormatter(i->i*step()+" mB");return b;}
	@Override public void tick(){super.tick();if(level==null||level.isClientSide)return;int next=sample();int mapped=next<0?0:ControlSignal.analog(next,minimum.getValue()*step(),maximum.getValue()*step());if(next!=reading||mapped!=signal){reading=Math.max(0,next);signal=mapped;setChanged();sendData();level.updateNeighborsAt(worldPosition,getBlockState().getBlock());}}
	private int sample(){for(Direction d:Direction.values()){BlockEntity be=level.getBlockEntity(worldPosition.relative(d));if(flow()){if(be instanceof GasDistributorBlockEntity gas)return gas.hasRecentFlow()?gas.getRecentFlowMb():0;if(be instanceof MeteringInletBlockEntity inlet){int now=inlet.getAdmittedMb();int delta=Math.max(0,now-previousCounter);previousCounter=now;return delta;}}else{VesselBlockEntity vessel=vessel(be);if(vessel!=null)return(int)(vessel.getTank().sedimentUnits()/Chemistry.UNIT_PER_MB);}}return-1;}
	@Nullable private static VesselBlockEntity vessel(BlockEntity be){if(be instanceof VesselBlockEntity v&&v.isAssembled())return v;if(be instanceof IMasterBound b&&b.getValidMaster() instanceof VesselBlockEntity v)return v;return null;}
	public int signal(){return signal;}
	@Override protected void write(CompoundTag tag,boolean client){super.write(tag,client);tag.putInt("reading",reading);tag.putInt("signal",signal);tag.putInt("previousCounter",previousCounter);}
	@Override protected void read(CompoundTag tag,boolean client){super.read(tag,client);reading=tag.getInt("reading");signal=tag.getInt("signal");previousCounter=tag.getInt("previousCounter");}
	@Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){tooltip.add(getBlockState().getBlock().getName());tooltip.add(Component.translatable(flow()?"goggles.chemicaladdon.flow":"goggles.chemicaladdon.solid_bed",reading));tooltip.add(Component.translatable("detector.chemicaladdon.signal",signal));return true;}
}
