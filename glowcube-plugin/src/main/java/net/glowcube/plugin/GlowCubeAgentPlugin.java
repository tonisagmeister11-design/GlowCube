package net.glowcube.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Das GlowCube-Agent-Plugin: Spieler mit dem GlowCube-Client koennen auf
 * diesem Server Agenten losschicken - wie in der Einzelspielerwelt.
 *
 * <p>Der Client schickt seine Befehle ueber den Kanal {@code glowcube:agent}
 * (Text mit Semikolons, UTF-8 mit VarInt-Laenge davor):
 * {@code start;AUFTRAG;nummer;art;tempo;abbau;xray;chunks;leuchten;zahl},
 * {@code werte;AUFTRAG;nummer;...}, {@code zurueck;AUFTRAG;nummer} (0 = alle),
 * {@code alle}, {@code ort;x;y;z}/{@code ort;weg} (Einsatzort),
 * {@code kiste;x;y;z}/{@code kiste;weg} (Sammelkiste), {@code ziel;x;y;z}
 * (Orbital Strike). Zurueck gehen {@code aus;AUFTRAG;nummer}, wenn ein Agent
 * fertig ist, und alle halbe Sekunde {@code status;...} fuer die
 * Agenten-Uebersicht im HUD.
 */
public final class GlowCubeAgentPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    static final String KANAL = "glowcube:agent";

    private final List<AgentArbeiter> agenten = new ArrayList<>();
    /** Wer welchen Spieler zuletzt getroffen hat (fuer den Guardian): Spieler -> Angreifer, Zeitpunkt. */
    private final Map<UUID, UUID> angreifer = new HashMap<>();
    private final Map<UUID, Long> angriffZeit = new HashMap<>();
    private final OrbitalStrike orbitalStrike = new OrbitalStrike(this);
    /** Einsatzort und Sammelkiste je Spieler. */
    private final Map<UUID, org.bukkit.Location> orte = new HashMap<>();
    private final Map<UUID, org.bukkit.Location> kisten = new HashMap<>();
    /** Wer zuletzt Agenten hatte - bekommt einmal eine leere Uebersicht, wenn alle weg sind. */
    private final java.util.Set<UUID> mitStatus = new java.util.HashSet<>();
    private int ticks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerIncomingPluginChannel(this, KANAL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, KANAL);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(orbitalStrike, this);
        Bauplaene.ordner(this);
        getLogger().info("GlowCube-Agenten bereit - Kanal " + KANAL);
    }

    @Override
    public void onDisable() {
        // Server faehrt herunter: Beute direkt ins Inventar, Agenten weg.
        for (AgentArbeiter agent : agenten) {
            try {
                agent.notfallUebergabe();
                agent.aufraeumen();
            } catch (RuntimeException fehler) {
                getLogger().warning("Agent beim Beenden nicht sauber entfernt: " + fehler);
            }
        }
        agenten.clear();
        orbitalStrike.aufraeumen();
    }

    // ------------------------------------------------------------- Befehle

    @Override
    public void onPluginMessageReceived(String kanal, Player spieler, byte[] daten) {
        if (!KANAL.equals(kanal)) {
            return;
        }
        String text;
        try {
            text = lesen(daten);
        } catch (RuntimeException kaputt) {
            return;
        }
        String[] t = text.split(";", -1);
        switch (t[0]) {
            case "start" -> {
                if (t.length >= 8) {
                    Auftrag a = auftrag(t[1]);
                    starten(spieler, a, zahl(t[2]), t[3], Werte.lesen(t, 4, this, a == Auftrag.BAUMEISTER));
                } else if (t.length >= 7) {
                    // Aelterer Client ohne Nummer.
                    Auftrag a = auftrag(t[1]);
                    starten(spieler, a, 1, t[2], Werte.lesen(t, 3, this, a == Auftrag.BAUMEISTER));
                }
            }
            case "werte" -> {
                if (t.length >= 7) {
                    Auftrag a = auftrag(t[1]);
                    int nummer = zahl(t[2]);
                    Werte w = Werte.lesen(t, 3, this, a == Auftrag.BAUMEISTER);
                    for (AgentArbeiter agent : agenten) {
                        if (agent.besitzer().equals(spieler.getUniqueId()) && agent.auftrag() == a
                                && agent.nummer() == nummer) {
                            agent.einstellen(w);
                        }
                    }
                }
            }
            case "zurueck" -> {
                if (t.length >= 2) {
                    zurueck(spieler.getUniqueId(), auftrag(t[1]), t.length >= 3 ? zahl(t[2]) : 0);
                }
            }
            case "alle" -> {
                for (Auftrag a : Auftrag.values()) {
                    zurueck(spieler.getUniqueId(), a, 0);
                }
            }
            case "ort" -> {
                if (t.length >= 2 && t[1].equals("weg")) {
                    orte.remove(spieler.getUniqueId());
                } else if (t.length >= 4) {
                    orte.put(spieler.getUniqueId(), new org.bukkit.Location(spieler.getWorld(),
                            zahl(t[1]), zahl(t[2]), zahl(t[3])));
                }
            }
            case "kiste" -> {
                if (t.length >= 2 && t[1].equals("weg")) {
                    kisten.remove(spieler.getUniqueId());
                } else if (t.length >= 4) {
                    org.bukkit.Location k = new org.bukkit.Location(spieler.getWorld(), zahl(t[1]), zahl(t[2]), zahl(t[3]));
                    if (!Kiste.istKiste(k.getBlock())) {
                        melden(spieler, NamedTextColor.RED, "Das ist keine Kiste und kein Fass.");
                    } else if (k.distanceSquared(spieler.getLocation()) > 10 * 10) {
                        melden(spieler, NamedTextColor.RED, "Die Kiste ist zu weit weg.");
                    } else {
                        kisten.put(spieler.getUniqueId(), k);
                        melden(spieler, NamedTextColor.GREEN, "Sammelkiste gesetzt: " + k.getBlockX() + " "
                                + k.getBlockY() + " " + k.getBlockZ() + " - volle Agenten bringen ihre Beute hierher.");
                    }
                }
            }
            case "ziel" -> orbitalStrike.zielSetzen(spieler, t);
            default -> {
            }
        }
    }

    private static int zahl(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException falsch) {
            return 0;
        }
    }

    org.bukkit.Location einsatzort(UUID spieler) {
        return orte.get(spieler);
    }

    org.bukkit.Location kiste(UUID spieler) {
        return kisten.get(spieler);
    }

    /** Die Bloecke, die sich andere Agenten schon vorgenommen haben. */
    java.util.Set<Long> reserviertVonAnderen(AgentArbeiter ich) {
        java.util.Set<Long> belegt = new java.util.HashSet<>();
        for (AgentArbeiter agent : agenten) {
            if (agent != ich && agent.reservierung() != Long.MIN_VALUE) {
                belegt.add(agent.reservierung());
            }
        }
        return belegt;
    }

    private static Auftrag auftrag(String name) {
        try {
            return Auftrag.valueOf(name);
        } catch (IllegalArgumentException falsch) {
            return Auftrag.ERZ;
        }
    }

    private void starten(Player spieler, Auftrag auftrag, int nummer, String art, Werte werte) {
        if (!spieler.hasPermission("glowcube.agent")) {
            melden(spieler, NamedTextColor.RED, "Du darfst hier keine Agenten losschicken.");
            aus(spieler, auftrag, nummer);
            return;
        }
        int jeArt = getConfig().getInt("max-agenten-je-art", 5);
        if (nummer < 1 || nummer > jeArt) {
            melden(spieler, NamedTextColor.RED, "Hier sind hoechstens " + jeArt + " Agenten je Art erlaubt.");
            aus(spieler, auftrag, nummer);
            return;
        }
        int eigene = 0;
        for (AgentArbeiter agent : agenten) {
            if (!agent.besitzer().equals(spieler.getUniqueId())) {
                continue;
            }
            if (agent.auftrag() == auftrag && agent.nummer() == nummer && !agent.beimZurueckkehren()) {
                melden(spieler, NamedTextColor.YELLOW, agent.titel() + " ist schon unterwegs.");
                return;
            }
            eigene++;
        }
        if (eigene >= getConfig().getInt("max-agenten-je-spieler", 20)) {
            melden(spieler, NamedTextColor.RED, "Du hast schon genug Agenten unterwegs.");
            aus(spieler, auftrag, nummer);
            return;
        }
        AgentArbeiter agent = switch (auftrag) {
            case WAECHTER, JAEGER -> Waechter.erschaffen(this, spieler, auftrag, nummer, art, werte);
            case BAUMEISTER -> Baumeister.erschaffen(this, spieler, nummer, art, werte,
                    baureiheVersatz(spieler.getUniqueId(), nummer));
            default -> Agent.erschaffen(this, spieler, auftrag, nummer, art, werte);
        };
        if (agent == null) {
            melden(spieler, NamedTextColor.RED, auftrag.anzeigename + " #" + nummer + " konnte nicht erscheinen.");
            aus(spieler, auftrag, nummer);
            return;
        }
        agenten.add(agent);
        mitStatus.add(spieler.getUniqueId());
        melden(spieler, NamedTextColor.AQUA, agent.titel() + " ist losgezogen.");
    }

    /** Mehrere Builder bauen nebeneinander. */
    private int baureiheVersatz(UUID besitzer, int nummer) {
        int versatz = 0;
        for (AgentArbeiter agent : agenten) {
            if (agent instanceof Baumeister b && agent.besitzer().equals(besitzer) && agent.nummer() < nummer
                    && !agent.fertig()) {
                versatz = Math.max(versatz, b.rechterRand() + 4);
            }
        }
        return versatz;
    }

    /** nummer 0: alle dieser Art. */
    private void zurueck(UUID besitzer, Auftrag auftrag, int nummer) {
        for (AgentArbeiter agent : agenten) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag
                    && (nummer == 0 || agent.nummer() == nummer)) {
                agent.zurueckrufen();
            }
        }
    }

    /** Merkt sich, wer einen Spieler getroffen hat - Pfeile zaehlen fuer den Schuetzen. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void getroffen(EntityDamageByEntityEvent ereignis) {
        if (!(ereignis.getEntity() instanceof Player opfer)) {
            return;
        }
        Entity taeter = ereignis.getDamager();
        if (taeter instanceof Projectile geschoss && geschoss.getShooter() instanceof Entity schuetze) {
            taeter = schuetze;
        }
        if (taeter instanceof LivingEntity && !taeter.equals(opfer)) {
            angreifer.put(opfer.getUniqueId(), taeter.getUniqueId());
            angriffZeit.put(opfer.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * PvP Pro freigeben: meldet der GlowCube-Client seinen Kanal an, bekommt
     * er "pvppro;an" - aber nur, wenn der Betreiber es in der config.yml
     * erlaubt hat und der Spieler die Berechtigung glowcube.pvppro hat.
     * Ohne diese Meldung bleibt PvP Pro im Client auf diesem Server aus.
     */
    @EventHandler
    public void kanalAngemeldet(org.bukkit.event.player.PlayerRegisterChannelEvent ereignis) {
        if (!KANAL.equals(ereignis.getChannel())) {
            return;
        }
        Player spieler = ereignis.getPlayer();
        if (getConfig().getBoolean("pvp-pro-erlaubt", true) && spieler.hasPermission("glowcube.pvppro")) {
            spieler.sendPluginMessage(this, KANAL, schreiben("pvppro;an"));
            getLogger().info("PvP Pro fuer " + spieler.getName() + " freigegeben.");
        }
    }

    /** Der letzte Angreifer, wenn er hoechstens fuenf Sekunden her ist. */
    UUID letzterAngreifer(UUID spieler) {
        Long zeit = angriffZeit.get(spieler);
        if (zeit == null || System.currentTimeMillis() - zeit > 5000) {
            return null;
        }
        return angreifer.get(spieler);
    }

    // ---------------------------------------------------------------- Tick

    private void tick() {
        try {
            orbitalStrike.tick();
        } catch (RuntimeException fehler) {
            getLogger().warning("Orbital Strike: " + fehler);
        }
        for (Iterator<AgentArbeiter> it = agenten.iterator(); it.hasNext(); ) {
            AgentArbeiter agent = it.next();
            try {
                agent.tick();
            } catch (RuntimeException fehler) {
                getLogger().severe("Agent abgestuerzt - Beute wird uebergeben: " + fehler);
                fehler.printStackTrace();
                agent.notfallUebergabe();
            }
            if (agent.fertig()) {
                agent.aufraeumen();
                Player spieler = Bukkit.getPlayer(agent.besitzer());
                if (spieler != null) {
                    aus(spieler, agent.auftrag(), agent.nummer());
                }
                it.remove();
            }
        }
        if (++ticks % 10 == 0) {
            statusSenden();
        }
    }

    /** Die Agenten-Uebersicht: jedem Spieler mit Agenten den Stand seiner Agenten. */
    private void statusSenden() {
        Map<UUID, StringBuilder> je = new HashMap<>();
        for (AgentArbeiter agent : agenten) {
            org.bukkit.entity.Entity k = agent.koerper();
            if (k == null || !k.isValid()) {
                continue;
            }
            StringBuilder b = je.computeIfAbsent(agent.besitzer(), x -> new StringBuilder());
            if (b.length() > 0) {
                b.append('|');
            }
            org.bukkit.Location l = k.getLocation();
            b.append(agent.auftrag().name()).append(',').append(agent.nummer()).append(',')
                    .append(sauber(agent.titel())).append(',').append(sauber(agent.zustandText())).append(',')
                    .append(agent.beute()).append(',').append(l.getBlockX()).append(',').append(l.getBlockY())
                    .append(',').append(l.getBlockZ()).append(',').append(weltName(l.getWorld()));
        }
        for (UUID id : new java.util.ArrayList<>(mitStatus)) {
            Player spieler = Bukkit.getPlayer(id);
            StringBuilder b = je.get(id);
            if (spieler != null) {
                try {
                    spieler.sendPluginMessage(this, KANAL, schreiben("status;" + (b == null ? "" : b)));
                } catch (RuntimeException ohneClient) {
                    // Kein GlowCube-Client - keine Uebersicht.
                }
            }
            if (b == null) {
                mitStatus.remove(id);
            }
        }
    }

    private static String sauber(String text) {
        return text.replace(',', ' ').replace('|', ' ').replace(';', ' ');
    }

    /** "overworld", "the_nether", "the_end" - so nennt sie auch der Client. */
    private static String weltName(org.bukkit.World w) {
        if (w == null) {
            return "overworld";
        }
        return switch (w.getEnvironment()) {
            case NETHER -> "the_nether";
            case THE_END -> "the_end";
            default -> "overworld";
        };
    }

    // ------------------------------------------------------------- Kleinkram

    void melden(Player spieler, NamedTextColor farbe, String text) {
        spieler.sendMessage(Component.text("[Agent] " + text, farbe));
    }

    /** Dem Client sagen, dass der Agent weg ist - der Schalter im Menue geht aus. */
    private void aus(Player spieler, Auftrag auftrag, int nummer) {
        try {
            spieler.sendPluginMessage(this, KANAL, schreiben("aus;" + auftrag.name() + ";" + nummer));
        } catch (RuntimeException ohneClient) {
            // Kein GlowCube-Client - dann gibt es auch keinen Schalter.
        }
    }

    private org.bukkit.command.CommandSender stillerBefehlsgeber;

    /**
     * Ein Befehlsgeber mit Konsolenrechten, dessen Rueckmeldungen verschluckt
     * werden - sonst stuende fuer jede befuellte Truhe eine Zeile im Server-Log.
     */
    org.bukkit.command.CommandSender stillerBefehlsgeber() {
        if (stillerBefehlsgeber == null) {
            try {
                stillerBefehlsgeber = getServer().createCommandSender(rueckmeldung -> { });
            } catch (RuntimeException | LinkageError fehlt) {
                stillerBefehlsgeber = getServer().getConsoleSender();
            }
        }
        return stillerBefehlsgeber;
    }

    double maxTempo() {
        return getConfig().getDouble("max-tempo", 4.0);
    }

    double maxAbbauTempo() {
        return getConfig().getDouble("max-abbau-tempo", 20.0);
    }

    double maxBauTempo() {
        return getConfig().getDouble("max-bau-tempo", 100.0);
    }

    int maxTunnelLaenge() {
        return getConfig().getInt("max-tunnel-laenge", 512);
    }

    int maxXrayChunks() {
        return getConfig().getInt("max-xray-chunks", 6);
    }

    boolean xrayErlaubt() {
        return getConfig().getBoolean("xray-erlaubt", true);
    }

    /** UTF-8-Text mit VarInt-Laenge davor - so schreibt ihn Minecrafts STRING_UTF8. */
    static String lesen(byte[] daten) {
        int laenge = 0;
        int stelle = 0;
        int i = 0;
        while (true) {
            byte b = daten[i++];
            laenge |= (b & 0x7F) << stelle;
            if ((b & 0x80) == 0) {
                break;
            }
            stelle += 7;
            if (stelle > 28) {
                throw new IllegalArgumentException("VarInt zu lang");
            }
        }
        return new String(daten, i, laenge, StandardCharsets.UTF_8);
    }

    static byte[] schreiben(String text) {
        byte[] roh = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        int wert = roh.length;
        while ((wert & ~0x7F) != 0) {
            aus.write((wert & 0x7F) | 0x80);
            wert >>>= 7;
        }
        aus.write(wert);
        aus.writeBytes(roh);
        return aus.toByteArray();
    }
}
