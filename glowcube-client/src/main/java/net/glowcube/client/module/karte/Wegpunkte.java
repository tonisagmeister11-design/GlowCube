package net.glowcube.client.module.karte;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.core.setting.TextListSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.render.WeltRender;
import net.glowcube.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Wegpunkte: benannte Orte, je Server bzw. Einzelspielerwelt und Dimension.
 *
 * <p>Sie stehen in der Welt als Lichtsaeule, auf der Minimap und der
 * Weltkarte als Punkt und hier im HUD mit Entfernung und Richtungspfeil.
 * Anlegen per {@code /wp add <name> [x y z]}, loeschen mit
 * {@code /wp del <name>}, alle zeigen mit {@code /wp}. Gespeichert werden sie
 * mit der GlowCube-Konfiguration - auch im Menue unter "Liste" bearbeitbar.
 */
public final class Wegpunkte extends HudModul {
    private static final String[] PFEILE = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static Wegpunkte instanz;

    /** Ein Wegpunkt. */
    public record Punkt(String welt, String dim, String name, int x, int y, int z) {
        String speichern() {
            return welt + "|" + dim + "|" + name + "|" + x + "|" + y + "|" + z;
        }

        static Punkt lesen(String zeile) {
            String[] teile = zeile.split("\\|");
            if (teile.length != 6) {
                return null;
            }
            try {
                return new Punkt(teile[0], teile[1], teile[2], Integer.parseInt(teile[3].trim()),
                        Integer.parseInt(teile[4].trim()), Integer.parseInt(teile[5].trim()));
            } catch (NumberFormatException kaputt) {
                return null;
            }
        }
    }

    private final TextListSetting liste = register(new TextListSetting("Liste",
            "Gespeicherte Wegpunkte: welt|dimension|name|x|y|z"));
    private final NumberSetting imHud = register(new NumberSetting("Im HUD",
            "Wie viele der naechsten Wegpunkte im HUD stehen", 5, 0, 12, 1));
    private final BooleanSetting saeule = register(new BooleanSetting("Lichtsaeule",
            "Wegpunkte in der Welt als Saeule zeigen", true));
    private final BooleanSetting tracer = register(new BooleanSetting("Tracer",
            "Linie vom Fadenkreuz zum Wegpunkt", false));
    private final NumberSetting sichtweite = register(new NumberSetting("Sichtweite",
            "Bis zu dieser Entfernung stehen Saeulen in der Welt", 2000, 50, 30000, 50));

    private final List<String> zeilen = new ArrayList<>();
    private final List<Integer> farben = new ArrayList<>();

    public Wegpunkte() {
        super("Wegpunkte", "Benannte Orte mit Saeule, Entfernung und Richtung", true, Category.KARTE);
        instanz = this;
    }

    // ------------------------------------------------------------------ Daten

    public static List<Punkt> alle() {
        List<Punkt> punkte = new ArrayList<>();
        if (instanz == null) {
            return punkte;
        }
        for (String zeile : instanz.liste.werte()) {
            Punkt p = Punkt.lesen(zeile);
            if (p != null) {
                punkte.add(p);
            }
        }
        return punkte;
    }

    /** Nur die Wegpunkte dieser Welt und Dimension. */
    public static List<Punkt> hier() {
        String welt = weltKennung();
        String dim = dimKennung();
        List<Punkt> punkte = new ArrayList<>();
        for (Punkt p : alle()) {
            if (p.welt().equals(welt) && p.dim().equals(dim)) {
                punkte.add(p);
            }
        }
        return punkte;
    }

    public static String weltKennung() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().ip.replace("|", "");
        }
        if (mc.getSingleplayerServer() != null) {
            return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName().replace("|", "");
        }
        return "?";
    }

    /** "overworld", "the_nether", "the_end" - aus dem Schluessel der Dimension gelesen. */
    public static String dimKennung() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return "?";
        }
        String text = mc.level.dimension().toString();
        int schraeg = text.lastIndexOf('/');
        String teil = schraeg >= 0 ? text.substring(schraeg + 1) : text;
        teil = teil.replace("]", "").trim();
        int doppelpunkt = teil.indexOf(':');
        return doppelpunkt >= 0 ? teil.substring(doppelpunkt + 1) : teil;
    }

    public static int farbe(String name) {
        float farbton = (Math.abs(name.hashCode()) % 360) / 360.0f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(farbton, 0.65f, 1.0f) & 0xFFFFFF);
    }

    /** Legt einen Wegpunkt an oder ueberschreibt einen gleichnamigen. */
    public static String hinzufuegen(String name, int x, int y, int z) {
        if (instanz == null) {
            return "Wegpunkte nicht geladen.";
        }
        String sauber = name.replace("|", "").trim();
        if (sauber.isEmpty()) {
            return "Der Wegpunkt braucht einen Namen.";
        }
        entfernenStill(sauber);
        instanz.liste.add(new Punkt(weltKennung(), dimKennung(), sauber, x, y, z).speichern());
        speichern();
        return "Wegpunkt \"" + sauber + "\" bei " + x + " " + y + " " + z + " gesetzt.";
    }

    public static String entfernen(String name) {
        return entfernenStill(name)
                ? speichernUnd("Wegpunkt \"" + name + "\" geloescht.")
                : "Keinen Wegpunkt \"" + name + "\" in dieser Welt gefunden.";
    }

    private static String speichernUnd(String text) {
        speichern();
        return text;
    }

    private static boolean entfernenStill(String name) {
        if (instanz == null) {
            return false;
        }
        String welt = weltKennung();
        String dim = dimKennung();
        List<String> werte = instanz.liste.werte();
        for (int i = werte.size() - 1; i >= 0; i--) {
            Punkt p = Punkt.lesen(werte.get(i));
            if (p != null && p.welt().equals(welt) && p.dim().equals(dim) && p.name().equalsIgnoreCase(name)) {
                instanz.liste.remove(i);
                return true;
            }
        }
        return false;
    }

    public static Punkt finden(String name) {
        for (Punkt p : hier()) {
            if (p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public static String auflisten() {
        List<Punkt> punkte = hier();
        if (punkte.isEmpty()) {
            return "Keine Wegpunkte in dieser Welt. /wp add <name> legt einen an.";
        }
        StringBuilder sb = new StringBuilder("Wegpunkte hier:");
        for (Punkt p : punkte) {
            sb.append("\n  ").append(p.name()).append(": ").append(p.x()).append(' ').append(p.y()).append(' ')
                    .append(p.z()).append(" (").append(entfernung(p)).append("m)");
        }
        return sb.toString();
    }

    /** Teleportiert per /tp - geht im Einzelspieler mit Cheats oder mit OP-Rechten. */
    public static String teleport(String name) {
        Punkt p = finden(name);
        Minecraft mc = Minecraft.getInstance();
        if (p == null || mc.player == null) {
            return "Keinen Wegpunkt \"" + name + "\" in dieser Welt gefunden.";
        }
        mc.player.connection.sendCommand("tp @s " + p.x() + ".5 " + p.y() + " " + p.z() + ".5");
        return "Teleport zu \"" + p.name() + "\" angefragt (braucht Cheats bzw. OP).";
    }

    private static void speichern() {
        try {
            GlowCubeClient.config().save();
        } catch (Throwable ignoriert) {
            // Spaetestens beim Beenden wird gespeichert.
        }
    }

    static int entfernung(Punkt p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        double dx = p.x() + 0.5 - mc.player.getX();
        double dy = p.y() - mc.player.getY();
        double dz = p.z() + 0.5 - mc.player.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    static String pfeil(Punkt p) {
        Minecraft mc = Minecraft.getInstance();
        double dx = p.x() + 0.5 - mc.player.getX();
        double dz = p.z() + 0.5 - mc.player.getZ();
        double zielGier = Math.toDegrees(Math.atan2(-dx, dz));
        double relativ = ((zielGier - mc.player.getYRot()) % 360 + 540) % 360 - 180;
        int i = (int) Math.round(relativ / 45.0);
        return PFEILE[((i % 8) + 8) % 8];
    }

    // ------------------------------------------------------------ Anzeigen

    @Override
    public void onWorldRender(WeltRender render) {
        if (!saeule.get() && !tracer.get()) {
            return;
        }
        for (Punkt p : hier()) {
            if (entfernung(p) > sichtweite.get()) {
                continue;
            }
            int f = farbe(p.name());
            if (saeule.get()) {
                render.box(new AABB(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1), f, true);
                Vec3 unten = new Vec3(p.x() + 0.5, p.y() + 1, p.z() + 0.5);
                render.linie(unten, unten.add(0, 120, 0), ColorUtil.fade(f, 0.8f));
            }
            if (tracer.get()) {
                render.tracer(new Vec3(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5), f);
            }
        }
    }

    @Override
    public void vorbereiten() {
        zeilen.clear();
        farben.clear();
        if (Minecraft.getInstance().player == null || imHud.getInt() == 0) {
            return;
        }
        List<Punkt> punkte = hier();
        punkte.sort(Comparator.comparingInt(Wegpunkte::entfernung));
        for (int i = 0; i < Math.min(imHud.getInt(), punkte.size()); i++) {
            Punkt p = punkte.get(i);
            int m = entfernung(p);
            String weite = m >= 1000 ? String.format(Locale.ROOT, "%.1fkm", m / 1000.0) : m + "m";
            zeilen.add(pfeil(p) + " " + p.name() + "  " + weite);
            farben.add(farbe(p.name()));
        }
    }

    @Override
    public float breite(HudZeichner z) {
        float w = 60;
        for (String zeile : zeilen) {
            w = Math.max(w, z.breite(zeile) + 8);
        }
        return w;
    }

    @Override
    public float hoehe() {
        return zeilen.isEmpty() ? 0 : zeilen.size() * 10 + 4;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        for (int i = 0; i < zeilen.size(); i++) {
            z.text(zeilen.get(i), x + 4, y + 3 + i * 10, farben.get(i), true);
        }
    }
}
