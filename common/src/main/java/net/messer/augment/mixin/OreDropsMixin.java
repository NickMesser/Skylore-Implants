package net.messer.augment.mixin;

import net.messer.augment.ImplantEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Block.class)
public abstract class OreDropsMixin {
    // NeoForge 1.21 moved the common ore tag from "forge:ores" to "c:ores".
    private static final TagKey<Block> ORES=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("c","ores"));
    private static final TagKey<Block> ORES_LEGACY=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("forge","ores"));
    @Inject(method="getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",at=@At("RETURN"),cancellable=true)
    private static void augment$extraOre(BlockState state,ServerLevel level,BlockPos pos,BlockEntity blockEntity,Entity entity,ItemStack tool,CallbackInfoReturnable<List<ItemStack>> cir) {
        if(entity instanceof Player p && !p.isCreative() && ImplantEffects.has(p,"nano_processor") && (state.is(ORES)||state.is(ORES_LEGACY)) && p.hasCorrectToolForDrops(state) && p.getRandom().nextFloat()<.1f) {
            var out=new ArrayList<>(cir.getReturnValue());
            for(var stack:cir.getReturnValue()) if(!stack.isEmpty()) {var extra=stack.copy();extra.setCount(1);out.add(extra);break;}
            cir.setReturnValue(out);
        }
    }
}
