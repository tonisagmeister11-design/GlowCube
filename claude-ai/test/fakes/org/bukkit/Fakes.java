package org.bukkit;
import java.util.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;
/** Die Bausteine der Testwelt. */
public final class Fakes {
    public static class Data implements BlockData {
        final Material m; String props;
        Data(Material m, String props) { this.m = m; this.props = props == null ? "" : props; }
        public Material getMaterial() { return m; }
        public String getAsString() { return "minecraft:" + m.name().toLowerCase(Locale.ROOT) + (props.isEmpty() ? "" : "[" + props + "]"); }
        public SoundGroup getSoundGroup() { return null; }
        public BlockData clone() { return FakeWorld.data(m, props); }
        String prop(String k) { for (String p : props.split(",")) { String[] kv = p.split("="); if (kv.length == 2 && kv[0].equals(k)) return kv[1]; } return null; }
        void setProp(String k, String v) {
            List<String> out = new ArrayList<>(); boolean found = false;
            for (String p : props.split(",")) { if (p.isEmpty()) continue; if (p.startsWith(k + "=")) { out.add(k + "=" + v); found = true; } else out.add(p); }
            if (!found) out.add(k + "=" + v);
            props = String.join(",", out);
        }
    }
    public static final class Crop extends Data implements Ageable {
        Crop(Material m, String p) { super(m, p); }
        public int getAge() { String a = prop("age"); return a == null ? 0 : Integer.parseInt(a); }
        public void setAge(int a) { setProp("age", String.valueOf(a)); }
        public int getMaximumAge() { return 7; }
    }
    public static final class Door extends Data implements Openable {
        Door(Material m, String p) { super(m, p); }
        public boolean isOpen() { return "true".equals(prop("open")); }
        public void setOpen(boolean o) { setProp("open", String.valueOf(o)); }
    }

    public static final class FBlock implements Block, Container {
        final FakeWorld w; final int x, y, z;
        FBlock(FakeWorld w, int x, int y, int z) { this.w = w; this.x = x; this.y = y; this.z = z; }
        public Material getType() { return w.get(x, y, z).getMaterial(); }
        public int getX() { return x; } public int getY() { return y; } public int getZ() { return z; }
        public World getWorld() { return w; }
        public Location getLocation() { return new Location(w, x, y, z); }
        public Block getRelative(int dx, int dy, int dz) { return new FBlock(w, x+dx, y+dy, z+dz); }
        public boolean isPassable() {
            Material m = getType();
            BlockData d = w.get(x, y, z);
            if (d instanceof Openable o) return o.isOpen();
            return !m.isSolid();
        }
        public boolean isLiquid() { String n = getType().name(); return n.equals("WATER") || n.equals("LAVA"); }
        public boolean isEmpty() { return getType().isAir(); }
        public byte getLightLevel() { return (byte) (y >= 65 ? 15 : 0); }
        public void setType(Material m) { setType(m, true); }
        public void setType(Material m, boolean physics) { if (m.isAir()) w.blocks.put(FakeWorld.key(x,y,z), FakeWorld.data(m, "")); else w.put(x, y, z, FakeWorld.data(m, "")); }
        public BlockData getBlockData() { return w.get(x, y, z).clone(); }
        public void setBlockData(BlockData d) { setBlockData(d, true); }
        public void setBlockData(BlockData d, boolean physics) { w.put(x, y, z, d.clone()); }
        public Collection<ItemStack> getDrops(ItemStack tool) {
            String n = getType().name();
            String t = tool == null ? "" : tool.getType().name();
            List<ItemStack> out = new ArrayList<>();
            if (getType().isAir() || n.endsWith("_LEAVES") || n.equals("SHORT_GRASS")) return out;
            if (n.equals("STONE")) { if (t.endsWith("_PICKAXE")) out.add(new ItemStack(Material.getMaterial("COBBLESTONE"), 1)); return out; }
            if (n.equals("GRASS_BLOCK")) { out.add(new ItemStack(Material.getMaterial("DIRT"), 1)); return out; }
            if (n.endsWith("IRON_ORE")) { if (t.startsWith("STONE_")||t.startsWith("IRON_")||t.startsWith("DIAMOND_")) out.add(new ItemStack(Material.getMaterial("RAW_IRON"), 1)); return out; }
            if (n.endsWith("COAL_ORE")) { if (t.endsWith("_PICKAXE")) out.add(new ItemStack(Material.getMaterial("COAL"), 1)); return out; }
            if (n.equals("WHEAT")) { out.add(new ItemStack(Material.getMaterial("WHEAT"), 1)); out.add(new ItemStack(Material.getMaterial("WHEAT_SEEDS"), 2)); return out; }
            if (n.endsWith("_BED") || n.endsWith("_DOOR")) { out.add(new ItemStack(getType(), 1)); return out; }
            out.add(new ItemStack(getType(), 1));
            return out;
        }
        public BlockState getState() { return this; }
        public Inventory getInventory() { return w.chests.computeIfAbsent(FakeWorld.key(x,y,z), k -> new FInventory(27, null)); }
        public String toString() { return getType() + "@" + x + "," + y + "," + z; }
    }

    public static class FInventory implements PlayerInventory {
        final ItemStack[] slots; final InventoryHolder holder;
        public FInventory(int size, InventoryHolder h) { slots = new ItemStack[size]; holder = h; }
        public HashMap<Integer, ItemStack> addItem(ItemStack... items) {
            HashMap<Integer, ItemStack> rest = new HashMap<>();
            for (int i = 0; i < items.length; i++) {
                ItemStack it = items[i]; int left = it.getAmount(); int max = it.getMaxStackSize();
                for (int s = 0; s < slots.length && left > 0; s++) {
                    if (slots[s] != null && slots[s].getType() == it.getType() && slots[s].getAmount() < max) {
                        int add = Math.min(left, max - slots[s].getAmount()); slots[s].setAmount(slots[s].getAmount() + add); left -= add; }
                }
                for (int s = 0; s < slots.length && left > 0; s++) {
                    if (slots[s] == null) { int add = Math.min(left, max); slots[s] = new ItemStack(it.getType(), add); left -= add; }
                }
                if (left > 0) rest.put(i, new ItemStack(it.getType(), left));
            }
            return rest;
        }
        public HashMap<Integer, ItemStack> removeItem(ItemStack... items) { return new HashMap<>(); }
        public ItemStack[] getContents() { ItemStack[] c = new ItemStack[slots.length]; for (int i = 0; i < slots.length; i++) c[i] = slots[i] == null ? null : slots[i].clone(); return c; }
        public void setContents(ItemStack[] items) { for (int i = 0; i < slots.length; i++) slots[i] = i < items.length ? items[i] : null; }
        public ItemStack getItem(int i) { return slots[i]; }
        public void setItem(int i, ItemStack s) { slots[i] = s == null ? null : s.clone(); }
        public int getSize() { return slots.length; }
        public void clear() { Arrays.fill(slots, null); }
        public int firstEmpty() { for (int i = 0; i < slots.length; i++) if (slots[i] == null) return i; return -1; }
        public InventoryHolder getHolder() { return holder; }
        public int count(String name) { int n = 0; for (ItemStack s : slots) if (s != null && s.getType().name().equals(name)) n += s.getAmount(); return n; }
        public String toString() { StringBuilder b = new StringBuilder(); for (ItemStack s : slots) if (s != null) b.append(s.getAmount()).append("x").append(s.getType()).append(" "); return b.toString(); }
    }

    public static class FEntity implements Entity {
        final FakeWorld w; Location loc; final EntityType type; final UUID id = UUID.randomUUID(); boolean valid = true; boolean gravity = true;
        final Set<String> tags = new HashSet<>();
        FEntity(FakeWorld w, Location l, EntityType t) { this.w = w; this.loc = l.clone(); this.type = t; }
        public Location getLocation() { return loc.clone(); }
        public boolean teleport(Location l) { loc = l.clone(); return true; }
        public World getWorld() { return w; }
        public void remove() { valid = false; w.entities.remove(this); }
        public boolean isValid() { return valid; }
        public boolean isDead() { return !valid; }
        public UUID getUniqueId() { return id; }
        public EntityType getType() { return type; }
        public void setGravity(boolean g) { gravity = g; }
        public void setVelocity(Vector v) {}
        public Vector getVelocity() { return new Vector(0, 0, 0); }
        public void setRotation(float y, float p) { loc.setYaw(y); loc.setPitch(p); }
        public void setCustomName(String n) {} public void setCustomNameVisible(boolean v) {} public void setSilent(boolean s) {}
        public void setInvulnerable(boolean i) {} public void setPersistent(boolean p) {}
        public List<Entity> getNearbyEntities(double x, double y, double z) { return new ArrayList<>(w.getNearbyEntities(loc, x, y, z)); }
        public String getName() { return type.name(); }
        public boolean isOnGround() { return true; }
        public double getHeight() { return 1.8; }
        public void setFireTicks(int t) {}
        public boolean addScoreboardTag(String t) { return tags.add(t); }
        public Set<String> getScoreboardTags() { return tags; }
        public boolean isInWater() { return false; }
    }

    public static class FLiving extends FEntity implements LivingEntity {
        double health = 20; final FEquipment equipment = new FEquipment(); int swings;
        FLiving(FakeWorld w, Location l, EntityType t) { super(w, l, t); }
        public EntityEquipment getEquipment() { return equipment; }
        public void swingMainHand() { swings++; }
        public void damage(double amount, Entity source) {
            health -= amount;
            if (health <= 0 && valid) {
                valid = false; w.entities.remove(this);
                String n = type.name();
                if (n.equals("COW")) { w.dropItemNaturally(loc, new ItemStack(Material.getMaterial("BEEF"), 2)); w.dropItemNaturally(loc, new ItemStack(Material.getMaterial("LEATHER"), 1)); }
                if (n.equals("ZOMBIE")) w.dropItemNaturally(loc, new ItemStack(Material.getMaterial("ROTTEN_FLESH"), 1));
            }
        }
        public double getHealth() { return health; }
        public Location getEyeLocation() { return loc.clone().add(0, 1.62, 0); }
        public void setAI(boolean a) {} public void setCollidable(boolean c) {} public void setRemoveWhenFarAway(boolean r) {} public void setCanPickupItems(boolean p) {}
        public boolean hasLineOfSight(Entity o) { return true; }
        public Block getTargetBlockExact(int max) {
            Vector d = loc.getDirection();
            for (int i = 1; i < max; i++) {
                Location p = loc.clone().add(0, 1.62, 0).add(d.clone().multiply(i * 0.5));
                Block b = w.getBlockAt(p);
                if (!b.getType().isAir()) return b;
            }
            return null;
        }
    }
    public static final class FMonster extends FLiving implements Monster {
        LivingEntity target;
        FMonster(FakeWorld w, Location l, EntityType t) { super(w, l, t); }
        public LivingEntity getTarget() { return target; }
        public void setTarget(LivingEntity t) { target = t; }
    }

    public static final class FEquipment implements EntityEquipment {
        public ItemStack hand;
        public void setItemInMainHand(ItemStack i) { hand = i; }
        public ItemStack getItemInMainHand() { return hand; }
        public void setHelmet(ItemStack i) {} public void setChestplate(ItemStack i) {} public void setLeggings(ItemStack i) {} public void setBoots(ItemStack i) {}
    }

    public static final class FItem extends FEntity implements Item {
        ItemStack stack;
        FItem(FakeWorld w, Location l, ItemStack s) { super(w, l, EntityType.valueOf("ITEM")); stack = s.clone(); }
        public ItemStack getItemStack() { return stack.clone(); }
        public void setItemStack(ItemStack s) { stack = s.clone(); }
        public int getPickupDelay() { return 0; }
    }

    public static final class FPlayer extends FLiving implements Player {
        final String name; public final FInventory inv = new FInventory(36, null); public final List<String> chat = new ArrayList<>();
        public FPlayer(FakeWorld w, Location l, String name) { super(w, l, EntityType.valueOf("PLAYER")); this.name = name; }
        public String getName() { return name; }
        public PlayerInventory getInventory() { return inv; }
        public InventoryView openInventory(Inventory i) { chat.add("[Inventar geoeffnet]"); return null; }
        public int getFoodLevel() { return 20; }
        public boolean isOnline() { return true; }
        public void playSound(Location l, Sound s, float v, float p) {}
        public void sendBlockDamage(Location l, float progress, int id) {}
        public void sendMessage(String m) { chat.add(m); }
        public boolean hasPermission(String p) { return true; }
        public void damage(double amount, Entity source) { health -= amount; }
    }
}
