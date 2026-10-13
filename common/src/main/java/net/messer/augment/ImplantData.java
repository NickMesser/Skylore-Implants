package net.messer.augment;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
public final class ImplantData extends SavedData {
    private final Map<UUID,CompoundTag> players=new HashMap<>();
    public static CompoundTag client=new CompoundTag();
    public static ImplantData of(Player p) {
        return p.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ImplantData::new,ImplantData::load),"augment_implants");
    }
    public static CompoundTag slots(Player p) {
        if(p.level().isClientSide) return client;
        return of(p).players.computeIfAbsent(p.getUUID(),id->new CompoundTag());
    }
    /** Slot values are 1.21 item-component compounds written by {@link ItemStack#CODEC}. */
    public static ItemStack stack(Player p,BodySlot slot) {
        var tag=slots(p).get(slot.id());
        if(!(tag instanceof CompoundTag compound)) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(NbtOps.INSTANCE,compound).result().orElse(ItemStack.EMPTY);
    }
    public static void store(Player p,BodySlot slot,ItemStack stack) {
        if(stack.isEmpty()) { slots(p).remove(slot.id()); return; }
        ItemStack.CODEC.encodeStart(NbtOps.INSTANCE,stack).result().ifPresent(tag->slots(p).put(slot.id(),tag));
    }
    public static String id(ItemStack stack) {
        if(stack.isEmpty()) return "";
        var key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(Augment.ID)?key.getPath():"";
    }
    public static int count(Player p,String id) {
        var slots=slots(p); String key="augment:"+id;
        int n=0; for(var s:BodySlot.values()) if(slots.getCompound(s.id()).getString("id").equals(key)) n++; return n;
    }
    public static boolean has(Player p,String id) { return count(p,id)>0; }
    /** Client copy of what every nearby player wears, slot id to implant id, for rendering. */
    private static final Map<UUID,CompoundTag> WORN=new HashMap<>();
    public static CompoundTag worn(UUID player) { return WORN.getOrDefault(player,new CompoundTag()); }
    public static void worn(UUID player,CompoundTag slots) { if(slots.isEmpty()) WORN.remove(player); else WORN.put(player,slots); }
    public static void clearWorn() { WORN.clear(); }
    /** Slot id to implant id for one player; the payload other clients render from. */
    public static CompoundTag wornBy(Player p) {
        var out=new CompoundTag();
        for(var s:BodySlot.values()) { String id=id(stack(p,s)); if(!id.isEmpty()) out.putString(s.id(),id); }
        return out;
    }
    public static void changed(ServerPlayer p) { of(p).setDirty(); ImplantEffects.attributes(p); Platform.sync.accept(p); }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        players.forEach((id,slots)->tag.put(id.toString(),slots.copy())); return tag;
    }
    public static ImplantData load(CompoundTag tag,HolderLookup.Provider registries) {
        var data=new ImplantData();
        for(String key:tag.getAllKeys()) try { data.players.put(UUID.fromString(key),tag.getCompound(key)); } catch(IllegalArgumentException ignored) {}
        return data;
    }
}
