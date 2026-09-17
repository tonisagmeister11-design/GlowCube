package net.glowcube.client.util;

import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.module.movement.NoFall;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), {@code PlayerUtils} - die Teile,
 * die hier gebraucht werden.
 */
public final class PlayerUtils {
    private PlayerUtils() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Ob gerade eine Bewegungstaste gedrueckt ist. */
    public static boolean istInBewegung() {
        return mc().player != null && (mc().player.zza != 0.0f || mc().player.xxa != 0.0f);
    }

    public static double abstandQuadrat(BlockPos pos) {
        if (mc().player == null) {
            return Double.MAX_VALUE;
        }
        return mc().player.position().distanceToSqr(Vec3.atCenterOf(pos));
    }

    /** Ob man in einem Loch steht, das einer Explosion standhaelt. */
    public static boolean istImLoch(boolean auchDoppelt) {
        if (mc().player == null || mc().level == null) {
            return false;
        }
        BlockPos pos = mc().player.blockPosition();
        int luecken = 0;

        for (Direction seite : Direction.values()) {
            if (seite == Direction.UP) {
                continue;
            }
            BlockState zustand = mc().level.getBlockState(pos.relative(seite));
            if (zustand.getBlock().getExplosionResistance() < 600.0f) {
                if (!auchDoppelt || seite == Direction.DOWN) {
                    return false;
                }
                luecken++;
                for (Direction weiter : Direction.values()) {
                    if (weiter == seite.getOpposite() || weiter == Direction.UP) {
                        continue;
                    }
                    BlockState daneben = mc().level.getBlockState(pos.relative(seite).relative(weiter));
                    if (daneben.getBlock().getExplosionResistance() < 600.0f) {
                        return false;
                    }
                }
            }
        }
        return luecken <= 1;
    }

    /**
     * Wie viel Leben im schlimmsten Fall gleich weg sein koennte.
     *
     * <p>Genau wie im Original wird nicht addiert, sondern das Schlimmste
     * genommen: zwei Kristalle gehen selten gleichzeitig hoch, einer reicht
     * zum Sterben.
     */
    public static float moeglicherSchaden(boolean explosionen, boolean fall) {
        if (mc().player == null || mc().level == null) {
            return 0.0f;
        }
        float schlimmstes = 0.0f;

        if (explosionen) {
            for (Entity wesen : mc().level.entitiesForRendering()) {
                if (wesen instanceof EndCrystal) {
                    float schaden = DamageUtils.kristallSchaden(mc().player, wesen.position());
                    if (schaden > schlimmstes) {
                        schlimmstes = schaden;
                    }
                }
            }

            // Betten im Nether und im End sind Bomben. Sechs Bloecke im
            // Umkreis sind die Reichweite, die auch das Original absucht.
            if (!mc().level.dimensionType().bedWorks()) {
                BlockPos mitte = mc().player.blockPosition();
                int weite = 6;
                for (BlockPos pos : BlockPos.betweenClosed(
                        mitte.offset(-weite, -weite, -weite), mitte.offset(weite, weite, weite))) {
                    if (mc().level.getBlockState(pos).getBlock() instanceof BedBlock) {
                        float schaden = DamageUtils.bettSchaden(mc().player,
                                new Vec3(pos.getX(), pos.getY(), pos.getZ()));
                        if (schaden > schlimmstes) {
                            schlimmstes = schaden;
                        }
                    }
                }
            }
        }

        if (fall && mc().player.fallDistance > 3.0f) {
            boolean noFallAn = GlowCubeClient.modules() != null
                    && GlowCubeClient.modules().get(NoFall.class).isEnabled();
            if (!noFallAn) {
                float schaden = DamageUtils.fallSchaden(mc().player);
                if (schaden > schlimmstes) {
                    schlimmstes = schaden;
                }
            }
        }

        return schlimmstes;
    }

    /** Leben plus Absorption - das, was wirklich noch da ist. */
    public static float lebenGesamt(LivingEntity wesen) {
        return wesen.getHealth() + wesen.getAbsorptionAmount();
    }
}
