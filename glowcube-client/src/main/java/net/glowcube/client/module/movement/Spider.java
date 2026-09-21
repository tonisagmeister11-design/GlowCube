package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Movement;
import net.minecraft.world.phys.Vec3;

/**
 * An Waenden hochklettern wie eine Spinne.
 *
 * <p>Nachgebildet dem {@code Spider} aus Aoba (GPL-3.0) und dem gleichen
 * Modul in Wurst/Meteor - alle machen dasselbe: stoesst der Spieler waagerecht
 * gegen einen Block und drueckt in ihn hinein, wird eine kleine
 * Aufwaerts-Geschwindigkeit gesetzt. Solange man in die Wand laeuft, klebt
 * man daran und steigt.
 */
public final class Spider extends Module {
    private final NumberSetting tempo = register(new NumberSetting("Tempo",
            "Wie schnell hochgeklettert wird", 0.2, 0.05, 0.6, 0.01));

    public Spider() {
        super("Spider", "An Waenden hochklettern", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        // Nur klettern, wenn man wirklich in die Wand hineinlaeuft.
        if (!player().horizontalCollision || !Movement.moving()) {
            return;
        }
        Vec3 v = player().getDeltaMovement();
        player().setDeltaMovement(v.x, tempo.get(), v.z);
        // Kein Fallschaden vom Abrutschen.
        player().fallDistance = 0.0f;
    }
}
