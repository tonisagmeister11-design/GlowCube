package net.glowcube.client.agent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Was der Agent ueber Bloecke wissen muss: wo er stehen und gehen kann, was
 * er abbauen darf, wie lange das dauert und was er gerade sucht.
 */
final class AgentBloecke {
    /** Kosten, die "geht nicht" bedeuten. */
    static final double NIE = Double.POSITIVE_INFINITY;

    static final ItemStack SPITZHACKE = new ItemStack(Items.DIAMOND_PICKAXE);
    static final ItemStack AXT = new ItemStack(Items.DIAMOND_AXE);
    static final ItemStack SCHAUFEL = new ItemStack(Items.DIAMOND_SHOVEL);

    private AgentBloecke() {
    }

    // ------------------------------------------------------------- Ziele

    /** Ist das ein Block, den dieser Auftrag sucht? */
    static boolean istZiel(BlockState s, Auftrag auftrag, String art) {
        return switch (auftrag) {
            case HOLZ -> s.is(BlockTags.LOGS);
            case STEIN -> s.is(BlockTags.BASE_STONE_OVERWORLD) && !s.is(Blocks.GRAVEL)
                    || s.is(Blocks.COBBLESTONE) || s.is(Blocks.COBBLED_DEEPSLATE);
            case ERZ -> istErz(s, art);
        };
    }

    private static boolean istErz(BlockState s, String art) {
        return switch (art) {
            case "Diamant" -> s.is(BlockTags.DIAMOND_ORES);
            case "Eisen" -> s.is(BlockTags.IRON_ORES);
            case "Gold" -> s.is(BlockTags.GOLD_ORES);
            case "Redstone" -> s.is(BlockTags.REDSTONE_ORES);
            case "Lapis" -> s.is(BlockTags.LAPIS_ORES);
            case "Kohle" -> s.is(BlockTags.COAL_ORES);
            case "Kupfer" -> s.is(BlockTags.COPPER_ORES);
            case "Smaragd" -> s.is(BlockTags.EMERALD_ORES);
            case "Antiker Schrott" -> s.is(Blocks.ANCIENT_DEBRIS);
            case "Quarz" -> s.is(Blocks.NETHER_QUARTZ_ORE);
            default -> s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.GOLD_ORES)
                    || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.COAL_ORES)
                    || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.EMERALD_ORES)
                    || s.is(Blocks.ANCIENT_DEBRIS) || s.is(Blocks.NETHER_QUARTZ_ORE);
        };
    }

    /**
     * Die Hoehe, in der sich die Erzart am meisten lohnt (Oberwelt nach den
     * Verteilungskurven seit 1.18, Nether fuer Schrott und Quarz).
     */
    static int besteHoehe(Auftrag auftrag, String art, Level welt) {
        if (auftrag != Auftrag.ERZ) {
            return Integer.MIN_VALUE;
        }
        boolean nether = welt.dimension() == Level.NETHER;
        return switch (art) {
            case "Diamant", "Redstone" -> -58;
            case "Eisen" -> 16;
            case "Gold" -> nether ? 30 : -16;
            case "Lapis" -> 0;
            case "Kohle" -> 96;
            case "Kupfer" -> 48;
            case "Smaragd" -> 120;
            case "Antiker Schrott" -> 15;
            case "Quarz" -> 60;
            default -> nether ? 15 : -54;
        };
    }

    // ------------------------------------------------------- Beschaffenheit

    static boolean geladen(ServerLevel welt, BlockPos p) {
        return welt.isLoaded(p);
    }

    /** Frei: man kann darin stehen (Luft, Gras, Blumen, Wasser). Lava und Feuer nie. */
    static boolean frei(ServerLevel welt, BlockPos p, BlockState s) {
        if (s.getFluidState().is(FluidTags.LAVA) || s.is(BlockTags.FIRE) || s.is(Blocks.POWDER_SNOW)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.COBWEB)) {
            return false;
        }
        return s.getCollisionShape(welt, p).isEmpty();
    }

    /** Traegt: darauf kann man stehen. */
    static boolean traegt(ServerLevel welt, BlockPos p, BlockState s) {
        return !s.getCollisionShape(welt, p).isEmpty() && !s.getFluidState().is(FluidTags.LAVA)
                && !s.is(Blocks.MAGMA_BLOCK) && !s.is(Blocks.CAMPFIRE) && !s.is(Blocks.SOUL_CAMPFIRE);
    }

    /**
     * Darf abgebaut werden? Nicht: Unzerstoerbares, sehr Hartes (Obsidian),
     * Bloecke mit Inhalt (Truhen, Spawner), Fluessigkeiten - und nichts, was
     * an Lava grenzt, sonst laeuft sie in den Gang.
     */
    static boolean abbaubar(ServerLevel welt, BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity()) {
            return false;
        }
        float haerte = s.getDestroySpeed(welt, p);
        if (haerte < 0 || haerte > 30) {
            return false;
        }
        for (Direction d : Direction.values()) {
            BlockPos n = p.relative(d);
            if (geladen(welt, n) && welt.getBlockState(n).getFluidState().is(FluidTags.LAVA)) {
                return false;
            }
        }
        return true;
    }

    /** Das schnellste der drei Werkzeuge fuer diesen Block. */
    static ItemStack werkzeug(BlockState s) {
        ItemStack bestes = SPITZHACKE;
        float tempo = SPITZHACKE.getDestroySpeed(s);
        for (ItemStack w : new ItemStack[]{AXT, SCHAUFEL}) {
            float t = w.getDestroySpeed(s);
            if (t > tempo) {
                tempo = t;
                bestes = w;
            }
        }
        return bestes;
    }

    /** Wie viele Ticks das Abbauen dauert - wie beim Spieler mit Diamantwerkzeug. */
    static int abbauTicks(ServerLevel welt, BlockPos p, BlockState s) {
        float haerte = s.getDestroySpeed(welt, p);
        if (haerte <= 0) {
            return 1;
        }
        ItemStack w = werkzeug(s);
        float tempo = w.getDestroySpeed(s);
        boolean passend = !s.requiresCorrectToolForDrops() || w.isCorrectToolForDrops(s);
        return Math.max(1, (int) Math.ceil(haerte * (passend ? 30f : 100f) / Math.max(1f, tempo)));
    }

    /** Bausteine: was der Agent zum Bruecken- und Turmbauen behaelt. */
    static boolean istBaustein(ItemStack s) {
        return s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.DIRT)
                || s.is(Items.NETHERRACK) || s.is(Items.STONE) || s.is(Items.DEEPSLATE)
                || s.is(Items.ANDESITE) || s.is(Items.DIORITE) || s.is(Items.GRANITE) || s.is(Items.TUFF)
                || s.is(Items.BLACKSTONE) || s.is(Items.BASALT);
    }
}
