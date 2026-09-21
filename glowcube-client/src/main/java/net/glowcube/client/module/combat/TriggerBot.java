package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Schlaegt zu, sobald ein Ziel unter dem Fadenkreuz liegt.
 *
 * <p>Nachgebildet dem {@code TriggerBot} aus Aoba (GPL-3.0): anders als
 * KillAura sucht sich das hier kein Ziel und dreht sich nicht - es schlaegt
 * nur genau das, worauf du ohnehin schon zielst. Dadurch faellt es kaum auf.
 */
public final class TriggerBot extends Module {
    private final BooleanSetting cooldown = register(new BooleanSetting("Cooldown",
            "Auf die Waffenaufladung warten", true));
    private final BooleanSetting nurSpieler = register(new BooleanSetting("OnlyPlayers",
            "Nur Spieler treffen", false));

    public TriggerBot() {
        super("TriggerBot", "Schlaegt das Ziel unter dem Fadenkreuz", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (!(mc.hitResult instanceof EntityHitResult treffer)) {
            return;
        }
        if (!(treffer.getEntity() instanceof LivingEntity ziel) || ziel == player()) {
            return;
        }
        if (!ziel.isAlive() || ziel.isInvulnerable()) {
            return;
        }
        if (nurSpieler.get() && !(ziel instanceof Player)) {
            return;
        }
        if (cooldown.get() && player().getAttackStrengthScale(0.0f) < 1.0f) {
            return;
        }
        mc.gameMode.attack(player(), ziel);
        player().swing(InteractionHand.MAIN_HAND);
        // Wie beim echten Linksklick - sonst schlaegt es jeden Tick und der
        // Server verwirft die Schlaege als unmoegliche Rate.
        player().resetAttackStrengthTicker();
    }
}
