package de.glowcube.claudeai.npc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import de.glowcube.claudeai.ClaudeAIPlugin;
import de.glowcube.claudeai.Settings;
import de.glowcube.claudeai.brain.Lexicon;
import de.glowcube.claudeai.move.Mover;
import de.glowcube.claudeai.task.CombatTask;
import de.glowcube.claudeai.task.GiveTask;
import de.glowcube.claudeai.task.Task;
import de.glowcube.claudeai.task.TorchTask;
import de.glowcube.claudeai.world.Blocks;
import de.glowcube.claudeai.world.Fx;
import de.glowcube.claudeai.world.Mats;
import net.kyori.adventure.text.Component;

/**
 * Claudes Koerper und Alltag: Aussehen, Inventar, Sprechen, Abbauen, Bauen, Aufgaben
 * und die Grundhaltung (folgen, beschuetzen, warten).
 */
public final class Npc implements InventoryHolder {

    public enum Mode { IDLE, FOLLOW, GUARD, STAY }

    public static final String TAG = "claudeai";

    private final ClaudeAIPlugin plugin;
    private final Random random = new Random();
    private final Mover mover = new Mover(this);
    private final Inventory inventory;

    private LivingEntity body;
    private boolean spawned;
    private Location lastLocation;
    private long ticks;

    private final Deque<Task> queue = new ArrayDeque<>();
    private Task current;
    private int planCounter;

    private Mode mode = Mode.IDLE;
    private UUID modePlayer;
    private Location modeLocation;

    private UUID partner;
    private long partnerUntil;
    private UUID actor;

    // Abbauen
    private Block workBlock;
    private int workDone;
    private int workNeeded;
    private boolean crackWorks = true;

    private final Map<String, Long> cooldowns = new HashMap<>();
    private boolean nightAsked;

    public Npc(ClaudeAIPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, Component.text(plugin.settings().name + "s Inventar"));
    }

    public Settings settings() {
        return plugin.settings();
    }

    public ClaudeAIPlugin plugin() {
        return plugin;
    }

    public Mover mover() {
        return mover;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public long ticks() {
        return ticks;
    }

    public Random random() {
        return random;
    }

    // ================================================================== Erscheinen

    public boolean isSpawned() {
        return spawned;
    }

    public LivingEntity body() {
        return body;
    }

    public boolean isBody(Entity e) {
        return e != null && body != null && e.getUniqueId().equals(body.getUniqueId());
    }

    public void spawn(Location at) {
        spawned = true;
        lastLocation = at.clone();
        spawnBody(at);
    }

    public void despawn() {
        stopAll();
        mode = Mode.IDLE;
        spawned = false;
        removeBody();
    }

    private void removeBody() {
        if (body != null && body.isValid()) {
            Location at = body.getLocation();
            Fx.particle(at.clone().add(0, 1, 0), () -> Particle.PORTAL, 40, 0.4);
            body.remove();
        }
        body = null;
    }

    private void spawnBody(Location at) {
        removeBody();
        World world = at.getWorld();
        LivingEntity e = null;
        try {
            Entity raw = world.spawnEntity(at, EntityType.valueOf("MANNEQUIN"));
            if (raw instanceof LivingEntity le) e = le;
            else if (raw != null) raw.remove();
        } catch (Throwable t) {
            plugin.getLogger().warning("Mannequin gibt es hier nicht - nehme einen Ruestungsstaender. (" + t + ")");
        }
        if (e == null) {
            Entity raw = world.spawnEntity(at, EntityType.valueOf("ARMOR_STAND"));
            e = (LivingEntity) raw;
            if (e instanceof ArmorStand stand) {
                stand.setArms(true);
                stand.setBasePlate(false);
            }
        }
        Settings s = settings();
        e.setCustomName(s.name);
        e.setCustomNameVisible(true);
        e.setPersistent(false);
        e.setInvulnerable(s.invulnerable);
        e.setRemoveWhenFarAway(false);
        e.setCanPickupItems(false);
        e.addScoreboardTag(TAG);
        body = e;
        Skin.apply(plugin, e, s.skin);
        refreshHand();
        Fx.particle(at.clone().add(0, 1, 0), () -> Particle.PORTAL, 60, 0.5);
        Fx.sound(at, () -> Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    public Location location() {
        if (body != null && body.isValid()) return body.getLocation();
        return lastLocation == null ? null : lastLocation.clone();
    }

    public World world() {
        Location l = location();
        return l == null ? null : l.getWorld();
    }

    public void moveBody(Location to) {
        if (body == null || !body.isValid()) return;
        body.teleport(to);
        lastLocation = to;
    }

    public void setWalking(boolean walking) {
        if (body != null && body.isValid()) body.setGravity(!walking);
    }

    public double walkSpeed() {
        return settings().walkSpeed;
    }

    /** Wie ein gezaehmter Wolf: neben das Ziel springen, wenn Laufen nicht geht. */
    public boolean teleportNear(Location target) {
        Location spot = standableNear(target, 3);
        if (spot == null) return false;
        Location from = location();
        if (from != null) Fx.particle(from.clone().add(0, 1, 0), () -> Particle.PORTAL, 30, 0.4);
        if (body == null || !body.isValid()) {
            spawnBody(spot);
        } else {
            body.teleport(spot);
        }
        lastLocation = spot;
        Fx.sound(spot, () -> Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f);
        return true;
    }

    /** Freier Stehplatz (Boden drunter, Platz fuer Fuesse und Kopf) nahe einer Position. */
    public static Location standableNear(Location target, int radius) {
        World w = target.getWorld();
        int bx = target.getBlockX();
        int by = target.getBlockY();
        int bz = target.getBlockZ();
        Location best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    int x = bx + dx, y = by + dy, z = bz + dz;
                    if (!Blocks.floor(Blocks.at(w, x, y - 1, z))) continue;
                    if (!Blocks.passable(Blocks.at(w, x, y, z)) || !Blocks.passable(Blocks.at(w, x, y + 1, z))) continue;
                    Block feet = Blocks.at(w, x, y, z);
                    if (feet != null && feet.isLiquid()) continue;
                    double d = dx * dx + dz * dz + dy * dy * 2 + (dx == 0 && dz == 0 ? 3 : 0);
                    if (d < bestD) {
                        bestD = d;
                        best = new Location(w, x + 0.5, y, z + 0.5, target.getYaw(), 0f);
                    }
                }
            }
        }
        if (best != null) {
            double dx = target.getX() - best.getX();
            double dz = target.getZ() - best.getZ();
            if (dx * dx + dz * dz > 0.01) best.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        return best;
    }

    // ================================================================== Ausdruck

    private long quietUntil;

    /** Eigene Meldungen fuer ein paar Ticks zurueckhalten, damit die Antwort zuerst kommt. */
    public void holdMessages(int ticksLong) {
        quietUntil = Math.max(quietUntil, ticks + ticksLong);
    }

    public void say(String message) {
        if (ticks < quietUntil) {
            sayLater(message, (int) (quietUntil - ticks));
            return;
        }
        sayNow(message);
    }

    private void sayNow(String message) {
        String line = settings().chatFormat.replace("%name%", settings().name).replace("%msg%", message)
                .replace('&', '§');
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(line);
        plugin.getLogger().info("<" + settings().name + "> " + message);
    }

    public void sayLater(String message, int delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (spawned) sayNow(message);
        }, Math.max(1, delayTicks));
    }

    public void swing() {
        if (body != null && body.isValid()) body.swingMainHand();
    }

    public void lookAt(Location target) {
        Location me = location();
        if (me == null || body == null || !body.isValid() || !me.getWorld().equals(target.getWorld())) return;
        double dx = target.getX() - me.getX();
        double dy = target.getY() - (me.getY() + 1.62);
        double dz = target.getZ() - me.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(horiz, 0.001)));
        body.setRotation(yaw, pitch);
    }

    public void lookAtBlock(Block b) {
        lookAt(b.getLocation().add(0.5, 0.5, 0.5));
    }

    public boolean cooldown(String key, int ticksLong) {
        long now = ticks;
        Long until = cooldowns.get(key);
        if (until != null && until > now) return false;
        cooldowns.put(key, now + ticksLong);
        return true;
    }

    // ================================================================== Gespraech

    public void setPartner(Player p) {
        partner = p.getUniqueId();
        partnerUntil = ticks + settings().listenSeconds * 20L;
    }

    /**
     * Hoert Claude diesem Spieler zu, auch ohne dass er ihren Namen sagt? Ja, wenn sie gerade
     * mit ihm redet, fuer ihn arbeitet, ihm folgt - oder wenn er der einzige Spieler in der Naehe ist.
     */
    public boolean isListeningTo(Player p) {
        UUID id = p.getUniqueId();
        if (partner != null && partner.equals(id) && ticks < partnerUntil) return true;
        if (modePlayer != null && modePlayer.equals(id) && mode != Mode.IDLE) return true;
        if (actor != null && actor.equals(id) && busy()) return true;
        Location me = location();
        if (me == null || !me.getWorld().equals(p.getWorld()) || me.distance(p.getLocation()) > 48) return false;
        for (Player other : me.getWorld().getPlayers()) {
            if (!other.getUniqueId().equals(id) && other.getLocation().distance(me) <= 48) return false;
        }
        return true;
    }

    public Player partner() {
        return partner == null ? null : Bukkit.getPlayer(partner);
    }

    /** Wer hat die aktuelle Aufgabe gegeben? Fuer Schutzgebiete. */
    public void setActor(Player p) {
        actor = p == null ? null : p.getUniqueId();
    }

    public Player actor() {
        Player p = actor == null ? null : Bukkit.getPlayer(actor);
        return p != null ? p : partner();
    }

    // ================================================================== Inventar

    public int count(Predicate<Material> what) {
        int n = 0;
        for (ItemStack s : inventory.getContents()) {
            if (s != null && what.test(s.getType())) n += s.getAmount();
        }
        return n;
    }

    public int count(Material m) {
        return count(x -> x == m);
    }

    /** Nimmt bis zu n Stueck heraus und liefert, wie viele es wirklich waren. */
    public int take(Predicate<Material> what, int n) {
        int left = n;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || !what.test(s.getType())) continue;
            int use = Math.min(left, s.getAmount());
            left -= use;
            if (use == s.getAmount()) inventory.setItem(i, null);
            else {
                s.setAmount(s.getAmount() - use);
                inventory.setItem(i, s);
            }
        }
        refreshHand();
        return n - left;
    }

    public int take(Material m, int n) {
        return take(x -> x == m, n);
    }

    /** Legt Items ins Inventar, was nicht passt, faellt vor die Fuesse. */
    public void give(ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0 || stack.getType().isAir()) return;
        Map<Integer, ItemStack> rest = inventory.addItem(stack);
        Location at = location();
        if (at != null) for (ItemStack r : rest.values()) at.getWorld().dropItemNaturally(at, r);
        refreshHand();
    }

    public void give(Material m, int n) {
        while (n > 0) {
            int part = Math.min(n, Math.max(1, m.getMaxStackSize()));
            give(new ItemStack(m, part));
            n -= part;
        }
    }

    /** Hebt herumliegende Items in der Naehe auf. */
    public int pickupNear(Location at, double radius) {
        int n = 0;
        for (Entity e : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (e instanceof Item item && e.isValid()) {
                ItemStack s = item.getItemStack();
                n += s.getAmount();
                give(s);
                item.remove();
            }
        }
        if (n > 0) Fx.sound(at, () -> Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.6f);
        return n;
    }

    public ItemStack bestTool(String kind) {
        ItemStack best = null;
        for (ItemStack s : inventory.getContents()) {
            if (s == null || !kind.equals(Mats.toolKind(s.getType()))) continue;
            if (best == null || Mats.toolTier(s.getType()) > Mats.toolTier(best.getType())) best = s;
        }
        return best;
    }

    public int bestTier(String kind) {
        ItemStack t = bestTool(kind);
        return t == null ? 0 : Mats.toolTier(t.getType());
    }

    private Material held;

    /** Zeigt ein Item in der Hand (nur Optik - das Item bleibt im Inventar). */
    public void hold(Material m) {
        held = m;
        refreshHand();
    }

    private void refreshHand() {
        if (body == null || !body.isValid() || body.getEquipment() == null) return;
        Material show = held != null && count(held) > 0 ? held : null;
        body.getEquipment().setItemInMainHand(show == null ? null : new ItemStack(show, 1));
    }

    /** Beste Waffe in die Hand, liefert den Schaden pro Schlag. */
    public double equipWeapon() {
        ItemStack sword = bestTool("sword");
        ItemStack axe = bestTool("axe");
        ItemStack use = sword != null ? sword : axe;
        if (use == null) {
            hold(null);
            return 1.5;
        }
        hold(use.getType());
        int tier = Mats.toolTier(use.getType());
        boolean isSword = "sword".equals(Mats.toolKind(use.getType()));
        double[] swordDamage = { 1, 4, 5, 6, 7, 8 };
        double[] axeDamage = { 1, 7, 9, 9, 9, 10 };
        return isSword ? swordDamage[tier] : axeDamage[tier] * 0.8;
    }

    // ================================================================== Abbauen und Setzen

    /**
     * Arbeitet einen Tick an einem Block. true, sobald die Arbeit fertig ist - danach ist
     * der Block weg, ausser ein Schutz-Plugin hat es verboten (das prueft der Aufrufer).
     * Dauer wie im Spiel, abhaengig vom besten Werkzeug im Inventar.
     */
    public boolean work(Block b) {
        if (b.getType().isAir() || Blocks.passable(b) && !b.getType().isSolid() && b.getType().getHardness() <= 0) {
            if (!b.getType().isAir()) breakBlock(b);
            resetWork();
            return true;
        }
        if (workBlock == null || !sameBlock(workBlock, b)) {
            resetWork();
            workBlock = b;
            String kind = Blocks.bestToolKind(b.getType());
            ItemStack tool = kind == null ? null : bestTool(kind);
            hold(tool == null ? null : tool.getType());
            workNeeded = Blocks.breakTicks(b.getType(), tool == null ? null : tool.getType());
            workDone = 0;
        }
        lookAtBlock(b);
        workDone++;
        if (workDone % 5 == 1) {
            swing();
            Fx.hitSound(b);
        }
        showCrack(b, (float) workDone / workNeeded);
        if (workDone < workNeeded) return false;
        showCrack(b, 0f);
        breakBlock(b);
        workBlock = null;
        return true;
    }

    /** Erster feste Block zwischen Claudes Augen und dem Ziel (null = freie Sicht). */
    public Block obstacleTo(Block target) {
        Location eye = location().add(0, 1.6, 0);
        double tx = target.getX() + 0.5, ty = target.getY() + 0.5, tz = target.getZ() + 0.5;
        double dx = tx - eye.getX(), dy = ty - eye.getY(), dz = tz - eye.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(len / 0.25);
        for (int i = 1; i < steps; i++) {
            double f = (double) i / steps;
            Block b = Blocks.at(eye.getWorld(), (int) Math.floor(eye.getX() + dx * f), (int) Math.floor(eye.getY() + dy * f),
                    (int) Math.floor(eye.getZ() + dz * f));
            if (b == null) return null;
            if (sameBlock(b, target)) return null;
            if (!Blocks.passable(b) && !b.isLiquid()) return b;
        }
        return null;
    }

    public void resetWork() {
        if (workBlock != null) showCrack(workBlock, 0f);
        workBlock = null;
        workDone = 0;
    }

    private static boolean sameBlock(Block a, Block b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ() && a.getWorld().equals(b.getWorld());
    }

    private void showCrack(Block b, float progress) {
        if (!crackWorks) return;
        try {
            Location at = b.getLocation();
            int id = body == null ? 0 : body.getUniqueId().hashCode();
            for (Player p : b.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(at) < 1024) p.sendBlockDamage(at, Math.min(1f, progress), id);
            }
        } catch (Throwable t) {
            crackWorks = false;
        }
    }

    /** Duerfte der Auftraggeber diesen Block abbauen? Fragt Schutz-Plugins (WorldGuard, Claims ...). */
    public boolean mayBreak(Block b) {
        if (!settings().respectProtection) return true;
        Player p = actor();
        if (p == null) return true;
        try {
            BlockBreakEvent event = new BlockBreakEvent(b, p);
            Bukkit.getPluginManager().callEvent(event);
            return !event.isCancelled();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Baut sofort ab; die Drops landen im Inventar. */
    public boolean breakBlock(Block b) {
        if (b.getType().isAir()) return true;
        if (!mayBreak(b)) return false;
        String kind = Blocks.bestToolKind(b.getType());
        ItemStack tool = kind == null ? null : bestTool(kind);
        Collection<ItemStack> drops = b.getDrops(tool);
        BlockData data = b.getBlockData();
        b.setType(Material.AIR);
        Fx.breakEffect(b, data);
        swing();
        for (ItemStack d : drops) give(d);
        return true;
    }

    /** Setzt einen Block mit Geraeusch und Armschwung. */
    public void placeBlock(Block b, BlockData data) {
        b.setBlockData(data, false);
        Fx.placeSound(b, data);
        if (random.nextInt(3) == 0) swing();
    }

    // ================================================================== Aufgaben

    public Task currentTask() {
        return current;
    }

    public List<Task> queuedTasks() {
        return new ArrayList<>(queue);
    }

    public int newPlan() {
        return ++planCounter;
    }

    /** Neuen Plan starten. replace = alles Bisherige abbrechen. */
    public void run(List<Task> tasks, boolean replace) {
        if (replace) stopAll();
        int id = newPlan();
        for (Task t : tasks) {
            t.plan = id;
            queue.add(t);
        }
    }

    public void run(Task task, boolean replace) {
        run(List.of(task), replace);
    }

    /** Sofort etwas anderes tun, danach mit der alten Aufgabe weitermachen. */
    public void interrupt(Task task) {
        if (current != null) {
            current.stop(this);
            queue.addFirst(current);
        }
        mover.stop();
        current = task;
        task.start(this);
    }

    public void stopAll() {
        if (current != null) current.stop(this);
        current = null;
        queue.clear();
        mover.stop();
        resetWork();
        hold(null);
    }

    public boolean busy() {
        return current != null || !queue.isEmpty();
    }

    // ================================================================== Grundhaltung

    public Mode mode() {
        return mode;
    }

    public Player modePlayer() {
        return modePlayer == null ? null : Bukkit.getPlayer(modePlayer);
    }

    public Location modeLocation() {
        return modeLocation;
    }

    public void setMode(Mode mode, Player player, Location where) {
        this.mode = mode;
        this.modePlayer = player == null ? null : player.getUniqueId();
        this.modeLocation = where == null ? null : where.clone();
        if (mode == Mode.STAY || mode == Mode.IDLE) mover.stop();
    }

    // ================================================================== Takt

    public void tick() {
        ticks++;
        if (!spawned) return;
        if (body == null || !body.isValid()) {
            if (ticks % 20 == 0) recoverBody();
            return;
        }
        lastLocation = body.getLocation();
        if (ticks % 20 == 0) body.setFireTicks(0);

        if (current == null && !queue.isEmpty()) {
            current = queue.poll();
            current.start(this);
        }
        if (current != null) {
            Task.Status status;
            try {
                status = current.tick(this);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Aufgabe " + current.label() + " ist abgestuerzt: " + ex);
                status = Task.Status.FAILED;
            }
            if (status != Task.Status.RUNNING) finishCurrent(status);
        } else {
            behave();
        }
        mover.tick();

        if (ticks % 10 == 0) guardScan();
        if (ticks % 100 == 0) care();
    }

    private void finishCurrent(Task.Status status) {
        Task done = current;
        current = null;
        done.stop(this);
        mover.stop();
        resetWork();
        if (status == Task.Status.FAILED) {
            String why = done.error();
            if (why != null && !why.isBlank()) say(why);
            if (done.plan > 0) queue.removeIf(t -> t.plan == done.plan);
        }
        if (queue.isEmpty()) hold(null);
    }

    private void recoverBody() {
        Player p = modePlayer();
        if ((mode == Mode.FOLLOW || mode == Mode.GUARD) && p != null && p.isOnline()) {
            Location spot = standableNear(p.getLocation(), 3);
            if (spot != null) spawnBody(spot);
            return;
        }
        if (lastLocation != null && Blocks.loaded(lastLocation.getWorld(), lastLocation.getBlockX(), lastLocation.getBlockZ())) {
            spawnBody(lastLocation);
        }
    }

    /** Was tun, wenn keine Aufgabe ansteht. */
    private void behave() {
        Location me = location();
        switch (mode) {
            case FOLLOW, GUARD -> {
                Player p = modePlayer();
                if (p == null || !p.isOnline() || p.isDead()) {
                    if (ticks % 40 == 0) lookAround(me);
                    return;
                }
                Location pl = p.getLocation();
                boolean sameWorld = pl.getWorld().equals(me.getWorld());
                double d = sameWorld ? pl.distance(me) : Double.MAX_VALUE;
                if (d > 4.0) {
                    mover.go(pl, 2.6, false, true);
                } else if (mover.moving() && d < 2.8) {
                    mover.stop();
                }
                if (!mover.moving() && ticks % 3 == 0) lookAt(p.getEyeLocation());
            }
            case STAY -> {
                if (modeLocation != null && me.distance(modeLocation) > 3 && me.getWorld().equals(modeLocation.getWorld())) {
                    mover.go(modeLocation, 1.2, false, true);
                } else if (ticks % 5 == 0) {
                    lookAround(me);
                }
            }
            default -> {
                if (ticks % 5 == 0) lookAround(me);
            }
        }
        if (settings().idleTalk && random.nextInt(9000) == 0) idleChatter();
    }

    private void lookAround(Location me) {
        Player nearest = null;
        double best = 100;
        for (Player p : me.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(me);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest != null) lookAt(nearest.getEyeLocation());
    }

    private void idleChatter() {
        Player p = partner();
        if (p == null || !p.isOnline() || p.getLocation().getWorld() != world()) return;
        say(Lexicon.pick(Lexicon.IDLE_LINES, random));
    }

    // ================================================================== Beschuetzen

    /** Wer steht unter Claudes Schutz? */
    private Location protectCenter() {
        if (mode == Mode.GUARD) {
            Player p = modePlayer();
            if (p != null && p.isOnline()) return p.getLocation();
            return modeLocation;
        }
        return null;
    }

    private void guardScan() {
        if (current instanceof CombatTask) return;
        Location center = protectCenter();
        if (center == null) return;
        LivingEntity threat = CombatTask.nearestHostile(center, 12, this);
        if (threat == null) return;
        if (cooldown("guard-say", 400)) say(Lexicon.pick(Lexicon.GUARD_LINES, random));
        interrupt(new CombatTask(threat, 1, null));
    }

    /** Jemand hat einen Spieler verletzt, den Claude beschuetzt (oder Claude selbst). */
    public void onAttack(Player victim, LivingEntity attacker) {
        if (attacker == null || isBody(attacker) || !attacker.isValid()) return;
        boolean guarding = (mode == Mode.GUARD || mode == Mode.FOLLOW) && modePlayer != null
                && modePlayer.equals(victim.getUniqueId());
        if (!guarding) return;
        if (attacker instanceof Player ap) {
            if (!settings().defendAgainstPlayers || ap.equals(victim)) return;
            if (cooldown("defend-player-say", 200)) say(Lexicon.pick(Lexicon.DEFEND_PLAYER_LINES, random).replace("%p%", ap.getName()));
        } else if (cooldown("defend-say", 200)) {
            say(Lexicon.pick(Lexicon.DEFEND_LINES, random).replace("%p%", victim.getName()));
        }
        if (current instanceof CombatTask ct && ct.target() == attacker) return;
        interrupt(new CombatTask(attacker, 1, null));
    }

    public void onHurt(Entity damager) {
        if (damager instanceof Player p) {
            if (cooldown("hurt", 100)) say(Lexicon.pick(Lexicon.HURT_LINES, random).replace("%p%", p.getName()));
        } else if (damager instanceof LivingEntity le && !(current instanceof CombatTask)) {
            interrupt(new CombatTask(le, 1, null));
        }
    }

    /** Kuemmert sich um den Gespraechspartner: warnt bei wenig Leben, bietet Essen an, Fackeln bei Nacht. */
    private void care() {
        Player p = partner();
        Location me = location();
        if (p == null || !p.isOnline() || me == null || !p.getWorld().equals(me.getWorld())) return;
        if (p.getLocation().distance(me) > 24) return;
        if (p.getHealth() <= 6 && !p.isDead() && cooldown("warn-health", 1200)) {
            say(Lexicon.pick(Lexicon.LOW_HEALTH_LINES, random).replace("%p%", p.getName()));
        }
        if (p.getFoodLevel() <= 10 && !busy() && cooldown("offer-food", 2400)) {
            if (count(Mats::isFood) > 0) {
                say("Du siehst hungrig aus, " + p.getName() + ". Hier, iss was!");
                run(new GiveTask(p.getUniqueId(), Mats::isFood, 8, null), false);
            }
        }
        long time = me.getWorld().getTime();
        boolean night = time > 13000 && time < 23000;
        if (!night) nightAsked = false;
        if (night && !nightAsked && !busy() && mode != Mode.STAY) {
            nightAsked = true;
            if (settings().idleTalk) {
                plugin.brain().ask(p, "Es wird dunkel. Soll ich hier Fackeln aufstellen?",
                        () -> run(List.of(new TorchTask(14)), true), null);
            }
        }
    }

    // ================================================================== Speichern

    public Location lastLocation() {
        return lastLocation;
    }

    public void restore(Location at, Mode mode, UUID player, List<ItemStack> items) {
        inventory.clear();
        for (ItemStack s : items) if (s != null) inventory.addItem(s);
        this.mode = mode;
        this.modePlayer = player;
        spawn(at);
    }

    public UUID modePlayerId() {
        return modePlayer;
    }
}
