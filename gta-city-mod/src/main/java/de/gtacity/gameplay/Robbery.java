package de.gtacity.gameplay;

import de.gtacity.item.CashItem;
import de.gtacity.shop.ShopType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.HashMap;
import java.util.Map;

/** Holding up a shop: sneak + right click the counter with a gun. */
public final class Robbery {
    private Robbery() {
    }

    private static final long COOLDOWN = 20L * 60 * 5;
    private static final Map<BlockPos, Long> ROBBED_UNTIL = new HashMap<>();

    public static void robCounter(ServerPlayer player, BlockPos pos, ShopType type) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Long until = ROBBED_UNTIL.get(pos);
        if (until != null && until > now) {
            player.sendOverlayMessage(Component.literal("Die Kasse ist leer.").withStyle(ChatFormatting.GRAY));
            return;
        }
        int base = switch (type) {
            case STORE -> 300;
            case WEAPONS -> 600;
            case CARS -> 1500;
        };
        int loot = base + level.getRandom().nextInt(base * 2);
        ItemEntity cash = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                CashItem.of(loot));
        level.addFreshEntity(cash);
        ROBBED_UNTIL.put(pos.immutable(), now + COOLDOWN);
        WantedSystem.commit(player, type == ShopType.WEAPONS ? 2 : 1);
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.2F);
        player.sendOverlayMessage(Component.literal("Kasse ausgeraubt! " + Economy.format(loot))
                .withStyle(ChatFormatting.GOLD));
    }
}
