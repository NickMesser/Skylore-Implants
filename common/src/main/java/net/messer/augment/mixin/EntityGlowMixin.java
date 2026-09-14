package net.messer.augment.mixin;

import net.messer.augment.ImplantEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    @Inject(method="isCurrentlyGlowing",at=@At("HEAD"),cancellable=true)
    private void augment$mindLink(CallbackInfoReturnable<Boolean> cir) {
        var entity=(Entity)(Object)this; var p=Minecraft.getInstance().player;
        if(entity.level().isClientSide && p!=null && (entity instanceof ItemEntity || entity instanceof ExperienceOrb) && entity.distanceToSqr(p)<=144 && ImplantEffects.has(p,"mind_link"))cir.setReturnValue(true);
    }
}
