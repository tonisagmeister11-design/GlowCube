package net.glowcube.client.mixin;

import net.glowcube.client.module.world.AntiChunkBan;
import net.minecraft.network.CompressionDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

/**
 * Haelfte zwei von AntiChunkBan - der Teil gegen den Chunkban.
 *
 * <p>Ein entpacktes Paket darf hoechstens 8 MiB gross werden. Wer einen Chunk
 * praepariert, der darueber liegt, kickt damit jeden, der ihn laedt; die Stelle
 * bleibt dann dauerhaft unbetretbar. Wir heben die Grenze auf, solange das
 * Modul an ist - dieselbe Stelle, an der BleachHack {@code PacketInflater}
 * abfaengt, nur ueber die Konstante statt ueber eine nachgebaute Methode.
 */
@Mixin(CompressionDecoder.class)
public abstract class CompressionDecoderMixin {

    @ModifyConstant(method = "decode", constant = @Constant(intValue = 8388608), require = 0)
    private int glowcube$grenzeHeben(int grenze) {
        return AntiChunkBan.aktiv() ? Integer.MAX_VALUE : grenze;
    }
}
