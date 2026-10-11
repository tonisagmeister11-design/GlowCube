package de.glowcube.bedwars;

import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Die fuenf Teams. */
public enum BwTeam {
    RED("Rot", ChatFormatting.RED, Blocks.WOOL.red(), Blocks.BED.red(), 0xB02E26),
    BLUE("Blau", ChatFormatting.BLUE, Blocks.WOOL.blue(), Blocks.BED.blue(), 0x3C44AA),
    GREEN("Gruen", ChatFormatting.GREEN, Blocks.WOOL.lime(), Blocks.BED.lime(), 0x80C71F),
    YELLOW("Gelb", ChatFormatting.YELLOW, Blocks.WOOL.yellow(), Blocks.BED.yellow(), 0xFED83D),
    PINK("Pink", ChatFormatting.LIGHT_PURPLE, Blocks.WOOL.pink(), Blocks.BED.pink(), 0xF38BAA);

    public final String label;
    public final ChatFormatting format;
    public final Block wool;
    public final Block bed;
    public final int rgb;

    BwTeam(String label, ChatFormatting format, Block wool, Block bed, int rgb) {
        this.label = label;
        this.format = format;
        this.wool = wool;
        this.bed = bed;
        this.rgb = rgb;
    }
}
