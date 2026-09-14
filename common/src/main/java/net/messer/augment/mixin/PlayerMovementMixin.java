package net.messer.augment.mixin;

import net.messer.augment.ImplantEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Player.class)
public abstract class PlayerMovementMixin {
    @ModifyArg(method={"checkMovementStatistics","jumpFromGround"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"),index=0)
    private float augment$efficientMovement(float amount) {
        return ImplantEffects.has((Player)(Object)this,"power_core")?amount*.75f:amount;
    }
}
