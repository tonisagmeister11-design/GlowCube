package net.glowcube.client.module.render;

import com.mojang.authlib.GameProfile;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/**
 * Freecam: die Kamera loest sich vom Koerper und fliegt frei herum, der
 * Koerper bleibt stehen, wo er ist.
 *
 * <p>So geht es: eine unsichtbare Stellvertreter-Figur, die nie in die Welt
 * kommt, wird zur Kamera ({@code setCameraEntity}). Die Mausbewegung dreht
 * sie statt der eigenen Figur (EntityDrehenMixin), die Bewegungstasten
 * schieben sie. Die eigene Figur bekommt so lange eine leere Eingabe - sie
 * steht still, und weil die Kamera nicht mehr in ihr sitzt, sieht man sie.
 *
 * <p>Der Server merkt davon nichts: der Spieler steht einfach da. Nur die
 * Chunks um den Koerper sind geladen - weit weg fliegen zeigt darum Leere.
 */
public final class Freecam extends Module {
    private static Freecam instanz;

    private final NumberSetting tempo = register(new NumberSetting("Tempo",
            "Bloecke je Tick (Sprinttaste verdoppelt)", 1.0, 0.1, 5.0, 0.1));
    private final BooleanSetting sperren = register(new BooleanSetting("Interaktion sperren",
            "Solange Freecam laeuft nichts abbauen, anklicken oder schlagen", true));

    private RemotePlayer kamera;
    private ClientInput vorherEingabe;
    private float gier;
    private float neigung;

    public Freecam() {
        super("Freecam", "Kamera fliegt frei, der Koerper bleibt stehen", Category.RENDER);
        instanz = this;
    }

    @Override
    public void onEnable() {
        if (!inGame()) {
            return;
        }
        kamera = new RemotePlayer(level(), new GameProfile(UUID.randomUUID(), "Freecam"));
        gier = player().getYRot();
        neigung = player().getXRot();
        kamera.setPos(player().getX(), player().getY(), player().getZ());
        kamera.xo = kamera.getX();
        kamera.yo = kamera.getY();
        kamera.zo = kamera.getZ();
        blickSetzen();
        kamera.noPhysics = true;

        vorherEingabe = player().input;
        player().input = new ClientInput();
        mc.setCameraEntity(kamera);
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            if (kamera != null) {
                mc.setCameraEntity(mc.player);
            }
            if (vorherEingabe != null) {
                mc.player.input = vorherEingabe;
            }
        }
        kamera = null;
        vorherEingabe = null;
    }

    @Override
    public void onTick() {
        if (kamera == null || kamera.level() != level()) {
            // Welt gewechselt oder nie gestartet: sauber neu aufsetzen.
            onDisable();
            onEnable();
            if (kamera == null) {
                return;
            }
        }
        if (mc.getCameraEntity() != kamera) {
            mc.setCameraEntity(kamera);
        }
        kamera.xo = kamera.getX();
        kamera.yo = kamera.getY();
        kamera.zo = kamera.getZ();

        double v = tempo.get() * (mc.options.keySprint.isDown() ? 2.0 : 1.0);
        double rad = Math.toRadians(gier);
        // Vorwaerts im Blick (nur waagrecht), rechts quer dazu.
        double vx = -Math.sin(rad);
        double vz = Math.cos(rad);
        double rx = -vz;
        double rz = vx;
        double dx = 0;
        double dy = 0;
        double dz = 0;
        if (mc.options.keyUp.isDown()) {
            dx += vx;
            dz += vz;
        }
        if (mc.options.keyDown.isDown()) {
            dx -= vx;
            dz -= vz;
        }
        if (mc.options.keyRight.isDown()) {
            dx += rx;
            dz += rz;
        }
        if (mc.options.keyLeft.isDown()) {
            dx -= rx;
            dz -= rz;
        }
        if (mc.options.keyJump.isDown()) {
            dy += 1;
        }
        if (mc.options.keyShift.isDown()) {
            dy -= 1;
        }
        double laenge = Math.sqrt(dx * dx + dz * dz);
        if (laenge > 1) {
            dx /= laenge;
            dz /= laenge;
        }
        kamera.setPos(kamera.getX() + dx * v, kamera.getY() + dy * v, kamera.getZ() + dz * v);
        blickSetzen();
    }

    private void blickSetzen() {
        kamera.setYRot(gier);
        kamera.setXRot(neigung);
        kamera.yRotO = gier;
        kamera.xRotO = neigung;
        kamera.setYHeadRot(gier);
    }

    /** Ob die Kamera gerade frei fliegt. */
    public static boolean aktiv() {
        return instanz != null && instanz.isEnabled() && instanz.kamera != null;
    }

    /** Die Mausbewegung dreht die Kamera, nicht die Figur. */
    public static void drehen(double dy, double dx) {
        Freecam f = instanz;
        f.gier += (float) dy * 0.15f;
        f.neigung = Math.max(-90f, Math.min(90f, f.neigung + (float) dx * 0.15f));
        if (f.kamera != null) {
            f.blickSetzen();
        }
    }

    /** Wo die Kamera gerade ist - fuer die Spieltests. */
    public static Entity kamera() {
        return instanz == null ? null : instanz.kamera;
    }

    @Override
    public boolean onBlockBreak(BlockPos pos) {
        return sperren.get();
    }

    @Override
    public boolean onBlockUse(BlockHitResult treffer, InteractionHand hand) {
        return sperren.get();
    }

    @Override
    public boolean onEntityAttack(Entity ziel) {
        return sperren.get();
    }

    @Override
    public boolean onEntityUse(Entity ziel, InteractionHand hand) {
        return sperren.get();
    }

    @Override
    public boolean bleibtNachWeltwechsel() {
        return false;
    }
}
