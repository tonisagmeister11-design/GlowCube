package net.glowcube.client.module.misc;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.integration.SeedBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Fliegt den Spieler selbsttaetig eine Spirale ab.
 *
 * SeedCrackerX rechnet aus Merkmalen der Welt - Erzadern, Dungeons, Strukturen -
 * den Seed zurueck. Es braucht davon genug, und es sieht nur, was der Client
 * ohnehin geladen bekommt. Wer stehen bleibt, wartet also ewig; wer Flaeche
 * abfliegt, ist schnell fertig. Genau das macht dieses Modul, damit man nicht
 * selbst stundenlang umherfliegen muss.
 *
 * Die Spirale wird in Chunk-Schritten aufgebaut und waechst nach aussen, so
 * dass um den Startpunkt herum keine Luecken bleiben.
 */
public final class SeedHunt extends Module {
    private final NumberSetting speed = register(new NumberSetting("Speed",
            "Bloecke pro Tick", 1.2, 0.2, 4.0, 0.1));
    private final NumberSetting height = register(new NumberSetting("Height",
            "Hoehe ueber dem Startpunkt", 80, 0, 200, 10));
    private final NumberSetting spacing = register(new NumberSetting("Spacing",
            "Abstand der Bahnen in Bloecken", 96, 32, 256, 16));
    private final NumberSetting legs = register(new NumberSetting("Legs",
            "Wie viele Teilstrecken, dann ist Schluss", 64, 4, 512, 4));
    private final BooleanSetting stopOnSeed = register(new BooleanSetting("StopOnSeed",
            "Aufhoeren, sobald der Seed da ist", true));
    private final BooleanSetting autoStart = register(new BooleanSetting("AutoStart",
            "Beim Betreten einer Welt von selbst losfliegen", false));

    /** Wird vom ModuleManager beim Betreten einer Welt abgefragt. */
    public boolean autoStart() {
        return autoStart.get();
    }

    // Startpunkt und Stand der Spirale.
    private Vec3 anker = Vec3.ZERO;
    private int bein;
    private int schritteImBein;
    private int gelaufen;
    private int richtung;
    private Vec3 ziel = Vec3.ZERO;
    private int ansage;

    public SeedHunt() {
        super("SeedHunt", "Fliegt eine Spirale ab, damit SeedCracker satt wird",
                Category.MISC, InputConstants.KEY_B);
    }

    @Override
    public void onEnable() {
        if (!inGame()) {
            return;
        }
        anker = player().position();
        bein = 1;
        schritteImBein = 1;
        gelaufen = 0;
        richtung = 0;
        ansage = 0;
        naechstesZiel();
        melden("SeedHunt laeuft - Spirale um den Startpunkt");
    }

    @Override
    public void onDisable() {
        if (inGame()) {
            player().setDeltaMovement(0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onTick() {
        if (stopOnSeed.get() && SeedBridge.seed() != null) {
            melden("Seed gefunden: " + SeedBridge.seed() + " - SeedHunt aus");
            setEnabled(false);
            return;
        }
        if (bein > legs.getInt()) {
            melden("Spirale abgeflogen - SeedHunt aus");
            setEnabled(false);
            return;
        }

        Vec3 hier = player().position();
        double dx = ziel.x - hier.x;
        double dz = ziel.z - hier.z;
        double entfernung = Math.sqrt(dx * dx + dz * dz);

        if (entfernung < 2.0) {
            naechstesZiel();
            return;
        }

        // Waagerecht auf das Ziel zu, senkrecht auf die Wunschhoehe.
        double tempo = speed.get();
        double zielHoehe = anker.y + height.get();
        double dy = Math.max(-tempo, Math.min(tempo, zielHoehe - hier.y));

        player().setDeltaMovement(dx / entfernung * tempo, dy, dz / entfernung * tempo);
        player().setOnGround(false);
        player().resetFallDistance();

        // Blickrichtung mitziehen, sonst fliegt man seitwaerts durch die Welt.
        player().setYRot((float) (Math.toDegrees(Math.atan2(-dx, dz))));

        if (++ansage >= 100) {
            ansage = 0;
            melden("SeedHunt: Bahn " + bein + " von " + legs.getInt());
        }
    }

    /**
     * Quadratische Spirale: zwei Teilstrecken gleicher Laenge, dann wird die
     * Laenge um einen Schritt groesser. So entsteht eine Bahn ohne Luecken.
     */
    private void naechstesZiel() {
        if (gelaufen >= schritteImBein) {
            gelaufen = 0;
            richtung = (richtung + 1) % 4;
            bein++;
            if (bein % 2 == 1) {
                schritteImBein++;
            }
        }
        gelaufen++;

        double weite = spacing.get();
        Vec3 versatz = switch (richtung) {
            case 0 -> new Vec3(weite, 0, 0);
            case 1 -> new Vec3(0, 0, weite);
            case 2 -> new Vec3(-weite, 0, 0);
            default -> new Vec3(0, 0, -weite);
        };
        Vec3 bisher = ziel.equals(Vec3.ZERO) ? player().position() : ziel;
        ziel = bisher.add(versatz.x, 0.0, versatz.z);
    }

    private void melden(String text) {
        if (inGame()) {
            player().displayClientMessage(Component.literal("[GlowCube] " + text), true);
        }
    }

    @Override
    public String hudSuffix() {
        return "Bahn " + bein;
    }
}
