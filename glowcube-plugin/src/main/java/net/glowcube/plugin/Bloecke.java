package net.glowcube.plugin;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;

/**
 * Was der Agent ueber Bloecke wissen muss - dieselben Regeln wie im Client
 * (AgentBloecke), hier ueber die Paper-API.
 */
final class Bloecke {
    static final double NIE = Double.POSITIVE_INFINITY;

    static final ItemStack SPITZHACKE = new ItemStack(Material.DIAMOND_PICKAXE);
    static final ItemStack AXT = new ItemStack(Material.DIAMOND_AXE);
    static final ItemStack SCHAUFEL = new ItemStack(Material.DIAMOND_SHOVEL);

    private static final BlockFace[] SEITEN = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private Bloecke() {
    }

    // ------------------------------------------------------------- Ziele

    static boolean istZiel(Material m, Auftrag auftrag, String art) {
        return switch (auftrag) {
            case HOLZ -> Tag.LOGS.isTagged(m);
            case STEIN -> Tag.BASE_STONE_OVERWORLD.isTagged(m) && m != Material.GRAVEL
                    || m == Material.COBBLESTONE || m == Material.COBBLED_DEEPSLATE;
            case ERZ -> istErz(m, art);
            // Guardian und Builder suchen keine Bloecke.
            case WAECHTER, BAUMEISTER -> false;
        };
    }

    private static boolean istErz(Material m, String art) {
        return switch (art) {
            case "Diamant" -> Tag.DIAMOND_ORES.isTagged(m);
            case "Eisen" -> Tag.IRON_ORES.isTagged(m);
            case "Gold" -> Tag.GOLD_ORES.isTagged(m);
            case "Redstone" -> Tag.REDSTONE_ORES.isTagged(m);
            case "Lapis" -> Tag.LAPIS_ORES.isTagged(m);
            case "Kohle" -> Tag.COAL_ORES.isTagged(m);
            case "Kupfer" -> Tag.COPPER_ORES.isTagged(m);
            case "Smaragd" -> Tag.EMERALD_ORES.isTagged(m);
            case "Antiker Schrott" -> m == Material.ANCIENT_DEBRIS;
            case "Quarz" -> m == Material.NETHER_QUARTZ_ORE;
            default -> Tag.DIAMOND_ORES.isTagged(m) || Tag.IRON_ORES.isTagged(m) || Tag.GOLD_ORES.isTagged(m)
                    || Tag.REDSTONE_ORES.isTagged(m) || Tag.LAPIS_ORES.isTagged(m) || Tag.COAL_ORES.isTagged(m)
                    || Tag.COPPER_ORES.isTagged(m) || Tag.EMERALD_ORES.isTagged(m)
                    || m == Material.ANCIENT_DEBRIS || m == Material.NETHER_QUARTZ_ORE;
        };
    }

    static int besteHoehe(Auftrag auftrag, String art, World welt) {
        boolean nether = welt.getEnvironment() == World.Environment.NETHER;
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

    static boolean geladen(World w, Pos p) {
        return p.y() >= w.getMinHeight() && p.y() < w.getMaxHeight() && w.isChunkLoaded(p.x() >> 4, p.z() >> 4);
    }

    static boolean frei(Block b) {
        Material m = b.getType();
        if (m == Material.LAVA || Tag.FIRE.isTagged(m) || m == Material.POWDER_SNOW
                || m == Material.SWEET_BERRY_BUSH || m == Material.COBWEB) {
            return false;
        }
        return b.isPassable();
    }

    static boolean traegt(Block b) {
        Material m = b.getType();
        return !b.isPassable() && m != Material.LAVA && m != Material.MAGMA_BLOCK
                && m != Material.CAMPFIRE && m != Material.SOUL_CAMPFIRE;
    }

    static boolean abbaubar(Block b) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid() || m == Material.WATER || m == Material.LAVA) {
            return false;
        }
        float haerte = m.getHardness();
        if (haerte < 0 || haerte > 30) {
            return false;
        }
        if (b.getState(false) instanceof TileState) {
            return false;
        }
        for (BlockFace f : SEITEN) {
            Block n = b.getRelative(f);
            if (n.getType() == Material.LAVA) {
                return false;
            }
        }
        return true;
    }

    static ItemStack werkzeug(Block b) {
        ItemStack bestes = SPITZHACKE;
        float tempo = b.getBlockData().getDestroySpeed(SPITZHACKE, false);
        for (ItemStack w : new ItemStack[]{AXT, SCHAUFEL}) {
            float t = b.getBlockData().getDestroySpeed(w, false);
            if (t > tempo) {
                tempo = t;
                bestes = w;
            }
        }
        return bestes;
    }

    /** Ticks zum Abbauen, wie beim Spieler mit Diamantwerkzeug. */
    static int abbauTicks(Block b) {
        float haerte = b.getType().getHardness();
        if (haerte <= 0) {
            return 1;
        }
        ItemStack w = werkzeug(b);
        float tempo = b.getBlockData().getDestroySpeed(w, false);
        boolean passend = !b.getBlockData().requiresCorrectToolForDrops() || b.isPreferredTool(w);
        return Math.max(1, (int) Math.ceil(haerte * (passend ? 30f : 100f) / Math.max(1f, tempo)));
    }

    static boolean istBaustein(ItemStack s) {
        if (s == null) {
            return false;
        }
        return switch (s.getType()) {
            case COBBLESTONE, COBBLED_DEEPSLATE, DIRT, NETHERRACK, STONE, DEEPSLATE, ANDESITE, DIORITE,
                 GRANITE, TUFF, BLACKSTONE, BASALT -> true;
            default -> false;
        };
    }
}
