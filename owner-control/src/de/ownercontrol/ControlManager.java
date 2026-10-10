package de.ownercontrol;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Besessenheit: Der Owner wird an die Stelle des Ziels gesetzt, schaut aus dessen Position und
 * bewegt es jeden Tick mit sich. Der Owner ist fuer alle anderen unsichtbar - sie sehen nur das
 * Ziel. Inventar, Gesundheit und Spielmodus werden abgeglichen, Chat laeuft ueber das Ziel.
 */
final class ControlManager implements Listener {

    private final OwnerControlPlugin plugin;
    private final Map<UUID, Session> byOwner = new HashMap<>();
    private final Map<UUID, Session> byTarget = new HashMap<>();
    /** Gesetzt, solange wir selbst als das Ziel chatten/Befehle ausfuehren. */
    private boolean injecting;

    ControlManager(OwnerControlPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    Session ofOwner(Player p) { return byOwner.get(p.getUniqueId()); }
    boolean isTarget(Player p) { return byTarget.containsKey(p.getUniqueId()); }

    // ------------------------------------------------------------------ Start / Stop

    String start(Player owner, Player target) {
        if (owner.getUniqueId().equals(target.getUniqueId())) return "Du kannst dich nicht selbst steuern.";
        if (isTarget(target)) return target.getName() + " wird schon gesteuert.";
        if (isTarget(owner)) return "Du wirst gerade selbst gesteuert.";
        if (plugin.isOwnerName(target.getName(), target.getUniqueId())) return "Einen Owner kannst du nicht steuern.";
        if (byOwner.containsKey(owner.getUniqueId())) stop(byOwner.get(owner.getUniqueId()), false);

        Session s = new Session(owner, target);
        try {
            s.saved.write(file(owner.getUniqueId()));
        } catch (Exception e) {
            return "Konnte deinen Zustand nicht sichern - abgebrochen: " + e.getMessage();
        }
        byOwner.put(owner.getUniqueId(), s);
        byTarget.put(target.getUniqueId(), s);

        owner.setGameMode(target.getGameMode());
        owner.teleport(target.getLocation());
        owner.getInventory().setContents(Session.clone(target.getInventory().getContents()));
        owner.getInventory().setHeldItemSlot(target.getInventory().getHeldItemSlot());
        s.lastInv = Session.clone(target.getInventory().getContents());
        owner.setAllowFlight(target.getAllowFlight());
        owner.setLevel(target.getLevel());
        owner.setExp(target.getExp());
        mirrorVitals(s);
        owner.setInvulnerable(true);

        if (plugin.getConfig().getBoolean("hide-owner", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(owner)) p.hidePlayer(plugin, owner);
        }
        owner.hideEntity(plugin, target); // sonst sitzt der Kopf des Ziels in deiner Kamera
        return null;
    }

    void stop(Session s, boolean notify) {
        if (byOwner.get(s.owner.getUniqueId()) != s) return;
        byOwner.remove(s.owner.getUniqueId());
        byTarget.remove(s.target.getUniqueId());
        Player o = s.owner;
        if (o.isOnline()) {
            if (s.target.isOnline()) syncInventory(s); // letzte Aenderungen noch ans Ziel
            for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(o)) p.showPlayer(plugin, o);
            if (s.target.isOnline()) o.showEntity(plugin, s.target);
            restore(o, s.saved);
            if (notify) o.sendMessage("§aSteuerung von " + s.target.getName() + " beendet.");
        }
        file(o.getUniqueId()).delete();
    }

    void stopAll() {
        for (Session s : new ArrayList<>(byOwner.values())) stop(s, false);
    }

    private void restore(Player o, Session.Snapshot z) {
        o.setInvulnerable(z.invulnerable);
        o.setGameMode(z.mode);
        o.getInventory().setContents(Session.clone(z.inv));
        o.getInventory().setHeldItemSlot(z.held);
        try { o.setHealth(z.health); } catch (RuntimeException ignored) { }
        o.setFoodLevel(z.food);
        o.setSaturation(z.saturation);
        o.setLevel(z.level);
        o.setExp(z.exp);
        o.setAllowFlight(z.allowFlight);
        o.setFlying(z.flying && z.allowFlight);
        o.setFallDistance(0f);
        if (z.loc != null && z.loc.getWorld() != null) o.teleport(z.loc);
    }

    private File file(UUID id) {
        File dir = new File(plugin.getDataFolder(), "sessions");
        dir.mkdirs();
        return new File(dir, id + ".yml");
    }

    // ------------------------------------------------------------------ Tick

    private void tick() {
        for (Session s : new ArrayList<>(byOwner.values())) {
            if (!s.owner.isOnline() || !s.target.isOnline()) { stop(s, true); continue; }
            Player o = s.owner, t = s.target;

            // Spielmodus/Flug folgen dem Ziel (z.B. wenn jemand /gamemode auf es ausfuehrt)
            if (o.getGameMode() != t.getGameMode()) o.setGameMode(t.getGameMode());
            if (o.getAllowFlight() != t.getAllowFlight()) o.setAllowFlight(t.getAllowFlight());
            if (t.getAllowFlight() && t.isFlying() != o.isFlying()) t.setFlying(o.isFlying());
            mirrorVitals(s);
            syncInventory(s);

            if (t.getInventory().getHeldItemSlot() != o.getInventory().getHeldItemSlot()) {
                t.getInventory().setHeldItemSlot(o.getInventory().getHeldItemSlot());
            }
            if (t.getLevel() != o.getLevel() || t.getExp() != o.getExp()) {
                t.setLevel(o.getLevel());
                t.setExp(o.getExp());
            }
            Location l = o.getLocation();
            t.teleport(l);
            t.setFallDistance(o.getFallDistance());
        }
    }

    /** Gesundheit, Hunger, Luft und Feuer gelten fuers Ziel - der Owner spiegelt sie nur. */
    private void mirrorVitals(Session s) {
        Player o = s.owner, t = s.target;
        try { o.setHealth(Math.max(0.1, t.getHealth())); } catch (RuntimeException ignored) { }
        o.setFoodLevel(t.getFoodLevel());
        o.setSaturation(t.getSaturation());
        o.setRemainingAir(t.getRemainingAir());
        o.setFireTicks(t.getFireTicks());
    }

    /**
     * Aenderungen am Inventar laufen in beide Richtungen: Aktionen des Owners (Bauen, Essen, Einsammeln)
     * landen beim Ziel, was das Ziel selbst einsammelt beim Owner. Massgeblich ist, wer seit dem
     * letzten Abgleich etwas veraendert hat.
     */
    private void syncInventory(Session s) {
        ItemStack[] mine = s.owner.getInventory().getContents();
        ItemStack[] theirs = s.target.getInventory().getContents();
        boolean ownerChanged = !Arrays.equals(mine, s.lastInv);
        boolean targetChanged = !Arrays.equals(theirs, s.lastInv);
        if (ownerChanged) {
            s.target.getInventory().setContents(Session.clone(mine));
            s.lastInv = Session.clone(mine);
        } else if (targetChanged) {
            s.owner.getInventory().setContents(Session.clone(theirs));
            s.lastInv = Session.clone(theirs);
        }
    }

    // ------------------------------------------------------------------ Als Ziel handeln

    void chatAs(Player target, String text) {
        injecting = true;
        try { target.chat(text); } finally { injecting = false; }
    }

    boolean commandAs(Player target, String cmd) {
        injecting = true;
        try { return target.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd); }
        finally { injecting = false; }
    }

    // ------------------------------------------------------------------ Events

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Session s = byOwner.get(p.getUniqueId());
        if (s != null && !injecting) {
            e.setCancelled(true);
            String msg = e.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> { if (s.target.isOnline()) chatAs(s.target, msg); });
        } else if (lockTarget() && !injecting && isTarget(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (lockTarget() && !injecting && isTarget(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) { lock(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) { lock(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) { lock(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) { lock(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) lock(p, e);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) lock(p, e);
    }

    private void lock(Player p, org.bukkit.event.Cancellable e) {
        if (lockTarget() && !injecting && isTarget(p)) e.setCancelled(true);
    }

    private boolean lockTarget() { return plugin.getConfig().getBoolean("lock-target", true); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Session s = byOwner.get(p.getUniqueId());
        if (s == null) s = byTarget.get(p.getUniqueId());
        if (s != null) stop(s, true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Session s = byTarget.get(e.getEntity().getUniqueId());
        if (s != null) Bukkit.getScheduler().runTask(plugin, () -> stop(s, true));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        for (Session s : byOwner.values()) {
            if (plugin.getConfig().getBoolean("hide-owner", true)) p.hidePlayer(plugin, s.owner);
        }
        // Absturzschutz: stand der Owner beim Absturz mitten in einer Steuerung, bekommt er seinen Zustand zurueck
        File f = file(p.getUniqueId());
        if (f.exists() && !byOwner.containsKey(p.getUniqueId())) {
            try {
                restore(p, Session.Snapshot.read(f));
                p.sendMessage("§eDein Zustand vor der letzten Steuerung wurde wiederhergestellt.");
                f.delete();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Wiederherstellung fehlgeschlagen: " + ex);
            }
        }
    }
}
