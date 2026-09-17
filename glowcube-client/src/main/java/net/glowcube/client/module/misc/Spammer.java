package net.glowcube.client.module.misc;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.core.setting.TextListSetting;

import java.util.List;
import java.util.Random;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code Spam}.
 *
 * <p>Alle Feinheiten des Originals sind da: eine Liste statt einer einzelnen
 * Nachricht, reihum oder zufaellig, das Zerlegen langer Texte in mehrere
 * Nachrichten mit eigenem Abstand, und der Zufallsanhang gegen
 * Wiederholungssperren.
 *
 * <p>Auch die harte Grenze bleibt: mehr als 256 Zeichen nimmt kein Server an,
 * also wird abgeschnitten statt hinausgeworfen zu werden.
 */
public final class Spammer extends Module {
    private final TextListSetting texte = register(new TextListSetting("Nachrichten",
            "Was reihum geschickt wird", "GlowCube"));
    private final NumberSetting pause = register(new NumberSetting("Pause",
            "Ticks zwischen zwei Nachrichten", 20, 0, 200, 1));
    private final BooleanSetting zufaellig = register(new BooleanSetting("Zufaellig",
            "Statt reihum eine zufaellige Nachricht waehlen", false));
    private final BooleanSetting zerlegen = register(new BooleanSetting("Zerlegen",
            "Lange Texte auf mehrere Nachrichten verteilen", false));
    private final NumberSetting zerlegeLaenge = register(new NumberSetting("Stueckgroesse",
            "Ab welcher Laenge zerlegt wird", 256, 1, 256, 1));
    private final NumberSetting zerlegePause = register(new NumberSetting("Stueckpause",
            "Ticks zwischen zwei Stuecken", 20, 0, 200, 1));
    private final BooleanSetting anhang = register(new BooleanSetting("Zufallsanhang",
            "Zufaellige Zeichen anhaengen, um Wiederholungssperren auszuweichen", false));
    private final BooleanSetting grossbuchstaben = register(new BooleanSetting("Grossbuchstaben",
            "Der Anhang darf auch Grossbuchstaben enthalten", true));
    private final NumberSetting anhangLaenge = register(new NumberSetting("Anhangslaenge",
            "Wie viele Zeichen der Anhang hat", 16, 1, 64, 1));

    private static final Random ZUFALL = new Random();

    private int index;
    private int uhr;
    private int stueck;
    private String text;

    public Spammer() {
        super("Spammer", "Schickt regelmaessig Chatnachrichten", Category.MISC);
    }

    @Override
    public void onEnable() {
        uhr = pause.getInt();
        index = 0;
        stueck = 0;
        text = null;
    }

    @Override
    public void onTick() {
        List<String> liste = texte.werte();
        if (liste.isEmpty()) {
            return;
        }
        if (uhr > 0) {
            uhr--;
            return;
        }

        if (text == null) {
            int i;
            if (zufaellig.get()) {
                i = ZUFALL.nextInt(liste.size());
            } else {
                if (index >= liste.size()) {
                    index = 0;
                }
                i = index++;
            }
            text = liste.get(i);
            if (anhang.get()) {
                text = text + " " + zufallsText(anhangLaenge.getInt(), grossbuchstaben.get());
            }
        }

        if (zerlegen.get() && text.length() > zerlegeLaenge.getInt()) {
            int stuecke = (int) Math.ceil((double) text.length() / zerlegeLaenge.getInt());
            int von = stueck * zerlegeLaenge.getInt();
            int bis = Math.min(von + zerlegeLaenge.getInt(), text.length());
            senden(text.substring(von, bis));

            stueck = (stueck + 1) % stuecke;
            uhr = zerlegePause.getInt();
            if (stueck == 0) {
                uhr = pause.getInt();
                text = null;
            }
        } else {
            String hinaus = text.length() > 256 ? text.substring(0, 256) : text;
            senden(hinaus);
            uhr = pause.getInt();
            text = null;
        }
    }

    private void senden(String nachricht) {
        if (nachricht.startsWith("/")) {
            player().connection.sendCommand(nachricht.substring(1));
        } else {
            player().connection.sendChat(nachricht);
        }
    }

    private static String zufallsText(int laenge, boolean auchGross) {
        StringBuilder bau = new StringBuilder(laenge);
        for (int i = 0; i < laenge; i++) {
            char c = (char) ('a' + ZUFALL.nextInt(26));
            if (auchGross && ZUFALL.nextBoolean()) {
                c = Character.toUpperCase(c);
            }
            bau.append(c);
        }
        return bau.toString();
    }
}
