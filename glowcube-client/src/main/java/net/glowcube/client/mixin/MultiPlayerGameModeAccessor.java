package net.glowcube.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Nach einem Wechsel des Hotbar-Platzes muss der Client dem Server sagen,
 * was er jetzt in der Hand haelt. Das Spiel macht das von selbst, aber erst
 * im naechsten Tick - wer im selben Tick setzen, bauen und zuruecktauschen
 * will (Scaffold, Nuker, AutoTool), kaeme zu spaet. Die Methode dafuer ist
 * geschuetzt; ein Invoker macht sie erreichbar.
 */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Invoker("ensureHasSentCarriedItem")
    void glowcube$auswahlMelden();
}
