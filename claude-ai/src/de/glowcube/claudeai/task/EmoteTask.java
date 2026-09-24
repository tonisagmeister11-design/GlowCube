package de.glowcube.claudeai.task;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.world.Fx;

/** Kleine Kunststuecke: huepfen, drehen, tanzen, winken, nicken, Herzchen. */
public final class EmoteTask extends Task {

    public enum Kind { JUMP, SPIN, DANCE, WAVE, NOD, LOVE, BOW }

    private final Kind kind;
    private final Player audience;
    private Location base;
    private int t;

    public EmoteTask(Kind kind, Player audience) {
        this.kind = kind;
        this.audience = audience;
    }

    @Override
    public String label() {
        return "mache Quatsch";
    }

    @Override
    public void start(Npc npc) {
        base = npc.location();
        t = 0;
        npc.mover().stop();
        npc.setWalking(true);
    }

    @Override
    public Status tick(Npc npc) {
        t++;
        Location at = base.clone();
        float yaw = base.getYaw();
        float pitch = 0;
        int length;
        switch (kind) {
            case JUMP -> {
                length = 36;
                at.add(0, hop(t, 12, 1.1), 0);
                if (t % 12 == 1) Fx.sound(at, () -> Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.2f, 2f);
            }
            case SPIN -> {
                length = 40;
                yaw += t * 18;
            }
            case DANCE -> {
                length = 100;
                at.add(Math.sin(t / 5.0) * 0.4, hop(t, 10, 0.5), 0);
                yaw += (t / 10 % 2 == 0 ? 1 : -1) * (t % 10) * 18;
                if (t % 5 == 0) npc.swing();
                if (t % 10 == 0) {
                    Fx.particle(at.clone().add(0, 2.3, 0), () -> Particle.NOTE, 1, 0.3);
                    Fx.sound(at, () -> Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 0.5f + (t % 40) / 40f);
                }
            }
            case WAVE -> {
                length = 30;
                if (t % 5 == 0) npc.swing();
            }
            case NOD -> {
                length = 24;
                pitch = (float) (Math.sin(t / 2.0) * 30);
            }
            case BOW -> {
                length = 30;
                pitch = t < 15 ? t * 4 : (30 - t) * 4;
            }
            case LOVE -> {
                length = 30;
                if (t % 6 == 0) Fx.particle(at.clone().add(0, 2.2, 0), () -> Particle.HEART, 2, 0.4);
            }
            default -> length = 1;
        }
        if (audience != null && audience.isOnline() && kind != Kind.SPIN && kind != Kind.DANCE) {
            double dx = audience.getLocation().getX() - at.getX();
            double dz = audience.getLocation().getZ() - at.getZ();
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
        at.setYaw(yaw);
        at.setPitch(pitch);
        npc.moveBody(at);
        if (t >= length) {
            npc.moveBody(base);
            npc.setWalking(false);
            return Status.DONE;
        }
        return Status.RUNNING;
    }

    private static double hop(int t, int period, double height) {
        double phase = (t % period) / (double) period;
        return Math.sin(phase * Math.PI) * height;
    }

    @Override
    public void stop(Npc npc) {
        if (base != null) npc.moveBody(base);
        npc.setWalking(false);
    }
}
