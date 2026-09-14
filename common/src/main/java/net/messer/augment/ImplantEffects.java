package net.messer.augment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Gameplay policy shared by loader adapters. Bonuses stack only where the card says per limb. */
public final class ImplantEffects {
    private static final Map<Player,Map<Holder<MobEffect>,MobEffectInstance>> OWNED=new WeakHashMap<>();
    public static boolean has(Player p,String id){return ImplantData.has(p,id);}
    /** 1.21 identifies attribute modifiers by {@link ResourceLocation}, not by UUID. */
    public static void modifier(Player p,Holder<Attribute> attr,String key,double amount,AttributeModifier.Operation operation) {
        var instance=p.getAttribute(attr); if(instance==null)return;
        var id=ResourceLocation.fromNamespaceAndPath(Augment.ID,key);
        var previous=instance.getModifier(id);
        if(previous!=null && previous.amount()==amount && previous.operation()==operation)return;
        if(previous!=null)instance.removeModifier(id);
        if(amount!=0)instance.addTransientModifier(new AttributeModifier(id,amount,operation));
    }
    public static void attributes(Player p) {
        var add=AttributeModifier.Operation.ADD_VALUE; var multiply=AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        modifier(p,Attributes.MAX_HEALTH,"heart",has(p,"synthetic_heart")?2:0,add);
        modifier(p,Attributes.ARMOR,"skin",has(p,"nano_skin")?2:0,add);
        modifier(p,Attributes.ARMOR_TOUGHNESS,"core",has(p,"techno_core")?1:0,add);
        modifier(p,Attributes.ATTACK_DAMAGE,"arms",ImplantData.count(p,"cyber_arm")+(has(p,"mech_jaw")&&p.getMainHandItem().isEmpty()?1:0),add);
        modifier(p,Attributes.ATTACK_SPEED,"impulse",has(p,"impulse_booster")?.15:0,multiply);
        modifier(p,Attributes.MOVEMENT_SPEED,"legs",has(p,"hyper_legs")?.2:0,multiply);
        modifier(p,Attributes.KNOCKBACK_RESISTANCE,"structure",(has(p,"bio_skeleton")?.2:0)+(has(p,"nano_weave_muscle")?.25:0),add);
        Platform.attributes.accept(p);
        if(p.getHealth()>p.getMaxHealth())p.setHealth(p.getMaxHealth());
    }
    public static float mining(Player p,BlockState state,float speed) {
        if(has(p,"mech_jaw")&&p.getMainHandItem().isEmpty()) {
            speed=Math.max(speed,Math.max(new ItemStack(Items.STONE_PICKAXE).getDestroySpeed(state),Math.max(new ItemStack(Items.STONE_AXE).getDestroySpeed(state),new ItemStack(Items.STONE_SHOVEL).getDestroySpeed(state))));
        }
        return speed*(1+.15f*ImplantData.count(p,"cybernetic_hand")+(has(p,"mech_tendons")?.1f:0));
    }
    public static boolean stoneHarvest(Player p,BlockState state) {
        return has(p,"mech_jaw")&&p.getMainHandItem().isEmpty() &&
            (new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state)||new ItemStack(Items.STONE_AXE).isCorrectToolForDrops(state)||new ItemStack(Items.STONE_SHOVEL).isCorrectToolForDrops(state));
    }
    public static float healing(Player p,float amount){return has(p,"pulse_enhancer")?amount*1.2f:amount;}
    public static int experience(Player p,int amount) {
        double rate=has(p,"cyber_brain")?.25:has(p,"augment_brain_chip")?.1:0;
        if(rate==0||amount<=0)return amount;
        var slots=ImplantData.slots(p); double credit=slots.getDouble("_xp_credit")+amount*rate;
        int bonus=(int)(credit+1e-9); slots.putDouble("_xp_credit",credit-bonus); ImplantData.of(p).setDirty(); return amount+bonus;
    }
    public static void melee(Player p,Entity target) {
        if(p.level().isClientSide)return;
        if(has(p,"plasma_pulse_arm"))target.igniteForSeconds(2);
        if(has(p,"servo_motor_arm")&&target instanceof Player victim&&victim.isBlocking()) {
            var shield=victim.getUseItem().getItem(); victim.getCooldowns().addCooldown(shield,100); victim.stopUsingItem(); victim.level().broadcastEntityEvent(victim,(byte)30);
        }
    }
    private static void effect(Player p,Holder<MobEffect> effect,boolean active) {
        var owned=OWNED.computeIfAbsent(p,k->new HashMap<>()); var current=p.getEffect(effect); var ours=owned.get(effect);
        if(!active) {
            if(ours!=null && current==ours && current.getAmplifier()==0 && current.getDuration()<=240)p.removeEffect(effect);
            owned.remove(effect);return;
        }
        // Never replace a potion/beacon effect; regain ownership when it expires.
        if(current!=null && (current!=ours || current.getAmplifier()!=0 || current.getDuration()>240))return;
        boolean regeneration=effect.value()==MobEffects.REGENERATION.value();
        int duration=regeneration?50:240;
        if(current==null || current.getDuration()<(regeneration?1:220)) {
            p.addEffect(new MobEffectInstance(effect,duration,0,true,false,false)); owned.put(effect,p.getEffect(effect));
        }
    }
    public static void tick(ServerPlayer p) {
        if(!p.isAlive())return;
        attributes(p);
        effect(p,MobEffects.NIGHT_VISION,has(p,"bio_optic_eye"));
        effect(p,MobEffects.WATER_BREATHING,has(p,"synthetic_lungs"));
        // Regeneration I's vanilla interval is 50 ticks. Avoid resetting its phase each tick.
        effect(p,MobEffects.REGENERATION,has(p,"nano_bloodstream")&&p.getHealth()<=6);
        if(has(p,"augment_shield"))p.removeEffect(MobEffects.POISON);
        if(has(p,"bio_feedback_system")&&p.tickCount%300==0&&p.getFoodData().getFoodLevel()<=6)p.getFoodData().eat(1,0);
        if(has(p,"quantum_interface")&&p.fallDistance>3 && !p.onGround() && !p.isFallFlying()) {
            var data=ImplantData.slots(p); long now=p.level().getGameTime();
            if(now>=data.getLong("_quantum_ready")) {p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,60,0,true,false)); data.putLong("_quantum_ready",now+160);ImplantData.of(p).setDirty();}
        }
        if(has(p,"data_hub")) {
            for(var e:p.level().getEntities(p,p.getBoundingBox().inflate(3),e->e instanceof ItemEntity||e instanceof ExperienceOrb)) {
                if(e instanceof ItemEntity item && item.hasPickUpDelay())continue;
                Vec3 delta=p.position().add(0,.5,0).subtract(e.position());
                if(delta.lengthSqr()>0.3 && p.hasLineOfSight(e)) e.setDeltaMovement(delta.normalize().scale(.18));
            }
        }
        if(p.tickCount%10==0 && has(p,"digital_retina"))
            for(var e:p.level().getEntitiesOfClass(Monster.class,p.getBoundingBox().inflate(16),m->m.distanceToSqr(p)<=256)) e.addEffect(new MobEffectInstance(MobEffects.GLOWING,15,0,true,false));
        if(p.tickCount%20==0 && p.isShiftKeyDown() && has(p,"optic_enhancer")) {
            var origin=p.blockPosition();
            for(var pos:BlockPos.betweenClosed(origin.offset(-12,-12,-12),origin.offset(12,12,12))) {
                if(pos.distSqr(origin)>144 || !p.level().hasChunkAt(pos))continue;
                var b=p.level().getBlockState(pos).getBlock();
                if(b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof SpawnerBlock)
                    p.serverLevel().sendParticles(p,ParticleTypes.END_ROD,true,pos.getX()+.5,pos.getY()+1.05,pos.getZ()+.5,2,.15,.1,.15,0);
            }
        }
    }
}
