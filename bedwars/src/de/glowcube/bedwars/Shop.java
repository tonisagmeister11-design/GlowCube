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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

public final class Shop {
   private static final int SIZE = 45;
   private static final List<Offer> OFFERS = new ArrayList<>();

   private static void add(int slot, ItemStack icon, String name, Item currency, int price, Give give) {
      OFFERS.add(new Offer(slot, icon, name, currency, price, give));
   }

   public static void open(ServerPlayer p) {
      if (Game.running()) {
         p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ShopMenu(id, inv, build()), Component.literal("Shop").withStyle(ChatFormatting.GOLD)));
      }
   }

   private static SimpleContainer build() {
      SimpleContainer c = new SimpleContainer(SIZE);
      ItemStack filler = new ItemStack((ItemLike)Items.STAINED_GLASS_PANE.gray());
      filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));

      for (int i = 0; i < SIZE; i++) {
         c.setItem(i, filler.copy());
      }

      for (Offer o : OFFERS) {
         ItemStack icon = o.icon.copy();
         icon.set(DataComponents.CUSTOM_NAME, Component.literal(o.name).withStyle(ChatFormatting.WHITE));
         icon.set(
            DataComponents.LORE,
            new ItemLore(
               List.of(
                  Component.literal("Preis: " + o.price + " " + currencyName(o.currency)).withStyle(ChatFormatting.GRAY),
                  Component.literal("Klicken zum Kaufen").withStyle(ChatFormatting.DARK_GRAY)
               )
            )
         );
         c.setItem(o.slot, icon);
      }

      return c;
   }

   private static String currencyName(Item item) {
      if (item == Items.IRON_INGOT) {
         return "Eisen";
      } else if (item == Items.GOLD_INGOT) {
         return "Gold";
      } else {
         return item == Items.EMERALD ? "Smaragd" : "Diamant";
      }
   }

   private static boolean sword(ServerPlayer p, int tier, Item item) {
      if (Game.playerSwordTier(p) >= tier) {
         p.sendSystemMessage(Component.literal("Du hast schon ein gleich gutes oder besseres Schwert.").withStyle(ChatFormatting.RED));
         return false;
      } else {
         Inventory inv = p.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.WOODEN_SWORD) || inv.getItem(i).is(Items.STONE_SWORD) || inv.getItem(i).is(Items.IRON_SWORD)) {
               inv.setItem(i, ItemStack.EMPTY);
            }
         }

         inv.add(new ItemStack(item));
         return true;
      }
   }

   private static boolean armor(ServerPlayer p, Game.TeamState t, int tier) {
      if (t.playerArmorTier >= tier) {
         p.sendSystemMessage(Component.literal("Du hast schon diese oder eine bessere Ruestung.").withStyle(ChatFormatting.RED));
         return false;
      } else {
         t.playerArmorTier = tier;
         Game.equipPlayerArmor(p, t);
         return true;
      }
   }

   private static int pickaxeTier(Inventory inv) {
      int tier = 0;

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.is(Items.WOODEN_PICKAXE)) {
            tier = Math.max(tier, 1);
         } else if (s.is(Items.IRON_PICKAXE)) {
            tier = Math.max(tier, 2);
         } else if (s.is(Items.DIAMOND_PICKAXE)) {
            tier = Math.max(tier, 3);
         }
      }

      return tier;
   }

   private static boolean pickaxe(ServerPlayer p, int tier, Item item) {
      Inventory inv = p.getInventory();
      int have = pickaxeTier(inv);
      if (have >= tier) {
         p.sendSystemMessage(Component.literal("Du hast schon diese oder eine bessere Spitzhacke.").withStyle(ChatFormatting.RED));
         return false;
      } else if (have < tier - 1) {
         p.sendSystemMessage(Component.literal("Kauf zuerst die Stufe davor.").withStyle(ChatFormatting.RED));
         return false;
      } else {
         for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.WOODEN_PICKAXE) || inv.getItem(i).is(Items.IRON_PICKAXE)) {
               inv.setItem(i, ItemStack.EMPTY);
            }
         }

         inv.add(new ItemStack(item));
         return true;
      }
   }

   private static int count(Inventory inv, Item item) {
      int n = 0;

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.is(item)) {
            n += s.getCount();
         }
      }

      return n;
   }

   private static void take(Inventory inv, Item item, int amount) {
      for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
         ItemStack s = inv.getItem(i);
         if (s.is(item)) {
            int n = Math.min(amount, s.getCount());
            s.shrink(n);
            if (s.isEmpty()) {
               inv.setItem(i, ItemStack.EMPTY);
            }

            amount -= n;
         }
      }
   }

   private static Give stack(Item item, int count) {
      return (p, t) -> {
         p.getInventory().add(new ItemStack(item, count));
         return true;
      };
   }

   static {
      // Reihe 1: Bloecke
      add(10, new ItemStack((ItemLike)Items.WOOL.white(), 16), "Wolle x16", Items.IRON_INGOT, 4, (p, t) -> {
         p.getInventory().add(new ItemStack(t.team.wool.asItem(), 16));
         return true;
      });
      add(11, new ItemStack(Items.END_STONE, 12), "Endstein x12", Items.IRON_INGOT, 24, stack(Items.END_STONE, 12));
      add(12, new ItemStack(Items.OAK_PLANKS, 16), "Holz x16", Items.GOLD_INGOT, 4, stack(Items.OAK_PLANKS, 16));
      add(13, new ItemStack(Items.SHEARS), "Schere", Items.IRON_INGOT, 20, stack(Items.SHEARS, 1));
      add(14, new ItemStack(Items.WOODEN_PICKAXE), "Holzspitzhacke", Items.IRON_INGOT, 10, (p, t) -> pickaxe(p, 1, Items.WOODEN_PICKAXE));
      add(15, new ItemStack(Items.IRON_PICKAXE), "Eisenspitzhacke", Items.GOLD_INGOT, 3, (p, t) -> pickaxe(p, 2, Items.IRON_PICKAXE));
      add(16, new ItemStack(Items.DIAMOND_PICKAXE), "Diamantspitzhacke", Items.GOLD_INGOT, 6, (p, t) -> pickaxe(p, 3, Items.DIAMOND_PICKAXE));
      // Reihe 2: Kampf
      add(19, new ItemStack(Items.STONE_SWORD), "Steinschwert", Items.IRON_INGOT, 10, (p, t) -> sword(p, 1, Items.STONE_SWORD));
      add(20, new ItemStack(Items.IRON_SWORD), "Eisenschwert", Items.GOLD_INGOT, 7, (p, t) -> sword(p, 2, Items.IRON_SWORD));
      add(21, new ItemStack(Items.DIAMOND_SWORD), "Diamantschwert", Items.EMERALD, 4, (p, t) -> sword(p, 3, Items.DIAMOND_SWORD));
      add(22, new ItemStack(Items.FIRE_CHARGE), "Feuerball", Items.IRON_INGOT, 40, (p, t) -> {
         ItemStack s = new ItemStack(Items.FIRE_CHARGE);
         s.set(DataComponents.CUSTOM_NAME, Component.literal("Feuerball").withStyle(ChatFormatting.GOLD));
         p.getInventory().add(s);
         return true;
      });
      add(23, new ItemStack(Items.GOLDEN_APPLE), "Goldener Apfel", Items.GOLD_INGOT, 3, stack(Items.GOLDEN_APPLE, 1));
      add(24, new ItemStack(Items.ENDER_PEARL), "Enderperle", Items.EMERALD, 4, stack(Items.ENDER_PEARL, 1));
      // Reihe 3: Ruestung
      add(29, new ItemStack(Items.CHAINMAIL_BOOTS), "Kettenruestung", Items.IRON_INGOT, 40, (p, t) -> armor(p, t, 1));
      add(30, new ItemStack(Items.IRON_BOOTS), "Eisenruestung", Items.GOLD_INGOT, 12, (p, t) -> armor(p, t, 2));
      add(31, new ItemStack(Items.DIAMOND_BOOTS), "Diamantruestung", Items.EMERALD, 6, (p, t) -> armor(p, t, 3));
      // Reihe 4: Team-Upgrades fuer Diamanten
      add(37, new ItemStack(Items.BEACON), "Heilfeld (Team)", Items.DIAMOND, 3, upgrade("Heilfeld", t -> !t.healPool, t -> t.healPool = true, "Euer Heilfeld steht schon."));
      add(38, new ItemStack(Items.IRON_SWORD), "Geschaerfte Klingen (Team)", Items.DIAMOND, 8, upgrade("Geschaerfte Klingen", t -> !t.sharpness, t -> t.sharpness = true, "Habt ihr schon."));
      add(39, new ItemStack(Items.IRON_CHESTPLATE), "Schutz I (Team)", Items.DIAMOND, 5, upgrade("Schutz I", t -> t.protection == 0, t -> t.protection = 1, "Habt ihr schon."));
      add(40, new ItemStack(Items.IRON_CHESTPLATE, 2), "Schutz II (Team)", Items.DIAMOND, 10, upgrade("Schutz II", t -> t.protection == 1, t -> t.protection = 2, "Zuerst Schutz I (oder schon gekauft)."));
      add(41, new ItemStack(Items.IRON_CHESTPLATE, 3), "Schutz III (Team)", Items.DIAMOND, 20, upgrade("Schutz III", t -> t.protection == 2, t -> t.protection = 3, "Zuerst Schutz II (oder schon gekauft)."));
      add(42, new ItemStack(Items.FURNACE), "Eisenschmiede I (Team)", Items.DIAMOND, 4, upgrade("Eisenschmiede I", t -> t.forge == 0, t -> t.forge = 1, "Habt ihr schon."));
      add(43, new ItemStack(Items.BLAST_FURNACE), "Eisenschmiede II (Team)", Items.DIAMOND, 8, upgrade("Eisenschmiede II", t -> t.forge == 1, t -> t.forge = 2, "Zuerst Eisenschmiede I (oder schon gekauft)."));
   }

   private static Give upgrade(String name, java.util.function.Predicate<Game.TeamState> can, java.util.function.Consumer<Game.TeamState> apply, String why) {
      return (p, t) -> {
         if (!can.test(t)) {
            p.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
            return false;
         } else {
            apply.accept(t);
            Game.upgradeBought(t, name, null, p);
            return true;
         }
      };
   }

   private interface Give {
      boolean give(ServerPlayer p, Game.TeamState t);
   }

   private record Offer(int slot, ItemStack icon, String name, Item currency, int price, Give give) {
   }

   private static final class ShopMenu extends ChestMenu {
      ShopMenu(int id, Inventory inv, Container c) {
         super(MenuType.GENERIC_9x5, id, inv, c, 5);
      }

      @Override
      public void clicked(int slot, int button, ContainerInput input, Player pl) {
         if (slot >= 0 && slot < SIZE) {
            if (pl instanceof ServerPlayer sp && input == ContainerInput.PICKUP) {
               this.buy(sp, slot);
            }
         } else if (slot < SIZE || input == ContainerInput.PICKUP) {
            super.clicked(slot, button, input, pl);
         }
      }

      private void buy(ServerPlayer p, int slot) {
         Game.TeamState t = Game.playerTeam();
         if (t != null) {
            for (Offer o : OFFERS) {
               if (o.slot == slot) {
                  Inventory inv = p.getInventory();
                  if (count(inv, o.currency) < o.price) {
                     p.sendSystemMessage(Component.literal("Dir fehlen " + currencyName(o.currency) + ".").withStyle(ChatFormatting.RED));
                     return;
                  }

                  if (o.give.give(p, t)) {
                     take(inv, o.currency, o.price);
                     p.sendSystemMessage(Component.literal("Gekauft: " + o.name).withStyle(ChatFormatting.GREEN));
                  }

                  return;
               }
            }
         }
      }
   }
}
