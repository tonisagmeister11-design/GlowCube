package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Ids;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * ElytraFly: Elytra wie einen Flugmodus steuern.
 *
 * <ul>
 *   <li><b>Steuerung</b>: die Tasten bestimmen die Richtung, Springen und
 *       Schleichen steigen und sinken, ohne Taste schwebt man.</li>
 *   <li><b>Boost</b>: fliegt wie Vanilla, nur mit Schub in Blickrichtung,
 *       solange man vorwaerts drueckt - wie eine Rakete ohne Rakete.</li>
 * </ul>
 * Mit "Auto-Start" oeffnet sich die Elytra von selbst, sobald man faellt.
 */
public final class ElytraFly extends Module {
    private final ModeSetting modus = register(new ModeSetting("Modus",
            "Steuerung: freie Richtung, Boost: Schub in Blickrichtung", "Steuerung", "Steuerung", "Boost"));
    private final NumberSetting tempo = register(new NumberSetting("Tempo",
            "Bloecke je Tick", 1.5, 0.1, 5.0, 0.1));
    private final NumberSetting steigen = register(new NumberSetting("Steigen",
            "Bloecke je Tick hoch und runter", 1.0, 0.1, 3.0, 0.1));
    private final BooleanSetting autoStart = register(new BooleanSetting("Auto-Start",
            "Elytra oeffnen, sobald man faellt", true));

    public ElytraFly() {
        super("ElytraFly", "Elytra frei steuern und schweben", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        boolean elytra = Ids.item(player().getItemBySlot(EquipmentSlot.CHEST)).equals("elytra");
        if (!player().isFallFlying()) {
            if (autoStart.get() && elytra && !player().onGround() && !player().isInWater()
                    && player().getDeltaMovement().y < -0.1 && player().tryToStartFallFlying()) {
                player().connection.send(new ServerboundPlayerCommandPacket(player(),
                        ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
            return;
        }
        double rad = Math.toRadians(player().getYRot());
        double vx = -Math.sin(rad);
        double vz = Math.cos(rad);
        if (modus.is("Boost")) {
            if (mc.options.keyUp.isDown()) {
                Vec3 blick = player().getLookAngle();
                Vec3 neu = player().getDeltaMovement().add(blick.scale(0.08 * tempo.get()));
                double max = tempo.get() * 2;
                if (neu.length() > max) {
                    neu = neu.normalize().scale(max);
                }
                player().setDeltaMovement(neu);
            }
            return;
        }
        double dx = 0;
        double dz = 0;
        double dy = 0;
        if (mc.options.keyUp.isDown()) {
            dx += vx;
            dz += vz;
        }
        if (mc.options.keyDown.isDown()) {
            dx -= vx;
            dz -= vz;
        }
        if (mc.options.keyRight.isDown()) {
            dx -= vz;
            dz += vx;
        }
        if (mc.options.keyLeft.isDown()) {
            dx += vz;
            dz -= vx;
        }
        if (mc.options.keyJump.isDown()) {
            dy += steigen.get();
        }
        if (mc.options.keyShift.isDown()) {
            dy -= steigen.get();
        }
        double laenge = Math.sqrt(dx * dx + dz * dz);
        if (laenge > 0) {
            dx = dx / laenge * tempo.get();
            dz = dz / laenge * tempo.get();
        }
        player().setDeltaMovement(dx, dy, dz);
    }

    @Override
    public String hudSuffix() {
        return modus.get();
    }
}
