package de.gtacity.client;

import de.gtacity.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Bullet tracers, muzzle flashes and impact sparks, drawn locally from a {@link Payloads.ShotFx} packet. */
public final class ShotEffects {
    private ShotEffects() {
    }

    private static final DustParticleOptions TRACER = new DustParticleOptions(0xFFE7A0, 0.45F);

    public static void spawn(Payloads.ShotFx fx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 from = new Vec3(fx.fx(), fx.fy(), fx.fz());
        Vec3 to = new Vec3(fx.tx(), fx.ty(), fx.tz());
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 step = d.normalize();
        for (double t = 1.0; t < Math.min(len, 80.0); t += 0.9) {
            Vec3 p = from.add(step.scale(t));
            level.addParticle(TRACER, p.x, p.y, p.z, 0, 0, 0);
        }
        level.addParticle(ParticleTypes.SMALL_FLAME, from.x, from.y, from.z, 0, 0.01, 0);
        level.addParticle(ParticleTypes.SMOKE, from.x, from.y, from.z, 0, 0.02, 0);
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.CRIT, to.x, to.y, to.z, (level.getRandom().nextDouble() - 0.5) * 0.3,
                    level.getRandom().nextDouble() * 0.2, (level.getRandom().nextDouble() - 0.5) * 0.3);
        }
    }
}
