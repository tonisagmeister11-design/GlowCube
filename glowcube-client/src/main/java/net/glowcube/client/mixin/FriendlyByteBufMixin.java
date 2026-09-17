package net.glowcube.client.mixin;

import net.glowcube.client.module.world.AntiChunkBan;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Haelfte eins von AntiChunkBan - der Teil gegen den Bookban.
 *
 * <p>Beim Lesen von NBT aus einem Paket zaehlt ein Waechter mit, wie gross das
 * Ergebnis werden darf. Ist ein Buch absichtlich zu gross, schlaegt er zu und
 * der Client fliegt raus. Wir tauschen den Waechter gegen einen ohne Grenze -
 * das Paket wird dann gelesen statt die Verbindung getrennt.
 *
 * <p>{@code require = 0} mit Absicht: findet Mixin den Aufruf nicht, bleibt
 * die Grenze eben stehen. Ein nicht wirkender Bookban-Schutz ist immer noch
 * besser als ein Spiel, das gar nicht mehr startet - und um genau diese
 * Wahl geht es hier, denn ein fehlgeschlagener Eingriff mit
 * {@code require = 1} bricht den Start ab.
 */
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufMixin {

    @ModifyArg(
            method = "readNbt(Lio/netty/buffer/ByteBuf;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;readNbt(Lio/netty/buffer/ByteBuf;Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/Tag;"),
            require = 0)
    private static NbtAccounter glowcube$ohneGrenze(NbtAccounter waechter) {
        return AntiChunkBan.aktiv() ? NbtAccounter.unlimitedHeap() : waechter;
    }
}
