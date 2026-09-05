package com.yu1745.chemicaladdon.control;

import java.util.List;
import java.util.Map;

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
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.ChemFluidType;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.item.TestPaperItem;
import com.yu1745.chemicaladdon.reactor.AbstractPhGaugeBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Reusable cartridge host that maps one paper-defined analyte to live-zero redstone. */
public class TestPaperDetectorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
	private ItemStack paper=ItemStack.EMPTY;
	private ScrollValueBehaviour minimum;
	private ScrollValueBehaviour maximum;
	private int signal;
	private double reading;
	private boolean attached;

	public TestPaperDetectorBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntities.TEST_PAPER_DETECTOR.get(),pos,state);}
	@Override public void addBehaviours(List<BlockEntityBehaviour> behaviours){minimum=rangeBehaviour("detector.chemicaladdon.minimum",.28);maximum=rangeBehaviour("detector.chemicaladdon.maximum",.72);minimum.value=1;maximum.value=1000;behaviours.add(minimum);behaviours.add(maximum);}
	private ScrollValueBehaviour rangeBehaviour(String key,double y){CenteredSideValueBoxTransform slot=new CenteredSideValueBoxTransform((s,d)->true){@Override public Vec3 getLocalOffset(LevelAccessor level,BlockPos pos,BlockState state){Direction side=getSide();return new Vec3(.5+side.getStepX()*.51,y,.5+side.getStepZ()*.51);}};ScrollValueBehaviour b=new ScrollValueBehaviour(Component.translatable(key),this,slot){@Override public ValueSettingsBoard createBoard(Player p,BlockHitResult h){return new ValueSettingsBoard(label,10001,1000,ImmutableList.of(Component.literal("‰")),new ValueSettingsFormatter(v->Component.literal(v.value()+"‰")));}};b.between(0,10001);b.withFormatter(i->i+"‰");return b;}
	@Override public void tick(){super.tick();if(level==null||level.isClientSide)return;ReactorControllerBlockEntity reactor=findReactor();attached=reactor!=null;if(!attached||!(paper.getItem() instanceof TestPaperItem item)){update(0,Double.NaN);return;}double nextReading=read(item.kind(),reactor);int nextSignal=signalFor(item.kind(),nextReading);update(nextSignal,nextReading);}
	private void update(int next,double value){if(next!=signal||value!=reading){signal=next;reading=value;setChanged();sendData();level.updateNeighborsAt(worldPosition,getBlockState().getBlock());}}
	@Nullable private ReactorControllerBlockEntity findReactor(){for(Direction d:Direction.values()){BlockEntity be=level.getBlockEntity(worldPosition.relative(d));if(be instanceof ReactorControllerBlockEntity r&&r.isAssembled())return r;if(be instanceof IMasterBound bound&&bound.getValidMaster() instanceof ReactorControllerBlockEntity r)return r;}return null;}
	private static double read(TestPaperItem.Kind kind,ReactorControllerBlockEntity reactor){
		if(kind==TestPaperItem.Kind.LITMUS||kind==TestPaperItem.Kind.PHENOLPHTHALEIN||kind==TestPaperItem.Kind.WIDE_PH)return AbstractPhGaugeBlockEntity.phOf(reactor.getTank(),reactor.getBlockPos());
		if(isGas(kind))return gasPermille(kind,reactor.getTank());
		long target=0,water=0;for(FluidStack stack:reactor.getTank().getFluids())if(Mixture.isMixture(stack)){Map<String,Integer>ions=Mixture.deriveUnitIonAmounts(stack);water+=Mixture.deriveUnitAmounts(stack).getOrDefault(Solution.WATER,0);target+=switch(kind){case SILVER_NITRATE->ions.getOrDefault("Cl-1",0);case BARIUM_CHLORIDE->ions.getOrDefault("SO4-2",0);case POTASSIUM_THIOCYANATE->ions.getOrDefault("Fe+3",0);case HYPOCHLORITE->ions.getOrDefault("ClO-1",0);case SULFITE->ions.getOrDefault("SO3-2",0);case AMMONIUM->ions.getOrDefault("NH4+1",0);case NITRATE_NITRITE->ions.getOrDefault("NO3-1",0)+ions.getOrDefault("NO2-1",0);case FERROUS_FERRIC->ions.getOrDefault("Fe+2",0)+ions.getOrDefault("Fe+3",0);case HARDNESS->ions.getOrDefault("Ca+2",0)+ions.getOrDefault("Mg+2",0);default->0;};}return water<=0?Double.NaN:target*1000.0/water;
	}
	private int signalFor(TestPaperItem.Kind kind,double value){
		if(!Double.isFinite(value))return 0;
		if(kind==TestPaperItem.Kind.LITMUS)return value<6.5?1:value>7.5?15:8;
		if(kind==TestPaperItem.Kind.PHENOLPHTHALEIN)return ControlSignal.digital(value>=8.2);
		if(kind==TestPaperItem.Kind.WIDE_PH)return ControlSignal.analog(value,minimum.getValue()/100.0,maximum.getValue()/100.0);
		if(kind==TestPaperItem.Kind.FERROUS_FERRIC||kind==TestPaperItem.Kind.NITRATE_NITRITE)return value<=minimum.getValue()?1:value>=maximum.getValue()?15:8;
		return isLogarithmic(kind)?ControlSignal.logarithmic(value,minimum.getValue(),maximum.getValue()):ControlSignal.analog(value,minimum.getValue(),maximum.getValue());
	}
	private static double gasPermille(TestPaperItem.Kind kind,ReactorTank tank){long total=0,target=0;for(FluidStack stack:tank.getFluids())if(stack.getFluid().getFluidType() instanceof ChemFluidType type&&type.isGas()){total+=stack.getAmount();ResourceLocation id=ForgeRegistries.FLUIDS.getKey(stack.getFluid());String path=id==null?"":id.getPath();boolean hit=switch(kind){case AMMONIA_GAS->path.equals("ammonia");case SULFUR_DIOXIDE_GAS->path.equals("sulfur_dioxide");case CHLORINE_GAS->path.equals("chlorine");case NOX_GAS->path.equals("nitric_oxide")||path.equals("nitrogen_dioxide");default->false;};if(hit)target+=stack.getAmount();}return total<=0?Double.NaN:target*1000.0/total;}
	private static boolean isGas(TestPaperItem.Kind k){return k==TestPaperItem.Kind.AMMONIA_GAS||k==TestPaperItem.Kind.SULFUR_DIOXIDE_GAS||k==TestPaperItem.Kind.CHLORINE_GAS||k==TestPaperItem.Kind.NOX_GAS;}
	private static boolean isLogarithmic(TestPaperItem.Kind k){return k==TestPaperItem.Kind.POTASSIUM_THIOCYANATE||k==TestPaperItem.Kind.HYPOCHLORITE||k==TestPaperItem.Kind.CHLORINE_GAS||k==TestPaperItem.Kind.NOX_GAS;}
	public int signal(){return signal;} public ItemStack paper(){return paper;} public void setPaper(ItemStack stack){paper=stack.copyWithCount(1);setChanged();sendData();} public ItemStack removePaper(){ItemStack old=paper;paper=ItemStack.EMPTY;setChanged();sendData();return old;}
	@Override protected void write(CompoundTag tag,boolean clientPacket){super.write(tag,clientPacket);if(!paper.isEmpty())tag.put("paper",paper.save(new CompoundTag()));tag.putInt("signal",signal);tag.putDouble("reading",reading);tag.putBoolean("attached",attached);}
	@Override protected void read(CompoundTag tag,boolean clientPacket){super.read(tag,clientPacket);paper=tag.contains("paper")?ItemStack.of(tag.getCompound("paper")):ItemStack.EMPTY;signal=tag.getInt("signal");reading=tag.getDouble("reading");attached=tag.getBoolean("attached");}
	@Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){tooltip.add(Component.translatable("goggles.chemicaladdon.test_paper_detector"));tooltip.add(paper.isEmpty()?Component.translatable("detector.chemicaladdon.no_paper"):paper.getHoverName());tooltip.add(Component.translatable("detector.chemicaladdon.range",minimum==null?0:minimum.getValue(),maximum==null?0:maximum.getValue()));tooltip.add(Component.translatable("detector.chemicaladdon.signal",signal));return true;}
}
