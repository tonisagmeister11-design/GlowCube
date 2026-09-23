package net.glowcube.client.agent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;

/**
 * Die Stellen, an denen sich der Agent auf 26.x anders schreibt als auf
 * 1.21.11 - gebuendelt, damit die uebrigen Agent-Dateien wortgleich zur
 * 1.21-Fassung bleiben koennen.
 */
final class Fassung26 {
    private Fassung26() {
    }

    /** Ein neuer Mannequin-Koerper. Ab 26.x fuehrt EntityType kein MANNEQUIN-Feld mehr. */
    @SuppressWarnings("unchecked")
    static Mannequin mannequin(ServerLevel welt) {
        EntityType<?> typ = EntityType.byString("minecraft:mannequin").orElse(null);
        if (typ == null) {
            return null;
        }
        Object neu = ((EntityType<Mannequin>) typ).create(welt, EntitySpawnReason.COMMAND);
        return neu instanceof Mannequin m ? m : null;
    }

    /** Armschwung, fuer alle in der Naehe sichtbar. */
    static void schwingen(LivingEntity wer) {
        // wird in der naechsten Runde mit dem 26.x-Schwung angebunden
    }
}
