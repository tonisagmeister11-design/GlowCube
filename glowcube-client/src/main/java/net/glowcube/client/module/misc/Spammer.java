package net.glowcube.client.module.misc;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;

import java.util.Random;

/**
 * Schickt in Abstaenden eine Nachricht in den Chat.
 *
 * Der Text steht in der Konfigurationsdatei. Die Zufallsziffer haengt hinten
 * an, weil Server zweimal dieselbe Zeile hintereinander verschlucken.
 * Nachempfunden Spammer aus BleachHack (GPL-3.0).
 */
public final class Spammer extends Module {
    private final NumberSetting interval = register(new NumberSetting("Interval",
            "Sekunden zwischen zwei Nachrichten", 10, 1, 120, 1));
    private final BooleanSetting eindeutig = register(new BooleanSetting("Unique",
            "Zufallsziffer anhaengen, sonst schluckt der Server Wiederholungen", true));

    /** Der Text selbst steht in glowcube.json unter "message". */
    private String text = "GlowCube";
    private final Random zufall = new Random();
    private int ticks;

    public Spammer() {
        super("Spammer", "Schickt regelmaessig eine Chatnachricht", Category.MISC);
    }

    public void setText(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    @Override
    public void onEnable() {
        ticks = 0;
    }

    @Override
    public void onTick() {
        if (++ticks < interval.getInt() * 20) {
            return;
        }
        ticks = 0;
        if (player().connection == null) {
            return;
        }
        String zeile = eindeutig.get() ? text + " " + zufall.nextInt(1000) : text;
        player().connection.sendChat(zeile);
    }
}
