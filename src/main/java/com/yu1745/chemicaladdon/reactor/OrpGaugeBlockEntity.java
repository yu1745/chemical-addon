package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.composition.parity.EngineReadings;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Truthful pe transmitter. Values are centi-pe so the generic integer gauge remains exact. */
public class OrpGaugeBlockEntity extends AbstractVesselGaugeBlockEntity implements IMasterBound {
	@Nullable private BlockPos masterPos;
	public OrpGaugeBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntities.ORP_GAUGE.get(),pos,state);}
	@Override protected String thresholdLabelKey(){return "orp_gauge.chemicaladdon.maximum";}
	@Override protected String thresholdUnit(){return " pe×0.01";}
	@Override protected int thresholdStep(){return 100;}
	@Override protected int thresholdMaxSteps(){return 31;}
	@Override protected int thresholdMilestoneSteps(){return 5;}
	@Override protected int defaultThresholdSteps(){return 14;}
	@Override protected int ambientValue(){return 0;}
	@Override protected int analogZero(){return 0;}
	@Override protected int needleTint(){return 0xffa050c0;}
	@Override protected float dialOffset(){return .5f;}
	@Override protected boolean isValueBoxSide(BlockState state,Direction side){return true;}
	@Override protected ReactorControllerBlockEntity findReactor(){BlockEntity be=getValidMaster();return be instanceof ReactorControllerBlockEntity r?r:null;}
	@Override protected int readValue(ReactorControllerBlockEntity reactor){EngineReadings.Snapshot s=EngineReadings.peek(reactor.getBlockPos());return s.valid?(int)Math.round(s.pe*100):0;}
	@Override public void setMaster(@Nullable BlockPos pos){masterPos=pos;setChanged();sendData();}
	@Override @Nullable public BlockPos getMasterPos(){return masterPos;}
	@Override @Nullable public BlockEntity getValidMaster(){if(level==null||masterPos==null)return null;BlockEntity be=level.getBlockEntity(masterPos);return be instanceof VesselBlockEntity v&&v.isAssembled()?be:null;}
	@Override protected void write(CompoundTag tag,boolean clientPacket){super.write(tag,clientPacket);if(masterPos!=null)tag.putLong("masterPos",masterPos.asLong());}
	@Override protected void read(CompoundTag tag,boolean clientPacket){super.read(tag,clientPacket);masterPos=tag.contains("masterPos")?BlockPos.of(tag.getLong("masterPos")):null;}
	@Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){tooltip.add(Component.literal(" ").append(getBlockState().getBlock().getName()));tooltip.add(Component.translatable("goggles.chemicaladdon.orp",getValue()/100.0).withStyle(ChatFormatting.LIGHT_PURPLE));tooltip.add(Component.translatable("goggles.chemicaladdon.range",getMinimum()/100.0,getThreshold()/100.0).withStyle(ChatFormatting.GRAY));return true;}
}
