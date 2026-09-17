package net.glowcube.client.core;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.glowcube.client.module.combat.AutoTotem;
import net.glowcube.client.module.combat.Criticals;
import net.glowcube.client.module.combat.KillAura;
import net.glowcube.client.module.movement.AutoSprint;
import net.glowcube.client.module.movement.AutoWalk;
import net.glowcube.client.module.movement.Scaffold;
import net.glowcube.client.module.movement.Flight;
import net.glowcube.client.module.movement.NoFall;
import net.glowcube.client.module.movement.NoSlow;
import net.glowcube.client.module.movement.Speed;
import net.glowcube.client.module.movement.Step;
import net.glowcube.client.module.misc.ClickGuiModule;
import net.glowcube.client.module.misc.SeedHunt;
import net.glowcube.client.module.misc.Spammer;
import net.glowcube.client.module.movement.PacketFly;
import net.glowcube.client.module.player.NoInteract;
import net.glowcube.client.module.world.AntiChunkBan;
import net.glowcube.client.module.world.FakeLag;
import net.glowcube.client.module.world.Nuker;
import net.glowcube.client.module.world.VeinMiner;
import net.glowcube.client.module.world.OreSim;
import net.glowcube.client.module.world.Timer;
import net.glowcube.client.module.player.AntiAfk;
import net.glowcube.client.module.player.AutoEat;
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
import net.glowcube.client.util.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

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
        add(new NoSlow());
        add(new AutoSprint());
        add(new AutoWalk());
        add(new Scaffold());
        // Combat
        add(new KillAura());
        add(new Criticals());
        add(new AutoTotem());
        // Player
        add(new AutoTool());
        add(new AutoEat());
        add(new AutoRespawn());
        add(new AntiAfk());
        add(new NoInteract());
        // Misc
        add(new SeedHunt());
        add(new Spammer());
        add(new ClickGuiModule());
        // World
        add(new Nuker());
        add(new VeinMiner());
        add(new OreSim());
        // Exploits
        add(new Timer());
        add(new FakeLag());
        add(new AntiChunkBan());
        add(new PacketFly());
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

        // Der Abbau muss jeden Tick bestaetigt werden, sonst haelt das Spiel
        // ihn fuer abgebrochen. Nuker und AutoTool melden sich dazwischen.
        BlockUtils.tickBeginn();
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick();
            }
        }
        BlockUtils.tickEnde();
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

    /**
     * Ein Paket geht hinaus. Liefert true, wenn ein Modul es abfaengt.
     *
     * <p>Die Schleife laeuft ueber eine Kopie nicht - die Modulliste steht
     * fest - aber sie darf nicht abbrechen, sobald eines abbricht: auch die
     * uebrigen wollen das Paket gesehen haben (FakeLag legt es zur Seite,
     * waehrend Criticals es zaehlt).
     */
    public boolean onPacketSend(Packet<?> packet) {
        boolean abbrechen = false;
        for (Module module : modules) {
            if (module.isEnabled() && module.onPacketSend(packet)) {
                abbrechen = true;
            }
        }
        return abbrechen;
    }

    /** Ein Paket kommt an. Liefert true, wenn ein Modul es verwirft. */
    public boolean onPacketReceive(Packet<?> packet) {
        boolean abbrechen = false;
        for (Module module : modules) {
            if (module.isEnabled() && module.onPacketReceive(packet)) {
                abbrechen = true;
            }
        }
        return abbrechen;
    }

    /** Kurz vor dem Bewegungspaket. Liefert true, wenn eines es ganz unterbindet. */
    public boolean onSendMovement() {
        boolean abbrechen = false;
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onSendMovement();
                if (module.blockMovementPackets()) {
                    abbrechen = true;
                }
            }
        }
        return abbrechen;
    }

    /** Ob ein Modul die clientseitige Bewegung gerade unterbindet. */
    public boolean blockClientMove() {
        for (Module module : modules) {
            if (module.isEnabled() && module.blockClientMove()) {
                return true;
            }
        }
        return false;
    }

    public boolean onBlockBreak(BlockPos pos) {
        for (Module module : modules) {
            if (module.isEnabled() && module.onBlockBreak(pos)) {
                return true;
            }
        }
        return false;
    }

    public boolean onBlockUse(BlockHitResult treffer, InteractionHand hand) {
        for (Module module : modules) {
            if (module.isEnabled() && module.onBlockUse(treffer, hand)) {
                return true;
            }
        }
        return false;
    }

    public boolean onEntityAttack(Entity ziel) {
        for (Module module : modules) {
            if (module.isEnabled() && module.onEntityAttack(ziel)) {
                return true;
            }
        }
        return false;
    }

    public boolean onEntityUse(Entity ziel, InteractionHand hand) {
        for (Module module : modules) {
            if (module.isEnabled() && module.onEntityUse(ziel, hand)) {
                return true;
            }
        }
        return false;
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
