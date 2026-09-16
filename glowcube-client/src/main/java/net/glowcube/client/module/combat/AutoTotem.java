package net.glowcube.client.module.combat;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.NumberSetting;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Legt einen Totem der Unsterblichkeit in die Zweithand, sobald es dort fehlt.
 *
 * Gesucht wird im ganzen Inventar. Der Tausch laeuft ueber denselben Weg, den
 * auch ein Mausklick nimmt - anders nimmt der Server es nicht an.
 * Nachempfunden AutoTotem aus BleachHack (GPL-3.0).
 */
public final class AutoTotem extends Module {
    /** Der Platz der Zweithand in der Inventaransicht des Spielers. */
    private static final int ZWEITHAND_PLATZ = 45;

    private final NumberSetting minHealth = register(new NumberSetting("Health",
            "Erst ab diesem Leben nachlegen - 20 heisst immer", 20, 1, 20, 1));
    private final NumberSetting delay = register(new NumberSetting("Delay",
            "Ticks zwischen zwei Versuchen", 5, 1, 40, 1));

    private int ticks;

    public AutoTotem() {
        super("AutoTotem", "Haelt ein Totem in der Zweithand", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (++ticks < delay.getInt()) {
            return;
        }
        ticks = 0;

        if (player().getHealth() > minHealth.getFloat()) {
            return;
        }
        if (player().getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        int quelle = suchen();
        if (quelle < 0) {
            return;
        }
        // Aufnehmen, in die Zweithand legen, Rest zuruecklegen - genau die
        // drei Klicks, die man von Hand auch machen wuerde.
        klick(quelle);
        klick(ZWEITHAND_PLATZ);
        klick(quelle);
    }

    /** Platznummer eines Totems im Inventar, oder -1. */
    private int suchen() {
        for (int platz = 0; platz < player().getInventory().getContainerSize(); platz++) {
            ItemStack stack = player().getInventory().getItem(platz);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                // Die Hotbar liegt in der Inventaransicht hinten.
                return platz < 9 ? platz + 36 : platz;
            }
        }
        return -1;
    }

    private void klick(int platz) {
        mc.gameMode.handleInventoryMouseClick(
                player().inventoryMenu.containerId, platz, 0, ClickType.PICKUP, player());
    }
}
