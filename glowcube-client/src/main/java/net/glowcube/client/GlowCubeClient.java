package net.glowcube.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.glowcube.client.command.GlowCubeCommands;
import net.glowcube.client.core.ConfigManager;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.ModuleManager;
import net.glowcube.client.hud.HudRenderer;
import net.glowcube.client.module.render.Search;
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
    private static Boolean seedCrackerFassung;

    private static ModuleManager modules;
    private static ConfigManager config;

    private final HudRenderer hud = new HudRenderer();

    /** Welche Tasten gerade unten sind - fuer die Flanke statt Dauerfeuer. */
    private final Set<Integer> held = new HashSet<>();

    @Override
    public void onInitializeClient() {
        modules = new ModuleManager();
        config = new ConfigManager(modules);
        config.load();
        modules.armLoaded();

        ClientTickEvents.END_CLIENT_TICK.register(client -> onTick());
        // Welt- und HUD-Rendern haengt fassungsabhaengig ab: bis 1.21.x an
        // Fabrics WorldRenderEvents/HudRenderCallback, ab 26.x am neuen
        // Rendersystem. Deshalb ueber die versionsgetrennte Bruecke.
        net.glowcube.client.render.RenderBruecke.registriere(modules, hud);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> modules.onWorldLeave());
        // Search durchsucht jeden Chunk einmal beim Laden statt jeden Tick
        // von neuem - dafuer muss es wissen, wann einer kommt und geht.
        ClientChunkEvents.CHUNK_LOAD.register((welt, chunk) -> Search.chunkGeladen(chunk.getPos()));
        ClientChunkEvents.CHUNK_UNLOAD.register((welt, chunk) -> Search.chunkEntladen(chunk.getPos()));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
        GlowCubeCommands.registrieren();

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
        Set<Integer> bound = new HashSet<>();
        for (Module module : modules.all()) {
            if (module.hasKey()) {
                bound.add(module.key());
            }
        }

        for (int key : bound) {
            boolean down = net.glowcube.client.render.Netz.tasteUnten(key);
            if (!down) {
                held.remove(key);
                continue;
            }
            // Nur die Flanke zaehlt, sonst schaltet ein Halten im Dauerfeuer.
            if (!held.add(key)) {
                continue;
            }
            // Nur ausserhalb von Menues und Chat, sonst tippt man Module an.
            if (net.glowcube.client.render.Netz.bildschirm() == null) {
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

    /**
     * Ob diese Spielfassung SeedCrackerX kennt. Es gibt SeedCrackerX nur bis
     * 1.21.x; ab 26.x nicht mehr. Danach richtet sich, ob SeedHunt und die
     * Seed-Anzeige ueberhaupt erscheinen - auf 26.x sollen sie es nicht.
     */
    public static boolean seedCrackerFassung() {
        if (seedCrackerFassung == null) {
            String fassung = FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("");
            seedCrackerFassung = fassung.startsWith("1.");
        }
        return seedCrackerFassung;
    }

    public static ModuleManager modules() {
        return modules;
    }

    public static ConfigManager config() {
        return config;
    }
}
