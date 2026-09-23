package net.glowcube.plugin;

/**
 * Die Einstellungen eines Agenten, wie sie der Client schickt - auf die
 * Grenzen aus der config.yml gestutzt: Tempo, Abbau- bzw. Bau-Tempo, X-Ray,
 * X-Ray-Weite, Leuchten und die Zahl je Agent (Farm-Radius, Tunnel-Laenge,
 * Tiere uebrig lassen).
 */
record Werte(double tempo, double abbauTempo, boolean xray, int xrayChunks, boolean leuchten, int zahl) {
    static final Werte STANDARD = new Werte(1, 1, true, 3, false, 0);

    static Werte lesen(String[] t, int ab, GlowCubeAgentPlugin plugin, boolean bau) {
        try {
            double tempo = Math.max(1, Math.min(plugin.maxTempo(), Double.parseDouble(t[ab])));
            double abbau = Math.max(1, Math.min(bau ? plugin.maxBauTempo() : plugin.maxAbbauTempo(),
                    Double.parseDouble(t[ab + 1])));
            boolean xray = Boolean.parseBoolean(t[ab + 2]) && plugin.xrayErlaubt();
            int chunks = Math.max(1, Math.min(plugin.maxXrayChunks(), Integer.parseInt(t[ab + 3])));
            boolean leuchten = t.length > ab + 4 && Boolean.parseBoolean(t[ab + 4]);
            int zahl = t.length > ab + 5 ? Math.max(0, Math.min(plugin.maxTunnelLaenge(), Integer.parseInt(t[ab + 5]))) : 0;
            return new Werte(tempo, abbau, xray, chunks, leuchten, zahl);
        } catch (RuntimeException kaputt) {
            return STANDARD;
        }
    }
}
