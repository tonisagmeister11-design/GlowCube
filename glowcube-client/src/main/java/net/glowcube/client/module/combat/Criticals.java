package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.mixin.LivingEntityAccessor;
import net.minecraft.network.protocol.Packet;
import net.glowcube.client.mixin.ServerboundInteractPacketAccessor;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code Criticals}.
 *
 * <p>Ein kritischer Treffer verlangt, dass man im Fallen zuschlaegt. Statt
 * wirklich zu springen, erzaehlt man dem Server kurz, man faelle - ueber
 * zwei oder drei Positionsmeldungen unmittelbar vor dem Schlag. Die
 * Hoehenwerte sind die Zahlen des Originals und sehen willkuerlich aus,
 * weil sie es sind: sie sind an bestimmten Server-Pruefungen
 * vorbeigemessen, nicht hergeleitet. Genau deshalb steht hier keine
 * eigene Zahl.
 *
 * <ul>
 *   <li><b>Paket</b> - zwei Meldungen, 0,0625 und zurueck.</li>
 *   <li><b>NCP neu</b> - zwei Meldungen mit winzigem Versatz.</li>
 *   <li><b>NCP alt</b> - drei Meldungen mit krummen Zahlen.</li>
 *   <li><b>Sprung</b> - wirklich springen und im Scheitelpunkt schlagen.</li>
 *   <li><b>Huepfer</b> - ein kurzer Stups nach oben statt eines Sprungs.</li>
 * </ul>
 *
 * <p>Fuer die Keule ist der Trick ein anderer und im Original getrennt
 * gehalten: dort zaehlt die Fallhoehe, also wird sie gemeldet - je mehr,
 * desto mehr Schaden.
 */
public final class Criticals extends Module {
    private final ModeSetting modus = register(new ModeSetting("Modus",
            "Wie der kritische Treffer erzwungen wird",
            "Paket", "Aus", "Paket", "NCP neu", "NCP alt", "Sprung", "Huepfer"));
    private final BooleanSetting nurKillAura = register(new BooleanSetting("Nur mit KillAura",
            "Nur zuschlagen lassen, wenn KillAura das Ziel gewaehlt hat", false));
    private final BooleanSetting keule = register(new BooleanSetting("Keulenschlag",
            "Mit einer Keule immer den Schmetterschlag ausloesen", true));
    private final NumberSetting zusatzHoehe = register(new NumberSetting("Zusatzhoehe",
            "Wie viel Fallhoehe die Keule zusaetzlich melden soll", 0, 0, 100, 1));

    private Packet<?> schlagPaket;
    private Packet<?> schwungPaket;
    private boolean sendenAusstehend;
    private int wartetakte;
    private double letztesY;
    private boolean wartetAufScheitel;

    public Criticals() {
        super("Criticals", "Erzwingt kritische Treffer", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        schlagPaket = null;
        schwungPaket = null;
        sendenAusstehend = false;
        wartetakte = 0;
        letztesY = 0.0;
        wartetAufScheitel = false;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!inGame() || modus.is("Aus")) {
            return false;
        }

        if (packet instanceof ServerboundInteractPacket schlag) {
            if (keule.get() && player().getMainHandItem().getItem() instanceof MaceItem) {
                if (player().isFallFlying()) {
                    return false;
                }
                // Der Schmetterschlag zaehlt die gemeldete Fallhoehe. 1.501
                // ist die Schwelle, ab der Vanilla ihn ueberhaupt wertet.
                melden(0.0);
                melden(1.501 + zusatzHoehe.get());
                melden(0.0);
                return false;
            }

            if (kritUnmoeglich()) {
                return false;
            }
            Entity ziel = level().getEntity(
                    ((ServerboundInteractPacketAccessor) schlag).glowcube$zielNummer());
            if (!(ziel instanceof LivingEntity)) {
                return false;
            }
            if (nurKillAura.get() && ziel != KillAura.aktuellesZiel()) {
                return false;
            }

            switch (modus.get()) {
                case "Paket" -> {
                    melden(0.0625);
                    melden(0.0);
                }
                case "NCP neu" -> {
                    melden(0.0000008);
                    melden(0.0);
                }
                case "NCP alt" -> {
                    melden(0.11);
                    melden(0.1100013579);
                    melden(0.0000013579);
                }
                case "Sprung", "Huepfer" -> {
                    if (!sendenAusstehend) {
                        sendenAusstehend = true;
                        schlagPaket = packet;
                        if (modus.is("Sprung")) {
                            ((LivingEntityAccessor) player()).glowcube$springen();
                            wartetAufScheitel = true;
                            letztesY = player().getY();
                        } else {
                            Vec3 tempo = player().getDeltaMovement();
                            player().setDeltaMovement(tempo.x, 0.25, tempo.z);
                            wartetakte = 4;
                        }
                        // Der Schlag geht erst spaeter hinaus - jetzt
                        // zurueckhalten.
                        return true;
                    }
                }
                default -> {
                }
            }
            return false;
        }

        if (net.glowcube.client.render.Netz.istSchwungPaket(packet) && !modus.is("Paket")) {
            if (kritUnmoeglich()) {
                return false;
            }
            if (sendenAusstehend && schwungPaket == null) {
                schwungPaket = packet;
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTick() {
        if (!sendenAusstehend) {
            return;
        }

        if (modus.is("Sprung") && wartetAufScheitel) {
            double jetzt = player().getY();
            if (jetzt <= letztesY) {
                // Scheitelpunkt erreicht - ab hier faellt man, und ab hier
                // zaehlt der Treffer als kritisch.
                wartetAufScheitel = false;
                wartetakte = 0;
            }
            letztesY = jetzt;
            return;
        }

        if (wartetakte > 0) {
            wartetakte--;
            return;
        }

        if (schlagPaket == null || schwungPaket == null) {
            sendenAusstehend = false;
            return;
        }
        player().connection.send(schlagPaket);
        player().connection.send(schwungPaket);
        schlagPaket = null;
        schwungPaket = null;
        sendenAusstehend = false;
    }

    /** Eine Positionsmeldung um {@code hoehe} hoeher, ohne Bodenkontakt. */
    private void melden(double hoehe) {
        player().connection.send(new ServerboundMovePlayerPacket.Pos(
                player().getX(), player().getY() + hoehe, player().getZ(), false, false));
    }

    /**
     * Wann ein kritischer Treffer ohnehin nicht zustande kommt - Vanilla
     * streicht ihn im Wasser, in Lava, an einer Leiter und am Boden.
     * Spinnweben nehmen dem Sprung die Wirkung.
     */
    private boolean kritUnmoeglich() {
        if ((modus.is("Sprung") || modus.is("Huepfer")) && inSpinnweben()) {
            return true;
        }
        return !player().onGround() || player().isInWater() || player().isInLava()
                || player().onClimbable();
    }

    /** Spinnweben nehmen dem Sprung die Wirkung - dann lohnt der Umweg nicht. */
    private boolean inSpinnweben() {
        return level().getBlockState(player().blockPosition()).is(Blocks.COBWEB)
                || level().getBlockState(player().blockPosition().above()).is(Blocks.COBWEB);
    }

    @Override
    public String hudSuffix() {
        return modus.get();
    }
}
