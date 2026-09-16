package net.glowcube.client.mixin;

import net.glowcube.client.module.world.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Timer. Der Taktgeber rechnet je Frame aus, wie viele Spiel-Ticks faellig
 * sind, und gibt die Zahl aus advanceTime zurueck. Wird diese Zahl mit dem
 * Faktor vervielfacht, laeuft die Welt entsprechend schneller oder langsamer.
 *
 * Nachbau des Timer aus BleachHack (GPL-3.0), dort MixinRenderTickCounter.
 * Fuer 1.21.11 sitzt derselbe Eingriff an DeltaTracker.Timer.advanceTime.
 *
 * Der Uebertrag merkt sich den Nachkommarest, damit auch krumme Faktoren wie
 * 1,5 auf Dauer stimmen und nicht dauernd abgerundet werden.
 */
@Mixin(targets = "net.minecraft.client.DeltaTracker$Timer")
public final class DeltaTrackerTimerMixin {
    private static float glowcube$uebertrag;

    @Inject(method = "advanceTime(JZ)I", at = @At("RETURN"), cancellable = true)
    private void glowcube$beschleunigen(long millis, boolean run, CallbackInfoReturnable<Integer> info) {
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
