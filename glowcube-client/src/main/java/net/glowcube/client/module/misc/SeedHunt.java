package net.glowcube.client.module.misc;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.integration.SeedBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Faehrt selbsttaetig Flaeche ab, damit SeedCrackerX zusammenbekommt, was es
 * zum Rechnen braucht.
 *
 * Der Cracker sieht nur, was der Client ohnehin geladen bekommt. Entscheidend
 * ist also nicht die Hoehe, sondern **wie viele neue Chunks je Minute**
 * durchlaufen werden. Deshalb: dicht ueber dem Boden bleiben, dafuer schnell
 * unterwegs sein. Hoch zu den Wolken zu steigen bringt gar nichts - die
 * Ladeentfernung ist waagerecht dieselbe, man sieht nur weiter.
 *
 * Die Bahn ist eine fortlaufende Spirale, keine Folge von Geraden: dadurch
 * gibt es keine Bremspunkte an den Ecken und das Tempo bleibt oben.
 */
public final class SeedHunt extends Module {
    private final ModeSetting mode = register(new ModeSetting("Mode",
            "Fliegen oder am Boden laufen", "Fliegen", "Fliegen", "Laufen"));
    private final NumberSetting speed = register(new NumberSetting("Speed",
            "Bloecke pro Tick - hoch heisst schnell", 2.5, 0.2, 6.0, 0.1));
    private final NumberSetting height = register(new NumberSetting("Height",
            "Bloecke ueber dem Boden, nicht ueber dem Meer", 12, 2, 80, 2));
    private final NumberSetting spacing = register(new NumberSetting("Spacing",
            "Abstand der Bahnen - kleiner heisst dichter", 128, 32, 384, 16));
    private final BooleanSetting stopOnSeed = register(new BooleanSetting("StopOnSeed",
            "Aufhoeren, sobald der Seed da ist", true));
    private final BooleanSetting autoStart = register(new BooleanSetting("AutoStart",
            "Beim Betreten einer Welt von selbst losfliegen", false));
    private final NumberSetting report = register(new NumberSetting("Report",
            "Sekunden zwischen zwei Standmeldungen", 5, 1, 60, 1));

    private Vec3 anker = Vec3.ZERO;
    private double winkel;
    private int ticks;
    private double gefahren;

    public SeedHunt() {
        super("SeedHunt", "Faehrt Flaeche ab und meldet den Fortschritt",
                Category.MISC, InputConstants.KEY_B);
    }

    public boolean autoStart() {
        return autoStart.get();
    }

    @Override
    public void onEnable() {
        if (!inGame()) {
            return;
        }
        anker = player().position();
        winkel = 0.0;
        ticks = 0;
        gefahren = 0.0;
        melden("SeedHunt laeuft - " + mode.get().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public void onDisable() {
        if (inGame()) {
            Vec3 jetzt = player().getDeltaMovement();
            player().setDeltaMovement(0.0, jetzt.y, 0.0);
        }
    }

    @Override
    public void onTick() {
        if (stopOnSeed.get() && SeedBridge.seed() != null) {
            melden("Seed gefunden: " + SeedBridge.seed());
            setEnabled(false);
            return;
        }

        double tempo = speed.get();

        // Fortlaufende Spirale: der Radius waechst je Umlauf um genau den
        // Bahnabstand, dadurch bleiben zwischen den Bahnen keine Luecken.
        double radius = Math.max(4.0, spacing.get() * winkel / (2.0 * Math.PI));
        // Bogenlaenge = Radius mal Winkelschritt - so bleibt das Tempo gleich,
        // egal wie weit aussen man gerade ist.
        winkel += tempo / radius;
        gefahren += tempo;

        Vec3 ziel = new Vec3(
                anker.x + Math.cos(winkel) * radius,
                player().position().y,
                anker.z + Math.sin(winkel) * radius);

        Vec3 hier = player().position();
        double dx = ziel.x - hier.x;
        double dz = ziel.z - hier.z;
        double laenge = Math.sqrt(dx * dx + dz * dz);
        if (laenge < 0.01) {
            return;
        }

        double vx = dx / laenge * tempo;
        double vz = dz / laenge * tempo;

        if (mode.is("Laufen")) {
            // Am Boden nur waagerecht schieben - die Schwerkraft macht den Rest.
            player().setDeltaMovement(vx, player().getDeltaMovement().y, vz);
            player().setSprinting(true);
        } else {
            // Dicht ueber dem Gelaende bleiben: die Wunschhoehe zaehlt ab
            // Boden, nicht ab Meereshoehe, sonst schrammt man an Bergen und
            // haengt ueber Taelern sinnlos hoch.
            int boden = level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(hier.x), (int) Math.floor(hier.z));
            double wunsch = boden + height.get();
            double dy = Math.max(-tempo, Math.min(tempo, wunsch - hier.y));
            player().setDeltaMovement(vx, dy, vz);
            player().setOnGround(false);
            player().resetFallDistance();
        }

        // Blick in Fahrtrichtung, sonst fliegt man seitwaerts.
        player().setYRot((float) Math.toDegrees(Math.atan2(-vx, vz)));

        if (++ticks >= report.getInt() * 20) {
            ticks = 0;
            melden(stand());
        }
    }

    /** Was in der Standmeldung und im HUD steht. */
    private String stand() {
        Double bits = SeedBridge.bits();
        String fortschritt = bits == null
                ? "SeedCracker nicht da"
                : String.format(java.util.Locale.ROOT, "%.1f Bit", bits);
        return String.format(java.util.Locale.ROOT,
                "%s  |  %.0f Bloecke  |  Radius %.0f",
                fortschritt, gefahren,
                Math.max(4.0, spacing.get() * winkel / (2.0 * Math.PI)));
    }

    private void melden(String text) {
        if (inGame()) {
            player().displayClientMessage(Component.literal("[GlowCube] " + text), true);
        }
    }

    @Override
    public String hudSuffix() {
        Double bits = SeedBridge.bits();
        return bits == null ? mode.get() : String.format(java.util.Locale.ROOT, "%.1f Bit", bits);
    }
}
