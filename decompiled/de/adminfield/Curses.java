/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Curse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Curses {
    private static final Material[] JUNK = new Material[]{Material.DIRT, Material.COBBLESTONE, Material.GRAVEL, Material.SAND, Material.ROTTEN_FLESH, Material.BONE, Material.STICK, Material.OAK_LEAVES, Material.SEAGRASS, Material.DEAD_BUSH, Material.CACTUS, Material.CLAY_BALL};
    private static final PotionEffectType[] MOODS = new PotionEffectType[]{PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST, PotionEffectType.GLOWING, PotionEffectType.NIGHT_VISION, PotionEffectType.SLOWNESS, PotionEffectType.NAUSEA, PotionEffectType.LEVITATION, PotionEffectType.INVISIBILITY};
    private final AdminFieldPlugin plugin;
    private final Map<UUID, Hex> hexes = new LinkedHashMap<UUID, Hex>();
    private File file;

    public Curses(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    public Hex hex(UUID uUID) {
        return this.hexes.get(uUID);
    }

    public boolean isCursed(UUID uUID) {
        return this.hexes.containsKey(uUID);
    }

    public Collection<Hex> all() {
        return new ArrayList<Hex>(this.hexes.values());
    }

    public int count() {
        return this.hexes.size();
    }

    public Curse cast(Player player, Player player2, Curse curse) {
        Curse curse2 = curse != null ? curse : Curse.values()[ThreadLocalRandom.current().nextInt(Curse.values().length)];
        this.hexes.put(player2.getUniqueId(), new Hex(player2.getUniqueId(), player2.getName(), curse2, player.getName(), System.currentTimeMillis()));
        this.save();
        return curse2;
    }

    public boolean lift(UUID uUID) {
        Hex hex = this.hexes.remove(uUID);
        if (hex == null) {
            return false;
        }
        Player player = Bukkit.getPlayer((UUID)uUID);
        if (player != null && hex.curse == Curse.LAUNENHAFT) {
            for (PotionEffectType potionEffectType : MOODS) {
                player.removePotionEffect(potionEffectType);
            }
        }
        this.save();
        return true;
    }

    public void liftAll() {
        for (UUID uUID : new ArrayList<UUID>(this.hexes.keySet())) {
            this.lift(uUID);
        }
    }

    public void tick() {
        if (this.hexes.isEmpty()) {
            return;
        }
        long l = this.plugin.getConfig().getLong("curses.duration-minutes", 0L);
        long l2 = System.currentTimeMillis();
        for (Hex hex : this.all()) {
            if (l > 0L && l2 - hex.castAt > l * 60000L) {
                this.lift(hex.target);
                continue;
            }
            Player player = Bukkit.getPlayer((UUID)hex.target);
            if (player == null || player.isDead()) continue;
            switch (hex.curse) {
                case STARRENDE_KUEHE: {
                    this.stareAt(player);
                    break;
                }
                case LAUNENHAFT: {
                    this.mood(player, hex, l2);
                    break;
                }
            }
        }
    }

    private void stareAt(Player player) {
        for (Entity entity : player.getNearbyEntities(24.0, 12.0, 24.0)) {
            if (!(entity instanceof Cow)) continue;
            Cow cow = (Cow)entity;
            cow.lookAt((Entity)player);
        }
    }

    private void mood(Player player, Hex hex, long l) {
        int n = Math.max(5, this.plugin.getConfig().getInt("curses.mood-interval-seconds", 25));
        if (l - hex.lastMood < (long)n * 1000L) {
            return;
        }
        hex.lastMood = l;
        PotionEffectType potionEffectType = MOODS[ThreadLocalRandom.current().nextInt(MOODS.length)];
        int n2 = 20 * ThreadLocalRandom.current().nextInt(4, 10);
        if (potionEffectType.equals(PotionEffectType.LEVITATION)) {
            n2 = 20;
        }
        player.addPotionEffect(new PotionEffect(potionEffectType, n2, ThreadLocalRandom.current().nextInt(0, 2), false, false, false));
    }

    public void onJump(Player player) {
        if (this.matches(player, Curse.SPRUNGSCHRECK)) {
            Location location = player.getLocation().subtract(player.getLocation().getDirection().multiply(1.5));
            player.playSound(location, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
        }
    }

    public void onDrop(Player player, List<Item> list) {
        if (!this.matches(player, Curse.FALSCHE_BEUTE) || list.isEmpty()) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) >= this.plugin.getConfig().getInt("curses.junk-drop-percent", 60)) {
            return;
        }
        for (Item item : list) {
            Material material = JUNK[ThreadLocalRandom.current().nextInt(JUNK.length)];
            item.setItemStack(new ItemStack(material, item.getItemStack().getAmount()));
        }
    }

    public void onSprint(Player player) {
        if (!this.matches(player, Curse.SCHWINDEL)) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) >= this.plugin.getConfig().getInt("curses.dizzy-percent", 45)) {
            return;
        }
        Location location = player.getLocation();
        location.setYaw(location.getYaw() + (float)ThreadLocalRandom.current().nextInt(-70, 71));
        location.setPitch(Math.max(-80.0f, Math.min(80.0f, location.getPitch() + (float)ThreadLocalRandom.current().nextInt(-20, 21))));
        player.teleport(location);
    }

    public void onOpenContainer(Player player) {
        if (!this.matches(player, Curse.GRUSELKISTE)) {
            return;
        }
        Sound[] soundArray = new Sound[]{Sound.AMBIENT_CAVE, Sound.ENTITY_ENDERMAN_STARE, Sound.ENTITY_GHAST_SCREAM, Sound.ENTITY_WITHER_SPAWN, Sound.BLOCK_BELL_RESONATE, Sound.ENTITY_WARDEN_HEARTBEAT};
        player.playSound(player.getLocation(), soundArray[ThreadLocalRandom.current().nextInt(soundArray.length)], 0.8f, 0.6f);
    }

    private boolean matches(Player player, Curse curse) {
        Hex hex = this.hexes.get(player.getUniqueId());
        return hex != null && hex.curse == curse;
    }

    public void load() {
        this.file = new File(this.plugin.getDataFolder(), "curses.yml");
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)this.file);
        ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("curses");
        if (configurationSection == null) {
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            try {
                UUID uUID = UUID.fromString(string);
                Curse curse = Curse.byName(configurationSection.getString(string + ".curse"));
                if (curse == null) continue;
                this.hexes.put(uUID, new Hex(uUID, configurationSection.getString(string + ".name", "Unbekannt"), curse, configurationSection.getString(string + ".cast-by", "Unbekannt"), configurationSection.getLong(string + ".cast-at")));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.plugin.getLogger().warning("Ein Fluch-Eintrag ist beschädigt und wurde übersprungen.");
            }
        }
    }

    public void save() {
        if (this.file == null) {
            this.file = new File(this.plugin.getDataFolder(), "curses.yml");
        }
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        for (Hex hex : this.hexes.values()) {
            String string = "curses." + String.valueOf(hex.target);
            yamlConfiguration.set(string + ".name", (Object)hex.name);
            yamlConfiguration.set(string + ".curse", (Object)hex.curse.name());
            yamlConfiguration.set(string + ".cast-by", (Object)hex.castBy);
            yamlConfiguration.set(string + ".cast-at", (Object)hex.castAt);
        }
        try {
            this.plugin.getDataFolder().mkdirs();
            yamlConfiguration.save(this.file);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("curses.yml konnte nicht gespeichert werden: " + iOException.getMessage());
        }
    }

    public static final class Hex {
        private final UUID target;
        private final String name;
        private final Curse curse;
        private final String castBy;
        private final long castAt;
        private long lastMood;

        Hex(UUID uUID, String string, Curse curse, String string2, long l) {
            this.target = uUID;
            this.name = string;
            this.curse = curse;
            this.castBy = string2;
            this.castAt = l;
        }

        public UUID target() {
            return this.target;
        }

        public String name() {
            return this.name;
        }

        public Curse curse() {
            return this.curse;
        }

        public String castBy() {
            return this.castBy;
        }

        public long castAt() {
            return this.castAt;
        }
    }
}

