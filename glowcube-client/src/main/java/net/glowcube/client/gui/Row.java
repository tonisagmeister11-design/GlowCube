package net.glowcube.client.gui;

import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.Setting;

/**
 * Eine Zeile der Liste - entweder eine Modulkarte oder eine ihrer
 * Einstellungen. Zeichnen und Klicken laufen ueber dieselbe Liste, damit
 * beides nie auseinanderlaufen kann.
 */
final class Row {
    final Module module;
    final Setting setting;
    final float y;
    final float height;

    Row(Module module, Setting setting, float y, float height) {
        this.module = module;
        this.setting = setting;
        this.y = y;
        this.height = height;
    }

    boolean isCard() {
        return setting == null;
    }
}
