package net.glowcube.client.module.player;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.mixin.MinecraftAccessor;
import net.minecraft.world.item.BlockItem;

/**
 * FastPlace: nimmt die Pause zwischen zwei Rechtsklicks heraus (Vanilla:
 * 4 Ticks). Bloecke setzen, Kristalle legen, Eier werfen - alles so schnell,
 * wie die Taste gedrueckt bleibt.
 */
public final class FastPlace extends Module {
    private final NumberSetting pause = register(new NumberSetting("Pause",
            "Ticks zwischen zwei Rechtsklicks (Vanilla 4)", 0, 0, 3, 1));
    private final BooleanSetting nurBloecke = register(new BooleanSetting("Nur Bloecke",
            "Nur wenn ein Block in der Hand liegt", false));

    public FastPlace() {
        super("FastPlace", "Rechtsklick ohne Pause - schneller bauen", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (nurBloecke.get() && !(player().getMainHandItem().getItem() instanceof BlockItem)
                && !(player().getOffhandItem().getItem() instanceof BlockItem)) {
            return;
        }
        MinecraftAccessor zugang = (MinecraftAccessor) (Object) mc;
        if (zugang.glowcube$rechtsklickPause() > pause.getInt()) {
            zugang.glowcube$setzeRechtsklickPause(pause.getInt());
        }
    }
}
