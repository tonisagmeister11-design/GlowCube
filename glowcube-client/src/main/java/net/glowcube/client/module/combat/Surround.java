package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.util.BlockUtils;
import net.glowcube.client.util.FindItemResult;
import net.glowcube.client.util.Ids;
import net.glowcube.client.util.InvUtils;
import net.minecraft.core.BlockPos;

/**
 * Surround: mauert die eigenen Fuesse mit Obsidian ein - die Grundstellung
 * im Kristall-PvP. Explosionen neben einem treffen dann kaum noch.
 */
public final class Surround extends Module {
    private final NumberSetting jeTick = register(new NumberSetting("Je Tick",
            "Wie viele Bloecke pro Tick gesetzt werden", 2, 1, 8, 1));
    private final BooleanSetting zentrieren = register(new BooleanSetting("Zentrieren",
            "Beim Einschalten in die Blockmitte stellen", true));
    private final BooleanSetting drunter = register(new BooleanSetting("Boden",
            "Auch den Block unter den Fuessen setzen", true));
    private final BooleanSetting springenAus = register(new BooleanSetting("Aus beim Springen",
            "Ausschalten, sobald man die Stelle verlaesst", true));
    private final BooleanSetting drehen = register(new BooleanSetting("Drehen",
            "Zum Setzen hinschauen (fuer Anticheats)", true));

    private double startY;
    private BlockPos start;
    private boolean fehlt;

    public Surround() {
        super("Surround", "Mauert die Fuesse mit Obsidian ein", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        if (!inGame()) {
            return;
        }
        if (zentrieren.get()) {
            player().setPos(Math.floor(player().getX()) + 0.5, player().getY(), Math.floor(player().getZ()) + 0.5);
            player().setDeltaMovement(0, player().getDeltaMovement().y, 0);
        }
        startY = player().getY();
        start = player().blockPosition();
    }

    @Override
    public void onTick() {
        if (start == null) {
            onEnable();
        }
        if (springenAus.get() && (Math.abs(player().getY() - startY) > 0.6
                || !player().blockPosition().equals(start))) {
            setEnabled(false);
            return;
        }
        FindItemResult block = InvUtils.findeInHotbar(stack -> {
            String id = Ids.item(stack);
            return id.equals("obsidian") || id.equals("crying_obsidian") || id.equals("ender_chest");
        });
        fehlt = !block.found();
        if (fehlt) {
            return;
        }
        BlockPos fuss = player().blockPosition();
        BlockPos[] stellen = drunter.get()
                ? new BlockPos[] {fuss.below(), fuss.north(), fuss.south(), fuss.east(), fuss.west()}
                : new BlockPos[] {fuss.north(), fuss.south(), fuss.east(), fuss.west()};
        int gesetzt = 0;
        for (BlockPos stelle : stellen) {
            if (!level().getBlockState(stelle).canBeReplaced()) {
                continue;
            }
            if (BlockUtils.setzen(stelle, block, drehen.get(), 50, true, true)) {
                gesetzt++;
                if (gesetzt >= jeTick.getInt()) {
                    return;
                }
            }
        }
    }

    @Override
    public String hudSuffix() {
        return fehlt ? "kein Obsidian" : null;
    }
}
