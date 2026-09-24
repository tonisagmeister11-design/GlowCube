package net.glowcube.client.module.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.hud.Meldungen;
import net.glowcube.client.util.Theme;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Totem-Pop-Zaehler: zaehlt, wie viele Totems jeder Spieler in der Naehe
 * schon verbraucht hat - wie in PvP-Clients. Der Server meldet jedes
 * ausgeloeste Totem mit dem Wesen-Ereignis 35; stirbt der Spieler (Ereignis
 * 3), steht kurz "gestorben nach n" da und der Zaehler beginnt neu.
 */
public final class TotemPops extends HudModul {
    private static final byte TOTEM = 35;
    private static final byte TOD = 3;
    private static TotemPops instanz;

    private final BooleanSetting melden = register(new BooleanSetting("Melden",
            "Jeden Pop als Benachrichtigung zeigen", true));
    private final BooleanSetting selbst = register(new BooleanSetting("Eigene",
            "Auch die eigenen Pops zaehlen", true));

    /** Die Pakete kommen im Netz-Thread an; verarbeitet wird im Spiel-Tick. */
    private final ConcurrentLinkedQueue<ClientboundEntityEventPacket> eingang = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> pops = new LinkedHashMap<>();
    private final List<String> zeilen = new ArrayList<>();

    public TotemPops() {
        super("Totem-Pops", "Zaehlt die verbrauchten Totems jedes Spielers", true, Category.PVP_HUD);
        instanz = this;
    }

    @Override
    public void onEnable() {
        pops.clear();
        eingang.clear();
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (packet instanceof ClientboundEntityEventPacket ereignis) {
            eingang.add(ereignis);
        }
        return false;
    }

    @Override
    public void onTick() {
        ClientboundEntityEventPacket ereignis;
        while ((ereignis = eingang.poll()) != null) {
            Entity wesen = ereignis.getEntity(level());
            if (!(wesen instanceof Player spieler) || (!selbst.get() && spieler == player())) {
                continue;
            }
            String name = spieler.getName().getString();
            if (ereignis.getEventId() == TOTEM) {
                int n = pops.merge(name, 1, Integer::sum);
                if (melden.get()) {
                    Meldungen.melden(name + " hat ein Totem verbraucht (" + n + ")", 0xFFFFC53D);
                }
            } else if (ereignis.getEventId() == TOD && pops.containsKey(name)) {
                int n = pops.remove(name);
                if (melden.get()) {
                    Meldungen.melden(name + " ist gestorben nach " + n + " Pops", 0xFFFF5F6D);
                }
            }
        }
    }

    /** Fuer die Spieltests. */
    public static int anzahl(String name) {
        return instanz == null ? 0 : instanz.pops.getOrDefault(name, 0);
    }

    @Override
    public void vorbereiten() {
        zeilen.clear();
        for (Map.Entry<String, Integer> e : pops.entrySet()) {
            zeilen.add(e.getKey() + ": " + e.getValue());
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
        return zeilen.isEmpty() ? 0 : zeilen.size() * 10 + 14;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        z.text("Totem-Pops", x + 4, y + 3, Theme.ACCENT_A, true);
        for (int i = 0; i < zeilen.size(); i++) {
            z.text(zeilen.get(i), x + 4, y + 14 + i * 10, Theme.TEXT, true);
        }
    }
}
