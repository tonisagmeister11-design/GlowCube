package net.glowcube.client.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Module;
import net.glowcube.client.gui.Layout;
import net.glowcube.client.integration.SeedBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Chatbefehle. Rein clientseitig - der Server sieht davon nichts.
 *
 * <ul>
 *   <li>{@code /glowcube seed <zahl>} setzt den Weltseed von Hand. Das ist
 *       der Weg, den OreSim braucht, wenn man den Seed schon kennt und ihn
 *       nicht erst von SeedCrackerX erarbeiten lassen will.</li>
 *   <li>{@code /glowcube seed} zeigt, was gerade bekannt ist.</li>
 *   <li>{@code /glowcube <modul>} schaltet ein Modul, ohne eine Taste zu
 *       belegen.</li>
 *   <li>{@code /glowcube fenster} raeumt die ClickGUI-Fenster ins Raster.</li>
 *   <li>{@code /glowcube ziel} markiert, worauf man schaut (bis 500 Bloecke),
 *       als Ziel fuer den Orbital Strike.</li>
 *   <li>{@code /strike x y z} (oder {@code /glowcube ziel x y z}) setzt das
 *       Ziel auf feste Koordinaten; {@code /strike} allein nimmt, worauf man
 *       schaut.</li>
 *   <li>{@code /agentort [x y z | weg]} setzt den Einsatzort der Agenten
 *       (ohne Zahlen: wo man steht); {@code /agentkiste [weg]} macht die Kiste,
 *       auf die man schaut, zur Sammelkiste.</li>
 *   <li>{@code /panic} schaltet sofort alle aktivierten Features aus.</li>
 *   <li>{@code /wp} (auch {@code /wegpunkt}) zeigt die Wegpunkte dieser Welt;
 *       {@code /wp add <name> [x y z]}, {@code /wp del <name>} und
 *       {@code /wp tp <name>} legen an, loeschen und teleportieren.</li>
 *   <li>{@code /coordinates} (auch {@code /koordinaten}) schreibt die eigene
 *       Position in den Chat - Klick darauf kopiert sie.</li>
 * </ul>
 */
public final class GlowCubeCommands {
    private GlowCubeCommands() {
    }

    public static void registrieren() {
        ClientCommandRegistrationCallback.EVENT.register((zweig, zugriff) -> {
            zweig.register(ClientCommandManager.literal("coordinates").executes(kontext -> koordinaten(kontext.getSource())));
            zweig.register(ClientCommandManager.literal("koordinaten").executes(kontext -> koordinaten(kontext.getSource())));
            zweig.register(ClientCommandManager.literal("agentort")
                    .executes(kontext -> {
                        net.minecraft.core.BlockPos p = kontext.getSource().getPlayer().blockPosition();
                        sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.ortSetzen(p.getX(), p.getY(), p.getZ()));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("weg").executes(kontext -> {
                        sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.ortLoeschen());
                        return 1;
                    }))
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                            .executes(kontext -> {
                                                sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.ortSetzen(
                                                        IntegerArgumentType.getInteger(kontext, "x"),
                                                        IntegerArgumentType.getInteger(kontext, "y"),
                                                        IntegerArgumentType.getInteger(kontext, "z")));
                                                return 1;
                                            })))));
            zweig.register(ClientCommandManager.literal("agentkiste")
                    .executes(kontext -> {
                        sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.kisteMarkieren());
                        return 1;
                    })
                    .then(ClientCommandManager.literal("weg").executes(kontext -> {
                        sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.kisteLoeschen());
                        return 1;
                    })));
            // Wegpunkte: /wp, /wp add <name> [x y z], /wp del <name>, /wp tp <name>
            for (String wort : new String[] {"wp", "wegpunkt"}) {
                zweig.register(ClientCommandManager.literal(wort)
                        .executes(kontext -> {
                            sagen(kontext.getSource(), net.glowcube.client.module.karte.Wegpunkte.auflisten());
                            return 1;
                        })
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(kontext -> {
                                            net.minecraft.core.BlockPos p = kontext.getSource().getPlayer().blockPosition();
                                            sagen(kontext.getSource(), net.glowcube.client.module.karte.Wegpunkte.hinzufuegen(
                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(kontext, "name"),
                                                    p.getX(), p.getY(), p.getZ()));
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                        .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                                .executes(kontext -> {
                                                                    sagen(kontext.getSource(), net.glowcube.client.module.karte.Wegpunkte.hinzufuegen(
                                                                            com.mojang.brigadier.arguments.StringArgumentType.getString(kontext, "name"),
                                                                            IntegerArgumentType.getInteger(kontext, "x"),
                                                                            IntegerArgumentType.getInteger(kontext, "y"),
                                                                            IntegerArgumentType.getInteger(kontext, "z")));
                                                                    return 1;
                                                                }))))))
                        .then(ClientCommandManager.literal("del")
                                .then(ClientCommandManager.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(kontext -> {
                                            sagen(kontext.getSource(), net.glowcube.client.module.karte.Wegpunkte.entfernen(
                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(kontext, "name")));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("tp")
                                .then(ClientCommandManager.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(kontext -> {
                                            sagen(kontext.getSource(), net.glowcube.client.module.karte.Wegpunkte.teleport(
                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(kontext, "name")));
                                            return 1;
                                        }))));
            }
            zweig.register(ClientCommandManager.literal("panic").executes(kontext -> {
                sagen(kontext.getSource(), "Panic: " + net.glowcube.client.module.misc.Panic.ausfuehren()
                        + " Features ausgeschaltet.");
                return 1;
            }));
            zweig.register(ClientCommandManager.literal("strike")
                    .executes(kontext -> {
                        sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.zielMarkieren());
                        return 1;
                    })
                    .then(zielKoordinaten()));
            zweig.register(ClientCommandManager.literal("glowcube")
                        .then(ClientCommandManager.literal("seed")
                                .executes(kontext -> {
                                    Long seed = SeedBridge.seed();
                                    sagen(kontext.getSource(), seed == null
                                            ? "Kein Seed bekannt."
                                            : "Seed: " + seed);
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("wert", LongArgumentType.longArg())
                                        .executes(kontext -> {
                                            long wert = LongArgumentType.getLong(kontext, "wert");
                                            SeedBridge.setzeSeed(wert);
                                            sagen(kontext.getSource(), "Seed gesetzt: " + wert);
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("ziel")
                                .executes(kontext -> {
                                    sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.zielMarkieren());
                                    return 1;
                                })
                                .then(zielKoordinaten()))
                        .then(ClientCommandManager.literal("fenster")
                                .executes(kontext -> {
                                    Layout.zuruecksetzen();
                                    GlowCubeClient.config().save();
                                    sagen(kontext.getSource(), "Fenster zurueckgesetzt.");
                                    return 1;
                                }))
                        .then(ClientCommandManager.argument("modul",
                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(kontext -> {
                                    String name = com.mojang.brigadier.arguments.StringArgumentType
                                            .getString(kontext, "modul");
                                    Module modul = GlowCubeClient.modules().get(name);
                                    if (modul == null) {
                                        sagen(kontext.getSource(), "Kein Modul namens " + name);
                                        return 0;
                                    }
                                    modul.toggle();
                                    GlowCubeClient.config().save();
                                    sagen(kontext.getSource(), modul.name() + ": "
                                            + (modul.isEnabled() ? "an" : "aus"));
                                    return 1;
                                }))
                        .executes(kontext -> {
                            sagen(kontext.getSource(), String.format(Locale.ROOT,
                                    "GlowCube %s - %d Module, %d aktiv. "
                                            + "Rechte Umschalttaste oeffnet das Menue.",
                                    GlowCubeClient.VERSION,
                                    GlowCubeClient.modules().all().size(),
                                    GlowCubeClient.modules().enabled().size()));
                            return 1;
                        }));
        });
    }

    /** x y z als drei ganze Zahlen - so, wie /coordinates sie kopiert. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, Integer> zielKoordinaten() {
        return ClientCommandManager.argument("x", IntegerArgumentType.integer())
                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                        .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                .executes(kontext -> {
                                    sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.zielSetzen(
                                            IntegerArgumentType.getInteger(kontext, "x"),
                                            IntegerArgumentType.getInteger(kontext, "y"),
                                            IntegerArgumentType.getInteger(kontext, "z")));
                                    return 1;
                                })));
    }

    /**
     * Die eigene Position in den Chat: Klick auf die Zahlen kopiert sie, Klick
     * auf [Als Strike-Ziel] schreibt /strike mit ihnen ins Chatfeld.
     */
    private static int koordinaten(FabricClientCommandSource quelle) {
        net.minecraft.core.BlockPos pos = quelle.getPlayer().blockPosition();
        String text = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        MutableComponent zahlen = Component.literal("[" + text + "]").withStyle(stil -> stil
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(text))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Klicken zum Kopieren"))));
        MutableComponent strike = Component.literal("[Als Strike-Ziel]").withStyle(stil -> stil
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent.SuggestCommand("/strike " + text))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/strike " + text + " ins Chatfeld"))));
        MutableComponent einsatz = Component.literal("[Als Einsatzort]").withStyle(stil -> stil
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.SuggestCommand("/agentort " + text))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/agentort " + text + " ins Chatfeld"))));
        quelle.sendFeedback(Component.literal("[GlowCube] Du stehst bei ").append(zahlen)
                .append(Component.literal(" ")).append(strike).append(Component.literal(" ")).append(einsatz));
        return 1;
    }

    private static void sagen(FabricClientCommandSource quelle,
                              String text) {
        quelle.sendFeedback(Component.literal("[GlowCube] " + text));
    }
}
