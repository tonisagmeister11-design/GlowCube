package net.glowcube.client.module.hud;

import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.ColorUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Was gerade ins Inventar kam oder es verliess - aus AxolotlClient
 * ({@code ItemUpdateHud}): "+3 Stein", "-1 Fackel", nach ein paar Sekunden
 * blendet die Zeile aus. Verglichen wird jeden Tick die Anzahl je
 * Gegenstandsart.
 */
public final class AufgesammeltHud extends HudModul {
    private final NumberSetting dauer = register(
            new NumberSetting("Dauer", "Wie viele Sekunden eine Zeile stehen bleibt", 6, 1, 20, 1));

    private Map<Item, Integer> vorher;
    private final List<Eintrag> eintraege = new ArrayList<>();

    private static final class Eintrag {
        final Item item;
        final String name;
        int menge;
        long zeit;

        Eintrag(Item item, String name, int menge) {
            this.item = item;
            this.name = name;
            this.menge = menge;
            this.zeit = System.currentTimeMillis();
        }
    }

    public AufgesammeltHud() {
        super("Aufgesammelt", "Zeigt, was gerade ins Inventar kam oder es verliess", true);
    }

    @Override
    public void onEnable() {
        vorher = null;
        eintraege.clear();
    }

    @Override
    public void onDisable() {
        vorher = null;
        eintraege.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null) {
            return;
        }
        Map<Item, Integer> jetzt = new HashMap<>();
        Map<Item, String> namen = new HashMap<>();
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                jetzt.merge(s.getItem(), s.getCount(), Integer::sum);
                namen.putIfAbsent(s.getItem(), s.getHoverName().getString());
            }
        }
        // Waehrend ein Behaelter offen ist, wandern Gegenstaende nur hin und
        // her - das waere kein Aufsammeln.
        if (vorher != null && mc.player.containerMenu == mc.player.inventoryMenu) {
            for (Map.Entry<Item, Integer> e : jetzt.entrySet()) {
                int alt = vorher.getOrDefault(e.getKey(), 0);
                if (e.getValue() != alt) {
                    melden(e.getKey(), namen.get(e.getKey()), e.getValue() - alt);
                }
            }
            for (Map.Entry<Item, Integer> e : vorher.entrySet()) {
                if (!jetzt.containsKey(e.getKey())) {
                    melden(e.getKey(), null, -e.getValue());
                }
            }
        }
        vorher = jetzt;
    }

    private void melden(Item item, String name, int menge) {
        for (Eintrag e : eintraege) {
            if (e.item == item && Integer.signum(e.menge) == Integer.signum(menge)) {
                e.menge += menge;
                e.zeit = System.currentTimeMillis();
                return;
            }
        }
        String anzeige = name != null ? name : new ItemStack(item).getHoverName().getString();
        eintraege.add(new Eintrag(item, anzeige, menge));
    }

    @Override
    public void vorbereiten() {
        long grenze = System.currentTimeMillis() - (long) (dauer.get() * 1000);
        for (Iterator<Eintrag> it = eintraege.iterator(); it.hasNext(); ) {
            if (it.next().zeit < grenze) {
                it.remove();
            }
        }
    }

    private static String zeile(Eintrag e) {
        return (e.menge > 0 ? "+" : "") + e.menge + " " + e.name;
    }

    @Override
    public float breite(HudZeichner z) {
        float w = 0;
        for (Eintrag e : eintraege) {
            w = Math.max(w, z.breite(zeile(e)));
        }
        return w + 8;
    }

    @Override
    public float hoehe() {
        return eintraege.size() * 11 + (eintraege.isEmpty() ? 0 : 2);
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        long jetzt = System.currentTimeMillis();
        float ganz = (float) (dauer.get() * 1000);
        for (int i = 0; i < eintraege.size(); i++) {
            Eintrag e = eintraege.get(i);
            float rest = Math.max(0f, Math.min(1f, (ganz - (jetzt - e.zeit)) / 1000f));
            int farbe = e.menge > 0 ? 0xFF5FE3A1 : 0xFFFF5F6D;
            z.text(zeile(e), x + 4, y + 2 + i * 11, ColorUtil.fade(farbe, Math.max(0.15f, rest)), true);
        }
    }
}
