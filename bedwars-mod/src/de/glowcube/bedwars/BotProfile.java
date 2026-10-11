package de.glowcube.bedwars;

import java.util.Random;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Alles, was einen Bot ausmacht und seinen Tod ueberlebt: Rolle, Geldbeutel, Ausruestung. */
public final class BotProfile {
    public enum Role { RAIDER, GATHERER, DEFENDER }

    public final String name;
    public final Game.TeamState team;
    public Role role;
    public boolean eliminated;
    public int respawnAt = -1;
    public BedwarsBot body;

    public int blocks, gapples;
    public int swordTier;   // 0 Holz, 1 Stein, 2 Eisen, 3 Diamant
    public int armorTier;   // 0 Leder, 1 Kette, 2 Eisen, 3 Diamant
    public boolean fortified;

    public BotProfile(String name, Game.TeamState team, Random rnd) {
        this.name = name;
        this.team = team;
        double r = rnd.nextDouble();
        this.role = r < 0.50 ? Role.RAIDER : r < 0.78 ? Role.GATHERER : Role.DEFENDER;
    }

    /** Einkaufsliste in fester Reihenfolge. Gibt true zurueck, wenn sich die Ausruestung geaendert hat. */
    public boolean shop() {
        boolean changed = false;
        if (swordTier < 1 && team.iron >= 10) {
            team.iron -= 10;
            swordTier = 1;
            changed = true;
        } else if (swordTier < 2 && team.gold >= 7) {
            team.gold -= 7;
            swordTier = 2;
            changed = true;
        } else if (swordTier < 3 && team.emerald >= 4) {
            team.emerald -= 4;
            swordTier = 3;
            changed = true;
        }
        if (armorTier < 1 && team.iron >= 40) {
            team.iron -= 40;
            armorTier = 1;
            changed = true;
        } else if (armorTier < 2 && team.gold >= 12) {
            team.gold -= 12;
            armorTier = 2;
            changed = true;
        } else if (armorTier < 3 && team.emerald >= 6) {
            team.emerald -= 6;
            armorTier = 3;
            changed = true;
        }
        if (blocks < 14 && team.iron >= 4) {
            team.iron -= 4;
            blocks += 16;
        }
        if (gapples < 3 && team.gold >= 3 && (armorTier >= 1 || team.gold >= 10)) {
            team.gold -= 3;
            gapples++;
        }
        return changed;
    }

    /** Beim Tod geht der Waffen-Fortschritt eine Stufe zurueck, Ruestung und Block-Vorrat bleiben. */
    public void onDeath() {
        swordTier = Math.max(0, swordTier - 1);
    }

    public void equip(BedwarsBot bot) {
        Item sword = switch (swordTier) {
            case 0 -> Items.WOODEN_SWORD;
            case 1 -> Items.STONE_SWORD;
            case 2 -> Items.IRON_SWORD;
            default -> Items.DIAMOND_SWORD;
        };
        bot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(sword));
        bot.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET));
        bot.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE));
        bot.setItemSlot(EquipmentSlot.LEGS, switch (armorTier) {
            case 0 -> dyed(Items.LEATHER_LEGGINGS);
            case 1 -> new ItemStack(Items.CHAINMAIL_LEGGINGS);
            case 2 -> new ItemStack(Items.IRON_LEGGINGS);
            default -> new ItemStack(Items.DIAMOND_LEGGINGS);
        });
        bot.setItemSlot(EquipmentSlot.FEET, switch (armorTier) {
            case 0 -> dyed(Items.LEATHER_BOOTS);
            case 1 -> new ItemStack(Items.CHAINMAIL_BOOTS);
            case 2 -> new ItemStack(Items.IRON_BOOTS);
            default -> new ItemStack(Items.DIAMOND_BOOTS);
        });
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            bot.setDropChance(slot, 0.0f);
        }
        bot.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0 + swordTier + 1.0);
    }

    private ItemStack dyed(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(team.team.rgb));
        return stack;
    }
}
