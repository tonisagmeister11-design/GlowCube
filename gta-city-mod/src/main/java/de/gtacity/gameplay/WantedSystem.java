package de.gtacity.gameplay;

import de.gtacity.entity.HelicopterEntity;
import de.gtacity.entity.NpcEntity;
import de.gtacity.entity.PoliceEntity;
import de.gtacity.registry.ModAttachments;
import de.gtacity.world.CityLayout;
import de.gtacity.world.CityPlaces;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GTA style wanted level (0-5 stars). Crimes raise it, police hunt you, and when no officer has seen you for a
 * while the stars disappear again.
 */
public final class WantedSystem {
    private WantedSystem() {
    }

    private static final class State {
        int lostSightTicks;
        int arrestHits;
        long lastArrestHit;
        int kills;
        long lastKill;
        /** Until then the police is still on its way: the stars cannot fade yet. */
        long respondingUntil;
    }

    /** Time the patrol cars get to arrive from out of sight before hiding starts to count. */
    private static final long RESPONSE_TIME = 20L * 20;

    /** Killings are forgotten after five minutes without a new one. */
    private static final long KILL_MEMORY = 20L * 60 * 5;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    public static int level(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) {
            return 0;
        }
        Integer level = player.getAttached(ModAttachments.WANTED);
        return level == null ? 0 : level;
    }

    public static void setLevel(Player player, int level) {
        int clamped = Math.max(0, Math.min(5, level));
        int old = level(player);
        if (clamped == old) {
            return;
        }
        player.setAttached(ModAttachments.WANTED, clamped);
        State s = state(player);
        s.lostSightTicks = 0;
        if (clamped == 0) {
            player.setAttached(ModAttachments.WANTED_HIDDEN, 0);
            s.kills = 0;
            s.arrestHits = 0;
        } else if (clamped > old) {
            s.respondingUntil = player.level().getGameTime() + RESPONSE_TIME;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
        }
    }

    /** Raise the wanted level to at least {@code stars}. */
    public static void commit(Player player, int stars) {
        if (player.getAbilities().instabuild && !CityRules.CREATIVE_WANTED) {
            return;
        }
        if (level(player) < stars) {
            setLevel(player, stars);
        }
        state(player).lostSightTicks = 0;
    }

    // ------------------------------------------------------------------ crimes

    /** Every third killed pedestrian is worth one more star: 3 kills = 1 star, 6 kills = 2 stars ... */
    public static void onNpcKilled(Player player, NpcEntity npc) {
        State s = state(player);
        long now = player.level().getGameTime();
        if (now - s.lastKill > KILL_MEMORY) {
            s.kills = 0;
        }
        s.lastKill = now;
        s.kills++;
        if (s.kills >= 3) {
            commit(player, Math.min(5, s.kills / 3));
        }
    }

    public static void onCopHurt(Player player) {
        commit(player, 1);
    }

    public static void onCopKilled(Player player) {
        commit(player, Math.max(2, Math.min(5, level(player) + 1)));
    }

    /** Stealing a police car always counts. An ordinary car only when an officer sees it. */
    public static void onCarJacked(Player player, boolean policeCar) {
        if (policeCar) {
            commit(player, 2);
        } else if (copSees(player, 40.0)) {
            commit(player, 1);
        }
    }

    public static boolean copSees(Player player, double range) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        for (PoliceEntity cop : level.getEntitiesOfClass(PoliceEntity.class, player.getBoundingBox().inflate(range))) {
            if (cop.hasLineOfSight(player)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ arrest / death

    public static void arrestProgress(ServerPlayer player) {
        State s = state(player);
        long now = player.level().getGameTime();
        if (now - s.lastArrestHit > 200) {
            s.arrestHits = 0;
        }
        s.lastArrestHit = now;
        s.arrestHits++;
        player.sendOverlayMessage(Component.literal("Die Polizei nimmt dich fest! (" + s.arrestHits + "/3)")
                .withStyle(ChatFormatting.BLUE));
        if (s.arrestHits >= 3) {
            busted(player);
        }
    }

    public static void busted(ServerPlayer player) {
        long fine = Math.min(Economy.get(player), 100 + Economy.get(player) / 10);
        Economy.add(player, -fine);
        setLevel(player, 0);
        BlockPos station = CityPlaces.nearest(CityLayout.LotType.POLICE, player.getBlockX(), player.getBlockZ());
        player.teleportTo(station.getX() + 0.5, station.getY(), station.getZ() + 0.5);
        player.setHealth(player.getMaxHealth());
        title(player, Component.literal("BUSTED").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD),
                Component.literal("Strafe: " + Economy.format(fine)).withStyle(ChatFormatting.WHITE));
    }

    public static void onPlayerDeath(ServerPlayer player) {
        long bill = Math.min(Economy.get(player), 200 + Economy.get(player) / 20);
        Economy.add(player, -bill);
        setLevel(player, 0);
        title(player, Component.literal("WASTED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal("Krankenhausrechnung: " + Economy.format(bill)).withStyle(ChatFormatting.WHITE));
    }

    public static void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int stars = level(player);
            if (stars == 0 || !player.isAlive()) {
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            State s = state(player);
            boolean seen = HelicopterEntity.seesPlayer(level, player);
            for (PoliceEntity cop : level.getEntitiesOfClass(PoliceEntity.class,
                    player.getBoundingBox().inflate(48.0))) {
                if (cop.hasLineOfSight(player)) {
                    seen = true;
                    if (cop.getTarget() == null) {
                        cop.setTarget(player);
                    }
                }
            }
            if (seen || level.getGameTime() < s.respondingUntil) {
                seen = true;
                s.lostSightTicks = 0;
            } else {
                s.lostSightTicks += 20;
            }
            player.setAttached(ModAttachments.WANTED_HIDDEN, seen ? 0 : 1);
            int needed = (6 + stars * 4) * 20;
            if (s.lostSightTicks >= needed) {
                setLevel(player, 0);
                player.sendOverlayMessage(Component.literal("Du hast die Cops abgehängt!")
                        .withStyle(ChatFormatting.GREEN));
                continue;
            }
            PoliceDispatch.dispatch(level, player, stars);
        }
    }

    public static void forget(Player player) {
        STATES.remove(player.getUUID());
    }
}
