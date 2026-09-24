package de.glowcube.claudeai.world;

import java.util.function.Supplier;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Geraeusche und Partikel. Die Konstanten werden erst beim Abspielen aufgeloest und jeder
 * Fehler geschluckt - ein fehlender Sound darf niemals eine Aufgabe abbrechen.
 */
public final class Fx {

    private Fx() {}

    public static void sound(Location at, Supplier<Sound> sound, float volume, float pitch) {
        try {
            at.getWorld().playSound(at, sound.get(), volume, pitch);
        } catch (Throwable ignored) {
            // Sound gibt es in dieser Version nicht
        }
    }

    public static void particle(Location at, Supplier<Particle> particle, int count, double spread) {
        try {
            at.getWorld().spawnParticle(particle.get(), at, count, spread, spread, spread, 0);
        } catch (Throwable ignored) {
            // egal
        }
    }

    /** Das echte "Block zerbricht"-Ereignis: Partikel und Geraeusch wie beim Spieler. */
    public static void breakEffect(Block block, BlockData data) {
        try {
            block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, data.getMaterial());
        } catch (Throwable ignored) {
            // egal
        }
    }

    public static void placeSound(Block block, BlockData data) {
        try {
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            at.getWorld().playSound(at, data.getSoundGroup().getPlaceSound(), 1f, 0.9f);
        } catch (Throwable ignored) {
            // egal
        }
    }

    public static void hitSound(Block block) {
        try {
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            at.getWorld().playSound(at, block.getBlockData().getSoundGroup().getHitSound(), 0.5f, 0.8f);
        } catch (Throwable ignored) {
            // egal
        }
    }
}
