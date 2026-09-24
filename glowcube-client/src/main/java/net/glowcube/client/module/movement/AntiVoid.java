package net.glowcube.client.module.movement;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.BlockUtils;
import net.glowcube.client.util.FindItemResult;
import net.glowcube.client.util.InvUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.FallingBlock;

/**
 * AntiVoid: faellt man ins Leere (oder tief), setzt es einen Block unter die
 * Fuesse und faengt den Sturz ab.
 *
 * <p>"Nur Leere" greift, sobald unter einem bis zum Weltboden nichts mehr
 * kommt - der Klassiker am Rand der End-Insel. "Jeder Sturz" greift schon ab
 * der eingestellten Fallhoehe und spart so auch den Fallschaden.
 */
public final class AntiVoid extends Module {
    private final ModeSetting modus = register(new ModeSetting("Modus",
            "Wann der Rettungsblock kommt", "Nur Leere", "Nur Leere", "Jeder Sturz"));
    private final NumberSetting fallhoehe = register(new NumberSetting("Fallhoehe",
            "Ab so vielen Bloecken Fall greift \"Jeder Sturz\"", 5, 2, 30, 1));

    public AntiVoid() {
        super("AntiVoid", "Setzt einen Block unter dich, wenn du ins Leere faellst", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (player().onGround() || player().isFallFlying() || player().getAbilities().flying
                || player().getDeltaMovement().y >= 0) {
            return;
        }
        boolean leere = nichtsDarunter();
        if (modus.is("Nur Leere") ? !leere : (!leere && player().fallDistance < fallhoehe.get())) {
            return;
        }
        BlockPos ziel = player().blockPosition().below();
        if (ziel.getY() < level().getMinY()) {
            return;
        }
        FindItemResult block = InvUtils.findeInHotbar(stack -> stack.getItem() instanceof BlockItem item
                && !(item.getBlock() instanceof FallingBlock)
                && item.getBlock().defaultBlockState().isCollisionShapeFullBlock(level(), ziel));
        if (!block.found()) {
            return;
        }
        if (BlockUtils.setzen(ziel, block, true, 100, true, false) && leere) {
            player().setDeltaMovement(0, 0, 0);
        }
    }

    private boolean nichtsDarunter() {
        BlockPos.MutableBlockPos pos = player().blockPosition().mutable();
        int unten = level().getMinY();
        for (int y = pos.getY() - 1; y >= unten; y--) {
            pos.setY(y);
            if (!level().getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }
}
