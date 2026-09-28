package de.gtacity.gameplay;

import de.gtacity.item.CashItem;
import de.gtacity.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Bank heists: drill the vault open while the cops storm the bank. */
public final class Heists {
    private Heists() {
    }

    private static final int DRILL_TICKS = 20 * 30;
    private static final long VAULT_COOLDOWN = 20L * 60 * 20;

    private record Heist(ServerPlayer player, BlockPos vault, int[] progress) {
    }

    private static final Map<UUID, Heist> ACTIVE = new HashMap<>();
    private static final Map<BlockPos, Long> EMPTY_UNTIL = new HashMap<>();

    public static void interact(ServerPlayer player, BlockPos vault, ItemStack held) {
        long now = player.level().getGameTime();
        Long until = EMPTY_UNTIL.get(vault);
        if (until != null && until > now) {
            long minutes = (until - now) / 1200 + 1;
            player.displayClientMessage(Component.literal("Der Tresor ist leer. Neue Lieferung in ca. " + minutes
                    + " min.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Heist running = ACTIVE.get(player.getUUID());
        if (running != null) {
            player.displayClientMessage(Component.literal("Der Bohrer läuft schon!").withStyle(ChatFormatting.GOLD),
                    true);
            return;
        }
        if (!held.is(ModItems.THERMAL_DRILL)) {
            player.displayClientMessage(Component.literal("Du brauchst einen Thermobohrer (Ammu-Nation).")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ACTIVE.put(player.getUUID(), new Heist(player, vault.immutable(), new int[]{0}));
        WantedSystem.commit(player, 3);
        WantedSystem.title(player, Component.literal("BANKÜBERFALL").withStyle(ChatFormatting.GOLD,
                        ChatFormatting.BOLD),
                Component.literal("Bleib am Tresor, bis der Bohrer durch ist!").withStyle(ChatFormatting.WHITE));
        player.level().playSound(null, vault, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 1.0F);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Heist> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Heist h = it.next();
            ServerPlayer p = h.player;
            if (p.isRemoved() || !p.isAlive() || p.distanceToSqr(h.vault.getCenter()) > 36.0) {
                p.displayClientMessage(Component.literal("Überfall abgebrochen!").withStyle(ChatFormatting.RED),
                        true);
                it.remove();
                continue;
            }
            ServerLevel level = (ServerLevel) p.level();
            int progress = ++h.progress[0];
            if (progress % 10 == 0) {
                level.playSound(null, h.vault, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
                level.sendParticles(ParticleTypes.LAVA, h.vault.getX() + 0.5, h.vault.getY() + 1.0,
                        h.vault.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
                int pct = progress * 100 / DRILL_TICKS;
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < 20; i++) {
                    bar.append(i < pct / 5 ? '|' : '.');
                }
                p.displayClientMessage(Component.literal("Bohren [" + bar + "] " + pct + "%")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            if (progress % 200 == 0) {
                level.playSound(null, h.vault, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 1.0F);
            }
            if (progress >= DRILL_TICKS) {
                openVault(level, p, h.vault);
                it.remove();
            }
        }
    }

    private static void openVault(ServerLevel level, ServerPlayer player, BlockPos vault) {
        int bundles = 8 + level.getRandom().nextInt(8);
        long total = 0;
        for (int i = 0; i < bundles; i++) {
            int value = 1000 + level.getRandom().nextInt(3000);
            total += value;
            ItemEntity cash = new ItemEntity(level, vault.getX() + 0.5, vault.getY() + 1.2, vault.getZ() + 0.5,
                    CashItem.of(value));
            cash.setDeltaMovement(level.getRandom().nextGaussian() * 0.1, 0.25, level.getRandom().nextGaussian() * 0.1);
            level.addFreshEntity(cash);
        }
        EMPTY_UNTIL.put(vault, level.getGameTime() + VAULT_COOLDOWN);
        WantedSystem.commit(player, 4);
        level.playSound(null, vault, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, 0.6F);
        WantedSystem.title(player, Component.literal("TRESOR OFFEN").withStyle(ChatFormatting.GREEN,
                ChatFormatting.BOLD), Component.literal("Beute: " + Economy.format(total) + " - hau ab!")
                .withStyle(ChatFormatting.WHITE));
    }
}
