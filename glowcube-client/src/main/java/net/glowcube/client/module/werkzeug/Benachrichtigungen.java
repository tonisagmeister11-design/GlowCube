package net.glowcube.client.module.werkzeug;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.hud.HudModul;
import net.glowcube.client.hud.HudZeichner;
import net.glowcube.client.hud.Meldungen;
import net.glowcube.client.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Benachrichtigungen oben in der Bildschirmmitte, statt alles in den Chat zu
 * schreiben: wer in Sichtweite kommt oder geht, wenig Leben - und alles, was
 * andere Module melden (Todespunkt, Totem-Pops, Haltbarkeit, Teleport ...).
 */
public final class Benachrichtigungen extends HudModul {
    private final BooleanSetting spieler = register(new BooleanSetting("Spieler",
            "Melden, wenn ein Spieler in Sichtweite kommt oder geht", true));
    private final BooleanSetting leben = register(new BooleanSetting("Wenig Leben",
            "Melden, wenn das eigene Leben unter die Schwelle faellt", true));
    private final NumberSetting schwelle = register(new NumberSetting("Leben-Schwelle",
            "Ab so wenig Leben (Herzhaelften) wird gewarnt", 8, 1, 19, 1));
    private final BooleanSetting ton = register(new BooleanSetting("Ton", "Leiser Ton bei jeder Meldung", true));
    private final NumberSetting dauer = register(new NumberSetting("Dauer",
            "Sekunden, die eine Meldung stehen bleibt", 4, 1, 15, 1));

    private final Set<String> bekannt = new HashSet<>();
    private boolean ersterTick = true;
    private boolean warNiedrig;

    public Benachrichtigungen() {
        super("Benachrichtigungen", "Meldungen oben in der Mitte statt im Chat", false, Category.WERKZEUG);
    }

    @Override
    public void onEnable() {
        ersterTick = true;
        bekannt.clear();
        Meldungen.ton = () -> {
            Minecraft mc = Minecraft.getInstance();
            if (ton.get() && mc.player != null) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.8f);
            }
        };
    }

    @Override
    public void onDisable() {
        Meldungen.ton = null;
    }

    @Override
    public void onTick() {
        if (spieler.get()) {
            Set<String> jetzt = new HashSet<>();
            for (Player p : level().players()) {
                if (p != player()) {
                    jetzt.add(p.getName().getString());
                }
            }
            if (!ersterTick) {
                for (String name : jetzt) {
                    if (!bekannt.contains(name)) {
                        Meldungen.melden(name + " ist in Sichtweite", 0xFFFFC53D);
                    }
                }
                for (String name : bekannt) {
                    if (!jetzt.contains(name)) {
                        Meldungen.melden(name + " ist weg", 0xFFB0B8C8);
                    }
                }
            }
            bekannt.clear();
            bekannt.addAll(jetzt);
        }
        ersterTick = false;
        if (leben.get()) {
            boolean niedrig = player().getHealth() <= schwelle.get() && player().isAlive();
            if (niedrig && !warNiedrig) {
                Meldungen.melden("Wenig Leben: " + (int) Math.ceil(player().getHealth()) + " ❤", 0xFFFF5F6D);
            }
            warNiedrig = niedrig;
        }
    }

    @Override
    public boolean frei() {
        return true;
    }

    @Override
    public float breite(HudZeichner z) {
        return 0;
    }

    @Override
    public float hoehe() {
        return 0;
    }

    @Override
    public void zeichnen(HudZeichner z, float x, float y) {
    }

    @Override
    public void zeichnenFrei(HudZeichner z, int bildBreite, int bildHoehe) {
        long dauerMs = (long) (dauer.get() * 1000);
        List<Meldungen.Meldung> liste = Meldungen.aktuelle(dauerMs);
        float y = 26;
        long jetzt = System.currentTimeMillis();
        for (Meldungen.Meldung m : liste) {
            long alter = jetzt - m.seit();
            // Die letzte halbe Sekunde ausblenden, die erste Viertelsekunde einschieben.
            float sicht = Math.min(1f, Math.min(alter / 250f, (dauerMs - alter) / 500f));
            if (sicht <= 0) {
                continue;
            }
            float w = z.breite(m.text()) + 16;
            float x = (bildBreite - w) / 2.0f;
            float schub = (1 - Math.min(1f, alter / 250f)) * -8;
            z.rundRect(x, y + schub, w, 14, 4, ColorUtil.fade(0xE0101420, sicht));
            z.rect(x, y + schub + 2, 2, 10, ColorUtil.fade(m.farbe(), sicht));
            z.text(m.text(), x + 9, y + schub + 3, ColorUtil.fade(0xFFF2F5FF, sicht), true);
            y += 17;
        }
    }
}
