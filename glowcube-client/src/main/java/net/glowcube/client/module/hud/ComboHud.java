package net.glowcube.client.module.hud;

import net.glowcube.client.hud.TextHudModul;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Treffer in Folge - aus AxolotlClient ({@code ComboHud}, nach KronHUD).
 * Jeder Treffer auf das zuletzt geschlagene Ziel zaehlt hoch; wirst du selbst
 * getroffen oder triffst zwei Sekunden lang nicht, beginnt es von vorn.
 *
 * <p>AxolotlClient haengt dafuer an einem Schadens-Ereignis. Hier wird
 * stattdessen jeden Tick auf den Anfang der Treffer-Animation
 * ({@code hurtTime}) geachtet - das gibt es auf 1.21.11 und 26.x gleich.
 */
public final class ComboHud extends TextHudModul {
    private LivingEntity ziel;
    private int anzahl;
    private long zeit;
    private int zielHurtVorher;
    private int eigenHurtVorher;

    public ComboHud() {
        super("Combo", "Zaehlt deine Treffer in Folge");
    }

    @Override
    public boolean onEntityAttack(Entity getroffen) {
        if (getroffen instanceof LivingEntity lebend && lebend != ziel) {
            ziel = lebend;
            zielHurtVorher = lebend.hurtTime;
        }
        return false;
    }

    @Override
    public void onDisable() {
        ziel = null;
        anzahl = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        int eigen = mc.player.hurtTime;
        if (eigen > eigenHurtVorher) {
            ziel = null;
            anzahl = 0;
        }
        eigenHurtVorher = eigen;

        if (ziel == null) {
            return;
        }
        if (!ziel.isAlive()) {
            ziel = null;
            return;
        }
        int hurt = ziel.hurtTime;
        if (hurt > zielHurtVorher) {
            anzahl++;
            zeit = System.currentTimeMillis();
        }
        zielHurtVorher = hurt;
    }

    @Override
    protected String text() {
        if (anzahl == 0) {
            return "Keine Treffer";
        }
        if (zeit + 2000 < System.currentTimeMillis()) {
            anzahl = 0;
            return "0 Treffer";
        }
        return anzahl == 1 ? "1 Treffer" : anzahl + " Treffer";
    }
}
