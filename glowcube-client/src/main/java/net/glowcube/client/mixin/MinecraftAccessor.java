package net.glowcube.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Die zuletzt gemessene Bildrate. Genau so liest sie AxolotlClient (1.21.11
 * und 26.2): ueber das statische Feld {@code fps}, das es auf beiden
 * Fassungen unter diesem Namen gibt - ein Getter ist nicht zugesichert.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("fps")
    static int glowcube$fps() {
        throw new AssertionError();
    }
}
