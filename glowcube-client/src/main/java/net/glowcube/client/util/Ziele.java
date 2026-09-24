package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/** Gemeinsame Zielwahl fuer CrystalAura, BedAura und AutoBlock. */
public final class Ziele {
    private Ziele() {
    }

    public static boolean taugt(Entity wesen, boolean spieler, boolean monster) {
        Minecraft mc = Minecraft.getInstance();
        if (!(wesen instanceof LivingEntity lebend) || wesen == mc.player || !lebend.isAlive()) {
            return false;
        }
        if (wesen instanceof Player p) {
            return spieler && !p.isCreative() && !p.isSpectator();
        }
        return monster && wesen instanceof Monster;
    }

    /** Das naechste passende Wesen in Reichweite, oder null. */
    public static LivingEntity naechstes(double reichweite, boolean spieler, boolean monster) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        LivingEntity bestes = null;
        double besterAbstand = reichweite * reichweite;
        for (Entity wesen : mc.level.entitiesForRendering()) {
            if (!taugt(wesen, spieler, monster)) {
                continue;
            }
            double abstand = wesen.distanceToSqr(mc.player);
            if (abstand <= besterAbstand) {
                besterAbstand = abstand;
                bestes = (LivingEntity) wesen;
            }
        }
        return bestes;
    }
}
