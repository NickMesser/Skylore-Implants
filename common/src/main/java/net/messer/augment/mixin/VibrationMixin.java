package net.messer.augment.mixin;

import net.messer.augment.ImplantEffects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerLevel.class)
public abstract class VibrationMixin {
    @Inject(method="gameEvent",at=@At("HEAD"),cancellable=true)
    private void augment$silent(Holder<GameEvent> event,Vec3 pos,GameEvent.Context context,CallbackInfo ci) {
        if(context.sourceEntity() instanceof Player p && p.isShiftKeyDown() && ImplantEffects.has(p,"sensory_module"))ci.cancel();
    }
}
