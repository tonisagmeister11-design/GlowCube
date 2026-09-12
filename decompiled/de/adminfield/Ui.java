/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class Ui {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String ACCENT = "#5ad1ff";
    public static final String ACCENT_2 = "#a06bff";

    private Ui() {
    }

    public static Component mm(String string) {
        return MM.deserialize((Object)string);
    }

    public static Component item(String string) {
        return MM.deserialize((Object)("<!i>" + string));
    }

    public static List<Component> lore(List<String> list) {
        ArrayList<Component> arrayList = new ArrayList<Component>(list.size());
        for (String string : list) {
            arrayList.add(Ui.item(string));
        }
        return arrayList;
    }

    public static ItemStack icon(Material material, String string, List<String> list) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(Ui.item(string));
        if (!list.isEmpty()) {
            itemMeta.lore(Ui.lore(list));
        }
        itemMeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE});
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public static ItemStack icon(Material material, String string) {
        return Ui.icon(material, string, List.of());
    }

    public static ItemStack glowing(Material material, String string, List<String> list) {
        ItemStack itemStack = Ui.icon(material, string, list);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setEnchantmentGlintOverride(Boolean.TRUE);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public static ItemStack head(OfflinePlayer offlinePlayer, String string, List<String> list) {
        ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta)itemStack.getItemMeta();
        skullMeta.setOwningPlayer(offlinePlayer);
        skullMeta.displayName(Ui.item(string));
        if (!list.isEmpty()) {
            skullMeta.lore(Ui.lore(list));
        }
        itemStack.setItemMeta((ItemMeta)skullMeta);
        return itemStack;
    }

    public static ItemStack toggle(boolean bl, String string, List<String> list) {
        ArrayList<String> arrayList = new ArrayList<String>(list);
        arrayList.add("");
        arrayList.add(bl ? "<green>▪ Aktiv <dark_gray>· Klick zum Ausschalten" : "<dark_gray>▪ <gray>Inaktiv <dark_gray>· Klick zum Einschalten");
        return bl ? Ui.glowing(Material.LIME_DYE, string, arrayList) : Ui.icon(Material.GRAY_DYE, string, arrayList);
    }

    public static String pos(Location location) {
        return location.getBlockX() + " / " + location.getBlockY() + " / " + location.getBlockZ();
    }

    public static String bar(double d, double d2, int n, String string, String string2) {
        if (d2 <= 0.0) {
            d2 = 1.0;
        }
        int n2 = (int)Math.round(Math.max(0.0, Math.min(1.0, d / d2)) * (double)n);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<").append(string).append(">");
        stringBuilder.append("|".repeat(n2));
        stringBuilder.append("<").append(string2).append(">");
        stringBuilder.append("|".repeat(Math.max(0, n - n2)));
        return stringBuilder.toString();
    }

    public static String ago(long l) {
        long l2 = Math.max(0L, l / 1000L);
        if (l2 < 60L) {
            return "vor " + l2 + " Sek.";
        }
        long l3 = l2 / 60L;
        if (l3 < 60L) {
            return "vor " + l3 + " Min.";
        }
        long l4 = l3 / 60L;
        if (l4 < 24L) {
            return "vor " + l4 + " Std.";
        }
        return "vor " + l4 / 24L + " Tagen";
    }

    public static String duration(long l) {
        long l2 = Math.max(0L, l / 60000L);
        long l3 = l2 / 60L;
        if (l3 <= 0L) {
            return l2 + "m";
        }
        long l4 = l3 / 24L;
        if (l4 <= 0L) {
            return l3 + "h " + l2 % 60L + "m";
        }
        return l4 + "d " + l3 % 24L + "h";
    }

    public static String pretty(String string) {
        String[] stringArray = string.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
        StringBuilder stringBuilder = new StringBuilder();
        for (String string2 : stringArray) {
            if (string2.isEmpty()) continue;
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(Character.toUpperCase(string2.charAt(0))).append(string2.substring(1));
        }
        return stringBuilder.toString();
    }

    public static String gameMode(GameMode gameMode) {
        return switch (gameMode) {
            default -> throw new MatchException(null, null);
            case GameMode.SURVIVAL -> "Überleben";
            case GameMode.CREATIVE -> "Kreativ";
            case GameMode.ADVENTURE -> "Abenteuer";
            case GameMode.SPECTATOR -> "Zuschauer";
        };
    }

    public static String gradeHigh(double d, double d2, double d3) {
        if (d >= d2) {
            return "<green>";
        }
        return d >= d3 ? "<yellow>" : "<red>";
    }

    public static String gradeLow(double d, double d2, double d3) {
        if (d <= d2) {
            return "<green>";
        }
        return d <= d3 ? "<yellow>" : "<red>";
    }
}

