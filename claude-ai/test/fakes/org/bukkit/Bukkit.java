package org.bukkit;
import java.util.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.*;
/** Testserver: Spieler, Welt und ein Scheduler, den der Test Tick fuer Tick vorspult. */
public abstract class Bukkit {
    public static final List<Player> players = new ArrayList<>();
    public static FakeWorld world;
    public static long tick;
    static final class Job implements BukkitTask { long due; long period; Runnable r; boolean cancelled; public void cancel() { cancelled = true; } }
    static final List<Job> jobs = new ArrayList<>();
    public static void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick++;
            for (Job j : new ArrayList<>(jobs)) {
                if (j.cancelled || j.due > tick) continue;
                j.r.run();
                if (j.period > 0) j.due = tick + j.period; else jobs.remove(j);
            }
        }
    }
    static final BukkitScheduler SCHED = new BukkitScheduler() {
        Job add(Runnable r, long delay, long period) { Job j = new Job(); j.r = r; j.due = tick + Math.max(1, delay); j.period = period; jobs.add(j); return j; }
        public BukkitTask runTaskTimer(org.bukkit.plugin.Plugin p, Runnable r, long d, long per) { return add(r, d, per); }
        public BukkitTask runTask(org.bukkit.plugin.Plugin p, Runnable r) { return add(r, 1, 0); }
        public BukkitTask runTaskLater(org.bukkit.plugin.Plugin p, Runnable r, long d) { return add(r, d, 0); }
    };
    public static Collection<? extends Player> getOnlinePlayers() { return players; }
    public static Player getPlayerExact(String n) { for (Player p : players) if (p.getName().equalsIgnoreCase(n)) return p; return null; }
    public static Player getPlayer(UUID id) { for (Player p : players) if (p.getUniqueId().equals(id)) return p; return null; }
    public static BukkitScheduler getScheduler() { return SCHED; }
    public static org.bukkit.plugin.PluginManager getPluginManager() { return new org.bukkit.plugin.PluginManager() {
        public void registerEvents(org.bukkit.event.Listener l, org.bukkit.plugin.Plugin p) {}
        public void callEvent(org.bukkit.event.Event e) {} }; }
    public static org.bukkit.command.ConsoleCommandSender getConsoleSender() { return null; }
    public static final List<String> commands = new ArrayList<>();
    public static boolean dispatchCommand(org.bukkit.command.CommandSender s, String line) { commands.add(line); return true; }
    public static Inventory createInventory(InventoryHolder owner, int size, net.kyori.adventure.text.Component title) { return new Fakes.FInventory(size, owner); }
    public static BlockData createBlockData(String data) {
        String s = data.replace("minecraft:", "");
        String props = "";
        int i = s.indexOf('[');
        if (i >= 0) { props = s.substring(i + 1, s.length() - 1); s = s.substring(0, i); }
        Material m = Material.getMaterial(s);
        if (m == null) throw new IllegalArgumentException(data);
        return FakeWorld.data(m, props);
    }
    public static List<World> getWorlds() { return List.of(world); }
    public static World getWorld(String n) { return world; }
    public static World getWorld(UUID id) { return world; }
}
