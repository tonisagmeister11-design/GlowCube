package net.glowcube.client.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.glowcube.client.module.world.AntiChunkBan;
import net.minecraft.network.CompressionDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Haelfte eins von AntiChunkBan - der Teil gegen den Chunkban.
 *
 * <p>Ein entpacktes Paket darf hoechstens 8 MiB gross werden. Wer einen Chunk
 * praepariert, der darueber liegt, kickt damit jeden, der ihn laedt; die
 * Stelle bleibt dann dauerhaft unbetretbar. Dieselbe Pruefung faengt
 * BleachHack ab, indem es das ganze Entpacken nachbaut.
 *
 * <p>Hier geht es einfacher: die Pruefung haengt an einem Schalter im
 * Decoder selbst ({@code validateDecompressed}), und der laesst sich
 * umlegen. Das ist stabiler als eine Konstante im Bytecode zu suchen - eine
 * Konstante kann der Uebersetzer verschieben, ein Feld nicht.
 *
 * <p>Der urspruengliche Wert wird gemerkt und zurueckgegeben, sobald das
 * Modul wieder aus ist. Sonst bliebe die Pruefung fuer den Rest der Sitzung
 * abgeschaltet.
 */
@Mixin(CompressionDecoder.class)
public abstract class CompressionDecoderMixin {

    @Shadow
    private boolean validateDecompressed;

    @Unique
    private boolean glowcube$umgelegt;

    @Inject(method = "decode", at = @At("HEAD"))
    private void glowcube$grenzeHeben(ChannelHandlerContext ctx, ByteBuf eingang,
                                      List<Object> ausgang, CallbackInfo info) {
        if (AntiChunkBan.aktiv()) {
            if (!glowcube$umgelegt) {
                glowcube$umgelegt = true;
                this.validateDecompressed = false;
            }
        } else if (glowcube$umgelegt) {
            glowcube$umgelegt = false;
            this.validateDecompressed = true;
        }
    }
}
