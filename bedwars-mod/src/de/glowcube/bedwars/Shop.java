package de.glowcube.bedwars;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** Der Shop: ein Kisten-Menue, in dem ein Klick einen Kauf ausloest. */
public final class Shop {
    private interface Give {
        boolean give(ServerPlayer player, Game.TeamState team);
    }

    private record Offer(int slot, ItemStack icon, String name, Item currency, int price, Give give) {}

    private static final List<Offer> OFFERS = new ArrayList<>();

    static {
        add(10, new ItemStack(Items.WOOL.white(), 16), "Wolle x16", Items.IRON_INGOT, 4, (p, t) -> {
            p.getInventory().add(new ItemStack(t.team.wool.asItem(), 16));
            return true;
        });
        add(11, new ItemStack(Items.STONE_SWORD), "Steinschwert", Items.IRON_INGOT, 10, (p, t) -> sword(p, 1, Items.STONE_SWORD));
        add(12, new ItemStack(Items.IRON_SWORD), "Eisenschwert", Items.GOLD_INGOT, 7, (p, t) -> sword(p, 2, Items.IRON_SWORD));
        add(13, new ItemStack(Items.DIAMOND_SWORD), "Diamantschwert", Items.EMERALD, 4, (p, t) -> sword(p, 3, Items.DIAMOND_SWORD));
        add(14, new ItemStack(Items.FIRE_CHARGE), "Feuerball", Items.IRON_INGOT, 40, (p, t) -> {
            ItemStack s = new ItemStack(Items.FIRE_CHARGE);
            s.set(DataComponents.CUSTOM_NAME, Component.literal("Feuerball").withStyle(ChatFormatting.GOLD));
            p.getInventory().add(s);
            return true;
        });
        add(15, new ItemStack(Items.GOLDEN_APPLE), "Goldener Apfel", Items.GOLD_INGOT, 3, (p, t) -> {
            p.getInventory().add(new ItemStack(Items.GOLDEN_APPLE));
            return true;
        });
        add(16, new ItemStack(Items.ENDER_PEARL), "Enderperle", Items.EMERALD, 4, (p, t) -> {
            p.getInventory().add(new ItemStack(Items.ENDER_PEARL));
            return true;
        });
        add(19, new ItemStack(Items.CHAINMAIL_BOOTS), "Kettenruestung", Items.IRON_INGOT, 40, (p, t) -> armor(p, t, 1));
        add(20, new ItemStack(Items.IRON_BOOTS), "Eisenruestung", Items.GOLD_INGOT, 12, (p, t) -> armor(p, t, 2));
        add(21, new ItemStack(Items.DIAMOND_BOOTS), "Diamantruestung", Items.EMERALD, 6, (p, t) -> armor(p, t, 3));
    }

    private static void add(int slot, ItemStack icon, String name, Item currency, int price, Give give) {
        OFFERS.add(new Offer(slot, icon, name, currency, price, give));
    }

    public static void open(ServerPlayer player) {
        if (!Game.running()) {
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ShopMenu(id, inv, build()),
                Component.literal("Shop").withStyle(ChatFormatting.GOLD)));
    }

    private static SimpleContainer build() {
        SimpleContainer c = new SimpleContainer(27);
        ItemStack filler = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = 0; i < 27; i++) {
            c.setItem(i, filler.copy());
        }
        for (Offer o : OFFERS) {
            ItemStack icon = o.icon.copy();
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(o.name).withStyle(ChatFormatting.WHITE));
            icon.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Preis: " + o.price + " " + currencyName(o.currency)).withStyle(ChatFormatting.GRAY),
                    Component.literal("Klicken zum Kaufen").withStyle(ChatFormatting.DARK_GRAY))));
            c.setItem(o.slot, icon);
        }
        return c;
    }

    private static String currencyName(Item item) {
        if (item == Items.IRON_INGOT) return "Eisen";
        if (item == Items.GOLD_INGOT) return "Gold";
        if (item == Items.EMERALD) return "Smaragd";
        return "Diamant";
    }

    private static boolean sword(ServerPlayer p, int tier, Item sword) {
        if (Game.playerSwordTier(p) >= tier) {
            p.sendSystemMessage(Component.literal("Du hast schon ein gleich gutes oder besseres Schwert.").withStyle(ChatFormatting.RED));
            return false;
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.WOODEN_SWORD) || inv.getItem(i).is(Items.STONE_SWORD) || inv.getItem(i).is(Items.IRON_SWORD)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        inv.add(new ItemStack(sword));
        return true;
    }

    private static boolean armor(ServerPlayer p, Game.TeamState t, int tier) {
        if (t.playerArmorTier >= tier) {
            p.sendSystemMessage(Component.literal("Du hast schon diese oder eine bessere Ruestung.").withStyle(ChatFormatting.RED));
            return false;
        }
        t.playerArmorTier = tier;
        Game.equipPlayerArmor(p, t);
        return true;
    }

    private static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static void take(Inventory inv, Item item, int amount) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int n = Math.min(amount, s.getCount());
                s.shrink(n);
                if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                amount -= n;
            }
        }
    }

    private static final class ShopMenu extends ChestMenu {
        ShopMenu(int id, Inventory inv, Container container) {
            super(MenuType.GENERIC_9x3, id, inv, container, 3);
        }

        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            if (slotId >= 0 && slotId < 27) {
                if (player instanceof ServerPlayer sp && input == ContainerInput.PICKUP) {
                    buy(sp, slotId);
                }
                return;
            }
            if (slotId >= 27 && input != ContainerInput.PICKUP) {
                return; // kein Hineinschieben in den Shop
            }
            super.clicked(slotId, button, input, player);
        }

        private void buy(ServerPlayer player, int slot) {
            Game.TeamState team = Game.playerTeam();
            if (team == null) return;
            for (Offer o : OFFERS) {
                if (o.slot != slot) continue;
                Inventory inv = player.getInventory();
                if (count(inv, o.currency) < o.price) {
                    player.sendSystemMessage(Component.literal("Dir fehlen " + currencyName(o.currency) + ".").withStyle(ChatFormatting.RED));
                    return;
                }
                if (o.give.give(player, team)) {
                    take(inv, o.currency, o.price);
                    player.sendSystemMessage(Component.literal("Gekauft: " + o.name).withStyle(ChatFormatting.GREEN));
                }
                return;
            }
        }
    }
}
