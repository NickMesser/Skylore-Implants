package net.messer.augment.mixin;

import net.messer.augment.ImplantEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
    @Redirect(method="playerTouch",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;giveExperiencePoints(I)V"))
    private void augment$experience(Player player,int value) {
        player.giveExperiencePoints(ImplantEffects.experience(player,value));
    }
}
