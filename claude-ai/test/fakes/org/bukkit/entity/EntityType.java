package org.bukkit.entity;
public final class EntityType {
    private static final java.util.Map<String, EntityType> ALL = new java.util.HashMap<>();
    private final String n; private EntityType(String n) { this.n = n; }
    public static EntityType valueOf(String name) { return ALL.computeIfAbsent(name, EntityType::new); }
    public String name() { return n; }
}
