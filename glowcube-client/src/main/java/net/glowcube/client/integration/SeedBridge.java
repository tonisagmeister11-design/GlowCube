package net.glowcube.client.integration;

import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.glowcube.client.GlowCubeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.lang.reflect.Method;
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

    /**
     * Den Seed von Hand setzen.
     *
     * <p>Fuer den Fall, dass man ihn schon kennt - im Einzelspieler sagt
     * {@code /seed} ihn sofort, und wer SeedCrackerX einmal hat laufen
     * lassen, will nicht jedes Mal von vorn anfangen. OreSim baut danach
     * seine Karte neu auf.
     */
    public static void setzeSeed(Long seed) {
        gefundenerSeed = seed;
        try {
            if (GlowCubeClient.modules() != null) {
                GlowCubeClient.modules().get(
                        net.glowcube.client.module.world.OreSim.class).seedGewechselt();
            }
        } catch (RuntimeException fehler) {
            GlowCubeClient.LOGGER.warn("OreSim liess sich nicht benachrichtigen", fehler);
        }
    }

    // ---------------------------------------------------------- Fortschritt

    /**
     * Wie viele Bit an Information SeedCrackerX bisher gesammelt hat.
     *
     * Der Weltseed hat 48 nutzbare Bit; sobald genug Merkmale beisammen sind,
     * faellt die Rechnung durch. Die Zahl waechst also sichtbar mit, waehrend
     * man Flaeche abfaehrt - das ist der Fortschrittsbalken, den es sonst
     * nicht gibt.
     *
     * Abgefragt wird ueber Reflexion, mit Absicht: SeedCrackerX ist optional,
     * und je nach Fassung kann die Klasse anders aussehen. Faellt etwas davon
     * weg, gibt es hier null statt eines Absturzes.
     */
    private static Method bitsMethode;
    private static Method storageMethode;
    private static Method getMethode;
    private static boolean reflexionVersucht;

    public static Double bits() {
        if (!reflexionVersucht) {
            reflexionVersucht = true;
            try {
                Class<?> cracker = Class.forName("kaptainwutax.seedcrackerX.SeedCracker");
                getMethode = cracker.getMethod("get");
                storageMethode = cracker.getMethod("getDataStorage");
                bitsMethode = storageMethode.getReturnType().getMethod("getBaseBits");
            } catch (Throwable egal) {
                GlowCubeClient.LOGGER.info("SeedCrackerX nicht gefunden - keine Bit-Anzeige");
                bitsMethode = null;
            }
        }
        if (bitsMethode == null) {
            return null;
        }
        try {
            Object instanz = getMethode.invoke(null);
            if (instanz == null) {
                return null;
            }
            Object speicher = storageMethode.invoke(instanz);
            if (speicher == null) {
                return null;
            }
            return ((Number) bitsMethode.invoke(speicher)).doubleValue();
        } catch (Throwable egal) {
            return null;
        }
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
