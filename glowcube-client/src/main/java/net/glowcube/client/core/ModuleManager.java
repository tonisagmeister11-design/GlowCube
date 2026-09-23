package net.glowcube.client.core;

import net.glowcube.client.module.combat.AntiKnockback;
import net.glowcube.client.module.combat.AutoTotem;
import net.glowcube.client.module.combat.PvpPro;
import net.glowcube.client.module.combat.Criticals;
import net.glowcube.client.module.combat.KillAura;
import net.glowcube.client.module.combat.MaceAura;
import net.glowcube.client.module.combat.TriggerBot;
import net.glowcube.client.module.movement.AutoSprint;
import net.glowcube.client.module.movement.AutoWalk;
import net.glowcube.client.module.movement.BunnyHop;
import net.glowcube.client.module.movement.FastLadder;
import net.glowcube.client.module.movement.Glide;
import net.glowcube.client.module.movement.HighJump;
import net.glowcube.client.module.movement.Jesus;
import net.glowcube.client.module.movement.Scaffold;
import net.glowcube.client.module.movement.Spider;
import net.glowcube.client.module.movement.Flight;
import net.glowcube.client.module.movement.NoFall;
import net.glowcube.client.module.movement.NoSlow;
import net.glowcube.client.module.movement.Speed;
import net.glowcube.client.module.movement.Step;
import net.glowcube.client.module.misc.ClickGuiModule;
import net.glowcube.client.module.misc.AutoPlay;
import net.glowcube.client.module.misc.SeedHunt;
import net.glowcube.client.module.misc.Spammer;
import net.glowcube.client.module.movement.PacketFly;
import net.glowcube.client.module.performance.UltraPerformance;
import net.glowcube.client.module.hud.AufgesammeltHud;
import net.glowcube.client.module.hud.BewegungHud;
import net.glowcube.client.module.hud.EigenerTextHud;
import net.glowcube.client.module.hud.InventarHud;
import net.glowcube.client.module.hud.KompassHud;
import net.glowcube.client.module.hud.MausHud;
import net.glowcube.client.module.hud.PfeileHud;
import net.glowcube.client.module.hud.RessourcenpaketeHud;
import net.glowcube.client.module.hud.ServerIpHud;
import net.glowcube.client.module.hud.SpeicherHud;
import net.glowcube.client.module.hud.SpielerzahlHud;
import net.glowcube.client.module.hud.TpsHud;
import net.glowcube.client.module.optik.EigenerName;
import net.glowcube.client.module.optik.Freelook;
import net.glowcube.client.module.optik.KeinBeaconStrahl;
import net.glowcube.client.module.optik.KeinRegen;
import net.glowcube.client.module.optik.KeinWackeln;
import net.glowcube.client.module.optik.KeineVignette;
import net.glowcube.client.module.optik.NiedrigesFeuer;
import net.glowcube.client.module.optik.TntTimer;
import net.glowcube.client.module.hud.ComboHud;
import net.glowcube.client.module.agent.AgentUebersicht;
import net.glowcube.client.module.agent.AgentZurueck;
import net.glowcube.client.module.agent.FarmAgent;
import net.glowcube.client.module.agent.JaegerAgent;
import net.glowcube.client.module.agent.TunnelAgent;
import net.glowcube.client.module.agent.ErzAgent;
import net.glowcube.client.module.agent.HolzAgent;
import net.glowcube.client.module.agent.GuardianAgent;
import net.glowcube.client.module.agent.BuilderAgent;
import net.glowcube.client.module.agent.SteinAgent;
import net.glowcube.client.module.hud.CpsHud;
import net.glowcube.client.module.hud.FpsHud;
import net.glowcube.client.module.hud.KeystrokesHud;
import net.glowcube.client.module.hud.KoordinatenHud;
import net.glowcube.client.module.hud.PingHud;
import net.glowcube.client.module.hud.ReichweiteHud;
import net.glowcube.client.module.hud.RuestungHud;
import net.glowcube.client.module.hud.TempoHud;
import net.glowcube.client.module.hud.TraenkeHud;
import net.glowcube.client.module.hud.UhrzeitHud;
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
import net.glowcube.client.module.player.XCarry;
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
        add(new Spider());
        add(new Glide());
        add(new HighJump());
        add(new FastLadder());
        add(new Jesus());
        add(new BunnyHop());
        // Combat
        add(new KillAura());
        add(new MaceAura());
        add(new TriggerBot());
        add(new AntiKnockback());
        add(new Criticals());
        add(new AutoTotem());
        add(new PvpPro());
        // Player
        add(new AutoTool());
        add(new AutoEat());
        add(new AutoRespawn());
        add(new AntiAfk());
        add(new NoInteract());
        add(new XCarry());
        // Misc
        add(new AutoPlay());
        // SeedHunt haengt ganz an SeedCrackerX - das gibt es nur bis 1.21.x.
        // Ab 26.x bleibt es darum draussen, sonst stuende ein Knopf im Menue,
        // der nichts tun kann.
        if (net.glowcube.client.GlowCubeClient.seedCrackerFassung()) {
            add(new SeedHunt());
        }
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
        // Kein Hack
        add(new UltraPerformance());

        // HUD-Anzeigen nach AxolotlClient. Die Reihenfolge hier ist die
        // Reihenfolge, in der sie am Bildschirmrand gestapelt werden.
        add(new FpsHud());
        add(new CpsHud());
        add(new PingHud());
        add(new TempoHud());
        add(new ReichweiteHud());
        add(new ComboHud());
        add(new UhrzeitHud());
        add(new KoordinatenHud());
        add(new KeystrokesHud());
        add(new RuestungHud());
        add(new TraenkeHud());
        add(new KompassHud());
        add(new TpsHud());
        add(new SpeicherHud());
        add(new ServerIpHud());
        add(new SpielerzahlHud());
        add(new BewegungHud());
        add(new EigenerTextHud());
        add(new PfeileHud());
        add(new InventarHud());
        add(new AufgesammeltHud());
        add(new MausHud());
        add(new RessourcenpaketeHud());

        // Optik-Einstellungen nach AxolotlClient.
        add(new Freelook());
        add(new KeinWackeln());
        add(new NiedrigesFeuer());
        add(new KeineVignette());
        add(new KeinRegen());
        add(new EigenerName());
        add(new KeinBeaconStrahl());
        add(new TntTimer());

        // Agenten: NPCs, die in der eigenen Welt fuer dich abbauen.
        add(new ErzAgent());
        add(new SteinAgent());
        add(new HolzAgent());
        add(new GuardianAgent());
        add(new BuilderAgent());
        add(new FarmAgent());
        add(new TunnelAgent());
        add(new JaegerAgent());
        add(new AgentUebersicht());
        add(new AgentZurueck());
    }

    private void add(Module module) {
        modules.add(module);
        byName.put(module.name().toLowerCase(java.util.Locale.ROOT), module);
        // Chatbefehle nehmen nur ein Wort: "Kein Wackeln" ist dort "keinwackeln".
        byName.putIfAbsent(kurzname(module.name()), module);
    }

    private static String kurzname(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace("-", "");
    }

    public List<Module> all() {
        return modules;
    }

    public Module get(String name) {
        Module module = byName.get(name.toLowerCase(java.util.Locale.ROOT));
        return module != null ? module : byName.get(kurzname(name));
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
        if (geradeBetreten && net.glowcube.client.GlowCubeClient.seedCrackerFassung()) {
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

    public void onWorldRender(net.glowcube.client.render.WeltRender render) {
        for (Module module : modules) {
            if (module.isEnabled()) {
                try {
                    module.onWorldRender(render);
                } catch (Throwable fehler) {
                    // Ein Zeichenfehler darf niemals das ganze Spiel abschiessen.
                    // Das Modul wird abgeschaltet und der Grund einmal ins
                    // Protokoll geschrieben - so kommt der Spieler weiter rein.
                    net.glowcube.client.GlowCubeClient.LOGGER.error(
                            "Renderfehler in {} - Modul wird abgeschaltet", module.name(), fehler);
                    module.setEnabled(false);
                }
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
        net.glowcube.client.render.Netz.nachricht(
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
                if (module.bleibtNachWeltwechsel()) {
                    pending.add(module);
                } else {
                    module.setEnabledSilently(false);
                }
            }
        }
    }
}
