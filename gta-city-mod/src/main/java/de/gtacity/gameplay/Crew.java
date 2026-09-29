package de.gtacity.gameplay;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.gtacity.entity.CarEntity;
import de.gtacity.network.Payloads;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multiplayer: players in the same world (LAN, a server, or friends invited with a mod like Essential) can team up
 * as a crew of up to four. Crew mates see each other on the radar and the map, can't shoot each other, and every job
 * one of them starts becomes a partner mission for the whole crew (everyone gets the full pay). Some missions on the
 * boards need a crew. The positions of all players are sent to everybody, so the map shows the others too.
 * <p>
 * Invite: map (M) → tab "Crew", or /crew einladen &lt;name&gt;; the other player clicks [Annehmen] in the chat.
 */
public final class Crew {
    private Crew() {
    }

    public static final int MAX = 4;
    private static final long INVITE_TICKS = 20L * 120;

    /** Crew id (the founder's UUID) per member. */
    private static final Map<UUID, UUID> CREW_OF = new HashMap<>();
    private static final Map<UUID, Set<UUID>> MEMBERS = new HashMap<>();
    /** Open invitations: invited player -> (inviter, game time). */
    private static final Map<UUID, Object[]> INVITES = new HashMap<>();

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> register(dispatcher));
    }

    // ------------------------------------------------------------------ queries

    /** A player by UUID: from the player list, or standing in a world (bots and test players are only there). */
    public static ServerPlayer find(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            return p;
        }
        for (var level : server.getAllLevels()) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer sp) {
                return sp;
            }
        }
        return null;
    }

    /** The other crew members that are online (in any world). */
    public static List<ServerPlayer> mates(ServerPlayer player) {
        List<ServerPlayer> out = new ArrayList<>();
        UUID crew = CREW_OF.get(player.getUUID());
        if (crew == null) {
            return out;
        }
        for (UUID id : MEMBERS.getOrDefault(crew, Set.of())) {
            ServerPlayer p = find(player.level().getServer(), id);
            if (p != null && p != player) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean together(ServerPlayer a, ServerPlayer b) {
        UUID crew = CREW_OF.get(a.getUUID());
        return crew != null && crew.equals(CREW_OF.get(b.getUUID()));
    }

    public static boolean inCrew(ServerPlayer player) {
        return !mates(player).isEmpty();
    }

    // ------------------------------------------------------------------ actions

    public static void invite(ServerPlayer from, ServerPlayer to) {
        if (from == to) {
            from.sendSystemMessage(Component.literal("Du kannst dich nicht selbst einladen.").withStyle(ChatFormatting.RED));
            return;
        }
        if (together(from, to)) {
            from.sendSystemMessage(Component.literal(to.getName().getString() + " ist schon in deiner Crew.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        UUID crew = CREW_OF.get(from.getUUID());
        if (crew != null && MEMBERS.get(crew).size() >= MAX) {
            from.sendSystemMessage(Component.literal("Deine Crew ist voll (höchstens " + MAX + ").")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        INVITES.put(to.getUUID(), new Object[]{from.getUUID(), from.level().getGameTime()});
        from.sendSystemMessage(Component.literal("Einladung an " + to.getName().getString() + " geschickt.")
                .withStyle(ChatFormatting.GREEN));
        Component accept = Component.literal("[Annehmen]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withBold(true).withClickEvent(new ClickEvent.RunCommand("/crew annehmen"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Der Crew beitreten"))));
        to.sendSystemMessage(Component.literal(from.getName().getString() + " lädt dich in die Crew ein. ")
                .withStyle(ChatFormatting.GOLD).append(accept)
                .append(Component.literal("  (oder Karte M → Crew)").withStyle(ChatFormatting.GRAY)));
        to.level().playSound(null, to.getX(), to.getY(), to.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    public static void accept(ServerPlayer player) {
        Object[] invite = INVITES.remove(player.getUUID());
        ServerPlayer from = invite == null ? null
                : find(player.level().getServer(), (UUID) invite[0]);
        if (from == null || player.level().getGameTime() - (long) invite[1] > INVITE_TICKS) {
            player.sendSystemMessage(Component.literal("Keine offene Einladung.").withStyle(ChatFormatting.RED));
            return;
        }
        leave(player, false);
        UUID crew = CREW_OF.computeIfAbsent(from.getUUID(), id -> id);
        Set<UUID> members = MEMBERS.computeIfAbsent(crew, id -> new LinkedHashSet<>());
        members.add(from.getUUID());
        if (members.size() >= MAX) {
            player.sendSystemMessage(Component.literal("Die Crew ist schon voll.").withStyle(ChatFormatting.RED));
            return;
        }
        members.add(player.getUUID());
        CREW_OF.put(player.getUUID(), crew);
        for (ServerPlayer p : online(player.level().getServer(), members)) {
            p.sendSystemMessage(Component.literal(player.getName().getString() + " ist jetzt in der Crew. Crew: "
                    + names(player.level().getServer(), members)).withStyle(ChatFormatting.GREEN));
        }
        player.sendSystemMessage(Component.literal("Jobs, die einer von euch annimmt, macht ihr jetzt zusammen "
                + "(Partnermission). Ihr seht euch auf Radar und Karte in Grün.").withStyle(ChatFormatting.GRAY));
    }

    public static void leave(ServerPlayer player, boolean tell) {
        UUID crew = CREW_OF.remove(player.getUUID());
        if (crew == null) {
            if (tell) {
                player.sendSystemMessage(Component.literal("Du bist in keiner Crew.").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        Set<UUID> members = MEMBERS.get(crew);
        members.remove(player.getUUID());
        MinecraftServer server = player.level().getServer();
        for (ServerPlayer p : online(server, members)) {
            p.sendSystemMessage(Component.literal(player.getName().getString() + " hat die Crew verlassen.")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (members.size() <= 1) {
            members.forEach(CREW_OF::remove);
            MEMBERS.remove(crew);
        }
        if (tell) {
            player.sendSystemMessage(Component.literal("Du hast die Crew verlassen.").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void forget(ServerPlayer player) {
        leave(player, false);
        INVITES.remove(player.getUUID());
    }

    private static List<ServerPlayer> online(MinecraftServer server, Set<UUID> ids) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = find(server, id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    private static String names(MinecraftServer server, Set<UUID> ids) {
        return String.join(", ", online(server, ids).stream().map(p -> p.getName().getString()).toList());
    }

    // ------------------------------------------------------------------ sync

    /** Twice a second: where everybody is, for the radar and the map. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 5) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer viewer : players) {
            List<Payloads.PlayerDot> dots = new ArrayList<>();
            for (ServerPlayer other : ((net.minecraft.server.level.ServerLevel) viewer.level()).players()) {
                if (other == viewer || other.level() != viewer.level() || other.isSpectator()) {
                    continue;
                }
                dots.add(new Payloads.PlayerDot(other.getName().getString(), (int) Math.floor(other.getX()),
                        (int) Math.floor(other.getZ()), other.getYRot(), together(viewer, other),
                        WantedSystem.level(other), other.getVehicle() instanceof CarEntity));
            }
            Object[] invite = INVITES.get(viewer.getUUID());
            String invitedBy = "";
            if (invite != null && viewer.level().getGameTime() - (long) invite[1] <= INVITE_TICKS) {
                ServerPlayer from = find(server, (UUID) invite[0]);
                invitedBy = from == null ? "" : from.getName().getString();
            }
            ServerPlayNetworking.send(viewer, new Payloads.Players(dots, invitedBy));
        }
    }

    // ------------------------------------------------------------------ commands

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("crew")
                .executes(Crew::info)
                .then(Commands.literal("einladen").then(Commands.argument("spieler", EntityArgument.player())
                        .executes(c -> {
                            invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "spieler"));
                            return 1;
                        })))
                .then(Commands.literal("annehmen").executes(c -> {
                    accept(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("verlassen").executes(c -> {
                    leave(c.getSource().getPlayerOrException(), true);
                    return 1;
                })));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        UUID crew = CREW_OF.get(player.getUUID());
        if (crew == null) {
            player.sendSystemMessage(Component.literal("Keine Crew. Einladen: /crew einladen <Name> oder Karte (M) → "
                    + "Crew.").withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.literal("Deine Crew: " + names(player.level().getServer(),
                    MEMBERS.get(crew))).withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }
}
