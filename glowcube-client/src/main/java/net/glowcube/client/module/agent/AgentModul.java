package net.glowcube.client.module.agent;

import net.glowcube.client.agent.AgentSteuerung;
import net.glowcube.client.agent.AgentWerte;
import net.glowcube.client.agent.Auftrag;
import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;

/**
 * Gemeinsame Basis aller Agenten: Einschalten schickt so viele Agenten los,
 * wie unter "Anzahl" steht (1 bis 5), Ausschalten ruft alle zurueck - sie
 * kommen zu dir und werfen dir alles zu. Die eigentliche Arbeit laeuft auf
 * dem Server ({@link AgentSteuerung}): in der eigenen Welt direkt, auf einem
 * Server ueber das GlowCube-Plugin.
 *
 * <p><b>Jeder Agent einzeln:</b> unter "Einstellen fuer" waehlt man den
 * Agenten (1 bis Anzahl); darunter stehen dann nur seine Einstellungen - so
 * kann etwa Erz-Agent 1 Diamanten und Erz-Agent 2 Eisen suchen, mit eigenem
 * Tempo. Tempo, Abbau-Tempo, X-Ray und Leuchten gelten sofort auch fuer
 * laufende Agenten; Anzahl hochdrehen schickt weitere los, runterdrehen ruft
 * die ueberzaehligen zurueck.
 */
public abstract class AgentModul extends Module {
    public static final int MAX = 5;

    private final Auftrag auftrag;
    private final NumberSetting anzahl;
    private final ModeSetting auswahl;
    private final BooleanSetting leuchten;
    private final Slot[] slots = new Slot[MAX + 1];

    private final boolean[] aktiv = new boolean[MAX + 1];
    private final AgentWerte[] gesendet = new AgentWerte[MAX + 1];
    private int letzteAnzahl;

    /** Die Einstellungen eines einzelnen Agenten. */
    private static final class Slot {
        ModeSetting art;
        NumberSetting tempo;
        NumberSetting wert;
        BooleanSetting xray;
        NumberSetting xrayWeite;
        NumberSetting zahl;
    }

    protected AgentModul(Auftrag auftrag, String beschreibung) {
        super(auftrag.anzeigename(), beschreibung, Category.AGENT);
        this.auftrag = auftrag;
        anzahl = register(new NumberSetting("Anzahl",
                "Wie viele Agenten dieser Art losgeschickt werden", 1, 1, MAX, 1));
        String[] namen = new String[MAX];
        for (int i = 0; i < MAX; i++) {
            namen[i] = "Agent " + (i + 1);
        }
        auswahl = register(new ModeSetting("Einstellen fuer",
                "Welchen Agenten du darunter einstellst", namen[0], namen))
                .sichtbarWenn(() -> anzahl() > 1);
        leuchten = register(new BooleanSetting("Leuchten",
                "Die Agenten leuchten - ihr Umriss ist durch Waende zu sehen", false));
        for (int n = 1; n <= MAX; n++) {
            final int nr = n;
            java.util.function.BooleanSupplier sichtbar = () -> gewaehlt() == nr;
            String vor = "#" + n + " ";
            Slot s = new Slot();
            s.art = artEinstellung(vor);
            if (s.art != null) {
                register(s.art).sichtbarWenn(sichtbar);
            }
            s.tempo = register(new NumberSetting(vor + "Tempo",
                    "Wie schnell dieser Agent laeuft (1 = wie ein Spieler)", 2, 1, 4, 0.5)).sichtbarWenn(sichtbar);
            if (auftrag.baut()) {
                s.wert = register(new NumberSetting(vor + "Abbau-Tempo",
                        "Wie viel schneller er abbaut als mit Diamantwerkzeug", 3, 1, 20, 1)).sichtbarWenn(sichtbar);
                s.xray = register(new BooleanSetting(vor + "X-Ray",
                        "Sieht Zielbloecke durch Stein und geht direkt zu ihnen", true)).sichtbarWenn(sichtbar);
                s.xrayWeite = register(new NumberSetting(vor + "X-Ray-Weite",
                        "Wie weit X-Ray reicht, in Chunks um den Agenten", 3, 1, 6, 1)).sichtbarWenn(sichtbar);
            } else if (auftrag == Auftrag.BAUMEISTER) {
                s.wert = register(new NumberSetting(vor + "Bau-Tempo",
                        "Wie viele Bloecke er pro Sekunde setzt", 20, 1, 100, 1)).sichtbarWenn(sichtbar);
            } else if (auftrag == Auftrag.BAUER || auftrag == Auftrag.TUNNEL) {
                s.wert = register(new NumberSetting(vor + "Abbau-Tempo",
                        "Wie viel schneller er arbeitet", 3, 1, 20, 1)).sichtbarWenn(sichtbar);
            }
            s.zahl = zahlEinstellung(vor);
            if (s.zahl != null) {
                register(s.zahl).sichtbarWenn(sichtbar);
            }
            slots[n] = s;
        }
    }

    public Auftrag auftrag() {
        return auftrag;
    }

    /** Die Auswahl je Agent (Erzart, Ausruestung, Schematic ...) - oder null. */
    protected ModeSetting artEinstellung(String vor) {
        return null;
    }

    /** Die Zahl je Agent (Radius, Laenge ...) - oder null. */
    protected NumberSetting zahlEinstellung(String vor) {
        return null;
    }

    private int anzahl() {
        return anzahl.getInt();
    }

    /** Der Agent, dessen Einstellungen gerade zu sehen sind (1 bis Anzahl). */
    private int gewaehlt() {
        int i = auswahl.modes().indexOf(auswahl.get()) + 1;
        return Math.max(1, Math.min(i, anzahl()));
    }

    private String art(int n) {
        return slots[n].art != null ? slots[n].art.get() : "";
    }

    private AgentWerte werte(int n) {
        Slot s = slots[n];
        return new AgentWerte(s.tempo.get(),
                s.wert != null ? s.wert.get() : 1,
                s.xray != null && s.xray.get(),
                s.xrayWeite != null ? s.xrayWeite.getInt() : 1,
                leuchten.get(),
                s.zahl != null ? s.zahl.getInt() : 0);
    }

    private boolean losschicken(int n) {
        gesendet[n] = werte(n);
        aktiv[n] = AgentSteuerung.starten(auftrag, n, art(n), gesendet[n]);
        return aktiv[n];
    }

    @Override
    public void onEnable() {
        boolean einer = false;
        for (int n = 1; n <= anzahl(); n++) {
            einer |= losschicken(n);
        }
        letzteAnzahl = anzahl();
        if (!einer) {
            setEnabledSilently(false);
        }
    }

    @Override
    public void onDisable() {
        AgentSteuerung.zurueck(auftrag, 0);
        for (int n = 1; n <= MAX; n++) {
            aktiv[n] = false;
        }
    }

    /** Geaenderte Einstellungen und Anzahl an die laufenden Agenten weiterreichen. */
    @Override
    public void onTick() {
        int jetzt = anzahl();
        if (jetzt != letzteAnzahl) {
            for (int n = letzteAnzahl + 1; n <= jetzt; n++) {
                if (!aktiv[n]) {
                    losschicken(n);
                }
            }
            for (int n = jetzt + 1; n <= letzteAnzahl; n++) {
                if (aktiv[n]) {
                    AgentSteuerung.zurueck(auftrag, n);
                    aktiv[n] = false;
                }
            }
            letzteAnzahl = jetzt;
        }
        for (int n = 1; n <= MAX; n++) {
            if (!aktiv[n]) {
                continue;
            }
            AgentWerte neu = werte(n);
            if (!neu.equals(gesendet[n])) {
                gesendet[n] = neu;
                AgentSteuerung.einstellen(auftrag, n, neu);
            }
        }
    }

    /** Der Server meldet: Agent Nummer n ist fertig. Sind alle weg, geht das Modul aus. */
    public void agentBeendet(int n) {
        if (n >= 1 && n <= MAX) {
            aktiv[n] = false;
        }
        for (int i = 1; i <= MAX; i++) {
            if (aktiv[i]) {
                return;
            }
        }
        setEnabledSilently(false);
    }

    @Override
    public String hudSuffix() {
        int laufend = 0;
        for (int i = 1; i <= MAX; i++) {
            if (aktiv[i]) {
                laufend++;
            }
        }
        return laufend > 1 ? "x" + laufend : null;
    }

    @Override
    public boolean bleibtNachWeltwechsel() {
        return false;
    }
}
