import java.util.*;

/** Hand-maintained corrections on top of the auto-derived stubs. */
public final class Overrides {

    public static final Set<String> ENUMS = Set.of(
            "org/bukkit/event/EventPriority",
            "org/bukkit/GameMode"
    );

    /** Klassen, die im Quellcode mit "new" erzeugt werden - duerfen nicht abstract sein. */
    public static final Set<String> CONCRETE = Set.of(
            "org/bukkit/NamespacedKey",
            "org/bukkit/inventory/ItemStack",
            "org/bukkit/attribute/AttributeModifier",
            "com/mojang/authlib/GameProfile",
            "com/mojang/authlib/properties/Property",
            "org/bukkit/configuration/file/YamlConfiguration",
            "org/bukkit/Location",
            "org/bukkit/util/Vector"
    );

    public static final Set<String> INTERFACES = Set.of(
            "org/bukkit/plugin/Plugin",
            "org/bukkit/event/Listener",
            "org/bukkit/command/CommandSender",
            "org/bukkit/command/CommandExecutor",
            "org/bukkit/command/TabCompleter",
            "org/bukkit/entity/Entity",
            "org/bukkit/entity/LivingEntity",
            "org/bukkit/entity/HumanEntity",
            "org/bukkit/entity/Player",
            "org/bukkit/entity/Mob",
            "org/bukkit/entity/Ageable",
            "org/bukkit/entity/Zombie",
            "org/bukkit/entity/Wither",
            "org/bukkit/entity/Creeper",
            "org/bukkit/entity/Slime",
            "org/bukkit/entity/Phantom",
            "org/bukkit/entity/Villager",
            "org/bukkit/entity/Boss",
            "org/bukkit/entity/Item",
            "org/bukkit/boss/BossBar",
            "org/bukkit/World",
            "org/bukkit/OfflinePlayer",
            "org/bukkit/inventory/InventoryHolder",
            "org/bukkit/inventory/Inventory",
            "org/bukkit/inventory/PlayerInventory",
            "org/bukkit/scheduler/BukkitScheduler",
            "org/bukkit/scheduler/BukkitTask",
            "org/bukkit/plugin/PluginManager",
            "org/bukkit/Server",
            "net/kyori/adventure/text/Component",
            "net/kyori/adventure/text/TextComponent",
            "net/kyori/adventure/text/ComponentLike",
            "org/bukkit/entity/Creature",
            "org/bukkit/entity/Monster",
            "org/bukkit/entity/Animals",
            "org/bukkit/entity/EnderDragon",
            "org/bukkit/entity/ArmorStand",
            "org/bukkit/persistence/PersistentDataHolder"
    );

    /** force these back to class shape even if a heuristic marked them otherwise */
    public static final Set<String> CLASSES = Set.of(
            "org/bukkit/Material",
            "org/bukkit/entity/EntityType",
            "org/bukkit/Sound",
            "org/bukkit/GameMode",
            "org/bukkit/Location",
            "org/bukkit/plugin/java/JavaPlugin"
    );

    /** child -> supertypes (interfaces land in "implements"/"extends", a class in "extends") */
    public static final Map<String, List<String>> HIERARCHY = Map.ofEntries(
            Map.entry("org/bukkit/entity/Player",
                    List.of("org/bukkit/entity/HumanEntity", "org/bukkit/command/CommandSender",
                            "org/bukkit/OfflinePlayer")),
            Map.entry("org/bukkit/entity/HumanEntity", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/entity/LivingEntity", List.of("org/bukkit/entity/Entity")),
            Map.entry("org/bukkit/entity/Mob", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/entity/Ageable", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/entity/Zombie", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/entity/Wither", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/entity/Creeper", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/entity/Slime", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/entity/Phantom", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/entity/Villager", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/inventory/PlayerInventory",
                    List.of("org/bukkit/inventory/Inventory")),
            Map.entry("org/bukkit/entity/Creature", List.of("org/bukkit/entity/Mob")),
            Map.entry("org/bukkit/entity/Monster", List.of("org/bukkit/entity/Creature")),
            Map.entry("org/bukkit/entity/Animals", List.of("org/bukkit/entity/Ageable")),
            Map.entry("org/bukkit/entity/EnderDragon", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("org/bukkit/entity/ArmorStand", List.of("org/bukkit/entity/LivingEntity")),
            Map.entry("net/kyori/adventure/text/TextComponent",
                    List.of("net/kyori/adventure/text/Component")),
            Map.entry("net/kyori/adventure/text/Component",
                    List.of("net/kyori/adventure/text/ComponentLike")),
            Map.entry("org/bukkit/plugin/java/JavaPlugin", List.of("org/bukkit/plugin/Plugin"))
    );

    /** owner -> method signatures as descriptors ("static " prefix for statics) */
    public static final Map<String, List<String>> EXTRA_METHODS = Map.ofEntries(
            Map.entry("org/bukkit/Material", List.of(
                    "static valueOf(Ljava/lang/String;)Lorg/bukkit/Material;",
                    "name()Ljava/lang/String;")),
            Map.entry("org/bukkit/entity/EntityType", List.of(
                    "static valueOf(Ljava/lang/String;)Lorg/bukkit/entity/EntityType;",
                    "name()Ljava/lang/String;")),
            Map.entry("org/bukkit/entity/Entity", List.of(
                    "remove()V",
                    "isValid()Z",
                    "isDead()Z",
                    "teleport(Lorg/bukkit/Location;)Z",
                    "setSilent(Z)V",
                    "setInvulnerable(Z)V",
                    "setGravity(Z)V",
                    "setPersistent(Z)V",
                    "setCustomNameVisible(Z)V",
                    "setVisibleByDefault(Z)V",
                    "isVisibleByDefault()Z",
                    "setRotation(FF)V",
                    "setFireTicks(I)V",
                    "addScoreboardTag(Ljava/lang/String;)Z",
                    "getUniqueId()Ljava/util/UUID;",
                    "getEntityId()I",
                    "getType()Lorg/bukkit/entity/EntityType;",
                    "getWorld()Lorg/bukkit/World;",
                    "getLocation()Lorg/bukkit/Location;",
                    "getName()Ljava/lang/String;")),
            Map.entry("org/bukkit/entity/LivingEntity", List.of(
                    "setAI(Z)V",
                    "setCollidable(Z)V",
                    "setRemoveWhenFarAway(Z)V",
                    "setCanPickupItems(Z)V")),
            Map.entry("org/bukkit/entity/Zombie", List.of("setBaby()V", "setBaby(Z)V")),
            Map.entry("org/bukkit/entity/Ageable", List.of("setBaby()V")),
            Map.entry("org/bukkit/entity/Wither", List.of(
                    "setInvulnerableTicks(I)V",
                    "getBossBar()Lorg/bukkit/boss/BossBar;")),
            Map.entry("org/bukkit/configuration/file/YamlConfiguration", List.of(
                    "contains(Ljava/lang/String;)Z",
                    "get(Ljava/lang/String;)Ljava/lang/Object;",
                    "getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                    "getLong(Ljava/lang/String;)J",
                    "getBoolean(Ljava/lang/String;)Z")),
            Map.entry("org/bukkit/configuration/ConfigurationSection", List.of(
                    "getItemStack(Ljava/lang/String;)Lorg/bukkit/inventory/ItemStack;")),
            Map.entry("org/bukkit/inventory/PlayerInventory", List.of(
                    "getContents()[Lorg/bukkit/inventory/ItemStack;")),
            Map.entry("org/bukkit/command/CommandExecutor", List.of(
                    "onCommand(Lorg/bukkit/command/CommandSender;Lorg/bukkit/command/Command;"
                            + "Ljava/lang/String;[Ljava/lang/String;)Z")),
            Map.entry("org/bukkit/entity/Creeper", List.of("setPowered(Z)V")),
            Map.entry("org/bukkit/entity/Boss", List.of("getBossBar()Lorg/bukkit/boss/BossBar;")),
            Map.entry("org/bukkit/scheduler/BukkitTask", List.of("cancel()V", "isCancelled()Z")),
            Map.entry("org/bukkit/scheduler/BukkitScheduler", List.of(
                    "runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;")),
            Map.entry("org/bukkit/event/server/PluginDisableEvent", List.of(
                    "getPlugin()Lorg/bukkit/plugin/Plugin;")),
            Map.entry("org/bukkit/event/player/PlayerInteractEntityEvent", List.of(
                    "getRightClicked()Lorg/bukkit/entity/Entity;",
                    "getPlayer()Lorg/bukkit/entity/Player;",
                    "setCancelled(Z)V")),
            Map.entry("org/bukkit/event/inventory/InventoryClickEvent", List.of(
                    "isRightClick()Z",
                    "isShiftClick()Z")),
            Map.entry("org/bukkit/event/entity/EntityPickupItemEvent", List.of(
                    "getEntity()Lorg/bukkit/entity/LivingEntity;",
                    "getItem()Lorg/bukkit/entity/Item;")),
            Map.entry("org/bukkit/entity/Item", List.of(
                    "getItemStack()Lorg/bukkit/inventory/ItemStack;")),
            Map.entry("org/bukkit/event/player/PlayerLoginEvent", List.of(
                    "getPlayer()Lorg/bukkit/entity/Player;")),
            Map.entry("org/bukkit/event/entity/EntityDamageEvent", List.of(
                    "getEntity()Lorg/bukkit/entity/Entity;",
                    "setCancelled(Z)V")),
            Map.entry("org/bukkit/event/entity/EntityTargetEvent", List.of(
                    "getTarget()Lorg/bukkit/entity/Entity;",
                    "getEntity()Lorg/bukkit/entity/Entity;",
                    "setCancelled(Z)V")),
            Map.entry("org/bukkit/entity/ArmorStand", List.of("setBasePlate(Z)V", "setArms(Z)V")),
            Map.entry("org/bukkit/entity/Slime", List.of("setSize(I)V")),
            Map.entry("org/bukkit/entity/Phantom", List.of("setSize(I)V")),
            Map.entry("org/bukkit/boss/BossBar", List.of("removeAll()V", "setVisible(Z)V")),
            Map.entry("org/bukkit/World", List.of(
                    "spawnEntity(Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;",
                    "getName()Ljava/lang/String;")),
            Map.entry("org/bukkit/entity/Player", List.of(
                    "hideEntity(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;)V",
                    "showEntity(Lorg/bukkit/plugin/Plugin;Lorg/bukkit/entity/Entity;)V",
                    "canSee(Lorg/bukkit/entity/Entity;)Z",
                    "isSneaking()Z",
                    "isGliding()Z",
                    "isSwimming()Z",
                    "isOnline()Z",
                    "performCommand(Ljava/lang/String;)Z",
                    "getEnderChest()Lorg/bukkit/inventory/Inventory;",
                    "updateInventory()V")),
            Map.entry("org/bukkit/Bukkit", List.of(
                    "static getPlayer(Ljava/util/UUID;)Lorg/bukkit/entity/Player;")),
            Map.entry("org/bukkit/Location", List.of(
                    "getYaw()F",
                    "getPitch()F",
                    "clone()Lorg/bukkit/Location;",
                    "getWorld()Lorg/bukkit/World;"))
    );

    public static final Map<String, Map<String, String>> EXTRA_FIELDS = Map.of(
            "org/bukkit/event/EventPriority", Map.of(
                    "HIGHEST", "Lorg/bukkit/event/EventPriority;"),
            "org/bukkit/Material", Map.ofEntries(
                    Map.entry("STONE", "Lorg/bukkit/Material;"),
                    Map.entry("NAME_TAG", "Lorg/bukkit/Material;"),
                    Map.entry("EGG", "Lorg/bukkit/Material;"),
                    Map.entry("CREEPER_HEAD", "Lorg/bukkit/Material;"),
                    Map.entry("PLAYER_HEAD", "Lorg/bukkit/Material;"),
                    Map.entry("BARRIER", "Lorg/bukkit/Material;"),
                    Map.entry("GRAY_DYE", "Lorg/bukkit/Material;"),
                    Map.entry("BUCKET", "Lorg/bukkit/Material;"),
                    Map.entry("TNT", "Lorg/bukkit/Material;"),
                    Map.entry("LIGHT_GRAY_STAINED_GLASS_PANE", "Lorg/bukkit/Material;"),
                    Map.entry("SKELETON_SKULL", "Lorg/bukkit/Material;"),
                    Map.entry("BOOK", "Lorg/bukkit/Material;"),
                    Map.entry("ROTTEN_FLESH", "Lorg/bukkit/Material;"),
                    Map.entry("WHEAT", "Lorg/bukkit/Material;"),
                    Map.entry("EMERALD", "Lorg/bukkit/Material;"),
                    Map.entry("GLOWSTONE_DUST", "Lorg/bukkit/Material;"),
                    Map.entry("STICK", "Lorg/bukkit/Material;"),
                    Map.entry("RED_BED", "Lorg/bukkit/Material;"),
                    Map.entry("COMPASS", "Lorg/bukkit/Material;")
            )
    );

    /** owner -> (erasureKey "name|(params)V" -> full source line), emitted before the derived ones */
    public static final Map<String, Map<String, String>> SOURCE_METHODS = Map.of(
            "org/bukkit/Bukkit", Map.of(
                    "getOnlinePlayers|()V",
                    "public static java.util.Collection<? extends org.bukkit.entity.Player> getOnlinePlayers() { return null; }",
                    "getWorlds|()V",
                    "public static java.util.List<org.bukkit.World> getWorlds() { return null; }"),
            "org/bukkit/World", Map.of(
                    "getPlayers|()V",
                    "java.util.List<org.bukkit.entity.Player> getPlayers();",
                    "getEntities|()V",
                    "java.util.List<org.bukkit.entity.Entity> getEntities();"),
            "org/bukkit/configuration/ConfigurationSection", Map.of(
                    "getKeys|(Z)V",
                    "java.util.Set<String> getKeys(boolean a0);"),
            "org/bukkit/entity/Entity", Map.of(
                    "getScoreboardTags|()V",
                    "java.util.Set<String> getScoreboardTags();"),
            "org/bukkit/entity/Player", Map.of(
                    "getActivePotionEffects|()V",
                    "java.util.Collection<org.bukkit.potion.PotionEffect> getActivePotionEffects();"),
            "org/bukkit/inventory/PlayerInventory", Map.of(
                    "addItem|([Lorg/bukkit/inventory/ItemStack;)V",
                    "java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> addItem("
                            + "org.bukkit.inventory.ItemStack... a0);")
    );

    private Overrides() {}
}
