package de.glowcube.bedwars;

import java.util.Random;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Alles, was einen Bot ueber seinen Tod hinaus ausmacht: Name, Aussehen, Charakter,
 * Inventar und sein aktueller Auftrag vom Team.
 */
public final class BotProfile {
   private static final Identifier VIRTUAL_LEATHER = Identifier.fromNamespaceAndPath("glowcube_bedwars", "virtual_leather");

   public enum Role {
      /** Stuermt frueh los und zerstoert Betten. */
      RUSHER,
      /** Baut das Bett zu und bleibt eher zu Hause. */
      BUILDER,
      /** Sammelt, holt Diamanten und Smaragde. */
      COLLECTOR,
      /** Sucht den Kampf und jagt Gegner. */
      FIGHTER
   }

   public enum Task {
      IDLE,
      GATHER,
      SHOP,
      FORTIFY,
      DEFEND,
      RAID,
      MID,
      HUNT,
      ESCORT,
      FOLLOW_PLAYER,
      RETREAT,
      REGROUP,
      CUT_BRIDGE
   }

   public final String name;
   public final Game.TeamState team;
   public final boolean alex;
   public final Role role;
   /** 0..1: wie gerne er kaempft. */
   public final double aggression;
   /** 0..1: grobes Koennen (aus dem Spielertyp). */
   public final double skill;
   /** Wie er an Maus und Tastatur ist. */
   public final Hands hands;
   /** Wie genervt er gerade ist (steigt bei Toden kurz hintereinander). */
   public double tilt;
   /** Wie er im Chat schreibt. */
   public final ChatBrain.Voice voice;
   public int lastDeathTick = -100000;
   /** 0..1: wie oft er im Chat schreibt. */
   public final double chattiness;
   /** 0..1: wie frech er im Chat ist. */
   public final double cheek;
   /** 0..1: wie vorsichtig er spielt. */
   public final double caution;

   public boolean eliminated;
   public int respawnAt = -1;
   public BedwarsBot body;

   // Inventar
   public int wool;
   public int endStone;
   public int planks;
   public int gapples;
   public int fireballs;
   public int swordTier;
   public int armorTier;
   public int pickaxe;
   public boolean shears;

   // Auftrag vom Team
   public Task task = Task.IDLE;
   public Game.TeamState taskTeam;
   public net.minecraft.world.entity.LivingEntity taskEntity;
   public net.minecraft.core.BlockPos taskPos;
   public int taskSince;
   public int taskLockUntil;
   /** Was er nach dem Einkaufen weitermacht. */
   public Task afterShop = Task.IDLE;

   // Statistik und Gedaechtnis
   public int kills;
   public int finals;
   public int deaths;
   public int beds;
   public int lastSpoke = -10000;

   public BotProfile(String name, Game.TeamState team, Random rnd, boolean alex, int difficulty) {
      this.name = name;
      this.team = team;
      this.alex = alex;
      double r = rnd.nextDouble();
      this.role = r < 0.28 ? Role.RUSHER : (r < 0.52 ? Role.BUILDER : (r < 0.74 ? Role.COLLECTOR : Role.FIGHTER));
      this.hands = Hands.roll(rnd, difficulty);
      this.skill = this.hands.skill();
      this.aggression = clamp(
         (this.role == Role.RUSHER || this.role == Role.FIGHTER ? 0.55 : 0.25) + rnd.nextDouble() * 0.45 - (this.role == Role.BUILDER ? 0.1 : 0.0)
      );
      this.caution = clamp(1.0 - this.aggression * 0.7 + (rnd.nextDouble() - 0.5) * 0.4);
      this.chattiness = 0.25 + rnd.nextDouble() * 0.75;
      this.cheek = rnd.nextDouble();
      this.voice = new ChatBrain.Voice(rnd, this.cheek, this.hands.style);
   }

   private static double clamp(double v) {
      return Math.max(0.0, Math.min(1.0, v));
   }

   public int blocks() {
      return this.wool + this.endStone + this.planks;
   }

   public int gearScore() {
      return this.swordTier * 2 + this.armorTier * 2 + (this.gapples > 0 ? 1 : 0);
   }

   /** Verbraucht einen Baublock; liefert den Block oder null. Wolle zum Bruecken, Endstein zum Schuetzen. */
   public net.minecraft.world.level.block.Block takeBlock(boolean defensive, int layer) {
      if (defensive && layer >= 3 && this.planks > 0) {
         this.planks--;
         return net.minecraft.world.level.block.Blocks.OAK_PLANKS;
      } else if (defensive && layer >= 2 && this.endStone > 0) {
         this.endStone--;
         return net.minecraft.world.level.block.Blocks.END_STONE;
      } else if (this.wool > 0) {
         this.wool--;
         return this.team.team.wool;
      } else if (this.endStone > 0) {
         this.endStone--;
         return net.minecraft.world.level.block.Blocks.END_STONE;
      } else if (this.planks > 0) {
         this.planks--;
         return net.minecraft.world.level.block.Blocks.OAK_PLANKS;
      } else {
         return null;
      }
   }

   // ------------------------------------------------------------------ Einkaufen

   private boolean pay(Item currency, int price) {
      Game.TeamState t = this.team;
      if (currency == Items.IRON_INGOT && t.iron >= price) {
         t.iron -= price;
         return true;
      } else if (currency == Items.GOLD_INGOT && t.gold >= price) {
         t.gold -= price;
         return true;
      } else if (currency == Items.EMERALD && t.emerald >= price) {
         t.emerald -= price;
         return true;
      } else if (currency == Items.DIAMOND && t.diamond >= price) {
         t.diamond -= price;
         return true;
      } else {
         return false;
      }
   }

   /**
    * Kauft ein, was fuer seine Rolle und die Lage am meisten bringt.
    * Liefert eine kurze Beschreibung des wichtigsten Kaufs (fuer den Chat) oder null.
    */
   public String shop() {
      String best = null;
      boolean bedAlive = this.team.bedAlive;
      boolean raider = this.task == Task.RAID || this.afterShop == Task.RAID || this.role == Role.RUSHER;
      boolean defender = this.task == Task.FORTIFY || this.afterShop == Task.FORTIFY || this.task == Task.DEFEND || this.afterShop == Task.DEFEND;

      for (int round = 0; round < 6; round++) {
         String bought = null;
         if (this.swordTier < 3 && this.pay(Items.EMERALD, 4) && this.swordTierUp(3)) {
            bought = "Diamantschwert";
         } else if (this.armorTier < 3 && this.team.emerald >= 6 && this.pay(Items.EMERALD, 6)) {
            this.armorTier = 3;
            bought = "Diamantruestung";
         } else if (this.armorTier < 2 && this.team.gold >= 12 && (this.swordTier >= 1 || !bedAlive) && this.pay(Items.GOLD_INGOT, 12)) {
            this.armorTier = 2;
            bought = "Eisenruestung";
         } else if (this.swordTier < 2 && this.team.gold >= 7 && this.pay(Items.GOLD_INGOT, 7)) {
            this.swordTier = 2;
            bought = "Eisenschwert";
         } else if (this.swordTier < 1 && this.pay(Items.IRON_INGOT, 10)) {
            this.swordTier = 1;
            bought = "Steinschwert";
         } else if (this.wool < (raider ? 32 : 16) && this.pay(Items.IRON_INGOT, 4)) {
            this.wool += 16;
            bought = "Wolle";
         } else if (this.armorTier < 1 && this.team.iron >= 40 && (this.swordTier >= 1 || !bedAlive) && this.pay(Items.IRON_INGOT, 40)) {
            this.armorTier = 1;
            bought = "Kettenruestung";
         } else if (defender && bedAlive && this.endStone < 12 && this.team.iron >= 24 && this.pay(Items.IRON_INGOT, 24)) {
            this.endStone += 12;
            bought = "Endstein";
         } else if (raider && !this.shears && this.team.iron >= 20 && this.pay(Items.IRON_INGOT, 20)) {
            this.shears = true;
            bought = "Schere";
         } else if (raider && this.pickaxe < 1 && this.team.iron >= 10 && this.pay(Items.IRON_INGOT, 10)) {
            this.pickaxe = 1;
            bought = "Holzspitzhacke";
         } else if (raider && this.pickaxe < 2 && this.pickaxe >= 1 && this.team.gold >= 3 && this.pay(Items.GOLD_INGOT, 3)) {
            this.pickaxe = 2;
            bought = "Eisenspitzhacke";
         } else if (this.gapples < 2 && this.team.gold >= 3 && (this.armorTier >= 1 || this.team.gold >= 8) && this.pay(Items.GOLD_INGOT, 3)) {
            this.gapples++;
            bought = "Goldapfel";
         } else if (defender && bedAlive && this.planks < 16 && this.team.gold >= 6 && this.endStone >= 12 && this.pay(Items.GOLD_INGOT, 4)) {
            this.planks += 16;
            bought = "Holz";
         } else if (this.fireballs < (bedAlive ? 2 : 4) && this.team.iron >= (bedAlive ? 60 : 45) && this.armorTier >= 1 && this.pay(Items.IRON_INGOT, 40)) {
            this.fireballs++;
            bought = "Feuerball";
         } else if (raider && this.pickaxe < 3 && this.pickaxe >= 2 && this.team.gold >= 9 && this.pay(Items.GOLD_INGOT, 6)) {
            this.pickaxe = 3;
            bought = "Diamantspitzhacke";
         }

         if (bought == null) {
            break;
         }

         if (best == null || bought.startsWith("Diamant") || bought.startsWith("Eisen")) {
            best = bought;
         }
      }

      return best;
   }

   private boolean swordTierUp(int tier) {
      this.swordTier = tier;
      return true;
   }

   /** Was er beim Tod verliert - wie im echten Bedwars: Schwert und Bloecke weg, Ruestung bleibt. */
   public void onDeath() {
      this.deaths++;
      int now = Game.tick();
      this.tilt = Math.min(1.0, this.tilt * Math.exp(-(now - this.lastDeathTick) / 1200.0) + 0.35);
      this.lastDeathTick = now;
      this.swordTier = 0;
      this.wool = 0;
      this.endStone = 0;
      this.planks = 0;
      this.fireballs = 0;
      this.gapples = 0;
      this.pickaxe = Math.max(0, this.pickaxe - 1);
   }

   // ------------------------------------------------------------------ Ausruestung

   public ItemStack swordStack() {
      Item item = switch (this.swordTier) {
         case 0 -> Items.WOODEN_SWORD;
         case 1 -> Items.STONE_SWORD;
         case 2 -> Items.IRON_SWORD;
         default -> Items.DIAMOND_SWORD;
      };
      ItemStack stack = new ItemStack(item);
      if (this.team.sharpness) {
         Game.enchant(stack, Enchantments.SHARPNESS, 1);
      }

      return stack;
   }

   public float swordDamage() {
      float base = switch (this.swordTier) {
         case 0 -> 4.0F;
         case 1 -> 5.0F;
         case 2 -> 6.0F;
         default -> 7.0F;
      };
      return this.team.sharpness ? base + 1.0F : base;
   }

   /**
    * Helm und Brustpanzer bleiben weg, damit man Steve und Alex sieht. Deren Ruestungswert
    * (Lederhelm 1 + Lederbrust 3) bekommt der Bot unsichtbar dazu.
    */
   public void equipArmor(BedwarsBot bot) {
      bot.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
      bot.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
      ItemStack legs = switch (this.armorTier) {
         case 0 -> this.dyed(Items.LEATHER_LEGGINGS);
         case 1 -> new ItemStack(Items.CHAINMAIL_LEGGINGS);
         case 2 -> new ItemStack(Items.IRON_LEGGINGS);
         default -> new ItemStack(Items.DIAMOND_LEGGINGS);
      };
      ItemStack boots = switch (this.armorTier) {
         case 0 -> this.dyed(Items.LEATHER_BOOTS);
         case 1 -> new ItemStack(Items.CHAINMAIL_BOOTS);
         case 2 -> new ItemStack(Items.IRON_BOOTS);
         default -> new ItemStack(Items.DIAMOND_BOOTS);
      };
      Game.enchant(legs, Enchantments.PROTECTION, this.team.protection);
      Game.enchant(boots, Enchantments.PROTECTION, this.team.protection);
      bot.setItemSlot(EquipmentSlot.LEGS, legs);
      bot.setItemSlot(EquipmentSlot.FEET, boots);
      AttributeInstance armor = bot.getAttribute(Attributes.ARMOR);
      if (armor != null) {
         armor.addOrUpdateTransientModifier(new AttributeModifier(VIRTUAL_LEATHER, 4.0 + this.team.protection, AttributeModifier.Operation.ADD_VALUE));
      }
   }

   private ItemStack dyed(Item item) {
      ItemStack stack = new ItemStack(item);
      stack.set(DataComponents.DYED_COLOR, new DyedItemColor(this.team.team.rgb));
      return stack;
   }

   public String taskLabel() {
      return switch (this.task) {
         case IDLE -> "nichts";
         case GATHER -> "Eisen sammeln";
         case SHOP -> "einkaufen";
         case FORTIFY -> "Bett zubauen";
         case DEFEND -> "Bett verteidigen";
         case RAID -> this.taskTeam == null ? "angreifen" : "Bett von " + this.taskTeam.team.label + " holen";
         case MID -> "Diamanten/Smaragde holen";
         case HUNT -> "jagen";
         case ESCORT -> "Teamkollegen begleiten";
         case FOLLOW_PLAYER -> "dir folgen";
         case RETREAT -> "zurueckziehen";
         case REGROUP -> "sammeln";
         case CUT_BRIDGE -> "Bruecke abreissen";
      };
   }
}
