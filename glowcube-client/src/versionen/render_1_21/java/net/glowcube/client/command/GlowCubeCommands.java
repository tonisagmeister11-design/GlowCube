package net.glowcube.client.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Module;
import net.glowcube.client.gui.Layout;
import net.glowcube.client.integration.SeedBridge;
import net.minecraft.network.chat.Component;

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
 * </ul>
 */
public final class GlowCubeCommands {
    private GlowCubeCommands() {
    }

    public static void registrieren() {
        ClientCommandRegistrationCallback.EVENT.register((zweig, zugriff) ->
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
                        })));
    }

    private static void sagen(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource quelle,
                              String text) {
        quelle.sendFeedback(Component.literal("[GlowCube] " + text));
    }
}
