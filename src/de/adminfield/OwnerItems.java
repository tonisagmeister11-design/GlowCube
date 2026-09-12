/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class OwnerItems {
    private static final double BASE_ATTACK_DAMAGE = 1.0;
    private static final double BASE_ATTACK_SPEED = 4.0;
    private final AdminFieldPlugin plugin;
    private final NamespacedKey idKey;
    private final Map<String, ItemStack> items = new LinkedHashMap<String, ItemStack>();

    public OwnerItems(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
        this.idKey = new NamespacedKey((Plugin)adminFieldPlugin, "owner_item");
        this.register();
    }

    public NamespacedKey idKey() {
        return this.idKey;
    }

    public String idOf(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null;
        }
        return (String)itemStack.getItemMeta().getPersistentDataContainer().get(this.idKey, PersistentDataType.STRING);
    }

    public ItemStack get(String string) {
        ItemStack itemStack = this.items.get(string);
        return itemStack == null ? null : itemStack.clone();
    }

    public List<String> ids() {
        return new ArrayList<String>(this.items.keySet());
    }

    private void register() {
        this.build("owner_sword", Material.NETHERITE_SWORD, "<gradient:#ffd166:#ff2e63><bold>Owner-Klinge</bold></gradient>", List.of("<gray>Schärfe <white>255<gray>. Mehr gibt das Spiel nicht her.", "<dark_gray>Ein Treffer, eine Geschichte.")).damage(60.0, 1.6).ench(Enchantment.SHARPNESS, 255).ench(Enchantment.FIRE_ASPECT, 10).ench(Enchantment.LOOTING, 10).ench(Enchantment.KNOCKBACK, 5).unbreakable().done();
        this.build("owner_axe", Material.NETHERITE_AXE, "<gradient:#ffd166:#ff2e63><bold>Donnerhammer</bold></gradient>", List.of("<gray>Spaltet alles – Bäume, Schilde, Hoffnungen.")).damage(80.0, 1.0).ench(Enchantment.SHARPNESS, 200).ench(Enchantment.EFFICIENCY, 100).unbreakable().done();
        this.build("owner_pickaxe", Material.NETHERITE_PICKAXE, "<gradient:#ffd166:#ff2e63><bold>Weltenbrecher</bold></gradient>", List.of("<gray>Blöcke lösen sich praktisch von selbst auf.", "<gray>Glück <white>255<gray>. Mehr gibt das Spiel nicht her.")).damage(30.0, 1.2).ench(Enchantment.EFFICIENCY, 255).ench(Enchantment.FORTUNE, 255).attr(Attribute.MINING_EFFICIENCY, 100.0, EquipmentSlotGroup.MAINHAND, "mining").attr(Attribute.BLOCK_INTERACTION_RANGE, 4.0, EquipmentSlotGroup.MAINHAND, "reach").unbreakable().done();
        this.build("knockback_stick_255", Material.STICK, "<gradient:#ffd166:#ff2e63><bold>Knockback-Stick 255</bold></gradient>", List.of("<gray>Rückstoß <white>255<gray>. Mehr gibt das Spiel nicht her.", "<dark_gray>Wer getroffen wird, ist erst mal weg.")).ench(Enchantment.KNOCKBACK, 255).unbreakable().glow().done();
        this.build("knockback_stick_25", Material.STICK, "<gradient:#ffd166:#ff8a00><bold>Knockback-Stick 25</bold></gradient>", List.of("<gray>Rückstoß <white>25<gray>.", "<dark_gray>Kräftig, aber noch zielbar.")).ench(Enchantment.KNOCKBACK, 25).unbreakable().glow().done();
        this.build("owner_bow", Material.BOW, "<gradient:#ffd166:#ff2e63><bold>Sternenbogen</bold></gradient>", List.of("<gray>Jeder Pfeil ein Einschlag.", "<dark_gray>Pfeile braucht er keine.")).ench(Enchantment.POWER, 255).ench(Enchantment.FLAME, 1).ench(Enchantment.INFINITY, 1).ench(Enchantment.PUNCH, 10).unbreakable().done();
        this.build("owner_trident", Material.TRIDENT, "<gradient:#ffd166:#ff2e63><bold>Sturmzahn</bold></gradient>", List.of("<gray>Kommt zurück und bringt das Gewitter mit.")).damage(50.0, 1.1).ench(Enchantment.LOYALTY, 3).ench(Enchantment.IMPALING, 100).ench(Enchantment.CHANNELING, 1).unbreakable().done();
        this.armor("owner_helmet", Material.NETHERITE_HELMET, "Owner-Helm", "<gray>Sieht dich alles überstehen.", 20.0, EquipmentSlotGroup.HEAD).ench(Enchantment.RESPIRATION, 10).ench(Enchantment.AQUA_AFFINITY, 1).attr(Attribute.MAX_ABSORPTION, 20.0, EquipmentSlotGroup.HEAD, "absorb").done();
        this.armor("owner_chestplate", Material.NETHERITE_CHESTPLATE, "Owner-Brustplatte", "<gray>Zwanzig zusätzliche Herzen.", 40.0, EquipmentSlotGroup.CHEST).ench(Enchantment.THORNS, 20).attr(Attribute.MAX_HEALTH, 40.0, EquipmentSlotGroup.CHEST, "health").done();
        this.armor("owner_leggings", Material.NETHERITE_LEGGINGS, "Owner-Hose", "<gray>Unbeeindruckt von so ziemlich allem.", 30.0, EquipmentSlotGroup.LEGS).attr(Attribute.MAX_HEALTH, 20.0, EquipmentSlotGroup.LEGS, "health").done();
        this.armor("owner_boots", Material.NETHERITE_BOOTS, "Owner-Stiefel", "<gray>Kein Sturz ist tief genug.", 20.0, EquipmentSlotGroup.FEET).ench(Enchantment.FEATHER_FALLING, 10).ench(Enchantment.DEPTH_STRIDER, 3).ench(Enchantment.SOUL_SPEED, 10).attr(Attribute.MOVEMENT_SPEED, 0.06, EquipmentSlotGroup.FEET, "speed").attr(Attribute.SAFE_FALL_DISTANCE, 200.0, EquipmentSlotGroup.FEET, "fall").done();
        this.build("wand_tnt", Material.BLAZE_ROD, "<gradient:#ff8a00:#ff2e63><bold>TNT-Regen</bold></gradient>", List.of("<gray>Rechtsklick lässt <white>" + this.plugin.getConfig().getInt("ownerstuff.tnt-amount", 150) + " TNT<gray> auf die", "<gray>anvisierte Stelle regnen.", "", "<dark_gray>Die Zünder sind gestaffelt, damit der", "<dark_gray>Server das auch überlebt.")).glow().done();
        this.build("wand_lightning", Material.END_ROD, "<gradient:#ffd166:#5ad1ff><bold>Blitzstab</bold></gradient>", List.of("<gray>Rechtsklick ruft einen Blitz", "<gray>auf die anvisierte Stelle.")).glow().done();
        this.build("wand_heal", Material.GHAST_TEAR, "<gradient:#7cf7ff:#00ff9d><bold>Heilstab</bold></gradient>", List.of("<gray>Rechtsklick heilt alle Spieler", "<gray>im Umkreis von 20 Blöcken vollständig.")).glow().done();
        this.build("wand_freeze", Material.PACKED_ICE, "<gradient:#7cf7ff:#3a7bff><bold>Eisstab</bold></gradient>", List.of("<gray>Rechtsklick friert alle Spieler", "<gray>im Umkreis für 10 Sekunden ein.", "<dark_gray>Admins und Owner sind ausgenommen.")).glow().done();
        this.build("wand_launch", Material.FIREWORK_ROCKET, "<gradient:#ff8a00:#ffd166><bold>Katapultstab</bold></gradient>", List.of("<gray>Rechtsklick schleudert alles im Umkreis", "<gray>in die Luft – sanfte Landung inklusive.")).glow().done();
        this.build("wand_time", Material.CLOCK, "<gradient:#ffd166:#a06bff><bold>Zeitstab</bold></gradient>", List.of("<gray>Rechtsklick schaltet zwischen", "<gray>Tag und Nacht um.")).glow().done();
        this.build("wand_clean", Material.HOPPER, "<gradient:#5ad1ff:#a06bff><bold>Aufräumstab</bold></gradient>", List.of("<gray>Rechtsklick entfernt Monster und", "<gray>herumliegende Items im Umkreis.")).glow().done();
        this.build("owner_elytra", Material.ELYTRA, "<gradient:#ffd166:#ff2e63><bold>Owner-Flügel</bold></gradient>", List.of("<gray>Halten ewig.", "<dark_gray>Raketen gibt es gleich dazu.")).ench(Enchantment.MENDING, 1).ench(Enchantment.UNBREAKING, 255).unbreakable().done();
    }

    private Builder build(String string, Material material, String string2, List<String> list) {
        return new Builder(this, string, material, string2, list);
    }

    private Builder armor(String string, Material material, String string2, String string3, double d, EquipmentSlotGroup equipmentSlotGroup) {
        return this.build(string, material, "<gradient:#ffd166:#ff2e63><bold>" + string2 + "</bold></gradient>", List.of(string3, "<dark_gray>Teil der Owner-Rüstung.")).attr(Attribute.ARMOR, d, equipmentSlotGroup, "armor").attr(Attribute.ARMOR_TOUGHNESS, 20.0, equipmentSlotGroup, "tough").attr(Attribute.KNOCKBACK_RESISTANCE, 1.0, equipmentSlotGroup, "kb").ench(Enchantment.PROTECTION, 100).ench(Enchantment.BLAST_PROTECTION, 100).ench(Enchantment.FIRE_PROTECTION, 100).ench(Enchantment.PROJECTILE_PROTECTION, 100).unbreakable();
    }

    private final class Builder {
        private final String id;
        private final ItemStack stack;
        private final ItemMeta meta;
        private final List<String> lore;
        final /* synthetic */ OwnerItems this$0;

        private Builder(OwnerItems ownerItems, String string, Material material, String string2, List<String> list) {
            OwnerItems ownerItems2 = ownerItems;
            Objects.requireNonNull(ownerItems2);
            this.this$0 = ownerItems2;
            this.lore = new ArrayList<String>();
            this.id = string;
            this.stack = new ItemStack(material);
            this.meta = this.stack.getItemMeta();
            this.meta.displayName(Ui.item(string2));
            this.meta.itemName(Ui.item(string2));
            this.meta.getPersistentDataContainer().set(ownerItems.idKey, PersistentDataType.STRING, (Object)string);
            this.meta.setRarity(ItemRarity.EPIC);
            this.meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_UNBREAKABLE});
            this.lore.addAll(list);
        }

        Builder ench(Enchantment enchantment, int n) {
            this.meta.addEnchant(enchantment, n, true);
            return this;
        }

        Builder unbreakable() {
            this.meta.setUnbreakable(true);
            return this;
        }

        Builder glow() {
            this.meta.setEnchantmentGlintOverride(Boolean.TRUE);
            return this;
        }

        Builder attr(Attribute attribute, double d, EquipmentSlotGroup equipmentSlotGroup, String string) {
            this.meta.addAttributeModifier(attribute, new AttributeModifier(new NamespacedKey((Plugin)this.this$0.plugin, this.id + "_" + string), d, AttributeModifier.Operation.ADD_NUMBER, equipmentSlotGroup));
            return this;
        }

        Builder damage(double d, double d2) {
            this.attr(Attribute.ATTACK_DAMAGE, d - 1.0, EquipmentSlotGroup.MAINHAND, "dmg");
            this.attr(Attribute.ATTACK_SPEED, d2 - 4.0, EquipmentSlotGroup.MAINHAND, "spd");
            return this;
        }

        void done() {
            this.lore.add("");
            this.lore.add("<gradient:#ffd166:#ff8a00><bold>OWNER</bold></gradient> <dark_gray>· AdminField");
            this.meta.lore(Ui.lore(this.lore));
            this.stack.setItemMeta(this.meta);
            this.this$0.items.put(this.id, this.stack);
        }
    }
}

