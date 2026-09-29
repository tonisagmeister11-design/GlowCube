package de.gtacity.gameplay;

import de.gtacity.registry.ModAttachments;
import de.gtacity.registry.ModSounds;
import de.gtacity.world.CityMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Villas in the Hills: buy them on the map and teleport home with a click. */
public final class Villas {
    private Villas() {
    }

    public static List<Long> owned(Player player) {
        List<Long> list = player.getAttached(ModAttachments.VILLAS);
        return list == null ? List.of() : list;
    }

    public static boolean owns(Player player, long id) {
        return owned(player).contains(id);
    }

    public static void buy(ServerPlayer player, long id) {
        CityMap.Place villa = CityMap.villa(id);
        if (villa == null || owns(player, id)) {
            return;
        }
        int price = CityMap.villaPrice(villa);
        if (!Economy.trySpend(player, price)) {
            player.sendOverlayMessage(Component.literal("Zu wenig Geld! Die Villa kostet " + Economy.format(price))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        List<Long> list = new ArrayList<>(owned(player));
        list.add(id);
        player.setAttached(ModAttachments.VILLAS, List.copyOf(list));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASH,
                SoundSource.PLAYERS, 1.0F, 0.9F);
        player.sendSystemMessage(Component.literal("Glückwunsch! " + CityMap.villaName(villa) + " gehört jetzt dir. "
                + "Auf der Karte (M) kannst du dich jederzeit hinteleportieren.").withStyle(ChatFormatting.GREEN));
    }

    public static void teleport(ServerPlayer player, long id) {
        CityMap.Place villa = CityMap.villa(id);
        if (villa == null || !owns(player, id)) {
            return;
        }
        if (WantedSystem.level(player) > 0) {
            player.sendOverlayMessage(Component.literal("Nicht mit Fahndungssternen! Häng erst die Polizei ab.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        BlockPos spot = villa.entrance();
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        player.sendOverlayMessage(Component.literal("Willkommen zu Hause: " + CityMap.villaName(villa))
                .withStyle(ChatFormatting.GOLD));
    }
}
