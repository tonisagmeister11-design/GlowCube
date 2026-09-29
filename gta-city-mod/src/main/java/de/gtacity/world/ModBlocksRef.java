package de.gtacity.world;

import de.gtacity.registry.ModBlocks;
import net.minecraft.world.level.block.state.BlockState;

/** Access to the mod's own blocks from the world generator. */
final class ModBlocksRef {
    private ModBlocksRef() {
    }

    static BlockState billboard(net.minecraft.core.Direction facing, int tile) {
        return ModBlocks.BILLBOARD.tile(facing, tile);
    }

    static BlockState elevator() {
        return ModBlocks.ELEVATOR.defaultBlockState();
    }

    static BlockState vault() {
        return ModBlocks.BANK_VAULT.defaultBlockState();
    }

    static BlockState weaponCounter() {
        return ModBlocks.WEAPON_COUNTER.defaultBlockState();
    }

    static BlockState storeCounter() {
        return ModBlocks.STORE_COUNTER.defaultBlockState();
    }

    static BlockState carCounter() {
        return ModBlocks.CAR_COUNTER.defaultBlockState();
    }

    static BlockState jobDesk() {
        return ModBlocks.JOB_DESK.defaultBlockState();
    }

    static BlockState shadyDesk() {
        return ModBlocks.SHADY_DESK.defaultBlockState();
    }

    static BlockState gasPump() {
        return ModBlocks.GAS_PUMP.defaultBlockState();
    }

    static BlockState atm() {
        return ModBlocks.ATM.defaultBlockState();
    }
}
