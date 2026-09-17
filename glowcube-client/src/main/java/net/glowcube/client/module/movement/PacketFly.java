package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Movement;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Uebertragen aus BleachHack (GPL-3.0), Modul {@code PacketFly}.
 *
 * <p>Der Spieler bewegt sich hier nicht durch die Spielphysik, sondern nur
 * durch das, was er dem Server ueber seine Lage erzaehlt. Deshalb liegen
 * Physik und das normale Bewegungspaket still ({@link #blockClientMove()} und
 * {@link #blockMovementPackets()}), und das Modul schickt seine Position
 * selbst.
 *
 * <p>Beide Betriebsarten des Originals sind da:
 * <ul>
 *   <li><b>Phase</b> fuehrt eine eigene, gemerkte Position mit und setzt den
 *       Spieler jeden Tick dorthin. Weil das Spiel dabei nie eine Kollision
 *       rechnet, geht es durch Bloecke.</li>
 *   <li><b>Paket</b> laesst den Spieler, wo er ist, und meldet nur Positionen
 *       daneben. Weniger auffaellig, aber ohne Phasing.</li>
 * </ul>
 *
 * <p>Die Einstellung <i>Fall</i> ist der Original-Antikick: alle paar Ticks
 * eine Meldung nach unten, weil ein Server sonst irgendwann merkt, dass da
 * jemand ewig in der Luft steht.
 *
 * <p><b>Abweichung vom Original:</b> BleachHack schreibt bei der
 * Rueckmeldung des Servers Blickwinkel und Neigung im Paket um, damit die
 * Kamera nicht springt. In 1.21.11 ist dieses Paket unveraenderlich - die
 * Werte stecken in einem Record. Uebrig bleibt der zweite Teil, den das
 * Original ohnehin als Schalter hat: die Rueckmeldung ganz schlucken.
 */
public final class PacketFly extends Module {
    private final ModeSetting modus = register(new ModeSetting("Modus",
            "Phase geht durch Bloecke, Paket bleibt an der Physik",
            "Phase", "Phase", "Paket"));
    private final NumberSetting waagrecht = register(new NumberSetting("Tempo",
            "Bloecke je Tick zur Seite", 0.5, 0.05, 2.0, 0.05));
    private final NumberSetting senkrecht = register(new NumberSetting("Steigen",
            "Bloecke je Tick nach oben und unten", 0.5, 0.05, 2.0, 0.05));
    private final NumberSetting fallen = register(new NumberSetting("Fallabstand",
            "Nach so vielen Ticks einmal nach unten melden (0 = nie)", 20, 0, 40, 1));
    private final BooleanSetting schlucken = register(new BooleanSetting("Rubberband schlucken",
            "Korrekturen des Servers verwerfen, statt sich zurueckziehen zu lassen", true));

    /** Die selbst gefuehrte Position - im Phase-Modus die Wahrheit. */
    private Vec3 gemerkt;
    private int takt;

    public PacketFly() {
        super("PacketFly", "Fliegt ueber Pakete statt ueber die Physik", Category.EXPLOIT);
    }

    @Override
    public void onEnable() {
        if (!inGame()) {
            return;
        }
        gemerkt = player().position();
        takt = 0;
    }

    @Override
    public boolean blockClientMove() {
        return modus.is("Phase");
    }

    @Override
    public boolean blockMovementPackets() {
        return true;
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        return schlucken.get() && packet instanceof ClientboundPlayerPositionPacket;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        // Reine Blickpakete verraten die Luecke zwischen erzaehlter und
        // wirklicher Lage - weg damit.
        if (packet instanceof ServerboundMovePlayerPacket.Rot) {
            return true;
        }
        // Volle Pakete (Position samt Blick) auf die reine Positionsform
        // eindampfen, so wie im Original.
        if (packet instanceof ServerboundMovePlayerPacket.PosRot voll) {
            player().connection.send(new ServerboundMovePlayerPacket.Pos(
                    voll.getX(0.0), voll.getY(0.0), voll.getZ(0.0),
                    voll.isOnGround(), player().horizontalCollision));
            return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (!player().isAlive()) {
            return;
        }
        if (gemerkt == null) {
            gemerkt = player().position();
        }

        double hTempo = waagrecht.get();
        double vTempo = senkrecht.get();
        takt++;

        Vec3 vorwaerts = new Vec3(0.0, 0.0, hTempo)
                .yRot(-(float) Math.toRadians(player().getYRot()));
        Vec3 richtung = Vec3.ZERO;

        if (mc.options.keyUp.isDown()) {
            richtung = richtung.add(vorwaerts);
        }
        if (mc.options.keyDown.isDown()) {
            richtung = richtung.add(vorwaerts.reverse());
        }
        if (mc.options.keyJump.isDown()) {
            richtung = richtung.add(0.0, vTempo, 0.0);
        }
        if (mc.options.keyShift.isDown()) {
            richtung = richtung.add(0.0, -vTempo, 0.0);
        }
        if (mc.options.keyLeft.isDown()) {
            richtung = richtung.add(vorwaerts.yRot((float) Math.toRadians(90.0)));
        }
        if (mc.options.keyRight.isDown()) {
            richtung = richtung.add(vorwaerts.yRot((float) -Math.toRadians(90.0)));
        }

        boolean faellig = fallen.getInt() > 0 && takt > fallen.getInt();

        if (modus.is("Phase")) {
            if (faellig) {
                richtung = richtung.add(0.0, -vTempo, 0.0);
                takt = 0;
            }
            gemerkt = gemerkt.add(richtung);

            player().setDeltaMovement(Vec3.ZERO);
            player().setPos(gemerkt.x, gemerkt.y, gemerkt.z);

            // Zwei Meldungen: erst die Stelle in der Luft, dann einen
            // Fingerbreit darunter mit "ich stehe". Das Original macht es
            // genauso - der Server verbucht damit keinen Fall.
            player().connection.send(new ServerboundMovePlayerPacket.Pos(
                    gemerkt.x, gemerkt.y, gemerkt.z, false, false));
            player().connection.send(new ServerboundMovePlayerPacket.Pos(
                    gemerkt.x, gemerkt.y - 0.01, gemerkt.z, true, false));
        } else {
            if (faellig) {
                richtung = new Vec3(0.0, -vTempo, 0.0);
                takt = 0;
            }
            double x = player().getX() + richtung.x;
            double y = player().getY() + richtung.y;
            double z = player().getZ() + richtung.z;

            player().connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
            // Die zweite Meldung liegt weit unter der Welt. Der Server
            // verwirft sie als unmoeglich, verbucht aber "auf dem Boden" -
            // derselbe Kniff wie im Original.
            player().connection.send(new ServerboundMovePlayerPacket.Pos(
                    x, player().getY() - 420.69, z, true, false));
        }
    }

    @Override
    public void onDisable() {
        gemerkt = null;
        if (inGame()) {
            player().setDeltaMovement(Vec3.ZERO);
            player().fallDistance = 0;
        }
    }

    @Override
    public String hudSuffix() {
        return modus.get();
    }
}
