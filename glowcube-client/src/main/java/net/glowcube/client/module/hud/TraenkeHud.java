package net.glowcube.client.module.hud;

import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.mixin.LivingEntityAccessor;
import net.glowcube.client.util.Theme;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Die laufenden Trankwirkungen - aus AxolotlClient ({@code PotionsHud}):
 * Name mit Stufe, darunter die Restzeit. Die Wirkungen kommen wie dort aus
 * dem Feld {@code activeEffects}, die Zeit ueber
 * {@code MobEffectUtil.formatDuration} mit der Tickrate der Welt.
 */
public final class TraenkeHud extends HudModul {
    private static final String[] STUFEN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final int ZEILE = 22;

    private final BooleanSetting hintergrund = register(
            new BooleanSetting("Hintergrund", "Dunkler Kasten hinter der Anzeige", true));

    private final List<String[]> zeilen = new ArrayList<>();

    public TraenkeHud() {
        super("Traenke", "Zeigt laufende Trankwirkungen mit Restzeit", true);
    }

    @Override
    public void vorbereiten() {
        zeilen.clear();
        if (mc.player == null || mc.level == null) {
            return;
        }
        float tickrate = mc.level.tickRateManager().tickrate();
        for (MobEffectInstance wirkung : ((LivingEntityAccessor) mc.player).glowcube$wirkungen().values()) {
            String name = Component.translatable(wirkung.getEffect().value().getDescriptionId()).getString();
            int stufe = wirkung.getAmplifier();
            name += stufe >= 0 && stufe < STUFEN.length ? STUFEN[stufe] : " " + (stufe + 1);
            String zeit = MobEffectUtil.formatDuration(wirkung, 1.0f, tickrate).getString();
            zeilen.add(new String[]{name, zeit});
        }
    }

    @Override
    public float breite(HudZeichner z) {
        float w = 0;
        for (String[] zeile : zeilen) {
            w = Math.max(w, Math.max(z.breite(zeile[0]), z.breite(zeile[1])));
        }
        return w + 8;
    }

    @Override
    public float hoehe() {
        return zeilen.size() * ZEILE;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
        if (zeilen.isEmpty()) {
            return;
        }
        if (hintergrund.get()) {
            z.rundRect(x, y, breite(z), hoehe(), 3, 0x99101420);
        }
        for (int i = 0; i < zeilen.size(); i++) {
            float zy = y + 2 + i * ZEILE;
            z.text(zeilen.get(i)[0], x + 4, zy, Theme.TEXT, true);
            z.text(zeilen.get(i)[1], x + 4, zy + 10, Theme.TEXT_DIM, true);
        }
    }
}
