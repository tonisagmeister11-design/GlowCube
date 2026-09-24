package org.bukkit;
import java.util.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
/** Eine kleine flache Welt mit Baeumen, Erzen, einer Kiste und Mobs. */
public final class FakeWorld implements World {
    public final Map<Long, BlockData> blocks = new HashMap<>();
    public final List<Entity> entities = new ArrayList<>();
    public final Map<Long, Inventory> chests = new HashMap<>();
    public long time = 1000;
    static long key(int x, int y, int z) { return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF); }

    public static BlockData data(Material m, String props) {
        String n = m.name();
        if (Set.of("WHEAT","CARROTS","POTATOES","BEETROOTS").contains(n)) return new Fakes.Crop(m, props);
        if (n.endsWith("_DOOR") || n.endsWith("_FENCE_GATE") || n.endsWith("_TRAPDOOR")) return new Fakes.Door(m, props);
        return new Fakes.Data(m, props);
    }

    public BlockData natural(int x, int y, int z) {
        String n;
        if (y < -63) n = "BEDROCK";
        else if (y < 61) n = "STONE";
        else if (y < 64) n = "DIRT";
        else if (y == 64) n = "GRASS_BLOCK";
        else n = "AIR";
        return data(Material.getMaterial(n), "");
    }
    public BlockData get(int x, int y, int z) { BlockData d = blocks.get(key(x, y, z)); return d != null ? d : natural(x, y, z); }
    public void put(int x, int y, int z, String name) { blocks.put(key(x, y, z), data(Material.getMaterial(name), "")); }
    public void put(int x, int y, int z, BlockData d) { blocks.put(key(x, y, z), d); }

    public void tree(int x, int z) {
        for (int y = 65; y <= 69; y++) put(x, y, z, "OAK_LOG");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = 67; y <= 70; y++)
            if ((dx != 0 || dz != 0 || y == 70) && get(x+dx, y, z+dz).getMaterial().isAir()) put(x+dx, y, z+dz, "OAK_LEAVES");
    }

    public Block getBlockAt(int x, int y, int z) { return new Fakes.FBlock(this, x, y, z); }
    public Block getBlockAt(Location l) { return getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    public Collection<Entity> getNearbyEntities(Location l, double x, double y, double z) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : new ArrayList<>(entities)) {
            if (!e.isValid()) continue;
            Location o = e.getLocation();
            if (Math.abs(o.getX()-l.getX()) <= x && Math.abs(o.getY()-l.getY()) <= y && Math.abs(o.getZ()-l.getZ()) <= z) out.add(e);
        }
        return out;
    }
    public Entity spawnEntity(Location l, EntityType t) {
        Fakes.FLiving e = t.name().equals("ZOMBIE") || t.name().equals("SKELETON") ? new Fakes.FMonster(this, l, t) : new Fakes.FLiving(this, l, t);
        entities.add(e);
        return e;
    }
    public Item dropItemNaturally(Location l, ItemStack i) { Fakes.FItem it = new Fakes.FItem(this, l, i); entities.add(it); return it; }
    public void playSound(Location l, Sound s, float v, float p) {}
    public void spawnParticle(Particle p, Location l, int c, double a, double b, double d, double e) {}
    public <T> void playEffect(Location l, Effect e, T data) {}
    public long getTime() { return time; }
    public String getName() { return "world"; }
    public UUID getUID() { return new UUID(1, 1); }
    public boolean isChunkLoaded(int x, int z) { return Math.abs(x) < 20 && Math.abs(z) < 20; }
    public int getMinHeight() { return -64; }
    public int getMaxHeight() { return 320; }
    public List<Player> getPlayers() { List<Player> out = new ArrayList<>(); for (Entity e : entities) if (e instanceof Player p) out.add(p); return out; }
    public boolean hasStorm() { return false; }
    public Location getSpawnLocation() { return new Location(this, 0, 65, 0); }
}
