package net.glowcube.client.module.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/** Eine Einteilung, die sich EntityESP und Tracers teilen. */
final class EntityGroups {
    static final int PLAYER  = 0xFFFF4D6D;
    static final int HOSTILE = 0xFFFF9F45;
    static final int PASSIVE = 0xFF3BF0D4;
    static final int ITEM    = 0xFF7FC4FF;

    private EntityGroups() {
    }

    /** 0 heisst: nicht anzeigen. */
    static int colorFor(Entity entity, boolean players, boolean hostile, boolean passive, boolean items) {
        if (entity instanceof Player) {
            return players ? PLAYER : 0;
        }
        if (entity instanceof Monster) {
            return hostile ? HOSTILE : 0;
        }
        if (entity instanceof Animal) {
            return passive ? PASSIVE : 0;
        }
        if (entity instanceof ItemEntity) {
            return items ? ITEM : 0;
        }
        return 0;
    }
}
