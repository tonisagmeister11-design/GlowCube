package net.glowcube.client.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
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
 *   <li>{@code /coordinates} (auch {@code /koordinaten}) schreibt die eigene
 *       Position in den Chat - Klick darauf kopiert sie.</li>
 * </ul>
 */
public final class GlowCubeCommands {
    private GlowCubeCommands() {
    }

    public static void registrieren() {
        ClientCommandRegistrationCallback.EVENT.register((zweig, zugriff) -> {
            zweig.register(ClientCommands.literal("coordinates").executes(kontext -> koordinaten(kontext.getSource())));
            zweig.register(ClientCommands.literal("koordinaten").executes(kontext -> koordinaten(kontext.getSource())));
            zweig.register(ClientCommands.literal("strike")
                    .executes(kontext -> {
                        sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.zielMarkieren());
                        return 1;
                    })
                    .then(zielKoordinaten()));
            zweig.register(ClientCommands.literal("glowcube")
                        .then(ClientCommands.literal("seed")
                                .executes(kontext -> {
                                    Long seed = SeedBridge.seed();
                                    sagen(kontext.getSource(), seed == null
                                            ? "Kein Seed bekannt."
                                            : "Seed: " + seed);
                                    return 1;
                                })
                                .then(ClientCommands.argument("wert", LongArgumentType.longArg())
                                        .executes(kontext -> {
                                            long wert = LongArgumentType.getLong(kontext, "wert");
                                            SeedBridge.setzeSeed(wert);
                                            sagen(kontext.getSource(), "Seed gesetzt: " + wert);
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("ziel")
                                .executes(kontext -> {
                                    sagen(kontext.getSource(), net.glowcube.client.agent.AgentSteuerung.zielMarkieren());
                                    return 1;
                                })
                                .then(zielKoordinaten()))
                        .then(ClientCommands.literal("fenster")
                                .executes(kontext -> {
                                    Layout.zuruecksetzen();
                                    GlowCubeClient.config().save();
                                    sagen(kontext.getSource(), "Fenster zurueckgesetzt.");
                                    return 1;
                                }))
                        .then(ClientCommands.argument("modul",
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
        return ClientCommands.argument("x", IntegerArgumentType.integer())
                .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                        .then(ClientCommands.argument("z", IntegerArgumentType.integer())
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
        quelle.sendFeedback(Component.literal("[GlowCube] Du stehst bei ").append(zahlen)
                .append(Component.literal(" ")).append(strike));
        return 1;
    }

    private static void sagen(FabricClientCommandSource quelle,
                              String text) {
        quelle.sendFeedback(Component.literal("[GlowCube] " + text));
    }
}
