package net.glowcube.client.integration;

import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.glowcube.client.GlowCubeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Nimmt den Seed entgegen, sobald SeedCrackerX ihn geknackt hat.
 *
 * SeedCrackerX sammelt beim Spielen Merkmale der Welt und rechnet daraus den
 * Seed zurueck. Fertig ist es damit irgendwann - man muss nicht danebensitzen.
 * Diese Klasse sorgt dafuer, dass das Ergebnis nicht in einer Chatzeile
 * untergeht: es landet zusaetzlich in einer Datei und bleibt abrufbar.
 *
 * Eingehaengt ueber den Fabric-Einstiegspunkt "seedcrackerx" (siehe
 * fabric.mod.json). Ohne SeedCrackerX passiert hier nichts.
 */
public final class SeedBridge implements SeedCrackerAPI {
    private static volatile Long gefundenerSeed;

    /** Der zuletzt gefundene Seed, oder null. */
    public static Long seed() {
        return gefundenerSeed;
    }

    @Override
    public void pushWorldSeed(long worldSeed) {
        gefundenerSeed = worldSeed;
        GlowCubeClient.LOGGER.info("SeedCrackerX meldet den Weltseed: {}", worldSeed);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Zweimal mit Absicht: die Zeile ueber der Hotbar faellt sofort auf,
            // die Chatzeile bleibt stehen und laesst sich markieren.
            mc.player.displayClientMessage(Component.literal("[GlowCube] Seed: " + worldSeed), true);
            // false heisst Chat, true heisst die Zeile ueber der Hotbar.
            mc.player.displayClientMessage(
                    Component.literal("[GlowCube] Weltseed: " + worldSeed), false);
            mc.player.displayClientMessage(Component.literal(
                    "[GlowCube] Auch gespeichert in config/glowcube-seeds.txt"), false);
        }
        merken(worldSeed);
    }

    /**
     * In eine Textdatei neben der Konfiguration schreiben. Eine Zeile pro Fund,
     * mit Zeitstempel - damit der Seed auch nach dem Beenden noch da ist.
     */
    private void merken(long worldSeed) {
        Path datei = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("glowcube-seeds.txt");
        String zeile = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + "  " + worldSeed + System.lineSeparator();
        try {
            Files.createDirectories(datei.getParent());
            Files.write(datei, zeile.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            GlowCubeClient.LOGGER.error("Konnte {} nicht schreiben", datei, e);
        }
    }
}
