package com.yu1745.chemicaladdon.control;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.network.PlcEditPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PlcScreen extends AbstractContainerScreen<PlcMenu> {
	private MultiLineEditBox editor;
	private PlcControllerBlockEntity.ProgramMode mode;
	public PlcScreen(PlcMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=300;imageHeight=220;mode=menu.plc().programMode();}
	@Override protected void init(){super.init();int x=(width-imageWidth)/2,y=(height-imageHeight)/2;editor=new MultiLineEditBox(font,x+10,y+28,280,150,Component.translatable("screen.chemicaladdon.plc.placeholder"),Component.empty());editor.setCharacterLimit(8192);editor.setValue(menu.plc().source());addRenderableWidget(editor);addRenderableWidget(Button.builder(modeText(),b->{mode=mode==PlcControllerBlockEntity.ProgramMode.INSTRUCTION?PlcControllerBlockEntity.ProgramMode.JAVASCRIPT:PlcControllerBlockEntity.ProgramMode.INSTRUCTION;b.setMessage(modeText());}).bounds(x+10,y+184,90,20).build());addRenderableWidget(Button.builder(Component.translatable("screen.chemicaladdon.plc.save"),b->send(0)).bounds(x+105,y+184,55,20).build());addRenderableWidget(Button.builder(Component.translatable(menu.plc().running()?"screen.chemicaladdon.plc.stop":"screen.chemicaladdon.plc.run"),b->send(menu.plc().running()?2:1)).bounds(x+165,y+184,55,20).build());}
	private Component modeText(){return Component.translatable(mode==PlcControllerBlockEntity.ProgramMode.INSTRUCTION?"screen.chemicaladdon.plc.instruction":"screen.chemicaladdon.plc.javascript");}
	private void send(int action){ChemicalAddon.sendToServer(new PlcEditPacket(menu.pos(),action,mode,editor.getValue()));if(action!=0)onClose();}
	@Override public void containerTick(){super.containerTick();editor.tick();}
	@Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){int x=(width-imageWidth)/2,y=(height-imageHeight)/2;g.fill(x,y,x+imageWidth,y+imageHeight,0xff20252a);g.fill(x+4,y+4,x+imageWidth-4,y+imageHeight-4,0xff3c474f);}
	@Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){g.drawString(font,title,10,9,0xffffff,false);PlcControllerBlockEntity plc=menu.plc();g.drawString(font,plc.fault().name()+(plc.error().isBlank()?"":" - "+plc.error()),110,9,plc.fault()==PlcFault.NONE?0x66ff66:0xff6666,false);}
}
