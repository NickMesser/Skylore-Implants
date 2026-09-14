package net.messer.augment.neoforge;

import net.messer.augment.*;
import net.messer.augment.client.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=Augment.ID,value=Dist.CLIENT)
public final class AugmentClient {
    public static final KeyMapping ZOOM=new KeyMapping("key.augment.zoom",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_Z,"key.categories.augment");
    private AugmentClient() {}

    // MenuScreens#register is private in 1.21; NeoForge exposes the mod-bus event instead.
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){e.register(Augment.CHAMBER_MENU.get(),ChamberScreen::new);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->BlockEntityRenderers.register(Augment.CHAMBER_ENTITY.get(),ChamberRenderer::new));}
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(ZOOM);}
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e){var mc=Minecraft.getInstance();if(mc.player!=null && mc.screen==null && ZOOM.isDown() && ImplantEffects.has(mc.player,"hyper_vision_lens"))e.setFOV(e.getFOV()*.22);}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.screen!=null||mc.options.hideGui)return;
        if(ImplantEffects.has(p,"virtual_assistant_node")){
            long clock=(p.level().getDayTime()+6000)%24000;String time=String.format(java.util.Locale.ROOT,"%02d:%02d",clock/1000,(clock%1000)*60/1000);
            String text=String.format(java.util.Locale.ROOT,"%d / %d / %d  |  %s  |  %s",p.getBlockX(),p.getBlockY(),p.getBlockZ(),p.getDirection().getName().toUpperCase(java.util.Locale.ROOT),time);
            var g=e.getGuiGraphics();g.fill(6,6,mc.font.width(text)+18,23,0xcc091923);g.drawString(mc.font,text,12,11,0xff69ebd4,false);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();var p=mc.player;
        if(p!=null && p.tickCount%5==0 && ImplantEffects.has(p,"holo_display_arm") && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target)
            p.displayClientMessage(Component.literal(target.getName().getString()+"  "+String.format(java.util.Locale.ROOT,"%.1f / %.1f HP",target.getHealth(),target.getMaxHealth())),true);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){ImplantData.client=new CompoundTag();}
}
