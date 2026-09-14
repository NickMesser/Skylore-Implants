package net.messer.augment;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.*;
import java.util.UUID;
public final class ChamberEntity extends BlockEntity {
    private UUID occupant;
    private int elapsed, cooldown;
    private boolean menuOpened;
    public float door,previousDoor;
    public ChamberEntity(BlockPos p,BlockState s) {super(Augment.CHAMBER_ENTITY.get(),p,s);}
    public boolean owns(Player p) {return p.getUUID().equals(occupant);}
    public void openMenu(Player p) {
        if(p instanceof ServerPlayer sp && owns(p) && elapsed>=28 && !menuOpened) {
            menuOpened=true; Platform.sync.accept(sp);
            sp.openMenu(new SimpleMenuProvider((id,inv,who)->new ChamberMenu(id,inv,worldPosition),Component.translatable("screen.augment.chamber")));
        }
    }
    public void release() {
        if(level==null||level.isClientSide)return;
        occupant=null; elapsed=0; menuOpened=false; cooldown=40;
        setClosed(false);
    }
    private void setClosed(boolean closed) {
        if(level==null||!level.getBlockState(worldPosition).is(Augment.CHAMBER.get()))return;
        level.setBlock(worldPosition,level.getBlockState(worldPosition).setValue(ChamberBlock.CLOSED,closed),3);
        var top=level.getBlockState(worldPosition.above());
        if(top.is(Augment.CHAMBER.get()))level.setBlock(worldPosition.above(),top.setValue(ChamberBlock.CLOSED,closed),3);
        level.playSound(null,worldPosition,closed?SoundEvents.IRON_DOOR_CLOSE:SoundEvents.IRON_DOOR_OPEN,SoundSource.BLOCKS,.65f,.8f);
        setChanged();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ChamberEntity be) {
        be.previousDoor=be.door;
        be.door=net.minecraft.util.Mth.clamp(be.door+(state.getValue(ChamberBlock.CLOSED)?1f:-1f)/28f,0,1);
        if(level.isClientSide) return;
        if(be.cooldown>0)be.cooldown--;
        if(be.occupant!=null) {
            Player player=level.getPlayerByUUID(be.occupant);
            if(player==null||!player.isAlive()||player.isSpectator()||player.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>4 || (be.menuOpened && !(player.containerMenu instanceof ChamberMenu))) {be.release();return;}
            be.elapsed++; be.openMenu(player); return;
        }
        // A saved closed state has no persistent lock; recover after restart/chunk load.
        if(state.getValue(ChamberBlock.CLOSED)) {be.setClosed(false);return;}
        if(be.cooldown>0)return;
        var box=new AABB(pos.getX()+.18,pos.getY(),pos.getZ()+.18,pos.getX()+.82,pos.getY()+1.94,pos.getZ()+.82);
        var players=level.getEntitiesOfClass(Player.class,box,p->p.isAlive()&&!p.isSpectator()&&!p.isPassenger());
        if(players.size()!=1)return;
        var p=players.get(0);
        if(Math.abs(p.getX()-pos.getX()-.5)>.15 || Math.abs(p.getZ()-pos.getZ()-.5)>.15 || p.containerMenu!=p.inventoryMenu)return;
        // Do not close on another entity standing in the entrance.
        if(level.getEntities(p,new AABB(pos).expandTowards(0,1,0),e->e instanceof net.minecraft.world.entity.LivingEntity).size()>0)return;
        be.occupant=p.getUUID(); be.elapsed=0; be.menuOpened=false;
        ((ServerPlayer)p).teleportTo(pos.getX()+.5,pos.getY()+.063,pos.getZ()+.5);
        be.setClosed(true);
    }
    @Override protected void saveAdditional(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries) {super.saveAdditional(tag,registries);}
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {return saveWithoutMetadata(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {return ClientboundBlockEntityDataPacket.create(this);}
}
