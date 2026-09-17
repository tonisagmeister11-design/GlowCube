package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), {@code DamageUtils}.
 *
 * <p>Ausgerechnet wird, was ein Treffer nach Ruestung und Effekten wirklich
 * abzieht. AutoTotem haengt daran: "haelt mich der naechste Kristall noch
 * aus?" ist eine Rechnung, keine Schaetzung.
 *
 * <p>Uebernommen sind die Explosionsformel samt Sichtbarkeitsanteil (das
 * Raster von Probepunkten durch die Hitbox, genau wie in der Vanilla-
 * Explosion), die Fallschadenrechnung mit Sprungkraft- und
 * Schwebe-Ausnahmen, und die Abzugskette aus Schwierigkeitsgrad, Ruestung
 * und Resistenz.
 *
 * <p><b>Nicht uebernommen:</b> der Abzug durch Schutz-Verzauberungen. Meteor
 * liest die Verzauberungsstufen ueber eigene Hilfsklassen aus; das haengt an
 * einem Registry-Zugriff, den GlowCube nicht mitschleppt. Die Folge ist,
 * dass der ausgerechnete Schaden mit guter Ruestung zu hoch liegt - AutoTotem
 * greift dadurch eher zu frueh als zu spaet zum Totem. Das ist die
 * ungefaehrlichere Richtung.
 */
public final class DamageUtils {
    private DamageUtils() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ------------------------------------------------------------ Explosion

    public static float kristallSchaden(LivingEntity ziel, Vec3 ort) {
        return explosionsSchaden(ziel, ort, 12.0f);
    }

    public static float bettSchaden(LivingEntity ziel, Vec3 ort) {
        return explosionsSchaden(ziel, ort, 10.0f);
    }

    /**
     * Die Vanilla-Formel: aus Abstand und Sichtbarkeit wird ein Anteil, und
     * daraus ueber {@code (i*i + i) / 2 * 7 * power + 1} der Rohschaden.
     */
    public static float explosionsSchaden(LivingEntity ziel, Vec3 ort, float staerke) {
        if (ziel == null || mc().level == null) {
            return 0.0f;
        }
        Vec3 stelle = ziel.position();
        double abstand = stelle.distanceTo(ort);
        if (abstand > staerke) {
            return 0.0f;
        }

        double sichtbar = sichtbarkeit(ort, ziel.getBoundingBox());
        double anteil = (1.0 - (abstand / staerke)) * sichtbar;
        float roh = (int) ((anteil * anteil + anteil) / 2.0 * 7.0 * staerke + 1.0);

        return abzuege(roh, ziel, mc().level.damageSources().explosion(null, null));
    }

    /**
     * Der Anteil der Hitbox, der die Explosion sieht. Das Raster und die
     * Schrittweiten sind die der Vanilla-Explosion - nicht gerundet oder
     * vereinfacht, sonst stimmt der Schaden nicht mehr.
     */
    private static double sichtbarkeit(Vec3 quelle, AABB kasten) {
        double dx = kasten.maxX - kasten.minX;
        double dy = kasten.maxY - kasten.minY;
        double dz = kasten.maxZ - kasten.minZ;

        double sx = 1.0 / (dx * 2.0 + 1.0);
        double sy = 1.0 / (dy * 2.0 + 1.0);
        double sz = 1.0 / (dz * 2.0 + 1.0);
        if (sx <= 0.0 || sy <= 0.0 || sz <= 0.0) {
            return 0.0;
        }

        double versatzX = (1.0 - Math.floor(1.0 / sx) * sx) * 0.5;
        double versatzZ = (1.0 - Math.floor(1.0 / sz) * sz) * 0.5;

        sx *= dx;
        sy *= dy;
        sz *= dz;

        int frei = 0;
        int gesamt = 0;
        for (double x = kasten.minX + versatzX; x <= kasten.maxX + versatzX; x += sx) {
            for (double y = kasten.minY; y <= kasten.maxY; y += sy) {
                for (double z = kasten.minZ + versatzZ; z <= kasten.maxZ + versatzZ; z += sz) {
                    if (!verdeckt(new Vec3(x, y, z), quelle)) {
                        frei++;
                    }
                    gesamt++;
                }
            }
        }
        return gesamt == 0 ? 0.0 : (double) frei / gesamt;
    }

    /**
     * Ob zwischen zwei Punkten etwas Sprengfestes steht. Wie im Original
     * zaehlen nur Bloecke ab Widerstand 600 - Obsidian und aufwaerts. Alles
     * Weichere fliegt bei der Explosion ohnehin weg.
     */
    private static boolean verdeckt(Vec3 von, Vec3 nach) {
        BlockHitResult treffer = mc().level.clip(new ClipContext(
                von, nach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc().player));
        if (treffer.getType() == HitResult.Type.MISS) {
            return false;
        }
        BlockState zustand = mc().level.getBlockState(treffer.getBlockPos());
        return zustand.getBlock().getExplosionResistance() >= 600.0f;
    }

    // ----------------------------------------------------------- Fallschaden

    public static float fallSchaden(LivingEntity wesen) {
        if (mc().level == null) {
            return 0.0f;
        }
        if (wesen instanceof Player spieler && spieler.getAbilities().flying) {
            return 0.0f;
        }
        if (wesen.hasEffect(MobEffects.SLOW_FALLING) || wesen.hasEffect(MobEffects.LEVITATION)) {
            return 0.0f;
        }

        // Ueber der Oberflaeche reicht die Hoehenkarte - das ist der schnelle
        // Weg, den auch das Original zuerst geht.
        int boden = mc().level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                wesen.getBlockX(), wesen.getBlockZ());
        if (wesen.getBlockY() >= boden) {
            return fallAbzuege(wesen, boden);
        }

        // Darunter (Hoehle, Schacht) hilft nur ein Strahl nach unten.
        BlockHitResult treffer = mc().level.clip(new ClipContext(
                wesen.position(),
                new Vec3(wesen.getX(), mc().level.getMinY(), wesen.getZ()),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, wesen));
        if (treffer.getType() == HitResult.Type.MISS) {
            return 0.0f;
        }
        return fallAbzuege(wesen, treffer.getBlockPos().getY());
    }

    private static float fallAbzuege(LivingEntity wesen, int boden) {
        int hoehe = (int) (wesen.getY() - boden + wesen.fallDistance - 3.0);
        MobEffectInstance sprungkraft = wesen.getEffect(MobEffects.JUMP_BOOST);
        if (sprungkraft != null) {
            hoehe -= sprungkraft.getAmplifier() + 1;
        }
        if (hoehe <= 0) {
            return 0.0f;
        }
        return abzuege(hoehe, wesen, mc().level.damageSources().fall());
    }

    // --------------------------------------------------------------- Abzuege

    /** Schwierigkeitsgrad, dann Ruestung, dann Resistenz - in dieser Reihenfolge. */
    public static float abzuege(float schaden, Entity wesen, DamageSource quelle) {
        if (quelle.scalesWithDifficulty()) {
            switch (mc().level.getDifficulty()) {
                case EASY -> schaden = Math.min(schaden / 2.0f + 1.0f, schaden);
                case HARD -> schaden *= 1.5f;
                default -> {
                }
            }
        }

        if (wesen instanceof LivingEntity lebend) {
            schaden = CombatRules.getDamageAfterAbsorb(lebend, schaden, quelle,
                    (float) Math.floor(lebend.getAttributeValue(Attributes.ARMOR)),
                    (float) lebend.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            schaden = resistenz(lebend, schaden);
        }

        return Math.max(schaden, 0.0f);
    }

    private static float resistenz(LivingEntity wesen, float schaden) {
        MobEffectInstance wirkung = wesen.getEffect(MobEffects.RESISTANCE);
        if (wirkung != null) {
            int stufe = wirkung.getAmplifier() + 1;
            schaden *= 1.0f - stufe * 0.2f;
        }
        return Math.max(schaden, 0.0f);
    }
}
