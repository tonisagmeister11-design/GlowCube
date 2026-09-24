package de.glowcube.claudeai.task;

import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import de.glowcube.claudeai.brain.Lexicon;
import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Fx;

/**
 * Kaempfen: ein bestimmtes Ziel, oder "count" Stueck einer Sorte (z.B. 3 Zombies),
 * oder so lange jagen, bis genug von einem Item da ist (Leder, Fleisch, Wolle ...).
 */
public final class CombatTask extends Task {

    private static final Set<String> EXTRA_HOSTILE = Set.of(
            "SLIME", "MAGMA_CUBE", "GHAST", "PHANTOM", "SHULKER", "HOGLIN", "ZOGLIN", "ENDER_DRAGON", "BREEZE", "CREAKING");

    private LivingEntity target;
    private final int count;
    private final Predicate<Entity> filter;
    private final String what;
    private Predicate<Material> untilItem;
    private int untilCount;
    private int baseline;

    private int killed;
    private int cooldown;
    private int ticks;
    private double damage;
    private Location lootAt;
    private int lootTimer;

    /** Ein festes Ziel (filter == null) oder bis zu count Ziele, die filter erfuellen. */
    public CombatTask(LivingEntity target, int count, Predicate<Entity> filter) {
        this(target, count, filter, null);
    }

    public CombatTask(LivingEntity target, int count, Predicate<Entity> filter, String what) {
        this.target = target;
        this.count = Math.max(1, count);
        this.filter = filter;
        this.what = what;
    }

    /** Jagen, bis so viele von einem Item im Inventar sind. */
    public CombatTask until(Predicate<Material> item, int wanted) {
        this.untilItem = item;
        this.untilCount = wanted;
        return this;
    }

    public LivingEntity target() {
        return target;
    }

    public static boolean isHostile(Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof Player) return false;
        return e instanceof Monster || EXTRA_HOSTILE.contains(e.getType().name());
    }

    public static LivingEntity nearestHostile(Location center, double radius, Npc npc) {
        LivingEntity best = null;
        double bestD = radius * radius;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius / 2, radius)) {
            if (!isHostile(e) || npc.isBody(e) || !e.isValid() || e.isDead()) continue;
            double d = e.getLocation().distanceSquared(center);
            if (d < bestD) {
                bestD = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    @Override
    public String label() {
        String name = target != null ? Lexicon.entityName(target) : what != null ? what : "Monster";
        if (untilItem != null) return "jage " + name;
        return "kaempfe gegen " + name + (count > 1 ? " (" + killed + "/" + count + ")" : "");
    }

    @Override
    public void start(Npc npc) {
        damage = npc.equipWeapon();
        ticks = 0;
        cooldown = 0;
        if (untilItem != null && baseline == 0) baseline = npc.count(untilItem);
    }

    @Override
    public Status tick(Npc npc) {
        ticks++;
        if (lootTimer > 0 && --lootTimer == 0 && lootAt != null) npc.pickupNear(lootAt, 4.5);
        if (untilItem != null && npc.count(untilItem) - baseline >= untilCount && lootTimer == 0) return Status.DONE;
        if (ticks > 20 * 120) return killed > 0 ? Status.DONE : fail("Das dauert zu lange, ich geb auf.");

        if (target == null || !target.isValid() || target.isDead()) {
            if (target != null && (target.isDead() || !target.isValid())) {
                killed++;
                lootAt = target.getLocation();
                lootTimer = 12;
                target = null;
            }
            boolean more = untilItem != null || filter != null && killed < count;
            if (!more) return lootTimer > 0 ? Status.RUNNING : Status.DONE;
            if (lootTimer > 0) return Status.RUNNING;
            target = findNext(npc);
            if (target == null) {
                if (killed > 0) return Status.DONE;
                return fail("Ich sehe hier " + (what != null ? "keine " + what : "nichts zum Bekaempfen") + ".");
            }
        }

        Location me = npc.location();
        Location tl = target.getLocation();
        if (!me.getWorld().equals(tl.getWorld()) || me.distance(tl) > 48) {
            target = null;
            return filter == null && untilItem == null ? Status.DONE : Status.RUNNING;
        }
        double d = me.distance(tl);
        if (d > 2.4) {
            npc.mover().go(tl, 1.8, false, false);
        } else if (npc.mover().moving()) {
            npc.mover().stop();
        }
        npc.lookAt(tl.clone().add(0, target.getHeight() * 0.7, 0));
        if (--cooldown <= 0 && d <= 3.3) {
            npc.swing();
            target.damage(damage, npc.body());
            Vector push = tl.toVector().subtract(me.toVector());
            if (push.length() > 0.01) {
                push = push.normalize().multiply(0.35).setY(0.25);
                target.setVelocity(push);
            }
            Fx.sound(tl, () -> Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1f);
            cooldown = 12;
        }
        return Status.RUNNING;
    }

    private LivingEntity findNext(Npc npc) {
        if (filter == null) return null;
        Location me = npc.location();
        LivingEntity best = null;
        double bestD = 32 * 32;
        for (Entity e : me.getWorld().getNearbyEntities(me, 32, 16, 32)) {
            if (!(e instanceof LivingEntity le) || npc.isBody(e) || !e.isValid() || e.isDead() || !filter.test(e)) continue;
            double dd = e.getLocation().distanceSquared(me);
            if (dd < bestD) {
                bestD = dd;
                best = le;
            }
        }
        return best;
    }

    @Override
    public void stop(Npc npc) {
        npc.mover().stop();
    }
}
