package net.messer.augment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.function.*;
/** Loader adapters inject the few services missing from vanilla. The shared layer never imports a loader. */
public final class Platform {
    public static Consumer<ServerPlayer> sync=p->{};
    public static Consumer<Player> attributes=p->{};
}
