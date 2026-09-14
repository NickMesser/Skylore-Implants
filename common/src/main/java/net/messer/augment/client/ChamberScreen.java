package net.messer.augment.client;

import net.messer.augment.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import java.util.*;

public final class ChamberScreen extends AbstractContainerScreen<ChamberMenu> {
    private BodySlot selected=BodySlot.HEAD;
    private int page,leftWidth,rows;
    private long openedAt;
    private final List<Button> installButtons=new ArrayList<>();
    private Button remove;
    public ChamberScreen(ChamberMenu menu,Inventory inv,Component title){super(menu,inv,title);}
    @Override protected void init(){
        imageWidth=Math.min(620,width-16);imageHeight=Math.min(360,height-16);
        super.init();leftWidth=(int)(imageWidth*.4);rows=Math.max(2,(imageHeight-110)/34);
        if(openedAt==0)openedAt=System.currentTimeMillis();
        widgets();
    }
    private List<ImplantCatalog.Definition> choices(){return ImplantCatalog.ALL.stream().filter(d->d.fits(selected)).toList();}
    private int installed(){return menu.implants.get(selected.ordinal())-1;}
    private void widgets(){
        clearWidgets(); installButtons.clear();
        int slotY=topPos+imageHeight-124;
        BodySlot[] displayOrder={BodySlot.HEAD,BodySlot.TORSO,BodySlot.R_EYE,BodySlot.L_EYE,BodySlot.R_ARM,BodySlot.L_ARM,BodySlot.R_HAND,BodySlot.L_HAND,BodySlot.R_LEG,BodySlot.L_LEG};
        for(int i=0;i<displayOrder.length;i++){
            var s=displayOrder[i];int x=leftPos+10+(i%2)*(leftWidth-20)/2,y=slotY+(i/2)*19;
            addRenderableWidget(Button.builder(Component.literal((s==selected?"> ":"")+s.label),b->{selected=s;page=0;widgets();}).bounds(x,y,(leftWidth-24)/2,17).build());
        }
        var list=choices();page=Mth.clamp(page,0,Math.max(0,(list.size()-1)/rows));
        for(int i=0;i<rows && page*rows+i<list.size();i++) {
            var d=list.get(page*rows+i);int index=ImplantCatalog.ALL.indexOf(d);
            var button=addRenderableWidget(Button.builder(Component.literal("Install"),b->{minecraft.gameMode.handleInventoryButtonClick(menu.containerId,selected.ordinal());minecraft.gameMode.handleInventoryButtonClick(menu.containerId,32+index);}).bounds(leftPos+imageWidth-60,topPos+61+i*34,49,18).build());
            installButtons.add(button);
        }
        remove=addRenderableWidget(Button.builder(Component.literal("Remove implant"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,10+selected.ordinal())).bounds(leftPos+leftWidth+10,topPos+imageHeight-47,115,18).build());
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;widgets();}).bounds(leftPos+imageWidth-58,topPos+imageHeight-47,20,18).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;widgets();}).bounds(leftPos+imageWidth-32,topPos+imageHeight-47,20,18).build()).active=(page+1)*rows<list.size();
        addRenderableWidget(Button.builder(Component.literal("Finish & exit"),b->onClose()).bounds(leftPos+imageWidth-100,topPos+imageHeight-23,90,17).build());
    }
    private int owned(String id){int n=0;for(var s:minecraft.player.getInventory().items)if(s.is(Augment.IMPLANTS.get(id).get()))n+=s.getCount();return n;}
    @Override protected void containerTick(){super.containerTick();var list=choices();for(int i=0;i<installButtons.size();i++)installButtons.get(i).active=installed()<0&&owned(list.get(page*rows+i).id())>0;remove.active=installed()>=0;}
    @Override protected void renderBg(GuiGraphics g,float delta,int mx,int my){
        int x=leftPos,y=topPos,w=imageWidth,h=imageHeight;
        g.fill(x-1,y-1,x+w+1,y+h+1,0xff3bdaca);g.fill(x,y,x+w,y+h,0xff09151e);
        g.fill(x+1,y+1,x+w-1,y+29,0xff112833);g.fill(x+leftWidth,y+35,x+leftWidth+1,y+h-28,0xff2b4853);
        g.drawString(font,"AUGMENT / MODIFICATION CHAMBER",x+10,y+11,0xffbdf9ef,false);
        int center=x+leftWidth/2, modelBottom=y+h-139;
        g.fill(x+10,y+37,x+leftWidth-10,y+h-130,0xff10222c);
        for(int line=y+39;line<y+h-130;line+=10)g.fill(x+11,line,x+leftWidth-11,line+1,0xff15303c);
        float zoom=Mth.clamp((System.currentTimeMillis()-openedAt)/650f,0,1);zoom=zoom*zoom*(3-2*zoom);
        int modelScale=(int)(Math.max(18,Math.min(68,(h-185)/2.1f))*(.6f+.4f*zoom));
        if(minecraft.player!=null){
            int half=(int)(modelScale*.95f);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g,center-half,modelBottom-modelScale*2,center+half,modelBottom,modelScale,.0625f,center-mx,modelBottom-modelScale*1.3f-my,minecraft.player);
        }
        g.drawString(font,"BODY / "+countInstalled()+" OF 10",x+13,y+40,0xff76aab4,false);
        int rx=x+leftWidth+10;
        String current=installed()>=0?ImplantCatalog.ALL.get(installed()).name():"Empty";
        g.drawString(font,font.plainSubstrByWidth(selected.label.toUpperCase(Locale.ROOT)+" / "+current,w-leftWidth-20),rx,y+38,0xff64e8d3,false);
        var list=choices();
        for(int row=0;row<rows && page*rows+row<list.size();row++){
            var d=list.get(page*rows+row);int ry=y+57+row*34;boolean isInstalled=installed()==ImplantCatalog.ALL.indexOf(d);
            g.fill(rx-2,ry,x+w-9,ry+31,isInstalled?0xff153d3e:0xff122631);
            g.renderItem(new ItemStack(Augment.IMPLANTS.get(d.id()).get()),rx+2,ry+6);
            String label=font.plainSubstrByWidth(d.name(),w-leftWidth-91);
            g.drawString(font,label,rx+23,ry+5,0xffe3f2f2,false);
            g.drawString(font,isInstalled?"INSTALLED":"In inventory: "+owned(d.id()),rx+23,ry+17,isInstalled?0xff5ff6c8:0xff80a2af,false);
        }
        if(list.isEmpty())g.drawString(font,"No compatible implants",rx,y+68,0xff8ba8b3,false);
        g.drawString(font,"Hover for implant details.",x+10,y+h-18,0xff73909e,false);
    }
    private int countInstalled(){int n=0;for(int i=0;i<10;i++)if(menu.implants.get(i)>0)n++;return n;}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        renderBackground(g,mx,my,delta);super.render(g,mx,my,delta);
        var list=choices();
        if(mx>=leftPos+leftWidth+8 && mx<leftPos+imageWidth-64){int row=(my-topPos-57)/34;
            if(my>=topPos+57 && row>=0&&row<rows&&page*rows+row<list.size()){
                var d=list.get(page*rows+row);var text=new ArrayList<net.minecraft.util.FormattedCharSequence>();
                text.add(Component.literal(d.name()).withStyle(net.minecraft.ChatFormatting.AQUA).getVisualOrderText());
                for(String line:d.description().split("\n"))if(!line.isBlank())text.addAll(font.split(Component.literal(line),Math.min(230,width-30)));
                g.renderTooltip(font,text,mx,my);
            }
        }
    }
    @Override public boolean isPauseScreen(){return false;}
}
