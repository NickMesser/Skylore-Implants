package net.messer.augment.mixin;

import net.messer.augment.ImplantEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Warden.class)
public abstract class WardenMixin {
    @Inject(method="canTargetEntity",at=@At("HEAD"),cancellable=true)
    private void augment$ignore(Entity entity,CallbackInfoReturnable<Boolean> cir) {
        if(entity instanceof Player p && p.isShiftKeyDown() && ImplantEffects.has(p,"sensory_module"))cir.setReturnValue(false);
    }
}
