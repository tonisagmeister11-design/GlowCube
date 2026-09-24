package org.bukkit.configuration.file;
public abstract class FileConfiguration {
    protected final java.util.Map<String, Object> map = new java.util.HashMap<>();
    public String getString(String p, String d) { Object o = map.get(p); return o == null ? d : o.toString(); }
    public int getInt(String p, int d) { Object o = map.get(p); return o instanceof Number n ? n.intValue() : d; }
    public boolean getBoolean(String p, boolean d) { Object o = map.get(p); return o instanceof Boolean b ? b : d; }
    public double getDouble(String p, double d) { Object o = map.get(p); return o instanceof Number n ? n.doubleValue() : d; }
    @SuppressWarnings("unchecked") public java.util.List<String> getStringList(String p) { Object o = map.get(p); return o instanceof java.util.List<?> l ? (java.util.List<String>) l : new java.util.ArrayList<>(); }
    public java.util.List<?> getList(String p) { Object o = map.get(p); return o instanceof java.util.List<?> l ? l : null; }
    public org.bukkit.configuration.ConfigurationSection getConfigurationSection(String p) { return null; }
    public void set(String p, Object v) { map.put(p, v); }
    public boolean contains(String p) { return map.containsKey(p); }
    public void save(java.io.File f) throws java.io.IOException {}
}
