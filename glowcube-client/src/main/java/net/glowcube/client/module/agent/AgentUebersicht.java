package net.glowcube.client.module.agent;

import net.glowcube.client.agent.AgentStatus;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Agenten-Uebersicht: ein Kasten im HUD mit allen deinen Agenten - wer, was
 * er gerade tut, wie viel Beute er dabei hat und wo er ist (Entfernung und
 * ein Pfeil in seine Richtung, von deiner Blickrichtung aus).
 */
public final class AgentUebersicht extends HudModul {
    private static final String[] PFEILE = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int ZEILE = 21;

    private final BooleanSetting hintergrund = register(
            new BooleanSetting("Hintergrund", "Dunkler Kasten hinter der Anzeige", true));

    private final List<String[]> zeilen = new ArrayList<>();

    public AgentUebersicht() {
        super("Agenten-Uebersicht", "HUD: alle deine Agenten mit Auftrag, Beute, Entfernung und Richtung",
                true, Category.AGENT);
    }

    @Override
    public void vorbereiten() {
        zeilen.clear();
        if (mc.player == null || mc.level == null) {
            return;
        }
        String hier = mc.level.dimension().toString();
        for (AgentStatus s : AgentStatus.aktuell()) {
            if (s.besitzer() != null && !s.besitzer().equals(mc.player.getUUID())) {
                continue;
            }
            String wo;
            if (!hier.contains(s.welt())) {
                wo = "andere Dimension";
            } else {
                double dx = s.x() - mc.player.getX();
                double dz = s.z() - mc.player.getZ();
                int meter = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                // Winkel zum Agenten relativ zur Blickrichtung: 0 = geradeaus.
                double winkel = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot();
                int stufe = Math.floorMod((int) Math.round(winkel / 45.0), 8);
                wo = meter + "m " + PFEILE[stufe] + "  Y " + Math.round(s.y());
            }
            String beute = s.auftrag().equals("WAECHTER") ? s.beute() + " besiegt"
                    : s.auftrag().equals("BAUMEISTER") ? s.beute() + " gesetzt" : s.beute() + " Items";
            zeilen.add(new String[] {s.titel(), String.format(Locale.ROOT, "%s · %s · %s", s.zustand(), beute, wo)});
        }
    }

    @Override
    public float breite(HudZeichner z) {
        float w = z.breite("Agenten");
        for (String[] zeile : zeilen) {
            w = Math.max(w, Math.max(z.breite(zeile[0]), z.breite(zeile[1])));
        }
        return w + 8;
    }

    @Override
    public float hoehe() {
        return zeilen.isEmpty() ? 0 : 12 + zeilen.size() * ZEILE;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        if (zeilen.isEmpty()) {
            return;
        }
        if (hintergrund.get()) {
            z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        }
        z.text("Agenten (" + zeilen.size() + ")", x + 4, y + 2, 0xFF5FD7FF, true);
        for (int i = 0; i < zeilen.size(); i++) {
            float zy = y + 13 + i * ZEILE;
            z.text(zeilen.get(i)[0], x + 4, zy, Theme.TEXT, true);
            z.text(zeilen.get(i)[1], x + 4, zy + 9, Theme.TEXT_DIM, true);
        }
    }

    @Override
    public boolean bleibtNachWeltwechsel() {
        return true;
    }
}
