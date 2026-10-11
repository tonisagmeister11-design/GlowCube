package de.glowcube.bedwars;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum BwTeam {
   RED("Rot", ChatFormatting.RED, DyeColor.RED, 11546150, "rot", "rote", "roten", "red"),
   BLUE("Blau", ChatFormatting.BLUE, DyeColor.BLUE, 3949738, "blau", "blaue", "blauen", "blue"),
   GREEN("Gruen", ChatFormatting.GREEN, DyeColor.LIME, 8439583, "gruen", "grün", "gruene", "grüne", "grünen", "gruenen", "green"),
   YELLOW("Gelb", ChatFormatting.YELLOW, DyeColor.YELLOW, 16701501, "gelb", "gelbe", "gelben", "yellow"),
   PINK("Pink", ChatFormatting.LIGHT_PURPLE, DyeColor.PINK, 15961002, "pink", "pinke", "pinken", "rosa");

   public final String label;
   public final ChatFormatting format;
   public final DyeColor dye;
   public final Block wool;
   public final Block bed;
   public final Block concrete;
   public final Block terracotta;
   public final Block glass;
   public final Block carpet;
   public final int rgb;
   /** Woerter, an denen die Chat-KI das Team erkennt. */
   public final String[] words;

   BwTeam(String label, ChatFormatting format, DyeColor dye, int rgb, String... words) {
      this.label = label;
      this.format = format;
      this.dye = dye;
      this.wool = Blocks.WOOL.pick(dye);
      this.bed = Blocks.BED.pick(dye);
      this.concrete = Blocks.CONCRETE.pick(dye);
      this.terracotta = Blocks.DYED_TERRACOTTA.pick(dye);
      this.glass = Blocks.STAINED_GLASS.pick(dye);
      this.carpet = Blocks.CARPET.pick(dye);
      this.rgb = rgb;
      this.words = words;
   }
}
