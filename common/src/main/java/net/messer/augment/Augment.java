package net.messer.augment;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.ChatFormatting;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;
public final class Augment {
    public static final String ID="augment";
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,ID);
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,ID);
    public static final DeferredHolder<Block,ChamberBlock> CHAMBER=BLOCKS.register("modification_chamber",()->new ChamberBlock(BlockBehaviour.Properties.of().strength(4,8).noOcclusion().isSuffocating((s,w,p)->false).isViewBlocking((s,w,p)->false).lightLevel(s->7)));
    public static final DeferredHolder<Item,Item> CHAMBER_ITEM=ITEMS.register("modification_chamber",()->new BlockItem(CHAMBER.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ChamberEntity>> CHAMBER_ENTITY=BLOCK_ENTITIES.register("modification_chamber",()->BlockEntityType.Builder.of(ChamberEntity::new,CHAMBER.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ChamberMenu>> CHAMBER_MENU=MENUS.register("modification_chamber",()->new MenuType<>(ChamberMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static final Map<String,DeferredHolder<Item,Item>> IMPLANTS=new LinkedHashMap<>();
    public static void init(IEventBus bus) {
        for(var d:ImplantCatalog.ALL) IMPLANTS.put(d.id(),ITEMS.register(d.id(),()->new Item(new Item.Properties().stacksTo(16)) {
            @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> lines,TooltipFlag flag) {
                for(String line:d.description().split("\\n")) if(!line.isBlank()) lines.add(Component.literal(line).withStyle(d.placeholder()?ChatFormatting.YELLOW:ChatFormatting.GRAY));
                if(!d.placeholder()) lines.add(Component.literal("Install in a Modification Chamber").withStyle(ChatFormatting.AQUA));
            }
        }));
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus);
    }
}
