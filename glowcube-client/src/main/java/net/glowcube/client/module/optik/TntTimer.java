package net.glowcube.client.module.optik;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

import java.util.Locale;

/**
 * Zeigt ueber gezuendetem TNT, wann es hochgeht - aus AxolotlClient
 * ({@code TntTime}). Statt eines eigenen Render-Mixins bekommt jedes TNT
 * clientseitig ein Namensschild mit der Restzeit; das zeichnet Minecraft auf
 * 1.21.11 und 26.3 von selbst. Der Server sieht davon nichts.
 */
public final class TntTimer extends Module {
    public TntTimer() {
        super("TNT-Timer", "Zeigt ueber gezuendetem TNT die Restzeit bis zur Explosion", Category.OPTIK);
    }

    @Override
    public void onTick() {
        if (mc.level == null) {
            return;
        }
        for (Entity wesen : mc.level.entitiesForRendering()) {
            if (wesen instanceof PrimedTnt tnt) {
                float sekunden = tnt.getFuse() / 20f;
                ChatFormatting farbe = sekunden > 3 ? ChatFormatting.GREEN
                        : sekunden > 1.5f ? ChatFormatting.YELLOW : ChatFormatting.RED;
                tnt.setCustomName(Component.literal(String.format(Locale.ROOT, "%.2f", sekunden)).withStyle(farbe));
                tnt.setCustomNameVisible(true);
            }
        }
    }

    @Override
    public void onDisable() {
        if (mc.level == null) {
            return;
        }
        for (Entity wesen : mc.level.entitiesForRendering()) {
            if (wesen instanceof PrimedTnt tnt) {
                tnt.setCustomName(null);
                tnt.setCustomNameVisible(false);
            }
        }
    }
}
