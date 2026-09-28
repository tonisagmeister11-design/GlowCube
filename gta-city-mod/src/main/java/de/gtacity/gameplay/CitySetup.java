package de.gtacity.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;

/** One-time world setup for city worlds: spawn point and game rules. */
final class CitySetup {
    private CitySetup() {
    }

    static void setSpawn(ServerLevel level, BlockPos pos) {
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), pos, 0.0F, 0.0F));
    }

    static void setGameRules(ServerLevel level, MinecraftServer server) {
        GameRules rules = level.getGameRules();
        rules.set(GameRules.SPAWN_PHANTOMS, false, server);
        rules.set(GameRules.SPAWN_PATROLS, false, server);
        rules.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
    }
}
