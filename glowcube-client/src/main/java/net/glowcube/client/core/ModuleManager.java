package net.glowcube.client.core;

import net.glowcube.client.module.combat.KillAura;
import net.glowcube.client.module.movement.AutoSprint;
import net.glowcube.client.module.movement.Flight;
import net.glowcube.client.module.movement.NoFall;
import net.glowcube.client.module.movement.Speed;
import net.glowcube.client.module.movement.Step;
import net.glowcube.client.module.player.AntiAfk;
import net.glowcube.client.module.player.AutoRespawn;
import net.glowcube.client.module.player.AutoTool;
import net.glowcube.client.module.render.FullBright;
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

    public ModuleManager() {
        // Render
        add(new XRay());
        add(new FullBright());
        add(new Zoom());
        // Movement
        add(new Flight());
        add(new Speed());
        add(new Step());
        add(new NoFall());
        add(new AutoSprint());
        // Combat
        add(new KillAura());
        // Player
        add(new AutoTool());
        add(new AutoRespawn());
        add(new AntiAfk());
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
            return;
        }
        // Nachgeholtes onEnable: beim Start und nach einem Weltwechsel.
        if (!pending.isEmpty()) {
            for (Module module : new ArrayList<>(pending)) {
                module.onEnable();
            }
            pending.clear();
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
