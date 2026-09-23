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
 * {@code start;AUFTRAG;art;tempo;abbau;xray;chunks}, {@code werte;...},
 * {@code zurueck;AUFTRAG}, {@code alle}. Zurueck geht {@code aus;AUFTRAG},
 * wenn ein Agent fertig ist - dann springt der Schalter im Menue um.
 */
public final class GlowCubeAgentPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    static final String KANAL = "glowcube:agent";

    private final List<AgentArbeiter> agenten = new ArrayList<>();
    /** Wer welchen Spieler zuletzt getroffen hat (fuer den Guardian): Spieler -> Angreifer, Zeitpunkt. */
    private final Map<UUID, UUID> angreifer = new HashMap<>();
    private final Map<UUID, Long> angriffZeit = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerIncomingPluginChannel(this, KANAL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, KANAL);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getServer().getPluginManager().registerEvents(this, this);
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
                if (t.length >= 7) {
                    Auftrag a = auftrag(t[1]);
                    starten(spieler, a, t[2], Werte.lesen(t, 3, this, a == Auftrag.BAUMEISTER));
                }
            }
            case "werte" -> {
                if (t.length >= 6) {
                    Auftrag a = auftrag(t[1]);
                    Werte w = Werte.lesen(t, 2, this, a == Auftrag.BAUMEISTER);
                    for (AgentArbeiter agent : agenten) {
                        if (agent.besitzer().equals(spieler.getUniqueId()) && agent.auftrag() == a) {
                            agent.einstellen(w);
                        }
                    }
                }
            }
            case "zurueck" -> {
                if (t.length >= 2) {
                    zurueck(spieler.getUniqueId(), auftrag(t[1]));
                }
            }
            case "alle" -> {
                for (Auftrag a : Auftrag.values()) {
                    zurueck(spieler.getUniqueId(), a);
                }
            }
            default -> {
            }
        }
    }

    private static Auftrag auftrag(String name) {
        try {
            return Auftrag.valueOf(name);
        } catch (IllegalArgumentException falsch) {
            return Auftrag.ERZ;
        }
    }

    private void starten(Player spieler, Auftrag auftrag, String art, Werte werte) {
        if (!spieler.hasPermission("glowcube.agent")) {
            melden(spieler, NamedTextColor.RED, "Du darfst hier keine Agenten losschicken.");
            aus(spieler, auftrag);
            return;
        }
        int eigene = 0;
        for (AgentArbeiter agent : agenten) {
            if (!agent.besitzer().equals(spieler.getUniqueId())) {
                continue;
            }
            if (agent.auftrag() == auftrag && !agent.beimZurueckkehren()) {
                melden(spieler, NamedTextColor.YELLOW, auftrag.anzeigename + " ist schon unterwegs.");
                return;
            }
            eigene++;
        }
        if (eigene >= getConfig().getInt("max-agenten-je-spieler", 5)) {
            melden(spieler, NamedTextColor.RED, "Du hast schon genug Agenten unterwegs.");
            aus(spieler, auftrag);
            return;
        }
        AgentArbeiter agent = switch (auftrag) {
            case WAECHTER -> Waechter.erschaffen(this, spieler, art, werte);
            case BAUMEISTER -> Baumeister.erschaffen(this, spieler, art, werte);
            default -> Agent.erschaffen(this, spieler, auftrag, art, werte);
        };
        if (agent == null) {
            melden(spieler, NamedTextColor.RED, "Der Agent konnte nicht erscheinen.");
            aus(spieler, auftrag);
            return;
        }
        agenten.add(agent);
        melden(spieler, NamedTextColor.AQUA, agent.titel() + " ist losgezogen.");
    }

    private void zurueck(UUID besitzer, Auftrag auftrag) {
        for (AgentArbeiter agent : agenten) {
            if (agent.besitzer().equals(besitzer) && agent.auftrag() == auftrag) {
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
                    aus(spieler, agent.auftrag());
                }
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------- Kleinkram

    void melden(Player spieler, NamedTextColor farbe, String text) {
        spieler.sendMessage(Component.text("[Agent] " + text, farbe));
    }

    /** Dem Client sagen, dass der Agent weg ist - der Schalter im Menue geht aus. */
    private void aus(Player spieler, Auftrag auftrag) {
        try {
            spieler.sendPluginMessage(this, KANAL, schreiben("aus;" + auftrag.name()));
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
