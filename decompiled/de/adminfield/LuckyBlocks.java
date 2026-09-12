/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class LuckyBlocks {
    private static final String SOURCE_PLUGIN = "GlowCubeLuckyBlocks";
    private static final String PACK_NAMESPACE = "glowcube";
    private final AdminFieldPlugin plugin;

    public LuckyBlocks(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    private Plugin source() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(SOURCE_PLUGIN);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    public boolean pluginPresent() {
        return this.source() != null;
    }

    public boolean enabled() {
        return this.plugin.getConfig().getBoolean("luckyblocks.enabled", true);
    }

    public boolean allowed(Tier tier) {
        if (tier == Tier.SUBSCRIBE) {
            return false;
        }
        List list = this.plugin.getConfig().getStringList("luckyblocks.allowed");
        if (list.isEmpty()) {
            return true;
        }
        for (String string : list) {
            if (!string.trim().equalsIgnoreCase(tier.id())) continue;
            return true;
        }
        return false;
    }

    public int amount(boolean bl) {
        int n = bl ? this.plugin.getConfig().getInt("luckyblocks.amount-shift", 64) : this.plugin.getConfig().getInt("luckyblocks.amount-click", 16);
        return Math.max(1, Math.min(64, n));
    }

    public ItemStack create(Tier tier, int n) {
        if (tier == Tier.SUBSCRIBE) {
            return null;
        }
        Plugin plugin = this.source();
        if (plugin == null) {
            return null;
        }
        ItemStack itemStack = new ItemStack(Material.NOTE_BLOCK, Math.max(1, Math.min(64, n)));
        ItemMeta itemMeta = itemStack.getItemMeta();
        NamespacedKey namespacedKey = NamespacedKey.fromString((String)("glowcube:" + tier.itemId()));
        if (namespacedKey != null) {
            itemMeta.setItemModel(namespacedKey);
        }
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "glow_id"), PersistentDataType.STRING, (Object)tier.itemId());
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "lucky_tier"), PersistentDataType.STRING, (Object)tier.id());
        itemMeta.displayName(Ui.item(tier.displayName()));
        itemMeta.itemName(Ui.item(tier.displayName()));
        ArrayList<Component> arrayList = new ArrayList<Component>();
        for (String string : tier.lore) {
            arrayList.add(Ui.item(string));
        }
        itemMeta.lore(arrayList);
        itemMeta.setRarity(tier.rarity);
        itemMeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public static Tier byId(String string) {
        if (string == null) {
            return null;
        }
        for (Tier tier : Tier.values()) {
            if (!tier.id().equalsIgnoreCase(string.trim())) continue;
            return tier;
        }
        try {
            return Tier.valueOf(string.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }

    public static enum Tier {
        NORMAL("normal", "lucky_block_normal", ItemRarity.UNCOMMON, "<yellow>", "<gradient:#ffe259:#ffa751><bold>GlowCube Lucky Block</bold></gradient>", new String[]{"<gray>Platzieren, abbauen, hoffen.", "<gray>Ruestung und Werkzeug <white>zwischen Diamant und Netherite<gray>.", "", "<dark_gray>Stufe: <yellow>NORMAL"}, new String[]{"Glowsteel-Set, Schwert, Spitzhacke", "Diamanten, Elytra, Reittiere"}),
        SUPER("super", "lucky_block_super", ItemRarity.RARE, "<aqua>", "<gradient:#12c2e9:#4f8bff><bold>GlowCube Super Lucky Block</bold></gradient>", new String[]{"<gray>Deutlich staerkerer Loot.", "<gray>Hier kommt das <aqua>Glowy Sword<gray> raus.", "", "<dark_gray>Stufe: <aqua>SUPER"}, new String[]{"Glowy Sword mit 10 Schaden", "Glowy-Set, Trident, Netherite, Totems"}),
        SUBSCRIBE("subscribe", "lucky_block_subscribe", ItemRarity.EPIC, "<light_purple>", "<gradient:#ff3d81:#ff0044><bold>GlowCube Subscribe Lucky Block</bold></gradient>", new String[]{"<gray>Die absolute Spitze.", "<gray>Chance auf das <light_purple>Legendary Glowy Sword<gray>.", "", "<dark_gray>Stufe: <light_purple>SUBSCRIBE"}, new String[]{"Legendary Glowy Sword mit 30 Schaden", "Legendary-Set, Subscribe Apple, Glow Core"});

        private final String id;
        private final String itemId;
        private final ItemRarity rarity;
        private final String color;
        private final String displayName;
        private final String[] lore;
        private final String[] contents;

        private Tier(String string2, String string3, ItemRarity itemRarity, String string4, String string5, String[] stringArray, String[] stringArray2) {
            this.id = string2;
            this.itemId = string3;
            this.rarity = itemRarity;
            this.color = string4;
            this.displayName = string5;
            this.lore = stringArray;
            this.contents = stringArray2;
        }

        public String id() {
            return this.id;
        }

        public String itemId() {
            return this.itemId;
        }

        public String color() {
            return this.color;
        }

        public String displayName() {
            return this.displayName;
        }

        public String[] contents() {
            return this.contents;
        }
    }
}

