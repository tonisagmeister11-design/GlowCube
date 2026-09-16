package net.glowcube.client.core;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.glowcube.client.module.combat.Criticals;
import net.glowcube.client.module.combat.KillAura;
import net.glowcube.client.module.movement.AutoSprint;
import net.glowcube.client.module.movement.AutoWalk;
import net.glowcube.client.module.movement.Flight;
import net.glowcube.client.module.movement.NoFall;
import net.glowcube.client.module.movement.Speed;
import net.glowcube.client.module.movement.Step;
import net.glowcube.client.module.misc.ClickGuiModule;
import net.glowcube.client.module.misc.SeedHunt;
import net.glowcube.client.module.misc.Spammer;
import net.glowcube.client.module.world.Nuker;
import net.glowcube.client.module.player.AntiAfk;
import net.glowcube.client.module.player.AutoRespawn;
import net.glowcube.client.module.player.AutoTool;
import net.glowcube.client.module.render.EntityEsp;
import net.glowcube.client.module.render.HoleEsp;
import net.glowcube.client.module.render.FullBright;
import net.glowcube.client.module.render.StorageEsp;
import net.glowcube.client.module.render.Search;
import net.glowcube.client.module.render.Tracers;
import net.glowcube.client.module.render.Trajectories;
import net.glowcube.client.module.render.XRay;
import net.glowcube.client.module.render.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Haelt alle Module, verteilt Ticks und Tastendruecke. */
public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> byName = new LinkedHashMap<>();
    /** Module, die an sind, deren onEnable aber noch aussteht. */
    private final Set<Module> pending = new HashSet<>();
    /** Ob beim letzten Tick schon eine Welt da war - fuer den Beitritt. */
    private boolean warInWelt;

    public ModuleManager() {
        // Render
        add(new XRay());
        add(new FullBright());
        add(new StorageEsp());
        add(new EntityEsp());
        add(new Tracers());
        add(new Search());
        add(new HoleEsp());
        add(new Trajectories());
        add(new Zoom());
        // Movement
        add(new Flight());
        add(new Speed());
        add(new Step());
        add(new NoFall());
        add(new AutoSprint());
        add(new AutoWalk());
        // Combat
        add(new KillAura());
        add(new Criticals());
        // Player
        add(new AutoTool());
        add(new AutoRespawn());
        add(new AntiAfk());
        // Misc
        add(new SeedHunt());
        add(new Spammer());
        add(new ClickGuiModule());
        // World
        add(new Nuker());
    }

    private void add(Module module) {
        modules.add(module);
        byName.put(module.name().toLowerCase(java.util.Locale.ROOT), module);
    }

    public List<Module> all() {
        return modules;
    }

    public Module get(String name) {
        return byName.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) {
                return type.cast(module);
            }
        }
        throw new IllegalArgumentException("Unbekanntes Modul: " + type.getName());
    }

    public List<Module> inCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.category() == category) {
                result.add(module);
            }
        }
        result.sort(Comparator.comparing(Module::name));
        return result;
    }

    public List<Module> enabled() {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.isEnabled()) {
                result.add(module);
            }
        }
        return result;
    }

    // ----------------------------------------------------------------- Events

    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        boolean inGame = mc.player != null && mc.level != null;
        if (!inGame) {
            warInWelt = false;
            return;
        }
        boolean geradeBetreten = !warInWelt;
        warInWelt = true;
        // Nachgeholtes onEnable: beim Start und nach einem Weltwechsel.
        if (!pending.isEmpty()) {
            for (Module module : new ArrayList<>(pending)) {
                module.onEnable();
            }
            pending.clear();
        }
        if (geradeBetreten) {
            SeedHunt seedHunt = get(SeedHunt.class);
            if (seedHunt.autoStart() && !seedHunt.isEnabled()) {
                seedHunt.setEnabled(true);
            }
        }

        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick();
            }
        }
    }

    /**
     * Nach dem Laden der Konfiguration: die dort eingeschalteten Module wollen
     * ihr onEnable noch haben - aber erst, wenn es eine Welt gibt. Zu diesem
     * Zeitpunkt steht die halbe Spielinstanz noch gar nicht.
     */
    public void armLoaded() {
        for (Module module : modules) {
            if (module.isEnabled()) {
                pending.add(module);
            }
        }
    }

    public void onWorldRender(WorldRenderContext context) {
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onWorldRender(context);
            }
        }
    }

    /** Ein Tastendruck ausserhalb von Textfeldern. */
    public void onKey(int key) {
        for (Module module : modules) {
            if (module.hasKey() && module.key() == key) {
                module.toggle();
                melden(module);
            }
        }
    }

    /**
     * Ohne HUD braucht es eine andere Rueckmeldung, sonst weiss man nach dem
     * Tastendruck nicht, ob etwas passiert ist. Die Zeile ueber der Hotbar
     * verschwindet von selbst wieder.
     */
    private void melden(Module module) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        String zustand = module.isEnabled() ? "an" : "aus";
        player.displayClientMessage(
                Component.literal("[GlowCube] " + module.name() + ": " + zustand), true);
    }

    /**
     * Beim Verlassen der Welt alles herunterfahren, damit nichts haengen bleibt -
     * die Module bleiben aber eingeschaltet und melden sich in der naechsten
     * Welt von selbst zurueck.
     */
    public void onWorldLeave() {
        pending.clear();
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onDisable();
                pending.add(module);
            }
        }
    }
}
