package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Ids;
import net.glowcube.client.util.Ziele;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * AutoBlock: haelt das Schild hoch, sobald ein Gegner in Reichweite zum
 * Schlag ausholt oder ein Pfeil auf einen zufliegt - und nimmt es danach
 * wieder herunter, damit man selbst zuschlagen kann.
 *
 * <p>Das Schild wird ueber die Benutzen-Taste gehalten, genau wie mit der
 * rechten Maustaste. Deshalb gehoert es in die Nebenhand (oder die Haupthand).
 */
public final class AutoBlock extends Module {
    private final NumberSetting reichweite = register(new NumberSetting("Reichweite",
            "Ab welcher Naehe ein ausholender Gegner zaehlt", 4.5, 2.0, 8.0, 0.5));
    private final NumberSetting nachhalten = register(new NumberSetting("Nachhalten",
            "So viele Ticks bleibt das Schild nach der Gefahr oben", 6, 0, 20, 1));
    private final BooleanSetting spieler = register(new BooleanSetting("Spieler", "Vor Spielern blocken", true));
    private final BooleanSetting monster = register(new BooleanSetting("Monster", "Vor Monstern blocken", true));
    private final BooleanSetting pfeile = register(new BooleanSetting("Geschosse",
            "Auch vor Pfeilen, Dreizacken und Feuerbaellen blocken", true));

    private boolean haelt;
    private int rest;

    public AutoBlock() {
        super("AutoBlock", "Hebt das Schild von selbst, wenn ein Treffer kommt", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        loslassen();
    }

    @Override
    public void onTick() {
        if (!schildDa()) {
            loslassen();
            return;
        }
        if (gefahr()) {
            rest = nachhalten.getInt();
            if (!haelt) {
                mc.options.keyUse.setDown(true);
                haelt = true;
            }
        } else if (haelt && --rest <= 0) {
            loslassen();
        }
    }

    private boolean schildDa() {
        return Ids.item(player().getOffhandItem()).equals("shield")
                || Ids.item(player().getMainHandItem()).equals("shield");
    }

    private boolean gefahr() {
        double r = reichweite.get();
        Vec3 brust = player().position().add(0, 1.0, 0);
        for (Entity wesen : level().entitiesForRendering()) {
            if (wesen == player()) {
                continue;
            }
            if (Ziele.taugt(wesen, spieler.get(), monster.get())) {
                LivingEntity lebend = (LivingEntity) wesen;
                if (lebend.swinging && lebend.distanceTo(player()) <= r) {
                    return true;
                }
                continue;
            }
            if (pfeile.get() && istGeschoss(wesen) && wesen.distanceTo(player()) < 12) {
                Vec3 flug = wesen.getDeltaMovement();
                if (flug.lengthSqr() < 0.01) {
                    continue;
                }
                Vec3 zuMir = brust.subtract(wesen.position()).normalize();
                if (flug.normalize().dot(zuMir) > 0.92) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean istGeschoss(Entity wesen) {
        String id = Ids.wesen(wesen);
        return id.contains("arrow") || id.equals("trident") || id.contains("fireball")
                || id.equals("wind_charge") || id.equals("shulker_bullet");
    }

    private void loslassen() {
        if (haelt) {
            mc.options.keyUse.setDown(false);
            haelt = false;
        }
    }

    @Override
    public String hudSuffix() {
        return haelt ? "blockt" : null;
    }
}
