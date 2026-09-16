package net.glowcube.client.module.player;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Waehlt beim Abbauen das schnellste Werkzeug aus der Hotbar. */
public final class AutoTool extends Module {
    public AutoTool() {
        super("AutoTool", "Bestes Werkzeug beim Abbauen", Category.PLAYER, InputConstants.KEY_K);
    }

    @Override
    public void onTick() {
        if (!mc.options.keyAttack.isDown()) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockState state = level().getBlockState(hit.getBlockPos());
        int best = -1;
        float bestSpeed = 0.0f;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player().getInventory().getItem(slot);
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }

        // Nur wechseln, wenn es wirklich besser ist als das, was in der Hand liegt.
        if (best >= 0 && bestSpeed > player().getMainHandItem().getDestroySpeed(state)) {
            player().getInventory().setSelectedSlot(best);
        }
    }
}
