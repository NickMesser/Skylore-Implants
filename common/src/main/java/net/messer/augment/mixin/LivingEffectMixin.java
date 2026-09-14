package net.messer.augment.mixin;

import net.messer.augment.ImplantEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(LivingEntity.class)
public abstract class LivingEffectMixin {
    @ModifyVariable(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),argsOnly=true)
    private MobEffectInstance augment$shorten(MobEffectInstance effect) {
        if((Object)this instanceof Player p && ImplantEffects.has(p,"augmented_nervous_system") && effect.getDuration()>0 &&
           (effect.getEffect().value()==MobEffects.MOVEMENT_SLOWDOWN.value() || effect.getEffect().value()==MobEffects.WEAKNESS.value() ||
            effect.getEffect().value()==MobEffects.DIG_SLOWDOWN.value() || effect.getEffect().value()==MobEffects.BLINDNESS.value())) {
            var copy=new MobEffectInstance(effect); ((EffectDurationAccessor)copy).augment$setDuration(Math.max(1,(int)(effect.getDuration()*.7)));return copy;
        }
        return effect;
    }
}
