/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.SiteKind;
import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class BuildSite {
    private final UUID id;
    private final String world;
    private double sumX;
    private double sumY;
    private double sumZ;
    private int samples;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private int placed;
    private int broken;
    private long firstSeen;
    private long lastSeen;
    private boolean announced;
    private final Map<UUID, Integer> contributions = new HashMap<UUID, Integer>();
    private final Map<UUID, String> names = new HashMap<UUID, String>();
    private final Map<Material, Integer> materials = new HashMap<Material, Integer>();
    public static final Comparator<BuildSite> BY_RECENT = Comparator.comparingLong(BuildSite::lastSeen).reversed();

    public BuildSite(String string, int n, int n2, int n3) {
        this.id = UUID.randomUUID();
        this.world = string;
        this.minX = n;
        this.maxX = n;
        this.minY = n2;
        this.maxY = n2;
        this.minZ = n3;
        this.maxZ = n3;
        this.sumX = n;
        this.sumY = n2;
        this.sumZ = n3;
        this.samples = 1;
        this.lastSeen = this.firstSeen = System.currentTimeMillis();
    }

    public BuildSite(String string, int n, int n2, int n3, int n4, int n5, int n6, double d, double d2, double d3, int n7, int n8, int n9, long l, long l2, boolean bl) {
        this.id = UUID.randomUUID();
        this.world = string;
        this.minX = n;
        this.minY = n2;
        this.minZ = n3;
        this.maxX = n4;
        this.maxY = n5;
        this.maxZ = n6;
        this.sumX = d;
        this.sumY = d2;
        this.sumZ = d3;
        this.samples = Math.max(1, n7);
        this.placed = n8;
        this.broken = n9;
        this.firstSeen = l;
        this.lastSeen = l2;
        this.announced = bl;
    }

    public void record(UUID uUID, String string, Block block, boolean bl) {
        int n = block.getX();
        int n2 = block.getY();
        int n3 = block.getZ();
        this.sumX += (double)n;
        this.sumY += (double)n2;
        this.sumZ += (double)n3;
        ++this.samples;
        this.minX = Math.min(this.minX, n);
        this.minY = Math.min(this.minY, n2);
        this.minZ = Math.min(this.minZ, n3);
        this.maxX = Math.max(this.maxX, n);
        this.maxY = Math.max(this.maxY, n2);
        this.maxZ = Math.max(this.maxZ, n3);
        if (bl) {
            ++this.placed;
            this.materials.merge(block.getType(), 1, Integer::sum);
        } else {
            ++this.broken;
        }
        this.contributions.merge(uUID, 1, Integer::sum);
        this.names.put(uUID, string);
        this.lastSeen = System.currentTimeMillis();
    }

    public UUID id() {
        return this.id;
    }

    public String worldName() {
        return this.world;
    }

    public World world() {
        return Bukkit.getWorld((String)this.world);
    }

    public int centerX() {
        return (int)Math.round(this.sumX / (double)this.samples);
    }

    public int centerY() {
        return (int)Math.round(this.sumY / (double)this.samples);
    }

    public int centerZ() {
        return (int)Math.round(this.sumZ / (double)this.samples);
    }

    public Location center() {
        World world = this.world();
        if (world == null) {
            return null;
        }
        return new Location(world, (double)this.centerX() + 0.5, (double)this.centerY(), (double)this.centerZ() + 0.5);
    }

    public Location safeSpot() {
        Location location = this.center();
        if (location == null) {
            return null;
        }
        World world = location.getWorld();
        int n = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
        int n2 = Math.max(location.getBlockY(), Math.min(n + 1, world.getMaxHeight() - 2));
        return new Location(world, location.getX(), (double)n2, location.getZ());
    }

    public double distanceSquared(String string, int n, int n2, int n3) {
        if (!this.world.equals(string)) {
            return Double.MAX_VALUE;
        }
        double d = this.centerX() - n;
        double d2 = this.centerY() - n2;
        double d3 = this.centerZ() - n3;
        return d * d + d2 * d2 + d3 * d3;
    }

    public int width() {
        return this.maxX - this.minX + 1;
    }

    public int height() {
        return this.maxY - this.minY + 1;
    }

    public int depth() {
        return this.maxZ - this.minZ + 1;
    }

    public int placed() {
        return this.placed;
    }

    public int broken() {
        return this.broken;
    }

    public int total() {
        return this.placed + this.broken;
    }

    public long firstSeen() {
        return this.firstSeen;
    }

    public long lastSeen() {
        return this.lastSeen;
    }

    public boolean announced() {
        return this.announced;
    }

    public void markAnnounced() {
        this.announced = true;
    }

    public boolean idle(long l) {
        return System.currentTimeMillis() - this.lastSeen > l;
    }

    public Map<Material, Integer> materials() {
        return this.materials;
    }

    public Map<UUID, Integer> contributions() {
        return this.contributions;
    }

    public SiteKind kind() {
        return SiteKind.classify(this.materials, this.width(), this.height(), this.depth(), this.placed, this.broken);
    }

    public UUID primaryBuilder() {
        UUID uUID = null;
        int n = -1;
        for (Map.Entry<UUID, Integer> entry : this.contributions.entrySet()) {
            if (entry.getValue() <= n) continue;
            n = entry.getValue();
            uUID = entry.getKey();
        }
        return uUID;
    }

    public String primaryBuilderName() {
        UUID uUID = this.primaryBuilder();
        if (uUID == null) {
            return "Unbekannt";
        }
        return this.names.getOrDefault(uUID, "Unbekannt");
    }

    public String nameOf(UUID uUID) {
        return this.names.getOrDefault(uUID, "Unbekannt");
    }

    public List<Map.Entry<UUID, Integer>> topContributors(int n) {
        ArrayList<Map.Entry<UUID, Integer>> arrayList = new ArrayList<Map.Entry<UUID, Integer>>(this.contributions.entrySet());
        arrayList.sort(Map.Entry.comparingByValue().reversed());
        return arrayList.subList(0, Math.min(n, arrayList.size()));
    }

    public List<Map.Entry<Material, Integer>> topMaterials(int n) {
        ArrayList<Map.Entry<Material, Integer>> arrayList = new ArrayList<Map.Entry<Material, Integer>>(this.materials.entrySet());
        arrayList.sort(Map.Entry.comparingByValue().reversed());
        return arrayList.subList(0, Math.min(n, arrayList.size()));
    }

    public Material iconMaterial() {
        List<Map.Entry<Material, Integer>> list = this.topMaterials(1);
        if (!list.isEmpty() && list.get(0).getKey().isItem()) {
            return list.get(0).getKey();
        }
        return this.kind().icon();
    }

    public Map<String, Object> serialize() {
        LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
        linkedHashMap.put("world", this.world);
        linkedHashMap.put("min", List.of(Integer.valueOf(this.minX), Integer.valueOf(this.minY), Integer.valueOf(this.minZ)));
        linkedHashMap.put("max", List.of(Integer.valueOf(this.maxX), Integer.valueOf(this.maxY), Integer.valueOf(this.maxZ)));
        linkedHashMap.put("sum", List.of(Double.valueOf(this.sumX), Double.valueOf(this.sumY), Double.valueOf(this.sumZ)));
        linkedHashMap.put("samples", this.samples);
        linkedHashMap.put("placed", this.placed);
        linkedHashMap.put("broken", this.broken);
        linkedHashMap.put("first-seen", this.firstSeen);
        linkedHashMap.put("last-seen", this.lastSeen);
        linkedHashMap.put("announced", this.announced);
        ArrayList<CallSite> arrayList = new ArrayList<CallSite>();
        for (Map.Entry<UUID, Integer> object : this.contributions.entrySet()) {
            arrayList.add((CallSite)((Object)(String.valueOf(object.getKey()) + ";" + String.valueOf(object.getValue()) + ";" + this.nameOf(object.getKey()))));
        }
        linkedHashMap.put("contributors", arrayList);
        ArrayList arrayList2 = new ArrayList();
        for (Map.Entry<Material, Integer> entry : this.topMaterials(12)) {
            arrayList2.add(entry.getKey().name() + ";" + String.valueOf(entry.getValue()));
        }
        linkedHashMap.put("materials", arrayList2);
        return linkedHashMap;
    }

    public void restoreContributor(UUID uUID, int n, String string) {
        this.contributions.put(uUID, n);
        this.names.put(uUID, string);
    }

    public void restoreMaterial(Material material, int n) {
        this.materials.put(material, n);
    }
}

