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
            mc.options.renderDistance().set(3);
            mc.options.simulationDistance().set(5);
            // Der Test-Server laeuft im Gleichschritt mit dem Client: jede
            // Bildbremse bremst auch ihn. Stattdessen alles Teure abschalten,
            // was ohne Grafikkarte Bilder kostet. Die Namen der Optionen
            // unterscheiden sich je Fassung - darum ueber Spiegelung.
            mc.options.framerateLimit().set(30);
            option(mc.options, "menuBackgroundBlurriness", 0);
            option(mc.options, "cloudStatus", "OFF");
            option(mc.options, "entityShadows", false);
            option(mc.options, "mipmapLevels", 0);
            option(mc.options, "ambientOcclusion", false);
            option(mc.options, "biomeBlendRadius", 0);
            option(mc.options, "particles", "MINIMAL");
            option(mc.options, "graphicsMode", "FAST");
            option(mc.options, "enableVsync", false);
        });
        var bauer = kontext.worldBuilder();
        flachwelt(bauer);
        Thread waechter = threadWaechter();
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
            pruefe("Arenen-Bauplaene", () -> arenen(kontext));
            pruefe("Mehrere Agenten", () -> mehrereAgenten(kontext));
            pruefe("ClickGUI mit der Maus", () -> klickGui(kontext));
            pruefe("Freecam", () -> freecam(kontext));
            pruefe("Karte und HUD", () -> karte(kontext, welt));
            pruefe("AutoArmor", () -> autoArmor(kontext, server));
            pruefe("Surround", () -> surround(kontext, server));
            pruefe("CrystalAura", () -> crystalAura(kontext, server));
            pruefe("Totem-Pops", () -> totemPops(kontext, server));
            pruefe("Alle Module an/aus", () -> rundlauf(kontext));
        } catch (Throwable t) {
            kaputt("Test selbst abgebrochen: " + t);
            t.printStackTrace();
        }
        waechter.interrupt();
        zusammenfassung();
    }

    /** Setzt eine Option, wenn es sie in dieser Fassung gibt; Aufzaehlungen per Name. */
    private static void option(Object optionen, String name, Object wert) {
        try {
            Object opt = optionen.getClass().getMethod(name).invoke(optionen);
            Object alt = opt.getClass().getMethod("get").invoke(opt);
            Object neu = wert;
            if (wert instanceof String konstante && alt instanceof Enum<?> e) {
                neu = null;
                for (Object c : e.getDeclaringClass().getEnumConstants()) {
                    if (((Enum<?>) c).name().equals(konstante)) {
                        neu = c;
                    }
                }
                if (neu == null) {
                    System.out.println("GLOWCUBE-TEST Option " + name + ": kein Wert " + konstante);
                    return;
                }
            }
            opt.getClass().getMethod("set", Object.class).invoke(opt, neu);
            System.out.println("GLOWCUBE-TEST Option " + name + " = " + neu);
        } catch (Throwable t) {
            System.out.println("GLOWCUBE-TEST Option " + name + " nicht gesetzt: " + t);
        }
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

        // Dasselbe mit Criticals: dabei gehen mehrere Positionspakete in einem
        // Tick hinaus - 26.x wirft dafuer hinaus, wenn der Positionstakt fehlt.
        Module crit = modulOderNull("Criticals");
        if (crit == null) {
            kaputt("Modul Criticals nicht gefunden");
            return;
        }
        server.runCommand("gamemode survival @a");
        server.runCommand("execute as @a at @s run summon minecraft:husk ~ ~ ~2 {NoAI:1b,Silent:1b,Health:10f}");
        k.waitTicks(10);
        int vorher2 = zaehle(server, "husk");
        k.runOnClient(mc -> {
            crit.setEnabled(true);
            aura.setEnabled(true);
        });
        k.waitTicks(120);
        k.runOnClient(mc -> {
            aura.setEnabled(false);
            crit.setEnabled(false);
        });
        boolean verbunden = k.computeOnClient(mc -> mc.getConnection() != null && mc.level != null);
        if (!verbunden) {
            kaputt("KillAura mit Criticals: vom Server geworfen");
            throw new IllegalStateException("Verbindung verloren");
        }
        int nachher2 = zaehle(server, "husk");
        server.runCommand("gamemode creative @a");
        server.runCommand("kill @e[type=minecraft:husk]");
        if (vorher2 > 0 && nachher2 < vorher2) {
            ok("KillAura mit Criticals trifft und bleibt verbunden (" + vorher2 + " -> " + nachher2 + ")");
        } else {
            kaputt("KillAura mit Criticals hat den Zombie nicht besiegt (" + vorher2 + " -> " + nachher2 + ")");
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

    // ------------------------------------------- Baupläne, mehrere Agenten

    private void arenen(ClientGameTestContext k) throws Exception {
        for (String name : new String[] {"PvP-Arena-Nether", "PvP-Arena-Wald", "Azalit-City-Arena"}) {
            String ergebnis = k.computeOnClient(mc -> {
                net.glowcube.client.bauplan.Bauplan plan = net.glowcube.client.bauplan.Bauplaene.laden(name);
                if (plan == null) {
                    return "nicht ladbar";
                }
                int fest = 0;
                java.util.Set<String> unbekannt = new java.util.TreeSet<>();
                for (net.glowcube.client.bauplan.Bauplan.Block b : plan.bloecke) {
                    String z = b.zustand();
                    String id = z.contains("[") ? z.substring(0, z.indexOf('[')) : z;
                    if (!id.contains(":")) {
                        id = "minecraft:" + id;
                    }
                    String ns = id.substring(0, id.indexOf(':'));
                    String pfad = id.substring(id.indexOf(':') + 1);
                    if (!BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.Identifier.fromNamespaceAndPath(ns, pfad))) {
                        unbekannt.add(id);
                    } else if (!id.equals("minecraft:air")) {
                        fest++;
                    }
                }
                if (!unbekannt.isEmpty()) {
                    return "unbekannte Bloecke " + unbekannt;
                }
                return fest < 1000 ? "nur " + fest + " Bloecke" : null;
            });
            if (ergebnis == null) {
                ok("Bauplan " + name + " laedt, alle Bloecke bekannt");
            } else {
                kaputt("Bauplan " + name + ": " + ergebnis);
            }
        }
    }

    /**
     * Genau wie im Menue: Anzahl auf 3, Modul an. Es muessen drei Agenten
     * laufen, und sie duerfen nicht uebereinander stehen.
     */
    private void mehrereAgenten(ClientGameTestContext k) throws Exception {
        String[][] arten = {{"Guardian-Agent", "WAECHTER"}, {"Stein-Agent", "STEIN"}};
        for (String[] art : arten) {
            Module m = modul(art[0]);
            k.runOnClient(mc -> {
                einstellen(m, "Anzahl", 3);
                m.setEnabled(true);
            });
            k.waitTicks(120);
            List<AgentStatus> laufend = k.computeOnClient(mc -> AgentStatus.aktuell().stream()
                    .filter(s -> s.auftrag().equals(art[1])).toList());
            double naechster = Double.MAX_VALUE;
            for (int i = 0; i < laufend.size(); i++) {
                for (int j = i + 1; j < laufend.size(); j++) {
                    AgentStatus a = laufend.get(i);
                    AgentStatus b = laufend.get(j);
                    double d = Math.sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.z() - b.z()) * (a.z() - b.z()));
                    naechster = Math.min(naechster, d);
                }
            }
            k.runOnClient(mc -> {
                m.setEnabled(false);
                einstellen(m, "Anzahl", 1);
            });
            k.waitTicks(200);
            String abstand = String.format(java.util.Locale.ROOT, "%.1f", naechster);
            if (laufend.size() != 3) {
                kaputt(art[0] + " x3: es laufen " + laufend.size() + " statt 3");
            } else if (naechster < 0.9) {
                kaputt(art[0] + " x3: zwei stehen uebereinander (Abstand " + abstand + ")");
            } else {
                ok(art[0] + " x3: drei laufen getrennt (kleinster Abstand " + abstand + " Bloecke)");
            }
        }
    }

    // ------------------------------------------------ ClickGUI per Maus

    /**
     * Bedient das Menue wie ein Mensch: echte Mausklicks ueber die
     * Test-Eingabe (sie laufen durch Minecrafts MouseHandler, also mit den
     * Tastennummern der jeweiligen Fassung). Erst "Hacks" waehlen, dann ein
     * Modul mit links an-, mit links wieder ausschalten.
     */
    private void klickGui(ClientGameTestContext k) throws Exception {
        Module ziel = modul("FastPlace");
        k.runOnClient(mc -> ziel.setEnabled(false));
        k.setScreen(() -> new net.glowcube.client.gui.ClickGuiScreen());
        try {
            klickGuiInnen(k, ziel);
        } finally {
            k.setScreen(() -> null);
            k.runOnClient(mc -> ziel.setEnabled(false));
        }
    }

    private void klickGuiInnen(ClientGameTestContext k, Module ziel) throws Exception {
        k.waitTicks(10);
        if (!linksKlicken(k, "WAHL_HACKS", null)) {
            kaputt("ClickGUI: Knopf Hacks nicht gefunden");
            k.setScreen(() -> null);
            return;
        }
        k.waitTicks(10);
        boolean gefunden = linksKlicken(k, "MODUL", ziel);
        k.waitTicks(5);
        boolean an = k.computeOnClient(mc -> ziel.isEnabled());
        linksKlicken(k, "MODUL", ziel);
        k.waitTicks(5);
        boolean wiederAus = k.computeOnClient(mc -> !ziel.isEnabled());
        k.setScreen(() -> null);
        k.runOnClient(mc -> ziel.setEnabled(false));
        if (!gefunden) {
            kaputt("ClickGUI: Zeile fuer FastPlace nicht gefunden");
        } else if (an && wiederAus) {
            ok("ClickGUI: Linksklick schaltet ein Modul an und wieder aus");
        } else {
            kaputt("ClickGUI: Linksklick schaltet nicht (an=" + an + ", wieder aus=" + wiederAus + ")");
        }
    }

    /** Klickt mit der linken Maustaste mitten auf ein Element des ClickGUI. */
    private static boolean linksKlicken(ClientGameTestContext k, String art, Module modul) throws Exception {
        double[] ort = k.computeOnClient(mc -> {
            try {
                Object bild = net.glowcube.client.render.Netz.bildschirm();
                java.lang.reflect.Field feld = bild.getClass().getDeclaredField("treffer");
                feld.setAccessible(true);
                for (Object t : new ArrayList<>((List<?>) feld.get(bild))) {
                    Class<?> c = t.getClass();
                    if (!String.valueOf(wert(c, t, "art")).equals(art)) {
                        continue;
                    }
                    if (modul != null && wert(c, t, "module") != modul) {
                        continue;
                    }
                    float x = (Float) wert(c, t, "x");
                    float y = (Float) wert(c, t, "y");
                    float w = (Float) wert(c, t, "w");
                    float h = (Float) wert(c, t, "h");
                    double massstab = (double) mc.getWindow().getWidth() / mc.getWindow().getGuiScaledWidth();
                    return new double[] {(x + w / 2) * massstab, (y + h / 2) * massstab};
                }
            } catch (ReflectiveOperationException e) {
                System.out.println("GLOWCUBE-TEST ClickGUI-Spiegelung: " + e);
            }
            return null;
        });
        if (ort == null) {
            return false;
        }
        k.getInput().setCursorPos(ort[0], ort[1]);
        k.waitTicks(1);
        k.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        return true;
    }

    private static Object wert(Class<?> c, Object t, String name) throws ReflectiveOperationException {
        java.lang.reflect.Method m = c.getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(t);
    }

    // ----------------------------------------------------- Neue Features

    private void freecam(ClientGameTestContext k) throws Exception {
        Module freecam = modul("Freecam");
        net.minecraft.world.phys.Vec3 vorher = k.computeOnClient(mc -> mc.player.position());
        k.runOnClient(mc -> freecam.setEnabled(true));
        k.waitTicks(5);
        k.runOnClient(mc -> mc.options.keyUp.setDown(true));
        k.waitTicks(20);
        k.runOnClient(mc -> mc.options.keyUp.setDown(false));
        String ergebnis = k.computeOnClient(mc -> {
            Entity kamera = mc.getCameraEntity();
            if (kamera == mc.player) {
                return "Kamera sitzt noch im Spieler";
            }
            double flug = kamera.position().distanceTo(mc.player.position());
            double gelaufen = mc.player.position().distanceTo(vorher);
            if (flug < 3) {
                return "Kamera ist nicht losgeflogen (" + String.format("%.1f", flug) + " Bloecke)";
            }
            if (gelaufen > 0.5) {
                return "Koerper ist mitgelaufen (" + String.format("%.1f", gelaufen) + " Bloecke)";
            }
            return null;
        });
        k.runOnClient(mc -> freecam.setEnabled(false));
        k.waitTicks(5);
        boolean zurueck = k.computeOnClient(mc -> mc.getCameraEntity() == mc.player);
        if (ergebnis != null) {
            kaputt("Freecam: " + ergebnis);
        } else if (!zurueck) {
            kaputt("Freecam: nach dem Ausschalten sitzt die Kamera nicht wieder im Spieler");
        } else {
            ok("Freecam fliegt los, Koerper bleibt stehen, Kamera kommt zurueck");
        }
    }

    private void karte(ClientGameTestContext k, Object weltObjekt) throws Exception {
        var welt = (net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext) weltObjekt;
        chunksAbwarten(welt);
        BufferedImage ohne = bild(k.takeScreenshot("hud-aus"));
        String gesetzt = k.computeOnClient(mc -> net.glowcube.client.module.karte.Wegpunkte.hinzufuegen("Testpunkt",
                mc.player.getBlockX() + 5, mc.player.getBlockY(), mc.player.getBlockZ() - 5));
        int anzahl = k.computeOnClient(mc -> net.glowcube.client.module.karte.Wegpunkte.hier().size());
        if (anzahl < 1) {
            kaputt("Wegpunkt wurde nicht gespeichert: " + gesetzt);
        } else {
            ok("Wegpunkt gespeichert (" + anzahl + " in dieser Welt)");
        }
        String[] hud = {"Minimap", "Wegpunkte", "Item-Zaehler", "Session-Statistik"};
        for (String name : hud) {
            Module m = modul(name);
            k.runOnClient(mc -> m.setEnabled(true));
        }
        k.waitTicks(30);
        BufferedImage mit = bild(k.takeScreenshot("hud-an"));
        double anteil = unterschied(ohne, mit);
        for (String name : hud) {
            Module m = modul(name);
            k.runOnClient(mc -> m.setEnabled(false));
        }
        String werte = String.format("%.2f%% der Pixel veraendert", anteil * 100);
        if (anteil > 0.01) {
            ok("Minimap, Wegpunkte, Item-Zaehler und Session-Statistik zeichnen (" + werte + ")");
        } else {
            kaputt("Minimap und HUD zeichnen kaum etwas (" + werte + ")");
        }
        Module weltkarte = modul("Weltkarte");
        k.runOnClient(mc -> weltkarte.setEnabled(true));
        k.waitTicks(30);
        BufferedImage karte = bild(k.takeScreenshot("weltkarte"));
        k.runOnClient(mc -> weltkarte.setEnabled(false));
        double anteilKarte = unterschied(ohne, karte);
        String werteKarte = String.format("%.1f%% der Pixel veraendert", anteilKarte * 100);
        if (anteilKarte > 0.3) {
            ok("Weltkarte ueberdeckt den Bildschirm (" + werteKarte + ")");
        } else {
            kaputt("Weltkarte zeichnet zu wenig (" + werteKarte + ")");
        }
        k.runOnClient(mc -> net.glowcube.client.module.karte.Wegpunkte.entfernen("Testpunkt"));
    }

    private void autoArmor(ClientGameTestContext k, Object serverObjekt) throws Exception {
        var server = (net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) serverObjekt;
        server.runCommand("clear @a");
        server.runCommand("give @a minecraft:diamond_chestplate");
        server.runCommand("give @a minecraft:iron_boots");
        k.waitTicks(10);
        Module m = modul("AutoArmor");
        k.runOnClient(mc -> m.setEnabled(true));
        k.waitTicks(40);
        k.runOnClient(mc -> m.setEnabled(false));
        String brust = k.computeOnClient(mc -> net.glowcube.client.util.Ids.item(
                mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)));
        String fuesse = k.computeOnClient(mc -> net.glowcube.client.util.Ids.item(
                mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)));
        server.runCommand("clear @a");
        if (brust.equals("diamond_chestplate") && fuesse.equals("iron_boots")) {
            ok("AutoArmor zieht Brustpanzer und Stiefel an");
        } else {
            kaputt("AutoArmor: Brust=" + brust + ", Fuesse=" + fuesse);
        }
    }

    private void surround(ClientGameTestContext k, Object serverObjekt) throws Exception {
        var server = (net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) serverObjekt;
        server.runCommand("gamemode survival @a");
        server.runCommand("clear @a");
        server.runCommand("give @a minecraft:obsidian 16");
        k.waitTicks(10);
        Module m = modul("Surround");
        k.runOnClient(mc -> m.setEnabled(true));
        k.waitTicks(40);
        k.runOnClient(mc -> m.setEnabled(false));
        int obsidian = k.computeOnClient(mc -> {
            net.minecraft.core.BlockPos f = mc.player.blockPosition();
            int n = 0;
            for (net.minecraft.core.BlockPos p : new net.minecraft.core.BlockPos[] {f.north(), f.south(), f.east(), f.west()}) {
                if (net.glowcube.client.util.Ids.block(mc.level.getBlockState(p)).equals("obsidian")) {
                    n++;
                }
            }
            return n;
        });
        server.runCommand("execute as @a at @s run fill ~-2 ~-1 ~-2 ~2 ~1 ~2 minecraft:air replace minecraft:obsidian");
        server.runCommand("clear @a");
        server.runCommand("gamemode creative @a");
        if (obsidian == 4) {
            ok("Surround mauert alle vier Seiten mit Obsidian ein");
        } else {
            kaputt("Surround: nur " + obsidian + " von 4 Seiten mit Obsidian");
        }
    }

    private void crystalAura(ClientGameTestContext k, Object serverObjekt) throws Exception {
        var server = (net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) serverObjekt;
        server.runCommand("gamemode creative @a");
        server.runCommand("clear @a");
        server.runCommand("give @a minecraft:end_crystal 16");
        server.runCommand("execute as @a at @s run fill ~-2 ~-1 ~-3 ~2 ~-1 ~-5 minecraft:obsidian");
        server.runCommand("execute as @a at @s run summon minecraft:husk ~ ~ ~-4 {NoAI:1b,Silent:1b,Health:20f}");
        k.waitTicks(20);
        int vorher = zaehle(server, "husk");
        Module m = modul("CrystalAura");
        k.runOnClient(mc -> {
            einstellen(m, "Monster", true);
            einstellen(m, "Spieler", false);
            m.setEnabled(true);
        });
        k.waitTicks(100);
        k.runOnClient(mc -> {
            m.setEnabled(false);
            einstellen(m, "Monster", false);
            einstellen(m, "Spieler", true);
        });
        int nachher = zaehle(server, "husk");
        server.runCommand("kill @e[type=minecraft:end_crystal]");
        server.runCommand("kill @e[type=minecraft:husk]");
        server.runCommand("execute as @a at @s run fill ~-3 ~-1 ~-6 ~3 ~-1 ~-2 minecraft:grass_block replace minecraft:obsidian");
        server.runCommand("clear @a");
        if (vorher > 0 && nachher < vorher) {
            ok("CrystalAura legt und zuendet Kristalle - der Husk ist tot");
        } else {
            kaputt("CrystalAura hat den Husk nicht besiegt (" + vorher + " -> " + nachher + ")");
        }
    }

    private void totemPops(ClientGameTestContext k, Object serverObjekt) throws Exception {
        var server = (net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext) serverObjekt;
        Module m = modul("Totem-Pops");
        k.runOnClient(mc -> m.setEnabled(true));
        server.runCommand("gamemode survival @a");
        server.runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
        k.waitTicks(10);
        server.runCommand("damage @p 100 minecraft:generic");
        int pops = 0;
        for (int i = 0; i < 20 && pops == 0; i++) {
            k.waitTicks(10);
            pops = k.computeOnClient(mc -> net.glowcube.client.module.hud.TotemPops.anzahl(
                    mc.player.getName().getString()));
        }
        String zustand = k.computeOnClient(mc -> "Nebenhand=" + net.glowcube.client.util.Ids.item(
                mc.player.getOffhandItem()) + ", Leben=" + mc.player.getHealth());
        k.runOnClient(mc -> m.setEnabled(false));
        server.runCommand("gamemode creative @a");
        server.runCommand("effect clear @a");
        if (pops == 1) {
            ok("Totem-Pop-Zaehler zaehlt den Pop");
        } else {
            kaputt("Totem-Pop-Zaehler steht bei " + pops + " statt 1 (" + zustand + ")");
        }
    }

    private static void einstellen(Module m, String name, Object wert) {
        for (net.glowcube.client.core.setting.Setting s : m.settings()) {
            if (!s.name().equals(name)) {
                continue;
            }
            if (s instanceof net.glowcube.client.core.setting.BooleanSetting b) {
                b.set((Boolean) wert);
            } else if (s instanceof net.glowcube.client.core.setting.NumberSetting n) {
                n.set(((Number) wert).doubleValue());
            }
        }
    }

    // --------------------------------------------------------- Rundlauf

    private void rundlauf(ClientGameTestContext k) throws Exception {
        List<Module> alle = k.computeOnClient(mc -> new ArrayList<>(GlowCubeClient.modules().all()));
        int ohneFehler = 0;
        for (Module m : alle) {
            if (AUSLASSEN.contains(m.name())) {
                continue;
            }
            System.out.println("GLOWCUBE-TEST Rundlauf: " + m.name());
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
            if (k.computeOnClient(mc -> mc.getConnection() == null || mc.level == null)) {
                kaputt("Modul " + m.name() + " hat die Verbindung zur Welt verloren (siehe Protokoll)");
                throw new IllegalStateException("Rundlauf abgebrochen nach " + m.name());
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
     * Ein Stichproben-Profiler fuer die CI: alle 10 Sekunden die Stapel von
     * Render- und Server-Thread mit CPU-Zeit, jede sechste Probe alle Threads.
     * Damit sieht man, wo die Zeit hingeht, wenn ein Schritt haengt.
     */
    private static Thread threadWaechter() {
        Thread t = new Thread(() -> {
            try {
                var mx = java.lang.management.ManagementFactory.getThreadMXBean();
                for (int probe = 0; probe < 40; probe++) {
                    Thread.sleep(10_000);
                    StringBuilder sb = new StringBuilder("GLOWCUBE-TEST Probe " + probe + ":\n");
                    for (var info : mx.dumpAllThreads(false, false)) {
                        String n = info.getThreadName();
                        boolean wichtig = n.equals("Render thread") || n.equals("Server thread");
                        if (!wichtig && probe % 6 != 5) {
                            continue;
                        }
                        long cpu = mx.getThreadCpuTime(info.getThreadId());
                        sb.append("  [").append(n).append("] ").append(info.getThreadState())
                                .append(" cpu=").append(cpu / 1_000_000).append("ms\n");
                        if (wichtig) {
                            var stapel = info.getStackTrace();
                            for (int i = 0; i < Math.min(25, stapel.length); i++) {
                                sb.append("      at ").append(stapel[i]).append('\n');
                            }
                        }
                    }
                    System.out.println(sb);
                }
            } catch (InterruptedException ende) {
                // Test ist fertig.
            }
        }, "GlowCube-Waechter");
        t.setDaemon(true);
        t.start();
        return t;
    }

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
            // Unbekannter Name: die Methode ohne Parameter nehmen, deren Ergebnis waitForChunksRender kennt.
            for (java.lang.reflect.Method m : welt.getClass().getMethods()) {
                if (m.getParameterCount() != 0) {
                    continue;
                }
                try {
                    m.getReturnType().getMethod("waitForChunksRender");
                } catch (NoSuchMethodException nein) {
                    continue;
                }
                m.setAccessible(true);
                client = m.invoke(welt);
                System.out.println("GLOWCUBE-TEST Client-Welt ueber " + m.getName() + "()");
                break;
            }
        }
        if (client == null) {
            throw new IllegalStateException("Keine Client-Welt gefunden");
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
