package net.messer.augment.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MobEffectInstance.class)
public interface EffectDurationAccessor {
    @Accessor("duration") void augment$setDuration(int duration);
}
