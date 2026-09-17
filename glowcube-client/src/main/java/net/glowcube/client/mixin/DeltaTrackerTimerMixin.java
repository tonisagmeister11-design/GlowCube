package net.glowcube.client.mixin;

import net.glowcube.client.module.world.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Timer. Der Taktgeber rechnet je Frame aus, wie viele Spiel-Ticks faellig
 * sind, und gibt die Zahl zurueck. Wird sie mit dem Faktor vervielfacht,
 * laeuft die Welt entsprechend schneller oder langsamer.
 *
 * Nachbau des Timer aus BleachHack (GPL-3.0), dort MixinRenderTickCounter.
 *
 * <p>Der Name der Zielmethode hat sich mit den Fassungen bewegt: bis 1.21.x
 * hiess sie {@code advanceTime(long, boolean)}, ab 26.x
 * {@code advanceGameTime(long)}. Beide Eingriffe stehen deshalb hier, jeder
 * mit {@code require = 0} - so greift genau der, dessen Methode es in der
 * gebauten Fassung wirklich gibt, und der andere faellt still weg.
 *
 * <p>Der Uebertrag merkt sich den Nachkommarest, damit auch krumme Faktoren
 * wie 1,5 auf Dauer stimmen und nicht dauernd abgerundet werden.
 */
@Mixin(targets = "net.minecraft.client.DeltaTracker$Timer")
public final class DeltaTrackerTimerMixin {
    private static float glowcube$uebertrag;

    // Bis 1.21.x
    @Inject(method = "advanceTime(JZ)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void glowcube$beschleunigenAlt(long millis, boolean run, CallbackInfoReturnable<Integer> info) {
        glowcube$anpassen(info);
    }

    // Ab 26.x
    @Inject(method = "advanceGameTime(J)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void glowcube$beschleunigenNeu(long millis, CallbackInfoReturnable<Integer> info) {
        glowcube$anpassen(info);
    }

    private static void glowcube$anpassen(CallbackInfoReturnable<Integer> info) {
        float faktor = Timer.factor();
        if (faktor == 1.0f) {
            return;
        }
        glowcube$uebertrag += info.getReturnValueI() * faktor;
        int ticks = (int) glowcube$uebertrag;
        glowcube$uebertrag -= ticks;
        info.setReturnValue(ticks);
    }
}
