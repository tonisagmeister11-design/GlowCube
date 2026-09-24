package net.glowcube.client.module.movement;

import com.mojang.blaze3d.platform.InputConstants;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.render.Netz;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * InventoryMove: laufen, springen und sprinten, waehrend das Inventar oder
 * eine Kiste offen ist.
 *
 * <p>Minecraft laesst beim Oeffnen eines Fensters alle Tasten los und gibt
 * die Tastendruecke danach an das Fenster statt an die Figur. Dieses Modul
 * liest die Bewegungstasten jeden Tick direkt von der Tastatur und setzt sie
 * wieder. Fenster mit Textfeld (Chat, Kreativ-Suche, Schilder) bleiben
 * aussen vor, sonst liefe man beim Tippen los.
 */
public final class InventoryMove extends Module {
    private final BooleanSetting pfeiltasten = register(new BooleanSetting("Pfeiltasten drehen",
            "Mit den Pfeiltasten umsehen, solange ein Fenster offen ist", true));
    private final NumberSetting drehTempo = register(new NumberSetting("Drehtempo",
            "Grad je Tick beim Umsehen mit den Pfeiltasten", 5, 1, 20, 1));

    private boolean warOffen;

    public InventoryMove() {
        super("InventoryMove", "Laufen mit offenem Inventar oder offener Kiste", Category.MOVEMENT);
    }

    private KeyMapping[] tasten() {
        return new KeyMapping[] {mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft,
                mc.options.keyRight, mc.options.keyJump, mc.options.keySprint};
    }

    @Override
    public void onTick() {
        Screen fenster = Netz.bildschirm();
        boolean passt = fenster instanceof AbstractContainerScreen<?>
                && !fenster.getClass().getSimpleName().contains("Creative");
        if (!passt) {
            if (warOffen) {
                // Beim Schliessen den echten Stand uebernehmen, sonst haengt
                // eine Taste, die im Fenster losgelassen wurde.
                for (KeyMapping taste : tasten()) {
                    taste.setDown(unten(taste));
                }
                warOffen = false;
            }
            return;
        }
        warOffen = true;
        for (KeyMapping taste : tasten()) {
            taste.setDown(unten(taste));
        }
        if (pfeiltasten.get()) {
            float d = drehTempo.getFloat();
            if (Netz.tasteUnten(262)) {
                player().setYRot(player().getYRot() + d);
            }
            if (Netz.tasteUnten(263)) {
                player().setYRot(player().getYRot() - d);
            }
            if (Netz.tasteUnten(264)) {
                player().setXRot(Math.min(90, player().getXRot() + d));
            }
            if (Netz.tasteUnten(265)) {
                player().setXRot(Math.max(-90, player().getXRot() - d));
            }
        }
    }

    private static boolean unten(KeyMapping taste) {
        try {
            InputConstants.Key key = InputConstants.getKey(taste.saveString());
            return key.getType() == InputConstants.Type.KEYSYM && Netz.tasteUnten(key.getValue());
        } catch (Exception unbekannt) {
            return false;
        }
    }
}
