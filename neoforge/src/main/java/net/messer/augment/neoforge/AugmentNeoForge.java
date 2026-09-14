package net.messer.augment.neoforge;

import net.messer.augment.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Augment.ID)
public final class AugmentNeoForge {
    private final java.util.Map<Player,Double> drawCredit=new java.util.WeakHashMap<>();
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,Augment.ID);

    public AugmentNeoForge(IEventBus modBus,ModContainer container) {
        Augment.init(modBus);
        TABS.register("implants",()->CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.augment"))
            .icon(()->new ItemStack(Augment.IMPLANTS.get("cyber_arm").get()))
            .displayItems((params,out)->{out.accept(Augment.CHAMBER_ITEM.get());Augment.IMPLANTS.values().forEach(i->out.accept(i.get()));})
            .build());
        TABS.register(modBus);
        modBus.addListener(this::payloads);
        Platform.sync=p->PacketDistributor.sendToPlayer(p,new Loadout(ImplantData.slots(p).copy()));
        Platform.attributes=p->{
            var add=AttributeModifier.Operation.ADD_VALUE; var mul=AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            double hydraulic=ImplantEffects.has(p,"hydraulic_limbs")?.5:0;
            ImplantEffects.modifier(p,Attributes.BLOCK_INTERACTION_RANGE,"block_reach",hydraulic+(ImplantEffects.has(p,"neural_interface")?1:0),add);
            ImplantEffects.modifier(p,Attributes.ENTITY_INTERACTION_RANGE,"entity_reach",hydraulic,add);
            ImplantEffects.modifier(p,Attributes.STEP_HEIGHT,"step",hydraulic>0?.4:0,add);
            ImplantEffects.modifier(p,NeoForgeMod.SWIM_SPEED,"swim",(ImplantEffects.has(p,"synthetic_lungs")?.3:0)+(ImplantEffects.has(p,"mech_tendons")?.15:0),mul);
        };
        NeoForge.EVENT_BUS.register(this);
    }

    private void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Loadout.TYPE,Loadout.STREAM_CODEC,
            (msg,ctx)->ctx.enqueueWork(()->ImplantData.client=msg.data()));
    }

    /** 1.21 carries payloads as {@link CustomPacketPayload} with a {@link StreamCodec}. */
    public record Loadout(CompoundTag data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Loadout> TYPE=new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Augment.ID,"loadout"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Loadout> STREAM_CODEC=new StreamCodec<>() {
            @Override public Loadout decode(RegistryFriendlyByteBuf buf) {CompoundTag tag=buf.readNbt();return new Loadout(tag==null?new CompoundTag():tag);}
            @Override public void encode(RegistryFriendlyByteBuf buf,Loadout msg) {buf.writeNbt(msg.data());}
        };
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {return TYPE;}
    }

    @SubscribeEvent public void tick(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p)ImplantEffects.tick(p);}
    private void sync(Player p){if(p instanceof ServerPlayer sp)ImplantData.changed(sp);}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){sync(e.getEntity());}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){sync(e.getEntity());}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){sync(e.getEntity());}
    @SubscribeEvent public void speed(PlayerEvent.BreakSpeed e){e.setNewSpeed(ImplantEffects.mining(e.getEntity(),e.getState(),e.getNewSpeed()));}
    @SubscribeEvent public void harvest(PlayerEvent.HarvestCheck e){if(ImplantEffects.stoneHarvest(e.getEntity(),e.getTargetBlock()))e.setCanHarvest(true);}
    @SubscribeEvent public void heal(LivingHealEvent e){if(e.getEntity() instanceof Player p)e.setAmount(ImplantEffects.healing(p,e.getAmount()));}
    @SubscribeEvent public void damage(LivingIncomingDamageEvent e){
        if(e.getEntity() instanceof Player p) {
            if(e.getSource().is(DamageTypes.WITHER)&&ImplantEffects.has(p,"augment_shield"))e.setAmount(e.getAmount()*.75f);
            if(e.getSource().is(DamageTypes.FLY_INTO_WALL)&&ImplantEffects.has(p,"cybernetic_spine"))e.setCanceled(true);
        }
        if(e.getSource().getEntity() instanceof Player p && e.getSource().getDirectEntity()==p && e.getAmount()>0)ImplantEffects.melee(p,e.getEntity());
    }
    @SubscribeEvent public void fall(LivingFallEvent e){if(e.getEntity() instanceof Player p){
        if(ImplantEffects.has(p,"cybernetic_spine"))e.setDistance(Math.max(0,e.getDistance()-4));
        if(ImplantEffects.has(p,"bio_skeleton"))e.setDamageMultiplier(e.getDamageMultiplier()*.85f);
    }}
    @SubscribeEvent public void shield(AttackEntityEvent e){
        // Shields reject damage before the incoming-damage event; disable them at the attack path.
        if(ImplantEffects.has(e.getEntity(),"servo_motor_arm") && e.getTarget() instanceof Player victim && victim.isBlocking()) {
            victim.getCooldowns().addCooldown(victim.getUseItem().getItem(),100);victim.stopUsingItem();victim.level().broadcastEntityEvent(victim,(byte)30);
        }
    }
    @SubscribeEvent public void poison(MobEffectEvent.Applicable e){
        if(e.getEntity() instanceof Player p && ImplantEffects.has(p,"augment_shield") && e.getEffectInstance().getEffect().value()==MobEffects.POISON.value())
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
    @SubscribeEvent public void food(LivingEntityUseItemEvent.Finish e){if(e.getEntity() instanceof ServerPlayer p && e.getItem().has(net.minecraft.core.component.DataComponents.FOOD) && ImplantEffects.has(p,"energy_converter"))p.getFoodData().eat(2,0);}
    @SubscribeEvent public void draw(LivingEntityUseItemEvent.Tick e){
        if(e.getEntity() instanceof Player p && (e.getItem().getItem() instanceof BowItem || e.getItem().getItem() instanceof CrossbowItem) && ImplantEffects.has(p,"pulse_wrist")) {
            double credit=drawCredit.getOrDefault(p,0.0)+1.0/.85-1.0;int extra=(int)credit;
            drawCredit.put(p,credit-extra);e.setDuration(Math.max(1,e.getDuration()-extra));
        }
    }
    @SubscribeEvent public void target(LivingChangeTargetEvent e){
        if(e.getNewAboutToBeSetTarget() instanceof Player p) {
            if(p.isShiftKeyDown() && ImplantEffects.has(p,"sensory_module") && e.getEntity() instanceof net.minecraft.world.entity.monster.warden.Warden) {e.setNewAboutToBeSetTarget(null);return;}
            if(e.getEntity() instanceof net.minecraft.world.entity.monster.Monster monster && monster.getTarget()!=p && ImplantEffects.has(p,"giga_ear"))e.getEntity().addEffect(new MobEffectInstance(MobEffects.GLOWING,100,0,true,false));
        }
    }
}
