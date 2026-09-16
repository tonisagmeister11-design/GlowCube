package net.glowcube.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.glowcube.client.core.ConfigManager;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.ModuleManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/** Einstiegspunkt. Haelt die drei Bausteine zusammen und verteilt die Ereignisse. */
public final class GlowCubeClient implements ClientModInitializer {
    public static final String MOD_ID = "glowcube";
    public static final String NAME = "GlowCube";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static String target;

    private static ModuleManager modules;
    private static ConfigManager config;

    /** Welche Tasten gerade unten sind - fuer die Flanke statt Dauerfeuer. */
    private final Set<Integer> held = new HashSet<>();

    @Override
    public void onInitializeClient() {
        modules = new ModuleManager();
        config = new ConfigManager(modules);
        config.load();
        modules.armLoaded();

        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> modules.onWorldLeave());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());

        LOGGER.info("{} {} geladen - {} Module", NAME, VERSION, modules.all().size());
    }

    private void onTick() {
        pollKeys();
        modules.onTick();
    }

    /**
     * Tasten direkt am Fenster abgefragt statt ueber KeyMappings: so laesst sich
     * jede Taste im GUI neu belegen, ohne sie vorher registriert zu haben.
     */
    private void pollKeys() {
        Minecraft mc = Minecraft.getInstance();
        // isKeyDown nimmt in 26.2 das Window-Objekt selbst; das rohe
        // Fensterhandle gibt Window nicht mehr heraus.
        Window window = mc.getWindow();

        Set<Integer> bound = new HashSet<>();
        for (Module module : modules.all()) {
            if (module.hasKey()) {
                bound.add(module.key());
            }
        }

        for (int key : bound) {
            boolean down = InputConstants.isKeyDown(window, key);
            if (!down) {
                held.remove(key);
                continue;
            }
            // Nur die Flanke zaehlt, sonst schaltet ein Halten im Dauerfeuer.
            if (!held.add(key)) {
                continue;
            }
            // Nur ausserhalb von Menues und Chat, sonst tippt man Module an.
            // Minecraft.screen gibt es in 26.2 nicht mehr zum Lesen; ob ein
            // Bildschirm offen ist, verraet aber der Mauszeiger: im Spiel ist
            // er gefangen, in jedem Menue und im Chat nicht.
            if (mc.mouseHandler.isMouseGrabbed()) {
                modules.onKey(key);
                config.save();
            }
        }
    }

    /**
     * Was im Wasserzeichen steht. Wird beim Loader erfragt statt eingetippt -
     * so kann da nie eine andere Fassung stehen als die, die wirklich laeuft.
     */
    public static String target() {
        if (target == null) {
            target = "Fabric " + FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("?");
        }
        return target;
    }

    public static ModuleManager modules() {
        return modules;
    }

    public static ConfigManager config() {
        return config;
    }
}
