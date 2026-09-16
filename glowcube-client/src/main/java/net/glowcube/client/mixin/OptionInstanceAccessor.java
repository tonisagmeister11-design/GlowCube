package net.glowcube.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Schreibt den Wert einer Option direkt. Der normale Weg ueber set() laesst
 * den Pruefer laufen, der Helligkeit bei 1.0 und Sichtfeld bei 30 abschneidet.
 */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
    @Accessor("value")
    void glowcube$setValue(T value);
}
