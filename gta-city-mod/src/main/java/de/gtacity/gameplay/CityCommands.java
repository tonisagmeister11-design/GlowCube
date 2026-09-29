package de.gtacity.gameplay;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import de.gtacity.registry.ModSounds;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.Collection;

/**
 * Chat commands:
 * <ul>
 *     <li>/crew, /crew einladen &lt;Name&gt;, /crew annehmen, /crew verlassen - the crew (see {@link Crew})</li>
 *     <li>/job ja, /job nein - answer a team job request (the [Ja] / [Nein] buttons in the chat run these)</li>
 *     <li>/geld geben|nehmen|setzen &lt;Spieler&gt; &lt;Betrag&gt; - money, only for operators (cheats / server rights)</li>
 * </ul>
 */
public final class CityCommands {
    private CityCommands() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("crew")
                .executes(c -> {
                    Crew.info(c.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("einladen").then(Commands.argument("spieler", EntityArgument.player())
                        .executes(c -> {
                            Crew.invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "spieler"));
                            return 1;
                        })))
                .then(Commands.literal("annehmen").executes(c -> {
                    Crew.accept(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("verlassen").executes(c -> {
                    Crew.leave(c.getSource().getPlayerOrException(), true);
                    return 1;
                })));

        dispatcher.register(Commands.literal("job")
                .then(Commands.literal("ja").executes(c -> {
                    Jobs.acceptTeam(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("nein").executes(c -> {
                    Jobs.declineTeam(c.getSource().getPlayerOrException());
                    return 1;
                })));

        dispatcher.register(Commands.literal("geld")
                .executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(() -> Component.literal("Dein Geld: " + Economy.format(Economy.get(player)))
                            .withStyle(ChatFormatting.GREEN), false);
                    return 1;
                })
                .then(money("geben", 1))
                .then(money("nehmen", -1))
                .then(money("setzen", 0)));
    }

    /** /geld geben|nehmen|setzen <Spieler> <Betrag> - operators only. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> money(String name, int mode) {
        return Commands.literal(name)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("spieler", EntityArgument.players())
                        .then(Commands.argument("betrag", LongArgumentType.longArg(0, 1_000_000_000L))
                                .executes(c -> {
                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "spieler");
                                    long amount = LongArgumentType.getLong(c, "betrag");
                                    for (ServerPlayer p : players) {
                                        long before = Economy.get(p);
                                        long now = switch (mode) {
                                            case 1 -> before + amount;
                                            case -1 -> Math.max(0, before - amount);
                                            default -> amount;
                                        };
                                        Economy.set(p, now);
                                        if (now > before) {
                                            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.CASH,
                                                    SoundSource.PLAYERS, 1.0F, 1.0F);
                                        }
                                        p.sendOverlayMessage(Component.literal("Dein Geld: " + Economy.format(now))
                                                .withStyle(ChatFormatting.GREEN));
                                        c.getSource().sendSuccess(() -> Component.literal(p.getName().getString()
                                                + " hat jetzt " + Economy.format(now)), true);
                                    }
                                    return players.size();
                                })));
    }
}
