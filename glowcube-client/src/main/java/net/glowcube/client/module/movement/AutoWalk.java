package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code AutoWalk} - dessen
 * einfache Betriebsart.
 *
 * <p>Der wesentliche Unterschied zu dem, was hier vorher stand: es wird
 * nicht mehr an der Geschwindigkeit gedreht, sondern die Laufen-Taste
 * gedrueckt gehalten. Damit gilt alles, was im Spiel an einem normalen
 * Schritt haengt - Hunger, Sprint, Treppen, Kollisionen, Anti-Cheat - statt
 * eines gesetzten Bewegungsvektors, der auf Servern sofort auffaellt.
 *
 * <p>Die Abschaltbedingungen sind die des Originals: bei eigener Eingabe
 * und bei Hoehenaenderung, jeweils zuschaltbar. Und der Chunk-Schutz
 * ebenfalls - wer in ungeladenes Gebiet laeuft, faellt durch die Welt.
 *
 * <p><b>Nicht uebernommen:</b> Meteors "Smart"-Betriebsart. Die laesst
 * Baritone laufen und ist ohne Baritone auch dort nur eine Fehlermeldung.
 */
public final class AutoWalk extends Module {
    private final ModeSetting richtung = register(new ModeSetting("Richtung",
            "Wohin gelaufen wird", "Vorwaerts", "Vorwaerts", "Rueckwaerts", "Links", "Rechts"));
    private final BooleanSetting beiEingabe = register(new BooleanSetting("Bei Eingabe aus",
            "Abschalten, sobald man selbst eine Bewegungstaste drueckt", false));
    private final BooleanSetting beiHoehe = register(new BooleanSetting("Bei Hoehenaenderung aus",
            "Abschalten, sobald es auf- oder abwaerts geht", false));
    private final BooleanSetting sprint = register(new BooleanSetting("Sprinten",
            "Dabei sprinten", true));
    private final BooleanSetting keineLeeren = register(new BooleanSetting("Keine leeren Chunks",
            "Am Rand des Geladenen stehenbleiben statt hineinzulaufen", true));

    public AutoWalk() {
        super("AutoWalk", "Laeuft von allein weiter", Category.MOVEMENT);
    }

    @Override
    public void onDisable() {
        loslassen();
    }

    @Override
    public void onTick() {
        if (beiHoehe.get() && player().yo != player().getY()) {
            setEnabled(false);
            return;
        }
        if (beiEingabe.get() && eigeneEingabe()) {
            setEnabled(false);
            return;
        }
        if (keineLeeren.get() && vorUngeladenem()) {
            loslassen();
            return;
        }

        switch (richtung.get()) {
            case "Vorwaerts" -> mc.options.keyUp.setDown(true);
            case "Rueckwaerts" -> mc.options.keyDown.setDown(true);
            case "Links" -> mc.options.keyLeft.setDown(true);
            case "Rechts" -> mc.options.keyRight.setDown(true);
            default -> {
            }
        }
        if (sprint.get() && richtung.is("Vorwaerts")) {
            player().setSprinting(true);
        }
    }

    /**
     * Ob die naechsten zwei Bloecke in Laufrichtung in einem geladenen
     * Chunk liegen. Ohne diese Pruefung laeuft man in unbeschriebenes
     * Gebiet und faellt durch die Welt.
     */
    private boolean vorUngeladenem() {
        double x = player().getX() + player().getDeltaMovement().x * 2.0;
        double z = player().getZ() + player().getDeltaMovement().z * 2.0;
        return !level().getChunkSource().hasChunk((int) (x / 16.0), (int) (z / 16.0));
    }

    /**
     * Eine gedrueckte Taste allein reicht nicht - AutoWalk drueckt ja
     * selbst. Gezaehlt wird nur, was der Spieler zusaetzlich macht.
     */
    private boolean eigeneEingabe() {
        boolean quer = mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        boolean laengs = mc.options.keyUp.isDown() || mc.options.keyDown.isDown();
        return switch (richtung.get()) {
            case "Vorwaerts", "Rueckwaerts" -> quer || mc.options.keyJump.isDown()
                    || mc.options.keyShift.isDown();
            default -> laengs || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
        };
    }

    private void loslassen() {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        if (inGame()) {
            player().setSprinting(false);
        }
    }

    @Override
    public String hudSuffix() {
        return richtung.get();
    }
}
