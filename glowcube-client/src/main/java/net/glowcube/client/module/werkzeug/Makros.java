package net.glowcube.client.module.werkzeug;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.TextListSetting;
import net.glowcube.client.hud.Meldungen;
import net.glowcube.client.render.Netz;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Makros: eine Taste - ein Befehl, eine Chatnachricht oder ein Modul-Schalter.
 *
 * <p>Jede Zeile der Liste hat die Form {@code TASTE: was}:
 * <ul>
 *   <li>{@code F6: /home} schickt den Befehl /home</li>
 *   <li>{@code G: gg} schreibt "gg" in den Chat</li>
 *   <li>{@code H: modul Freecam} schaltet ein GlowCube-Modul um</li>
 * </ul>
 * Tasten: A-Z, 0-9, F1-F12, NUM0-NUM9. Solange ein Fenster (Chat, Inventar)
 * offen ist, loest nichts aus.
 */
public final class Makros extends Module {
    private final TextListSetting liste = register(new TextListSetting("Makros",
            "Je Zeile \"TASTE: /befehl\", \"TASTE: text\" oder \"TASTE: modul Name\""));

    private final Set<Integer> unten = new HashSet<>();

    public Makros() {
        super("Makros", "Eigene Tasten fuer Befehle, Nachrichten und Module", Category.WERKZEUG);
    }

    @Override
    public void onTick() {
        if (Netz.bildschirm() != null) {
            unten.clear();
            return;
        }
        for (String zeile : liste.werte()) {
            int doppelpunkt = zeile.indexOf(':');
            if (doppelpunkt <= 0) {
                continue;
            }
            int taste = taste(zeile.substring(0, doppelpunkt));
            if (taste < 0) {
                continue;
            }
            boolean jetzt = Netz.tasteUnten(taste);
            if (jetzt && !unten.contains(taste)) {
                ausfuehren(zeile.substring(doppelpunkt + 1).trim());
            }
            if (jetzt) {
                unten.add(taste);
            } else {
                unten.remove(taste);
            }
        }
    }

    private void ausfuehren(String was) {
        if (was.isEmpty()) {
            return;
        }
        if (was.startsWith("/")) {
            player().connection.sendCommand(was.substring(1));
        } else if (was.toLowerCase(Locale.ROOT).startsWith("modul ")) {
            Module modul = GlowCubeClient.modules().get(was.substring(6).trim());
            if (modul == null) {
                Meldungen.melden("Makro: kein Modul \"" + was.substring(6).trim() + "\"", 0xFFFF5F6D);
                return;
            }
            modul.toggle();
            Meldungen.melden(modul.name() + (modul.isEnabled() ? " an" : " aus"), 0xFF6BE08A);
        } else {
            player().connection.sendChat(was);
        }
    }

    /** Tastenname zum Tastencode (GLFW-Nummern, wie Minecraft sie nutzt); -1 wenn unbekannt. */
    static int taste(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        if (n.length() == 1) {
            char c = n.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                return c;
            }
            return -1;
        }
        try {
            if (n.startsWith("NUM")) {
                int ziffer = Integer.parseInt(n.substring(3));
                return ziffer >= 0 && ziffer <= 9 ? 320 + ziffer : -1;
            }
            if (n.startsWith("F")) {
                int nummer = Integer.parseInt(n.substring(1));
                return nummer >= 1 && nummer <= 12 ? 289 + nummer : -1;
            }
        } catch (NumberFormatException kaputt) {
            return -1;
        }
        return -1;
    }
}
