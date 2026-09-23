package net.glowcube.plugin;

/** Tempo, Abbau-Tempo und X-Ray, wie sie der Client schickt - auf die Grenzen aus der config.yml gestutzt. */
record Werte(double tempo, double abbauTempo, boolean xray, int xrayChunks) {
    static final Werte STANDARD = new Werte(1, 1, true, 3);

    static Werte lesen(String[] t, int ab, GlowCubeAgentPlugin plugin, boolean bau) {
        try {
            double tempo = Math.max(1, Math.min(plugin.maxTempo(), Double.parseDouble(t[ab])));
            double abbau = Math.max(1, Math.min(bau ? plugin.maxBauTempo() : plugin.maxAbbauTempo(),
                    Double.parseDouble(t[ab + 1])));
            boolean xray = Boolean.parseBoolean(t[ab + 2]) && plugin.xrayErlaubt();
            int chunks = Math.max(1, Math.min(plugin.maxXrayChunks(), Integer.parseInt(t[ab + 3])));
            return new Werte(tempo, abbau, xray, chunks);
        } catch (RuntimeException kaputt) {
            return STANDARD;
        }
    }
}
