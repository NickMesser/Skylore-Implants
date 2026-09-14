package net.messer.augment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
public final class ChamberMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final ContainerData implants=new SimpleContainerData(10);
    private BodySlot selectedSlot=BodySlot.HEAD;
    public ChamberMenu(int id,Inventory inventory) { this(id,inventory,BlockPos.ZERO); }
    public ChamberMenu(int id,Inventory inventory,BlockPos pos) {
        super(Augment.CHAMBER_MENU.get(),id); this.pos=pos; addDataSlots(implants);
        for(int i=0;i<36;i++) addSlot(new Slot(inventory,i,-10000,-10000));
        refresh(inventory.player);
    }
    private void refresh(Player p) {
        if(p.level().isClientSide) return;
        for(var s:BodySlot.values()) { String id=ImplantData.id(ImplantData.stack(p,s)); int index=-1;
            for(int i=0;i<ImplantCatalog.ALL.size();i++) if(ImplantCatalog.ALL.get(i).id().equals(id)) index=i;
            implants.set(s.ordinal(),index+1);
        }
    }
    @Override public boolean stillValid(Player p) {
        return p.isAlive() && p.level().getBlockEntity(pos) instanceof ChamberEntity be && be.owns(p) && p.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)<4;
    }
    @Override public boolean clickMenuButton(Player player,int button) {
        if(!(player instanceof ServerPlayer p)||!stillValid(p)) return false;
        // Vanilla's button packet carries a signed byte. Keep every action below 128.
        if(button>=0 && button<10) {selectedSlot=BodySlot.values()[button];return true;}
        if(button>=10 && button<20) {
            var s=BodySlot.values()[button-10]; var stack=ImplantData.stack(p,s);
            if(stack.isEmpty()) return false;
            ImplantData.slots(p).remove(s.id());
            if(!p.getInventory().add(stack)) p.drop(stack,false);
        } else if(button>=32 && button<32+ImplantCatalog.ALL.size()) {
            var d=ImplantCatalog.ALL.get(button-32); var s=selectedSlot;
            if(!d.fits(s)||!ImplantData.stack(p,s).isEmpty()) return false;
            int source=-1;
            for(int i=0;i<p.getInventory().getContainerSize();i++) if(p.getInventory().getItem(i).is(Augment.IMPLANTS.get(d.id()).get())) {source=i;break;}
            if(source<0) return false;
            var installed=p.getInventory().getItem(source).split(1);
            ImplantData.store(p,s,installed);
        } else return false;
        ImplantData.changed(p); p.getInventory().setChanged(); refresh(p); broadcastChanges(); return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) { /* inventory is read-only during surgery */ }
    @Override public ItemStack quickMoveStack(Player p,int index) {return ItemStack.EMPTY;}
    @Override public void removed(Player p) {
        super.removed(p);
        if(!p.level().isClientSide && p.level().getBlockEntity(pos) instanceof ChamberEntity be && be.owns(p)) be.release();
    }
}
