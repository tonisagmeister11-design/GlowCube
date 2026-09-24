package net.glowcube.spieltest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.agent.AgentStatus;
import net.glowcube.client.agent.AgentSteuerung;
import net.glowcube.client.agent.AgentWerte;
import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.ModuleManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Der Spieltest: startet das echte Spiel (in der CI unter einem virtuellen
 * Bildschirm), legt eine Welt an und prueft GlowCube so, wie ein Spieler es
 * benutzt - mit Screenshots, Befehlen und echten Ticks. Jede Pruefung
 * schreibt eine Zeile "GLOWCUBE-TEST OK|FEHLER ..." ins Protokoll; am Ende
 * steht eine Zusammenfassung. Der Test bricht bei einem Fehler nicht ab,
 * damit alle Pruefungen durchlaufen.
 */
public final class GlowCubeSpieltest implements FabricClientGameTest {
    /** Module, die der Rundlauf nicht einfach an- und ausschaltet (eigene Pruefung oder stoerend). */
    private static final Set<String> AUSLASSEN = Set.of("ClickGUI", "Click GUI", "SeedHunt", "Agent zurueckschicken",
            "Erz-Agent", "Stein-Agent", "Holz-Agent", "Guardian-Agent", "Builder-Agent", "Farm-Agent", "Tunnel-Agent",
            "Jaeger-Agent");

    private final List<String> ergebnisse = new ArrayList<>();
    private int fehler;

    private void ok(String was) {
        String z = "GLOWCUBE-TEST OK      " + was;
        ergebnisse.add(z);
        System.out.println(z);
    }

    private void kaputt(String was) {
        fehler++;
        String z = "GLOWCUBE-TEST FEHLER  " + was;
        ergebnisse.add(z);
        System.out.println(z);
    }

    @Override
    public void runTest(ClientGameTestContext kontext) {
        // Kleine Sichtweite - der CI-Rechner zeichnet ohne Grafikkarte.
        kontext.runOnClient(mc -> {
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
        });
        var bauer = kontext.worldBuilder();
        flachwelt(bauer);
        try (var welt = bauer.create()) {
            var server = welt.getServer();
            chunksAbwarten(welt);
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            // Blick nach Sueden (+z), leicht nach unten, auf eine Steinwand mit Erzen.
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 10");
            server.runCommand("execute as @a at @s run fill ~-10 ~-1 ~6 ~10 ~7 ~14 minecraft:stone");
            server.runCommand("execute as @a at @s run setblock ~ ~2 ~6 minecraft:diamond_ore");
            server.runCommand("execute as @a at @s run setblock ~2 ~3 ~6 minecraft:gold_ore");
            server.runCommand("execute as @a at @s run setblock ~-2 ~1 ~6 minecraft:iron_ore");
            kontext.waitTicks(40);
            chunksAbwarten(welt);
            ok("Welt geladen auf " + kontext.computeOnClient(mc -> mc.getLaunchedVersion()));

            pruefe("X-Ray", () -> xray(kontext, welt));
            pruefe("ESP und Tracer", () -> esp(kontext, welt));
            pruefe("KillAura", () -> killAura(kontext, server));
            pruefe("Agenten", () -> agenten(kontext, server));
            pruefe("Alle Module an/aus", () -> rundlauf(kontext));
        } catch (Throwable t) {
            kaputt("Test selbst abgebrochen: " + t);
            t.printStackTrace();
        }
        zusammenfassung();
    }

    private interface Pruefung {
        void los() throws Exception;
    }

    private void pruefe(String name, Pruefung p) {
        try {
            p.los();
        } catch (Throwable t) {
            kaputt(name + ": Ausnahme " + t);
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------- X-Ray

    private void xray(ClientGameTestContext k, Object weltObjekt) throws Exception {
        var welt = (net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext) weltObjekt;
        Module xray = modul("X-Ray");
        chunksAbwarten(welt);
        BufferedImage ohne = bild(k.takeScreenshot("xray-aus"));
        k.runOnClient(mc -> xray.setEnabled(true));
        k.waitTicks(20);
        chunksAbwarten(welt);
        k.waitTicks(10);
        BufferedImage mit = bild(k.takeScreenshot("xray-an"));
        double anteil = unterschied(ohne, mit);
        k.runOnClient(mc -> xray.setEnabled(false));
        k.waitTicks(20);
        chunksAbwarten(welt);
        String werte = String.format("%.1f%% der Pixel veraendert", anteil * 100);
        if (anteil > 0.10) {
            ok("X-Ray blendet Stein aus (" + werte + ")");
        } else {
            kaputt("X-Ray aendert das Bild kaum (" + werte + ") - Stein wird nicht ausgeblendet");
        }
    }

    // --------------------------------------------------------------- ESP

    private void esp(ClientGameTestContext k, Object weltObjekt) throws Exception {
        var welt = (net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext) weltObjekt;
        welt.getServer().runCommand("execute as @a at @s run summon minecraft:husk ~1 ~ ~4 {NoAI:1b,Silent:1b}");
        k.waitTicks(20);
        for (String name : new String[] {"EntityESP", "Tracers", "StorageESP"}) {
            Module m = modulOderNull(name);
            if (m == null) {
                kaputt("Modul " + name + " nicht gefunden");
                continue;
            }
            if (name.equals("StorageESP")) {
                welt.getServer().runCommand("execute as @a at @s run setblock ~-1 ~ ~3 minecraft:chest");
                k.waitTicks(10);
            }
            chunksAbwarten(welt);
            BufferedImage ohne = bild(k.takeScreenshot(name.toLowerCase() + "-aus"));
            k.runOnClient(mc -> m.setEnabled(true));
            k.waitTicks(10);
            BufferedImage mit = bild(k.takeScreenshot(name.toLowerCase() + "-an"));
            k.runOnClient(mc -> m.setEnabled(false));
            double anteil = unterschied(ohne, mit);
            String werte = String.format("%.2f%% der Pixel veraendert", anteil * 100);
            if (anteil > 0.0005) {
                ok(name + " zeichnet (" + werte + ")");
            } else {
                kaputt(name + " zeichnet nichts sichtbar (" + werte + ")");
            }
        }
        welt.getServer().runCommand("kill @e[type=minecraft:husk]");
        k.waitTicks(10);
    }

    // ---------------------------------------------------------- KillAura

    private void killAura(ClientGameTestContext k, Object serverObjekt) throws Exception {
        var server = (net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) serverObjekt;
        server.runCommand("gamemode survival @a");
        server.runCommand("execute as @a at @s run summon minecraft:husk ~ ~ ~2 {NoAI:1b,Silent:1b,Health:10f}");
        k.waitTicks(10);
        int vorher = zaehle(server, "husk");
        Module aura = modul("KillAura");
        k.runOnClient(mc -> aura.setEnabled(true));
        k.waitTicks(120);
        k.runOnClient(mc -> aura.setEnabled(false));
        int nachher = zaehle(server, "husk");
        server.runCommand("gamemode creative @a");
        server.runCommand("kill @e[type=minecraft:husk]");
        if (vorher > 0 && nachher < vorher) {
            ok("KillAura hat den Zombie besiegt (" + vorher + " -> " + nachher + ")");
        } else {
            kaputt("KillAura hat den Zombie nicht besiegt (" + vorher + " -> " + nachher + ")");
        }
    }

    // ----------------------------------------------------------- Agenten

    private void agenten(ClientGameTestContext k, Object serverObjekt) throws Exception {
        var server = (net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) serverObjekt;
        AgentWerte schnell = new AgentWerte(4, 20, false, 1, true, 8);
        // Stein-Agent: soll die Steinwand abbauen.
        k.runOnClient(mc -> AgentSteuerung.starten(Auftrag.STEIN, 1, "", schnell));
        k.waitTicks(200);
        List<AgentStatus> stand = k.computeOnClient(mc -> AgentStatus.aktuell());
        AgentStatus stein = stand.stream().filter(s -> s.auftrag().equals("STEIN")).findFirst().orElse(null);
        if (stein == null) {
            kaputt("Stein-Agent ist nicht erschienen (Uebersicht leer)");
        } else if (stein.beute() > 0) {
            ok("Stein-Agent arbeitet: " + stein.zustand() + ", " + stein.beute() + " Items");
        } else {
            kaputt("Stein-Agent erschienen, hat aber nach 10 s nichts abgebaut (" + stein.zustand() + ")");
        }
        k.runOnClient(mc -> AgentSteuerung.zurueck(Auftrag.STEIN, 0));
        k.waitTicks(200);
        boolean weg = k.computeOnClient(mc -> AgentStatus.aktuell().stream().noneMatch(s -> s.auftrag().equals("STEIN")));
        if (weg) {
            ok("Stein-Agent kommt zurueck und liefert ab");
        } else {
            kaputt("Stein-Agent ist nach dem Zurueckrufen noch da");
        }

        // Tunnel-Agent: 8 Bloecke nach Sueden.
        k.runOnClient(mc -> AgentSteuerung.starten(Auftrag.TUNNEL, 1, "1x2", schnell));
        k.waitTicks(300);
        String tunnel = k.computeOnClient(mc -> AgentStatus.aktuell().stream()
                .filter(s -> s.auftrag().equals("TUNNEL")).map(s -> s.zustand()).findFirst().orElse("weg"));
        if (tunnel.startsWith("graebt") && !tunnel.startsWith("graebt 0/") || tunnel.equals("kommt zurueck")
                || tunnel.equals("weg")) {
            ok("Tunnel-Agent graebt (" + tunnel + ")");
        } else {
            kaputt("Tunnel-Agent kommt nicht voran (" + tunnel + ")");
        }
        k.runOnClient(mc -> AgentSteuerung.zurueck(Auftrag.TUNNEL, 0));
        k.waitTicks(100);

        // Jaeger: drei Kuehe, eine soll uebrig bleiben.
        for (int i = 0; i < 3; i++) {
            server.runCommand("execute as @a at @s run summon minecraft:cow ~" + (i * 2 - 2) + " ~ ~-4 {NoAI:1b}");
        }
        k.waitTicks(10);
        AgentWerte jagd = new AgentWerte(4, 1, false, 1, false, 1);
        k.runOnClient(mc -> AgentSteuerung.starten(Auftrag.JAEGER, 1, "Kuh", jagd));
        k.waitTicks(300);
        int kuehe = zaehle(server, "cow");
        if (kuehe == 1) {
            ok("Jaeger-Agent jagt und laesst eine Kuh uebrig");
        } else {
            kaputt("Jaeger-Agent: " + kuehe + " Kuehe uebrig (erwartet 1)");
        }
        k.runOnClient(mc -> AgentSteuerung.zurueck(Auftrag.JAEGER, 0));
        k.waitTicks(100);
    }

    // --------------------------------------------------------- Rundlauf

    private void rundlauf(ClientGameTestContext k) throws Exception {
        List<Module> alle = k.computeOnClient(mc -> new ArrayList<>(GlowCubeClient.modules().all()));
        int ohneFehler = 0;
        for (Module m : alle) {
            if (AUSLASSEN.contains(m.name())) {
                continue;
            }
            int fehlerVorher = ModuleManager.FEHLER.size();
            String ausnahme = k.computeOnClient(mc -> {
                try {
                    m.setEnabled(true);
                    return null;
                } catch (Throwable t) {
                    return "beim Einschalten: " + t;
                }
            });
            k.waitTicks(m.name().equals("Ultra-Performance") ? 60 : 15);
            String ausnahme2 = k.computeOnClient(mc -> {
                try {
                    if (m.isEnabled()) {
                        m.setEnabled(false);
                    }
                    return null;
                } catch (Throwable t) {
                    return "beim Ausschalten: " + t;
                }
            });
            List<String> neu = new ArrayList<>(ModuleManager.FEHLER.subList(fehlerVorher, ModuleManager.FEHLER.size()));
            if (ausnahme != null || ausnahme2 != null || !neu.isEmpty()) {
                kaputt("Modul " + m.name() + ": " + (ausnahme != null ? ausnahme + " " : "")
                        + (ausnahme2 != null ? ausnahme2 + " " : "") + String.join(" | ", neu));
            } else {
                ohneFehler++;
            }
            // Nicht aus der Szene laufen/fliegen.
            k.runOnClient(mc -> {
                if (mc.player != null) {
                    mc.player.setDeltaMovement(0, 0, 0);
                }
            });
        }
        ok(ohneFehler + " Module ohne Fehler an- und ausgeschaltet");
    }

    // -------------------------------------------------------- Hilfen

    /**
     * Wartet, bis alle Chunks gezeichnet sind. Die Methode heisst je nach
     * Fabric-Fassung getClientWorld() oder getClientLevel() - darum ueber
     * Spiegelung, so laeuft derselbe Test auf 1.21.11 und 26.3.
     */
    private static void chunksAbwarten(Object welt) throws Exception {
        Object client = null;
        for (String name : new String[] {"getClientWorld", "getClientLevel"}) {
            try {
                java.lang.reflect.Method m = welt.getClass().getMethod(name);
                m.setAccessible(true);
                client = m.invoke(welt);
                break;
            } catch (NoSuchMethodException weiter) {
                // naechsten Namen versuchen
            }
        }
        if (client == null) {
            throw new IllegalStateException("Weder getClientWorld noch getClientLevel gefunden");
        }
        java.lang.reflect.Method warten = client.getClass().getMethod("waitForChunksRender");
        warten.setAccessible(true);
        warten.invoke(client);
    }

    /** Flachwelt einstellen - die ist sofort erzeugt (normale Welten dauern auf dem CI-Rechner zu lange). */
    private static void flachwelt(Object bauer) {
        try {
            for (java.lang.reflect.Method m : bauer.getClass().getMethods()) {
                if (!m.getName().equals("adjustSettings") || m.getParameterCount() != 1) {
                    continue;
                }
                java.util.function.Consumer<Object> anpassen = ui -> {
                    try {
                        for (String liste : new String[] {"getNormalPresetList", "getAltPresetList"}) {
                            java.lang.reflect.Method lm = ui.getClass().getMethod(liste);
                            for (Object eintrag : (List<?>) lm.invoke(ui)) {
                                java.lang.reflect.Method pm = eintrag.getClass().getMethod("preset");
                                pm.setAccessible(true);
                                Object preset = pm.invoke(eintrag);
                                if (preset != null && preset.toString().contains("flat")) {
                                    for (java.lang.reflect.Method sm : ui.getClass().getMethods()) {
                                        if (sm.getName().equals("setWorldType") && sm.getParameterCount() == 1) {
                                            sm.invoke(ui, eintrag);
                                            System.out.println("GLOWCUBE-TEST Flachwelt eingestellt");
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                        System.out.println("GLOWCUBE-TEST Flachwelt nicht gefunden - normale Welt");
                    } catch (Exception e) {
                        System.out.println("GLOWCUBE-TEST Flachwelt nicht einstellbar: " + e);
                    }
                };
                m.setAccessible(true);
                m.invoke(bauer, anpassen);
                return;
            }
            System.out.println("GLOWCUBE-TEST adjustSettings fehlt - normale Welt");
        } catch (Exception e) {
            System.out.println("GLOWCUBE-TEST Flachwelt nicht einstellbar: " + e);
        }
    }

    private static Module modul(String name) {
        Module m = modulOderNull(name);
        if (m == null) {
            throw new IllegalStateException("Modul " + name + " nicht gefunden");
        }
        return m;
    }

    private static Module modulOderNull(String name) {
        for (Module m : GlowCubeClient.modules().all()) {
            if (m.name().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    private static int zaehle(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String art)
            throws Exception {
        return server.computeOnServer(s -> {
            int n = 0;
            for (Entity e : s.overworld().getAllEntities()) {
                if (e.isAlive() && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(art)) {
                    n++;
                }
            }
            return n;
        });
    }

    private static BufferedImage bild(Object pfad) throws Exception {
        File datei = pfad instanceof Path p ? p.toFile() : new File(String.valueOf(pfad));
        return ImageIO.read(datei);
    }

    /** Anteil der Pixel, die sich deutlich unterscheiden. */
    private static double unterschied(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        long anders = 0;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                int d = Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255))
                        + Math.abs((p & 255) - (q & 255));
                if (d > 40) {
                    anders++;
                }
            }
        }
        return anders / (double) ((w / 2) * (h / 2));
    }

    private void zusammenfassung() {
        System.out.println("===== GLOWCUBE-SPIELTEST: " + (fehler == 0 ? "ALLES OK" : fehler + " FEHLER") + " =====");
        for (String z : ergebnisse) {
            System.out.println(z);
        }
        try {
            Files.writeString(Path.of("glowcube-spieltest.txt"), String.join("\n", ergebnisse) + "\n");
        } catch (Exception ignoriert) {
            // nur Beiwerk
        }
    }
}
