package net.glowcube.client.module.world;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Uebertragen aus BleachHack (GPL-3.0), Modul {@code FakeLag}.
 *
 * <p>Die Bewegungspakete werden nicht gesendet, sondern gesammelt. Fuer den
 * Server steht man so lange still, wo man in Wahrheit schon weg ist; beim
 * Ausschalten geht der ganze Stapel auf einmal hinaus und man "springt"
 * dorthin. Genau das sieht von aussen aus wie Lag.
 *
 * <p>Original-Reihenfolge und -Grenzfaelle sind uebernommen: Modus
 * (Dauerhaft/Puls), die abschaltende Zeitgrenze, und beim Leeren des Stapels
 * werden die reinen Blickpakete uebersprungen - nur die mit Position
 * gehen hinaus, sonst waere die Reihenfolge fuer den Server unsinnig.
 */
public final class FakeLag extends Module {
    private final ModeSetting modus = register(new ModeSetting("Modus",
            "Dauerhaft sammelt, bis du ausschaltest. Puls leert regelmaessig.",
            "Dauerhaft", "Dauerhaft", "Puls"));
    private final BooleanSetting grenzeAn = register(new BooleanSetting("Zeitgrenze",
            "Nach einer Weile von selbst abschalten", false));
    private final NumberSetting grenze = register(new NumberSetting("Sekunden",
            "Wie lange, bevor abgeschaltet wird", 5, 0, 15, 1));
    private final NumberSetting puls = register(new NumberSetting("Pulslaenge",
            "Sekunden zwischen zwei Entladungen", 1, 0, 5, 1));

    private final List<ServerboundMovePlayerPacket> stapel = new ArrayList<>();
    private long start;

    public FakeLag() {
        super("FakeLag", "Haelt Bewegungspakete zurueck und laesst sie gebuendelt los",
                Category.EXPLOIT);
    }

    @Override
    public void onEnable() {
        start = System.currentTimeMillis();
        stapel.clear();
    }

    @Override
    public void onDisable() {
        if (inGame()) {
            entladen();
        }
        stapel.clear();
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket bewegung) {
            stapel.add(bewegung);
            return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        long vergangen = System.currentTimeMillis() - start;
        if (modus.is("Dauerhaft")) {
            if (grenzeAn.get() && vergangen > grenze.get() * 1000L) {
                setEnabled(false);
            }
        } else if (vergangen > puls.get() * 1000L) {
            // Aus und gleich wieder an: onDisable entlaedt, onEnable stellt
            // die Uhr zurueck. So macht es das Original auch.
            setEnabled(false);
            setEnabled(true);
        }
    }

    /**
     * Die reinen Blickpakete bleiben liegen. Sie tragen keine Position; in
     * einem Stapel, der sonst aus Positionen besteht, wuerden sie den Server
     * nur zwischendurch auf die alte Stelle zurueckrechnen lassen.
     */
    private void entladen() {
        for (ServerboundMovePlayerPacket paket : new ArrayList<>(stapel)) {
            if (!(paket instanceof ServerboundMovePlayerPacket.Rot)) {
                player().connection.send(paket);
            }
        }
        stapel.clear();
    }

    @Override
    public String hudSuffix() {
        return stapel.isEmpty() ? null : String.valueOf(stapel.size());
    }
}
