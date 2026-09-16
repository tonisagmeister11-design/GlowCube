package net.glowcube.client.core;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.glowcube.client.core.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Basis jedes Features. Ein Modul kennt seinen Zustand, seine Einstellungen und
 * seine Taste; alles andere macht der ModuleManager.
 */
public abstract class Module {
    protected static final Minecraft mc = Minecraft.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();

    private boolean enabled;
    private int key;
    /** 0..1, laeuft beim Ein-/Ausschalten weich hoch bzw. runter - nur fuer die Darstellung. */
    private float animation;

    protected Module(String name, String description, Category category) {
        this(name, description, category, InputConstants.UNKNOWN.getValue());
    }

    protected Module(String name, String description, Category category, int key) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = key;
    }

    // ------------------------------------------------------------------ Haken

    /** Wird beim Einschalten gerufen. */
    public void onEnable() {
    }

    /** Wird beim Ausschalten gerufen - auch beim Verlassen der Welt. */
    public void onDisable() {
    }

    /** Jeden Client-Tick, nur solange das Modul an ist und eine Welt existiert. */
    public void onTick() {
    }

    /** Jeden Frame in der Welt, nach den Entities. */
    public void onWorldRender(WorldRenderContext context) {
    }

    // ----------------------------------------------------------------- Zustand

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        if (this.enabled == value) {
            return;
        }
        this.enabled = value;
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /** Schaltet ohne onEnable/onDisable - fuer das Laden der Konfiguration. */
    public void setEnabledSilently(boolean value) {
        this.enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public int key() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public boolean hasKey() {
        return key != InputConstants.UNKNOWN.getValue();
    }

    public String keyName() {
        if (!hasKey()) {
            return "--";
        }
        // getKey nimmt jetzt ein KeyEvent; eines laesst sich dafuer bauen.
        return InputConstants.getKey(new KeyEvent(key, 0, 0))
                .getDisplayName().getString().toUpperCase(java.util.Locale.ROOT);
    }

    /** Was im HUD hinter dem Namen steht, z.B. "Speed [2.5]". Null heisst: nichts. */
    public String hudSuffix() {
        return null;
    }

    public float animation() {
        return animation;
    }

    public void setAnimation(float animation) {
        this.animation = animation;
    }

    // --------------------------------------------------------------- Settings

    protected <T extends Setting> T register(T setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting> settings() {
        return settings;
    }

    // --------------------------------------------------------------- Abkuerzer

    protected LocalPlayer player() {
        return mc.player;
    }

    protected ClientLevel level() {
        return mc.level;
    }

    protected boolean inGame() {
        return mc.player != null && mc.level != null;
    }
}
