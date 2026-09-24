package de.glowcube.claudeai.move;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;

import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Blocks;
import de.glowcube.claudeai.world.Fx;
import de.glowcube.claudeai.world.Mats;

/**
 * Laesst den Koerper einen Weg ablaufen: Schritt fuer Schritt per Teleport, mit Stufen,
 * Fallen, Schwimmen, Tueren auf- und zumachen und Graben.
 */
public final class Mover {

    public enum Status { IDLE, MOVING, ARRIVED, FAILED }

    private final Npc npc;
    private Location target;
    private double reach;
    private boolean dig;
    private boolean teleport;

    private List<PathFinder.Node> path;
    private int index;
    private Location pathFor;
    private int repaths;
    private int stuck;
    private Location lastPos;
    private Status status = Status.IDLE;
    /** Wegsuche hoechstens alle paar Ticks - ein unerreichbares Ziel darf den Server nicht bremsen. */
    private long lastSearch = -100;
    private Block openDoor;
    private int doorTimer;

    public Mover(Npc npc) {
        this.npc = npc;
    }

    public Status status() {
        return status;
    }

    public boolean moving() {
        return status == Status.MOVING;
    }

    /**
     * Neues Ziel. Wird dasselbe Ziel (z.B. ein laufender Spieler) jeden Tick erneut gesetzt,
     * rechnet der Weg nur neu, wenn es sich merklich bewegt hat.
     */
    public void go(Location to, double reach, boolean dig, boolean teleport) {
        boolean sameParams = target != null && to.getWorld().equals(target.getWorld())
                && this.dig == dig && Math.abs(this.reach - reach) < 0.01;
        if (sameParams) {
            boolean moved = target.distanceSquared(to) > 0.25;
            // Ergebnis (angekommen / gescheitert) bleibt stehen, solange das Ziel dasselbe ist
            if (!moved && (status == Status.ARRIVED || status == Status.FAILED)) return;
            if (status == Status.MOVING) {
                this.target = to.clone();
                this.teleport = teleport;
                return;
            }
        }
        this.target = to.clone();
        this.reach = reach;
        this.dig = dig;
        this.teleport = teleport;
        this.path = null;
        this.repaths = 0;
        this.stuck = 0;
        this.status = Status.MOVING;
    }

    public void stop() {
        status = Status.IDLE;
        path = null;
        target = null;
        npc.resetWork();
        npc.setWalking(false);
    }

    public Status tick() {
        closeDoorLater();
        if (status != Status.MOVING || target == null) return status;
        Location cur = npc.location();
        if (cur == null) return status = Status.FAILED;
        World world = cur.getWorld();

        if (!world.equals(target.getWorld())) {
            if (teleport && npc.teleportNear(target)) return finish(Status.ARRIVED);
            return finish(Status.FAILED);
        }
        if (reachedNow(cur)) return finish(Status.ARRIVED);

        double far = cur.distance(target);
        if (far > 56 && teleport) {
            npc.teleportNear(target);
            return finish(reachedNow(npc.location()) ? Status.ARRIVED : Status.MOVING);
        }

        if (path == null || pathFor == null || pathFor.distanceSquared(target) > 4 && far > 3) {
            if (npc.ticks() - lastSearch < 8 && path == null) return status;
            lastSearch = npc.ticks();
            if (!computePath(cur)) {
                if (teleport && npc.teleportNear(target)) return finish(Status.ARRIVED);
                return finish(Status.FAILED);
            }
        }
        if (index >= path.size()) {
            // Teilweg zu Ende gelaufen, Ziel noch nicht erreicht: neu rechnen
            if (++repaths > 6) {
                if (teleport && npc.teleportNear(target)) return finish(Status.ARRIVED);
                return finish(Status.FAILED);
            }
            path = null;
            return status;
        }

        PathFinder.Node next = path.get(index);
        // Graben, wenn der Weg durch Bloecke fuehrt
        if (next.dig() || !bodyClear(world, next)) {
            for (int dy = 0; dy <= 1; dy++) {
                Block b = Blocks.at(world, next.x(), next.y() + dy, next.z());
                if (b != null && !Blocks.passable(b)) {
                    if (!dig || !Blocks.diggable(b)) {
                        path = null;
                        return status;
                    }
                    npc.lookAtBlock(b);
                    if (!npc.work(b)) return status;
                    if (!Blocks.passable(b)) return finish(Status.FAILED); // geschuetzt
                }
            }
            // Beim Hochsteigen muss auch ueber dem aktuellen Kopf Platz sein
            if (next.y() > Math.floor(cur.getY() + 0.01)) {
                Block above = Blocks.at(world, cur.getBlockX(), cur.getBlockY() + 2, cur.getBlockZ());
                if (above != null && !Blocks.passable(above) && dig && Blocks.diggable(above)) {
                    if (!npc.work(above)) return status;
                    if (!Blocks.passable(above)) return finish(Status.FAILED);
                }
            }
        }
        openDoorAt(world, next);

        step(cur, next);
        Location now = npc.location();
        if (lastPos != null && lastPos.distanceSquared(now) < 0.0004) {
            if (++stuck > 30) {
                stuck = 0;
                path = null;
                if (++repaths > 6) {
                    if (teleport && npc.teleportNear(target)) return finish(Status.ARRIVED);
                    return finish(Status.FAILED);
                }
            }
        } else {
            stuck = 0;
        }
        lastPos = now;
        return status;
    }

    private boolean reachedNow(Location cur) {
        double dx = cur.getX() - target.getX();
        double dy = cur.getY() + 1.0 - target.getY();
        double dz = cur.getZ() - target.getZ();
        double r = Math.max(reach, 0.6);
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    private Status finish(Status s) {
        status = s;
        path = null;
        npc.resetWork();
        npc.setWalking(false);
        return s;
    }

    private boolean computePath(Location cur) {
        int maxNodes = dig ? 6000 : 4000;
        PathFinder finder = new PathFinder(cur.getWorld(), dig, maxNodes);
        int sx = cur.getBlockX();
        int sy = (int) Math.floor(cur.getY() + 0.05);
        int sz = cur.getBlockZ();
        List<PathFinder.Node> found = finder.find(sx, sy, sz,
                PathFinder.near(target.getX(), target.getY(), target.getZ(), Math.max(reach, 0.8)));
        pathFor = target.clone();
        if (found == null) return false;
        path = found;
        index = 0;
        npc.setWalking(true);
        return true;
    }

    private boolean bodyClear(World world, PathFinder.Node n) {
        return Blocks.passable(Blocks.at(world, n.x(), n.y(), n.z())) && Blocks.passable(Blocks.at(world, n.x(), n.y() + 1, n.z()));
    }

    /** Ein Tick Bewegung Richtung naechster Knoten. */
    private void step(Location cur, PathFinder.Node next) {
        double tx = next.x() + 0.5;
        double ty = next.y();
        double tz = next.z() + 0.5;
        double dx = tx - cur.getX();
        double dy = ty - cur.getY();
        double dz = tz - cur.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        boolean inWater = cur.getBlock() != null && Blocks.isWater(cur.getBlock().getType());
        double speed = inWater ? 0.12 : npc.walkSpeed() * (path.size() - index > 8 ? 1.25 : 1.0);
        double nx = cur.getX();
        double ny = cur.getY();
        double nz = cur.getZ();

        if (dy > 0.01) {
            // hochsteigen: erst anheben, dabei leicht vorwaerts
            ny += Math.min(dy, 0.35);
            double h = Math.min(horiz, speed * 0.5);
            if (horiz > 0) { nx += dx / horiz * h; nz += dz / horiz * h; }
        } else if (dy < -0.01 && horiz > 0.3) {
            // erst an die Kante, dann fallen
            double h = Math.min(horiz, speed);
            nx += dx / horiz * h;
            nz += dz / horiz * h;
        } else if (dy < -0.01) {
            ny += Math.max(dy, -0.5);
            double h = Math.min(horiz, speed * 0.3);
            if (horiz > 0) { nx += dx / horiz * h; nz += dz / horiz * h; }
        } else {
            double h = Math.min(horiz, speed);
            if (horiz > 0) { nx += dx / horiz * h; nz += dz / horiz * h; }
            ny = ty;
        }

        float yaw = horiz > 0.05 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : cur.getYaw();
        npc.moveBody(new Location(cur.getWorld(), nx, ny, nz, yaw, 0f));

        if (Math.abs(nx - tx) < 0.05 && Math.abs(ny - ty) < 0.05 && Math.abs(nz - tz) < 0.05) index++;
    }

    private void openDoorAt(World world, PathFinder.Node n) {
        for (int dy = 0; dy <= 1; dy++) {
            Block b = Blocks.at(world, n.x(), n.y() + dy, n.z());
            if (b == null || !Mats.isWoodenDoorLike(b.getType())) continue;
            BlockData data = b.getBlockData();
            if (data instanceof Openable o && !o.isOpen()) {
                o.setOpen(true);
                b.setBlockData(o);
                Fx.sound(b.getLocation(), () -> Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f);
                npc.swing();
                openDoor = b;
                doorTimer = 30;
            }
            return;
        }
    }

    /** Tuer hinter sich wieder zumachen - wie ein gut erzogener Spieler. */
    private void closeDoorLater() {
        if (openDoor == null || --doorTimer > 0) return;
        Location me = npc.location();
        if (me != null && me.getWorld().equals(openDoor.getWorld())
                && me.distanceSquared(openDoor.getLocation().add(0.5, 0, 0.5)) < 2.5) {
            doorTimer = 10;
            return;
        }
        BlockData data = openDoor.getBlockData();
        if (data instanceof Openable o && o.isOpen()) {
            o.setOpen(false);
            openDoor.setBlockData(o);
            Fx.sound(openDoor.getLocation(), () -> Sound.BLOCK_WOODEN_DOOR_CLOSE, 1f, 1f);
        }
        openDoor = null;
    }
}
