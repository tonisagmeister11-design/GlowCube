package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Greift Ziele in Reichweite an.
 *
 * Diese Fassung ist der KillAura von Meteor Client (GPL-3.0) nachgebildet:
 * dieselbe Reihenfolge der Abbruchpruefungen, dieselbe Zielauswahl mit
 * Sortierung und Obergrenze, dieselbe Trennung von Reichweite im Freien und
 * hinter Waenden, dasselbe Waffenwechseln mit Zuruecklegen. Uebersetzt wurde
 * nur das Geruest - Meteors Setting-Builder wurden zu unseren Setting-Klassen,
 * sein Event-Bus zu onTick. Die Entscheidungen sind seine.
 *
 * Quelle: meteordevelopment/meteorclient/systems/modules/combat/KillAura.java
 */
public final class KillAura extends Module {
    /**
     * Damit andere Module fragen koennen, worauf gerade geschlagen wird -
     * Criticals braucht das fuer die Einstellung "Nur mit KillAura".
     */
    private static KillAura instanz;


    // ---------------------------------------------------------- Zielauswahl

    private final ModeSetting priority = register(new ModeSetting("Priority",
            "Wonach das erste Ziel gewaehlt wird",
            "Blickwinkel", "Blickwinkel", "Naehe", "Wenig Leben", "Viel Leben"));
    private final NumberSetting maxTargets = register(new NumberSetting("MaxTargets",
            "Wie viele gleichzeitig getroffen werden", 1, 1, 10, 1));
    private final NumberSetting range = register(new NumberSetting("Range",
            "Reichweite im Freien", 4.0, 1.0, 6.0, 0.1));
    private final NumberSetting wallsRange = register(new NumberSetting("WallsRange",
            "Reichweite durch Waende - 0 heisst gar nicht", 0.0, 0.0, 6.0, 0.1));

    private final BooleanSetting players = register(new BooleanSetting("Players", "Spieler", true));
    private final BooleanSetting hostile = register(new BooleanSetting("Hostile", "Monster", true));
    private final BooleanSetting passive = register(new BooleanSetting("Passive", "Tiere", false));
    private final BooleanSetting ignoreNamed = register(new BooleanSetting("IgnoreNamed",
            "Benannte in Ruhe lassen", false));
    private final BooleanSetting ignoreTamed = register(new BooleanSetting("IgnoreTamed",
            "Gezaehmte in Ruhe lassen", true));

    // ------------------------------------------------------------- Verhalten

    private final BooleanSetting onlyOnClick = register(new BooleanSetting("OnlyOnClick",
            "Nur solange die Angriffstaste gehalten wird", false));
    private final BooleanSetting onlyOnLook = register(new BooleanSetting("OnlyOnLook",
            "Nur, was unter dem Fadenkreuz liegt", false));
    private final BooleanSetting pauseOnUse = register(new BooleanSetting("PauseOnUse",
            "Aussetzen beim Abbauen und Benutzen", true));
    private final BooleanSetting autoSwitch = register(new BooleanSetting("AutoSwitch",
            "Auf eine Waffe wechseln", false));
    private final BooleanSetting swapBack = register(new BooleanSetting("SwapBack",
            "Danach zurueck auf das vorherige Feld", true));
    private final BooleanSetting waitCooldown = register(new BooleanSetting("Cooldown",
            "Auf die Waffenaufladung warten", true));
    private final NumberSetting hitDelay = register(new NumberSetting("HitDelay",
            "Ticks zwischen zwei Schlaegen", 0, 0, 20, 1));

    private final List<LivingEntity> ziele = new ArrayList<>();
    private int vorherigesFeld = -1;
    private boolean gewechselt;
    private int wartet;

    public KillAura() {
        instanz = this;
        super("KillAura", "Greift Ziele in Reichweite an", Category.COMBAT,
                com.mojang.blaze3d.platform.InputConstants.KEY_R);
    }

    @Override
    public void onDisable() {
        aufhoeren();
    }

    @Override
    public void onTick() {
        // Reihenfolge der Abbruchpruefungen wie bei Meteor: erst der Zustand
        // des Spielers, dann die Einschraenkungen, dann die Ziele.
        if (!player().isAlive()) {
            aufhoeren();
            return;
        }
        if (pauseOnUse.get() && player().isUsingItem()) {
            aufhoeren();
            return;
        }
        if (onlyOnClick.get() && !mc.options.keyAttack.isDown()) {
            aufhoeren();
            return;
        }

        ziele.clear();
        if (onlyOnLook.get()) {
            Entity unterFadenkreuz = mc.hitResult instanceof EntityHitResult treffer
                    ? treffer.getEntity() : null;
            if (unterFadenkreuz instanceof LivingEntity lebend && taugt(lebend)) {
                ziele.add(lebend);
            }
        } else {
            sammeln();
        }

        if (ziele.isEmpty()) {
            aufhoeren();
            return;
        }

        if (wartet > 0) {
            wartet--;
            return;
        }
        if (waitCooldown.get() && player().getAttackStrengthScale(0.0f) < 1.0f) {
            return;
        }

        if (autoSwitch.get()) {
            waffeWaehlen();
        }

        for (LivingEntity ziel : ziele) {
            mc.gameMode.attack(player(), ziel);
            player().swing(InteractionHand.MAIN_HAND);
        }
        wartet = hitDelay.getInt();
    }

    /** Alles in Reichweite einsammeln, sortieren und auf MaxTargets kuerzen. */
    private void sammeln() {
        double weite = Math.max(range.get(), wallsRange.get());
        AABB feld = player().getBoundingBox().inflate(weite);

        List<Entity> gefunden = level().getEntities(player(), feld,
                e -> e instanceof LivingEntity lebend && taugt(lebend));

        gefunden.sort(reihenfolge());
        for (Entity e : gefunden) {
            if (ziele.size() >= maxTargets.getInt()) {
                break;
            }
            ziele.add((LivingEntity) e);
        }
    }

    private Comparator<Entity> reihenfolge() {
        if (priority.is("Naehe")) {
            return Comparator.comparingDouble(e -> e.distanceToSqr(player()));
        }
        if (priority.is("Wenig Leben")) {
            return Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth());
        }
        if (priority.is("Viel Leben")) {
            return Comparator.comparingDouble(e -> -((LivingEntity) e).getHealth());
        }
        // Blickwinkel: was am naechsten an der Blickrichtung liegt, zuerst.
        return Comparator.comparingDouble(this::winkelAbstand);
    }

    /** Wie weit ein Ziel von der Blickrichtung abweicht, in Grad. */
    private double winkelAbstand(Entity ziel) {
        Vec3 hin = ziel.getBoundingBox().getCenter().subtract(
                player().getEyePosition(1.0f).x,
                player().getEyePosition(1.0f).y,
                player().getEyePosition(1.0f).z);
        Vec3 blick = player().getViewVector(1.0f);
        double laenge = Math.sqrt(hin.x * hin.x + hin.y * hin.y + hin.z * hin.z);
        if (laenge == 0.0) {
            return 0.0;
        }
        double punkt = (hin.x * blick.x + hin.y * blick.y + hin.z * blick.z) / laenge;
        return Math.toDegrees(Math.acos(Mth.clamp(punkt, -1.0, 1.0)));
    }

    /**
     * Taugt dieses Wesen als Ziel?
     *
     * Die getrennte Reichweite hinter Waenden stammt von Meteor: im Freien
     * darf weiter zugeschlagen werden als durch Bloecke hindurch, und wer
     * WallsRange auf 0 laesst, trifft gar nicht durch Waende.
     */
    private boolean taugt(LivingEntity wesen) {
        if (wesen == player() || !wesen.isAlive() || wesen.isInvulnerable()) {
            return false;
        }
        if (ignoreNamed.get() && wesen.hasCustomName()) {
            return false;
        }
        if (ignoreTamed.get() && wesen instanceof TamableAnimal zahm && zahm.isTame()) {
            return false;
        }

        boolean erlaubt;
        if (wesen instanceof Player) {
            erlaubt = players.get();
        } else if (wesen instanceof Monster) {
            erlaubt = hostile.get();
        } else if (wesen instanceof Animal) {
            erlaubt = passive.get();
        } else {
            erlaubt = false;
        }
        if (!erlaubt) {
            return false;
        }

        double abstand = wesen.distanceTo(player());
        boolean frei = player().hasLineOfSight(wesen);
        return frei ? abstand <= range.get() : abstand <= wallsRange.get();
    }

    /**
     * Auf eine Waffe wechseln und das vorherige Feld merken.
     *
     * Erkannt wird ueber Item-Tags, nicht ueber Klassen - so macht es Meteor
     * auch. Seit 1.21 sind Werkzeuge datengesteuert, eine Klasse SwordItem
     * gibt es gar nicht mehr; was ein Schwert ist, entscheidet das Tag.
     */
    private void waffeWaehlen() {
        int beste = -1;
        for (int feld = 0; feld < 9; feld++) {
            ItemStack stack = player().getInventory().getItem(feld);
            if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
                beste = feld;
                break;
            }
        }
        if (beste < 0) {
            return;
        }
        if (!gewechselt) {
            vorherigesFeld = player().getInventory().getSelectedSlot();
            gewechselt = true;
        }
        player().getInventory().setSelectedSlot(beste);
    }

    /** Aufhoeren heisst auch: die Waffe zuruecklegen. */
    private void aufhoeren() {
        ziele.clear();
        if (gewechselt && swapBack.get() && vorherigesFeld >= 0 && inGame()) {
            player().getInventory().setSelectedSlot(vorherigesFeld);
        }
        gewechselt = false;
        vorherigesFeld = -1;
    }

    @Override
    public String hudSuffix() {
        return ziele.isEmpty() ? range.display() : ziele.size() + " Ziel(e)";
    }

    /** Das erste Ziel dieses Ticks, oder null. */
    public static LivingEntity aktuellesZiel() {
        if (instanz == null || !instanz.isEnabled() || instanz.ziele.isEmpty()) {
            return null;
        }
        return instanz.ziele.get(0);
    }

}
