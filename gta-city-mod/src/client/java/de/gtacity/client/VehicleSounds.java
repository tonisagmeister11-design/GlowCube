package de.gtacity.client;

import de.gtacity.entity.CarEntity;
import de.gtacity.entity.CarVariant;
import de.gtacity.entity.HelicopterEntity;
import de.gtacity.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Looping vehicle sounds: every car nearby hums (higher with speed), police cars wail when the siren is on, tyres
 * squeal while drifting and the police helicopter can be heard from far away.
 */
public final class VehicleSounds {
    private VehicleSounds() {
    }

    private static final double ENGINE_RANGE = 40.0;
    private static final double SIREN_RANGE = 90.0;
    private static final double HELI_RANGE = 130.0;

    private static final Map<String, Loop> PLAYING = new HashMap<>();

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            PLAYING.clear();
            return;
        }
        PLAYING.values().removeIf(AbstractTickableSoundInstance::isStopped);
        for (Entity entity : mc.level.entitiesForRendering()) {
            double dist = entity.distanceTo(player);
            if (entity instanceof CarEntity car) {
                if (dist < ENGINE_RANGE) {
                    boolean superCar = car.getVariant().shape == CarVariant.Shape.SUPER;
                    start(mc, "engine", car, superCar ? ModSounds.ENGINE_SUPER : ModSounds.ENGINE, ENGINE_RANGE,
                            e -> true, VehicleSounds::engine);
                    start(mc, "skid", car, ModSounds.SKID, ENGINE_RANGE, e -> ((CarEntity) e).isDrifting(),
                            (loop, e) -> {
                                loop.volume(1.1F);
                                loop.pitch(0.9F + Math.min(0.3F, Math.abs(((CarEntity) e).measuredSpeed) * 0.15F));
                            });
                }
                if (dist < SIREN_RANGE && car.isSirenOn()) {
                    start(mc, "siren", car, ModSounds.SIREN, SIREN_RANGE, e -> ((CarEntity) e).isSirenOn(),
                            (loop, e) -> {
                                loop.volume(5.0F);
                                loop.pitch(1.0F);
                            });
                }
            } else if (entity instanceof HelicopterEntity && dist < HELI_RANGE) {
                start(mc, "rotor", entity, ModSounds.HELI_ROTOR, HELI_RANGE, e -> true, (loop, e) -> {
                    loop.volume(7.0F);
                    loop.pitch(1.0F);
                });
            }
        }
    }

    private static void engine(Loop loop, Entity entity) {
        CarEntity car = (CarEntity) entity;
        float max = car.getVariant().shape.maxSpeed;
        float fraction = Mth.clamp(Math.abs(car.measuredSpeed) / max, 0.0F, 1.0F);
        boolean mine = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() == car;
        // Parked, empty cars are silent; anything with a driver idles and revs up with the speed.
        boolean running = mine || car.isVehicle() || fraction > 0.01F;
        float target = running ? 0.55F + fraction * 1.25F : 0.5F;
        loop.pitch(loop.currentPitch() + (target - loop.currentPitch()) * 0.15F);
        float volume = running ? (mine ? 0.9F : 0.55F) + fraction * 0.9F : 0.0F;
        loop.volume(volume);
    }

    private static void start(Minecraft mc, String kind, Entity entity, SoundEvent sound, double range,
                              Predicate<Entity> keep, Updater updater) {
        String key = kind + entity.getId();
        if (PLAYING.containsKey(key) || !keep.test(entity)) {
            return;
        }
        Loop loop = new Loop(sound, entity, range, keep, updater);
        PLAYING.put(key, loop);
        mc.getSoundManager().play(loop);
    }

    @FunctionalInterface
    private interface Updater {
        void update(Loop loop, Entity entity);
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final Entity entity;
        private final double range;
        private final Predicate<Entity> keep;
        private final Updater updater;

        Loop(SoundEvent sound, Entity entity, double range, Predicate<Entity> keep, Updater updater) {
            super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.entity = entity;
            this.range = range;
            this.keep = keep;
            this.updater = updater;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 0.6F;
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
            updater.update(this, entity);
        }

        void volume(float volume) {
            this.volume = volume;
        }

        void pitch(float pitch) {
            this.pitch = Mth.clamp(pitch, 0.5F, 2.0F);
        }

        float currentPitch() {
            return pitch;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            LocalPlayer player = Minecraft.getInstance().player;
            if (entity.isRemoved() || player == null || entity.distanceTo(player) > range + 8.0 || !keep.test(entity)) {
                stop();
                return;
            }
            x = entity.getX();
            y = entity.getY();
            z = entity.getZ();
            updater.update(this, entity);
        }
    }
}
