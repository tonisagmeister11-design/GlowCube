package net.glowcube.client.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Macht das Feld {@code onGround} im Bewegungspaket beschreibbar.
 *
 * <p>NoFall lebt davon: der Server rechnet den Sturzschaden aus dem, was der
 * Client ueber seine eigene Lage meldet. Steht in jedem Paket "ich stehe",
 * entsteht nie ein Sturz.
 *
 * <p>Frueher stand die Umschreiberei hier im Konstruktor. Das war zu frueh
 * und zu breit: es traf auch die Pakete, die Module selbst bauen - und
 * Criticals braucht dort das genaue Gegenteil ("ich falle gerade"). Jetzt
 * setzt NoFall den Wert erst beim Senden, und die Pakete, die ein Modul
 * aus seinem eigenen Haken heraus schickt, kommen an diesem Weg gar nicht
 * vorbei.
 */
@Mixin(ServerboundMovePlayerPacket.class)
public abstract class ServerboundMovePlayerPacketMixin implements AmBodenSetzbar {

    @Shadow
    @Final
    @Mutable
    private boolean onGround;

    @Override
    public void glowcube$setzeAmBoden(boolean wert) {
        this.onGround = wert;
    }
}
