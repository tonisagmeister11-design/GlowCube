package net.glowcube.client.module.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.Render3D;
import net.minecraft.world.entity.Entity;

/** Kaesten um Lebewesen und Items. */
public final class EntityEsp extends Module {
    private final NumberSetting range = register(new NumberSetting("Range",
            "Umkreis in Bloecken", 64, 8, 256, 8));
    private final BooleanSetting players = register(new BooleanSetting("Players", "Spieler", true));
    private final BooleanSetting hostile = register(new BooleanSetting("Hostile", "Monster", true));
    private final BooleanSetting passive = register(new BooleanSetting("Passive", "Tiere", false));
    private final BooleanSetting items = register(new BooleanSetting("Items", "Liegende Gegenstaende", false));

    public EntityEsp() {
        super("EntityESP", "Kaesten um Entities", Category.RENDER);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (!inGame()) {
            return;
        }
        double maxDistanceSq = range.get() * range.get();
        for (Entity entity : level().entitiesForRendering()) {
            if (entity == player() || entity.distanceToSqr(player()) > maxDistanceSq) {
                continue;
            }
            int color = EntityGroups.colorFor(entity, players.get(), hostile.get(), passive.get(), items.get());
            if (color != 0) {
                Render3D.box(context, entity.getBoundingBox(), color, true);
            }
        }
    }
}
