package net.glowcube.client.module.hud;

import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.Theme;
import net.minecraft.server.packs.repository.Pack;

import java.util.ArrayList;
import java.util.List;

/**
 * Die aktiven Ressourcenpakete - nach AxolotlClients {@code PackDisplayHud}.
 * Das eingebaute Standardpaket und die Mod-Ressourcen werden weggelassen.
 */
public final class RessourcenpaketeHud extends HudModul {
    private final List<String> namen = new ArrayList<>();

    public RessourcenpaketeHud() {
        super("Ressourcenpakete", "Zeigt die aktiven Ressourcenpakete", false);
    }

    @Override
    public void vorbereiten() {
        namen.clear();
        for (Pack pack : mc.getResourcePackRepository().getSelectedPacks()) {
            String id = pack.getId();
            if (id.equals("vanilla") || id.equals("fabric") || id.startsWith("fabric-") || id.equals("mod_resources")) {
                continue;
            }
            namen.add(pack.getTitle().getString());
        }
    }

    @Override
    public float breite(HudZeichner z) {
        float w = 0;
        for (String n : namen) {
            w = Math.max(w, z.breite(n));
        }
        return w + 8;
    }

    @Override
    public float hoehe() {
        return namen.isEmpty() ? 0 : namen.size() * 11 + 2;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        for (int i = 0; i < namen.size(); i++) {
            z.text(namen.get(i), x + 4, y + 2 + i * 11, Theme.TEXT, true);
        }
    }
}
