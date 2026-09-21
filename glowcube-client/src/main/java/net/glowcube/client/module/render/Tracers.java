package net.glowcube.client.module.render;

import net.glowcube.client.render.WeltRender;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.world.entity.Entity;

/** Linien vom Fadenkreuz zu allem, was zaehlt. */
public final class Tracers extends Module {
    private final NumberSetting range = register(new NumberSetting("Range",
            "Umkreis in Bloecken", 96, 8, 256, 8));
    private final BooleanSetting players = register(new BooleanSetting("Players", "Spieler", true));
    private final BooleanSetting hostile = register(new BooleanSetting("Hostile", "Monster", false));
    private final BooleanSetting passive = register(new BooleanSetting("Passive", "Tiere", false));
    private final BooleanSetting items = register(new BooleanSetting("Items", "Liegende Gegenstaende", false));

    public Tracers() {
        super("Tracers", "Linien zu Entities", Category.RENDER);
    }

    @Override
    public void onWorldRender(WeltRender render) {
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
                render.tracer(entity.getBoundingBox().getCenter(), color);
            }
        }
    }
}
