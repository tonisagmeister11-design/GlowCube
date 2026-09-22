package net.glowcube.client.module.optik;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.render.Netz;
import net.minecraft.client.CameraType;

/**
 * Sich umsehen, ohne dass sich die Spielfigur mitdreht - aus AxolotlClient
 * ({@code Freelook}). Solange es laeuft, steht die Kamera in der dritten
 * Person; die Mausbewegung dreht nur die Kamera ({@code EntityDrehenMixin}),
 * und die Kamera nimmt diesen Blick statt des Blicks der Figur
 * ({@code KameraMixin}). Beim Loslassen ist alles wie vorher.
 */
public final class Freelook extends Module {
    private static final int LINKE_ALT_TASTE = 342;
    private static Freelook instanz;

    private final ModeSetting modus = register(new ModeSetting("Modus",
            "Halten: nur solange die Taste gedrueckt ist. Umschalten: an und aus per Tastendruck",
            "Halten", "Halten", "Umschalten"));
    private final BooleanSetting umkehren = register(
            new BooleanSetting("Invertieren", "Maus hoch und runter umkehren", false));

    private boolean laeuft;
    /** Per Taste gestartet? Nur dann endet "Halten" beim Loslassen - ein Klick im Menue schaltet dauerhaft. */
    private boolean perTaste;
    private float gier;
    private float neigung;
    private CameraType vorher;

    public Freelook() {
        super("Freelook", "Umsehen, ohne dass sich die Spielfigur mitdreht", Category.OPTIK, LINKE_ALT_TASTE);
        instanz = this;
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            return;
        }
        gier = mc.player.getYRot();
        neigung = mc.player.getXRot();
        vorher = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        perTaste = hasKey() && Netz.tasteUnten(key());
        laeuft = true;
    }

    @Override
    public void onDisable() {
        if (laeuft && vorher != null) {
            mc.options.setCameraType(vorher);
        }
        laeuft = false;
    }

    @Override
    public void onTick() {
        if (modus.is("Halten") && perTaste && !Netz.tasteUnten(key())) {
            setEnabled(false);
        }
    }

    /** Ob die Kamera gerade frei ist. */
    public static boolean aktiv() {
        return instanz != null && instanz.laeuft && instanz.isEnabled();
    }

    /** Mausbewegung auf die freie Kamera - wie Entity.turn es sonst mit der Figur tut. */
    public static void drehen(double dy, double dx) {
        Freelook f = instanz;
        float nachOben = (float) dx * 0.15f * (f.umkehren.get() ? -1 : 1);
        f.gier += (float) dy * 0.15f;
        f.neigung = Math.max(-90f, Math.min(90f, f.neigung + nachOben));
    }

    public static float gier() {
        return instanz.gier;
    }

    public static float neigung() {
        return instanz.neigung;
    }
}
