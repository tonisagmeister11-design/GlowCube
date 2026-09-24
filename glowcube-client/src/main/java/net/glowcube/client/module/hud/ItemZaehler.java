package net.glowcube.client.module.hud;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.TextListSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.util.Ids;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item-Zaehler: wie viel man von bestimmten Items im ganzen Inventar hat -
 * mit Symbol, ohne das Inventar zu oeffnen. Welche Items, steht in der
 * Liste (Namen wie "diamond" oder "minecraft:ender_pearl").
 */
public final class ItemZaehler extends HudModul {
    private final TextListSetting items = register(new TextListSetting("Items",
            "Welche Items gezaehlt werden (z.B. diamond, ender_pearl)",
            "diamond", "netherite_ingot", "iron_ingot", "gold_ingot", "emerald", "ender_pearl",
            "golden_apple", "totem_of_undying", "end_crystal", "obsidian"));
    private final BooleanSetting nullZeigen = register(new BooleanSetting("Auch 0 zeigen",
            "Items zeigen, von denen man gerade nichts hat", false));

    private final List<ItemStack> symbole = new ArrayList<>();
    private final List<Integer> anzahlen = new ArrayList<>();
    /** Die Symbole erst spaet anlegen: ItemStacks vor dem Laden der Register stuerzen ab. */
    private final Map<String, ItemStack> symbolSpeicher = new HashMap<>();

    public ItemZaehler() {
        super("Item-Zaehler", "Zeigt, wie viel du von wichtigen Items hast", true, Category.HUD);
    }

    @Override
    public void vorbereiten() {
        symbole.clear();
        anzahlen.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Map<String, Integer> zaehlung = new HashMap<>();
        var inventar = mc.player.getInventory();
        for (int i = 0; i < inventar.getContainerSize(); i++) {
            ItemStack stack = inventar.getItem(i);
            if (!stack.isEmpty()) {
                zaehlung.merge(Ids.item(stack), stack.getCount(), Integer::sum);
            }
        }
        for (String name : items.werte()) {
            String kurz = name.trim().toLowerCase(java.util.Locale.ROOT);
            if (kurz.contains(":")) {
                kurz = kurz.substring(kurz.indexOf(':') + 1);
            }
            int anzahl = zaehlung.getOrDefault(kurz, 0);
            if (anzahl == 0 && !nullZeigen.get()) {
                continue;
            }
            ItemStack symbol = symbolSpeicher.computeIfAbsent(kurz, k -> {
                Item item = Ids.itemNachName(k);
                return item == null ? ItemStack.EMPTY : new ItemStack(item);
            });
            if (symbol.isEmpty()) {
                continue;
            }
            symbole.add(symbol);
            anzahlen.add(anzahl);
        }
    }

    @Override
    public float breite(HudZeichner z) {
        float w = 20;
        for (int anzahl : anzahlen) {
            w = Math.max(w, 22 + z.breite(String.valueOf(anzahl)));
        }
        return w + 4;
    }

    @Override
    public float hoehe() {
        return symbole.isEmpty() ? 0 : symbole.size() * 17 + 3;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        for (int i = 0; i < symbole.size(); i++) {
            float zy = y + 2 + i * 17;
            z.gegenstand(symbole.get(i), (int) x + 2, (int) zy);
            int anzahl = anzahlen.get(i);
            z.text(String.valueOf(anzahl), x + 21, zy + 5, anzahl == 0 ? 0xFFFF5F6D : 0xFFF2F5FF, true);
        }
    }
}
